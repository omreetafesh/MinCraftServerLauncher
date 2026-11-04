package main;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/**
 * Reads a Minecraft world's region files (.mca) and renders a top-down map: one 512×512 tile per
 * region, one pixel per block, colored by block and biome with map-style hill shading.
 * Only ever reads the world — it never writes to the server folder. Rendered tiles are cached under
 * %LOCALAPPDATA% and re-rendered when their region file changes.
 */
final class WorldMap {

    private WorldMap() {}

    enum Dim {
        OVERWORLD("Overworld", "minecraft:overworld", "overworld", "", "", ""),
        NETHER("Nether", "minecraft:the_nether", "the_nether", "DIM-1", "_nether", "DIM-1"),
        END("The End", "minecraft:the_end", "the_end", "DIM1", "_the_end", "DIM1");

        final String label, id;
        private final String folder26, legacyDim, paperSuffix, paperDim;
        Dim(String label, String id, String folder26, String legacyDim, String paperSuffix, String paperDim) {
            this.label = label; this.id = id; this.folder26 = folder26;
            this.legacyDim = legacyDim; this.paperSuffix = paperSuffix; this.paperDim = paperDim;
        }

        static Dim fromId(String id) {
            for (Dim d : values()) if (d.id.equals(id)) return d;
            return null;
        }

        /** The region folder: Minecraft 26+ (dimensions/…), older vanilla (DIM-1…) or Paper (world_nether/…). */
        File regionDir(File serverDir, String level) {
            File world = new File(serverDir, level);
            List<File> candidates = new ArrayList<>();
            candidates.add(new File(world, "dimensions/minecraft/" + folder26 + "/region"));
            candidates.add(new File(world, legacyDim.isEmpty() ? "region" : legacyDim + "/region"));
            if (!paperSuffix.isEmpty())
                candidates.add(new File(serverDir, level + paperSuffix + "/" + paperDim + "/region"));
            for (File f : candidates) {
                String[] mca = f.list((d, n) -> n.endsWith(".mca"));
                if (mca != null && mca.length > 0) return f;
            }
            return null;
        }
    }

    static final int SIZE = 512;           // blocks (= pixels) per region side
    static final int LOW = 4;              // the low-res copy keeps 1 pixel per LOW×LOW blocks
    static final int LOW_SIZE = SIZE / LOW;

    /** A rendered region: full-res pixels (may be null once dropped) and the always-kept low-res copy. */
    record Tile(int rx, int rz, long sourceModified, int[] full, int[] low) {}

    // ─── Region files ────────────────────────────────────────────────

    /** "r.-1.2.mca" → {-1, 2}, or null. */
    static int[] regionCoords(String fileName) {
        String[] p = fileName.split("\\.");
        if (p.length != 4 || !p[0].equals("r") || !p[3].equals("mca")) return null;
        try { return new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])}; }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * A region kept in memory, so that when the server saves it again only the chunks that changed
     * are redrawn (a few milliseconds) instead of all 1024 (a few hundred).
     */
    static final class Region {
        final int[] color = new int[SIZE * SIZE];    // before hill shading
        final int[] height = new int[SIZE * SIZE];
        final long[] stamp = new long[1024];          // each chunk's header entry (location + save time); 0 = none
        final boolean[] drawn = new boolean[1024];
        boolean incomplete;                           // a chunk was caught mid-write; it's re-read on the next update
        int retries;                                  // used by MapView to re-check such a region a few times

        Region() { Arrays.fill(height, Integer.MIN_VALUE); }

        boolean any() {
            for (boolean b : drawn) if (b) return true;
            return false;
        }

        /** The finished pixels (shaded), or null if nothing is drawn. */
        int[] pixels() {
            if (!any()) return null;
            int[] out = color.clone();
            shade(out, height);
            return out;
        }
    }

    /** Renders one region file; null if it holds no finished chunks. */
    static int[] renderRegion(File mca, Dim dim) throws IOException {
        Region r = new Region();
        update(r, mca, dim);
        return r.pixels();
    }

    /**
     * Redraws the chunks whose header entry changed since the last call (the server stores each
     * re-saved chunk in new sectors and stamps its save time, so the entry changes on every save).
     * Returns true if any chunk was redrawn.
     */
    static boolean update(Region r, File mca, Dim dim) throws IOException {
        boolean changed = false;
        r.incomplete = false;
        byte[] header = new byte[8192];
        try (RandomAccessFile f = new RandomAccessFile(mca, "r")) {
            if (f.length() < 8192) return false;
            f.readFully(header);
            ByteBuffer h = ByteBuffer.wrap(header);
            for (int i = 0; i < 1024; i++) {
                int loc = h.getInt(i * 4), time = h.getInt(4096 + i * 4);
                long stamp = loc == 0 ? 0 : ((long) loc << 32) | (time & 0xFFFFFFFFL);
                if (stamp == r.stamp[i]) continue;
                int cx = i & 31, cz = i >> 5;
                clearChunk(r, cx, cz);
                r.drawn[i] = false;
                if (loc != 0) {
                    try {
                        Map<String, Object> chunk = readChunk(f, loc, mca.getParentFile(), cx, cz);
                        if (chunk != null) r.drawn[i] = renderChunk(chunk, dim, r.color, r.height, cx * 16, cz * 16);
                    } catch (IOException | RuntimeException e) {
                        stamp = 0; // caught mid-write, or undecodable: try it again next time
                        r.incomplete = true;
                    }
                }
                r.stamp[i] = stamp;
                changed = true;
            }
        }
        return changed;
    }

    private static void clearChunk(Region r, int cx, int cz) {
        for (int z = 0; z < 16; z++) {
            int row = (cz * 16 + z) * SIZE + cx * 16;
            Arrays.fill(r.color, row, row + 16, 0);
            Arrays.fill(r.height, row, row + 16, Integer.MIN_VALUE);
        }
    }

    private static Map<String, Object> readChunk(RandomAccessFile f, int loc, File dir, int cx, int cz) throws IOException {
        long offset = (long) (loc >>> 8) * 4096;
        if (offset + 5 > f.length()) return null;
        f.seek(offset);
        int length = f.readInt();
        int type = f.readUnsignedByte();
        InputStream raw;
        if ((type & 128) != 0) {
            // Oversized chunk, stored next to the region file as c.<x>.<z>.mcc
            File ext = findExternal(dir, cx, cz);
            if (ext == null) return null;
            raw = new ByteArrayInputStream(Files.readAllBytes(ext.toPath()));
            type &= 127;
        } else {
            if (length <= 1 || length > 1 << 24) return null;
            byte[] buf = new byte[length - 1];
            f.readFully(buf);
            raw = new ByteArrayInputStream(buf);
        }
        InputStream in = switch (type) {
            case 1 -> new GZIPInputStream(raw);
            case 2 -> new InflaterInputStream(raw);
            case 3 -> raw;
            default -> null; // 4 = LZ4 (opt-in server setting) — not supported
        };
        if (in == null) return null;
        DataInputStream d = new DataInputStream(new BufferedInputStream(in, 16384));
        if (d.readByte() != 10) return null;
        d.readUTF();
        return Nbt.compound(d);
    }

    /** External chunk files are named after absolute chunk coords; we only know the region-relative ones. */
    private static File findExternal(File dir, int cx, int cz) {
        String[] files = dir.list((d, n) -> n.startsWith("c.") && n.endsWith(".mcc"));
        if (files == null) return null;
        for (String n : files) {
            String[] p = n.split("\\.");
            try {
                if (p.length == 4 && Math.floorMod(Integer.parseInt(p[1]), 32) == cx
                        && Math.floorMod(Integer.parseInt(p[2]), 32) == cz) return new File(dir, n);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    // ─── Chunk → pixels ──────────────────────────────────────────────

    /** One 16×16×16 section: block info per palette entry, packed indices, biome tints. */
    private static final class Section {
        int[] blocks;          // palette → Blocks info
        long[] data;           // packed palette indices (null = single entry)
        int bits;
        String[] biomes;       // biome palette
        long[] biomeData;
        int biomeBits;
        int[] decoded;         // lazily unpacked indices

        int block(int x, int y, int z) {
            if (data == null) return blocks[0];
            if (decoded == null) decoded = unpack(data, bits, 4096);
            int i = decoded[(y << 8) | (z << 4) | x];
            return i < blocks.length ? blocks[i] : Blocks.AIR;
        }

        String biome(int x, int y, int z) {
            if (biomes == null || biomes.length == 0) return null;
            if (biomeData == null || biomes.length == 1) return biomes[0];
            int idx = ((y >> 2) << 4) | ((z >> 2) << 2) | (x >> 2);
            int per = 64 / biomeBits;
            int v = (int) ((biomeData[idx / per] >>> ((idx % per) * biomeBits)) & ((1L << biomeBits) - 1));
            return v < biomes.length ? biomes[v] : biomes[0];
        }
    }

    static int[] unpack(long[] data, int bits, int count) {
        int[] out = new int[count];
        int per = 64 / bits;
        long mask = (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            int li = i / per;
            if (li >= data.length) break;
            out[i] = (int) ((data[li] >>> ((i % per) * bits)) & mask);
        }
        return out;
    }

    private static int bitsFor(int paletteSize, int min) {
        int b = 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1));
        return Math.max(min, b);
    }

    @SuppressWarnings("unchecked")
    private static boolean renderChunk(Map<String, Object> chunk, Dim dim, int[] color, int[] height, int ox, int oz) {
        Object status = chunk.get("Status");
        if (status instanceof String s && !s.equals("minecraft:full") && !s.equals("full")) return false;
        if (!(chunk.get("sections") instanceof List<?> list) || list.isEmpty()) return false;

        // Index sections by Y
        int minSec = Integer.MAX_VALUE, maxSec = Integer.MIN_VALUE;
        Map<Integer, Section> byY = new HashMap<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Section s = toSection((Map<String, Object>) m);
            if (s == null || !(m.get("Y") instanceof Number y)) continue;
            byY.put(y.intValue(), s);
            minSec = Math.min(minSec, y.intValue());
            maxSec = Math.max(maxSec, y.intValue());
        }
        if (byY.isEmpty()) return false;
        Section[] secs = new Section[maxSec - minSec + 1];
        for (var e : byY.entrySet()) secs[e.getKey() - minSec] = e.getValue();
        int minY = minSec * 16, maxY = maxSec * 16 + 15;

        // The Nether's bedrock roof hides everything: start below it and look through the first cave
        boolean nether = dim == Dim.NETHER;
        int startY = nether ? Math.min(maxY, 117) : maxY;

        boolean drew = false;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = startY, b = Blocks.AIR;
                if (nether) {
                    // Skip solid rock until an air gap, then take the first block below it
                    while (y >= minY && !Blocks.isClear(at(secs, minSec, x, y, z))) y--;
                    if (y < minY) { y = startY; b = at(secs, minSec, x, y, z); }
                }
                if (b == Blocks.AIR) {
                    for (; y >= minY; y--) {
                        int sec = Math.floorDiv(y, 16) - minSec;
                        Section s = secs[sec];
                        if (s == null || (s.data == null && Blocks.isClear(s.blocks[0]))) { y = (sec + minSec) * 16; continue; }
                        b = s.block(x, y & 15, z);
                        if (!Blocks.isClear(b)) break;
                    }
                }
                if (y < minY || Blocks.isClear(b)) continue;

                Section top = secs[Math.floorDiv(y, 16) - minSec];
                String biome = top.biome(x, y & 15, z);
                int rgb;
                if (Blocks.isWater(b)) {
                    // Look through the water: deeper = darker, the floor shows through shallows
                    int depth = 0, floor = Blocks.AIR, yy = y;
                    while (yy >= minY && depth < 24) {
                        int bb = at(secs, minSec, x, yy, z);
                        if (!Blocks.isWater(bb) && !Blocks.isClear(bb)) { floor = bb; break; }
                        yy--; depth++;
                    }
                    int water = Biomes.water(biome);
                    int under = floor == Blocks.AIR ? water : Blocks.color(floor, biome);
                    double t = Math.min(0.92, 0.58 + depth * 0.045);
                    rgb = darken(mix(under, water, t), 1.0 - Math.min(0.35, depth * 0.018));
                } else {
                    rgb = Blocks.color(b, biome);
                }
                int i = (oz + z) * SIZE + ox + x;
                color[i] = 0xFF000000 | rgb;
                height[i] = Blocks.isWater(b) ? Integer.MAX_VALUE - 1 : y; // water stays flat
                drew = true;
            }
        }
        return drew;
    }

    private static int at(Section[] secs, int minSec, int x, int y, int z) {
        int sec = Math.floorDiv(y, 16) - minSec;
        if (sec < 0 || sec >= secs.length || secs[sec] == null) return Blocks.AIR;
        return secs[sec].block(x, y & 15, z);
    }

    @SuppressWarnings("unchecked")
    private static Section toSection(Map<String, Object> m) {
        if (!(m.get("block_states") instanceof Map<?, ?> bs)) return null;
        if (!(bs.get("palette") instanceof List<?> pal) || pal.isEmpty()) return null;
        Section s = new Section();
        s.blocks = new int[pal.size()];
        for (int i = 0; i < pal.size(); i++) s.blocks[i] = Blocks.info(blockName(pal.get(i)));
        if (pal.size() > 1 && bs.get("data") instanceof long[] data) {
            s.data = data;
            s.bits = bitsFor(pal.size(), 4);
        }
        if (m.get("biomes") instanceof Map<?, ?> bi && bi.get("palette") instanceof List<?> bp) {
            s.biomes = new String[bp.size()];
            for (int i = 0; i < bp.size(); i++) s.biomes[i] = blockName(bp.get(i));
            if (bp.size() > 1 && bi.get("data") instanceof long[] bd) {
                s.biomeData = bd;
                s.biomeBits = bitsFor(bp.size(), 1);
            }
        }
        return s;
    }

    /**
     * A palette entry: a plain string (26+), a string wrapped in {"": …} (26+ lists that mix
     * strings and compounds), {id, properties} (26+) or {Name, Properties} (1.13–1.21).
     */
    private static String blockName(Object o) {
        if (o instanceof String s) return s;
        if (o instanceof Map<?, ?> m) {
            for (String k : new String[]{"Name", "id", ""})
                if (m.get(k) instanceof String s) return s;
        }
        return "minecraft:air";
    }

    /** Map-style relief: a block higher than its northern neighbour is lit, lower is shadowed. */
    private static void shade(int[] color, int[] height) {
        for (int z = SIZE - 1; z >= 0; z--) {
            for (int x = 0; x < SIZE; x++) {
                int i = z * SIZE + x;
                if (color[i] == 0) continue;
                int h = height[i];
                if (h == Integer.MAX_VALUE - 1) continue; // water
                int hn = z > 0 && height[i - SIZE] != Integer.MIN_VALUE ? height[i - SIZE] : h;
                if (hn == Integer.MAX_VALUE - 1) hn = h;
                int hw = x > 0 && height[i - 1] != Integer.MIN_VALUE ? height[i - 1] : h;
                if (hw == Integer.MAX_VALUE - 1) hw = h;
                double d = (h - hn) * 0.7 + (h - hw) * 0.3;
                double f = d > 0 ? 1 + Math.min(0.16, d * 0.05) : 1 - Math.min(0.24, -d * 0.06);
                color[i] = 0xFF000000 | darken(color[i] & 0xFFFFFF, f);
            }
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int darken(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** Averages LOW×LOW blocks into one pixel (only over drawn blocks, so coastlines stay sharp). */
    static int[] downsample(int[] full) {
        int[] low = new int[LOW_SIZE * LOW_SIZE];
        for (int lz = 0; lz < LOW_SIZE; lz++) {
            for (int lx = 0; lx < LOW_SIZE; lx++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int dz = 0; dz < LOW; dz++) {
                    for (int dx = 0; dx < LOW; dx++) {
                        int c = full[(lz * LOW + dz) * SIZE + lx * LOW + dx];
                        if (c == 0) continue;
                        r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
                    }
                }
                if (n > 0) low[lz * LOW_SIZE + lx] = 0xFF000000 | (r / n << 16) | (g / n << 8) | (b / n);
            }
        }
        return low;
    }

    // ─── Tile cache ──────────────────────────────────────────────────

    private static final int CACHE_MAGIC = 0x4D41504B, CACHE_VERSION = 2;

    /** %LOCALAPPDATA%\MinecraftServerLauncher\map-cache\<hash of the region folder> */
    static File cacheDir(File regionDir) {
        String base = System.getenv("LOCALAPPDATA");
        File root = base != null ? new File(base, "MinecraftServerLauncher")
                                 : new File(System.getProperty("user.home"), ".minecraft-server-launcher");
        String key = Integer.toHexString(regionDir.getAbsolutePath().toLowerCase().hashCode());
        return new File(root, "map-cache/" + key);
    }

    /** Reads a cached tile's header and low-res pixels; null if missing, stale or unreadable. */
    static Tile readCachedLow(File cacheFile, int rx, int rz, long sourceModified) {
        if (!cacheFile.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {
            if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_VERSION) return null;
            long mod = in.readLong();
            if (mod != sourceModified) return null;
            boolean empty = in.readBoolean();
            if (empty) return new Tile(rx, rz, mod, null, null);
            int lowLen = in.readInt();
            byte[] lowZ = in.readNBytes(lowLen);
            return new Tile(rx, rz, mod, null, inflate(lowZ, LOW_SIZE * LOW_SIZE));
        } catch (IOException e) {
            return null;
        }
    }

    /** Full-res pixels from a cached tile, or null. */
    static int[] readCachedFull(File cacheFile) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {
            if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_VERSION) return null;
            in.readLong();
            if (in.readBoolean()) return null;
            in.skipNBytes(in.readInt());
            int fullLen = in.readInt();
            return inflate(in.readNBytes(fullLen), SIZE * SIZE);
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes a tile (full == null marks a region with nothing to draw). Written to .part, then moved. */
    static void writeCache(File cacheFile, long sourceModified, int[] full) {
        File part = new File(cacheFile.getPath() + ".part");
        try {
            cacheFile.getParentFile().mkdirs();
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(part)))) {
                out.writeInt(CACHE_MAGIC);
                out.writeInt(CACHE_VERSION);
                out.writeLong(sourceModified);
                out.writeBoolean(full == null);
                if (full != null) {
                    byte[] low = deflate(downsample(full)), hi = deflate(full);
                    out.writeInt(low.length); out.write(low);
                    out.writeInt(hi.length);  out.write(hi);
                }
            }
            Files.move(part.toPath(), cacheFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            part.delete(); // the map still works, it just renders again next time
        }
    }

    private static byte[] deflate(int[] px) {
        ByteBuffer bb = ByteBuffer.allocate(px.length * 4);
        bb.asIntBuffer().put(px);
        Deflater def = new Deflater(Deflater.BEST_SPEED);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(px.length);
        try (DeflaterOutputStream out = new DeflaterOutputStream(bos, def)) {
            out.write(bb.array());
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        } finally {
            def.end();
        }
        return bos.toByteArray();
    }

    private static int[] inflate(byte[] z, int count) throws IOException {
        byte[] raw;
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(z))) {
            raw = in.readAllBytes();
        }
        if (raw.length != count * 4) throw new IOException("bad tile size");
        int[] px = new int[count];
        ByteBuffer.wrap(raw).asIntBuffer().get(px);
        return px;
    }

    // ─── NBT ─────────────────────────────────────────────────────────

    /** A minimal NBT reader. Skips the bulky parts the map never uses (light, block entities…). */
    static final class Nbt {
        private static final Set<String> SKIP = Set.of(
            "BlockLight", "SkyLight", "block_entities", "Heightmaps", "PostProcessing", "block_ticks",
            "fluid_ticks", "structures", "CarvingMasks", "Lights", "blending_data", "UpgradeData",
            "LiquidsToBeTicked", "ToBeTicked", "Entities", "TileEntities", "TileTicks", "LiquidTicks");

        static Map<String, Object> compound(DataInputStream d) throws IOException {
            Map<String, Object> m = new HashMap<>();
            for (int t; (t = d.readByte()) != 0; ) {
                String name = d.readUTF();
                if (SKIP.contains(name)) skip(t, d);
                else m.put(name, value(t, d));
            }
            return m;
        }

        static Object value(int t, DataInputStream d) throws IOException {
            return switch (t) {
                case 1 -> d.readByte();
                case 2 -> d.readShort();
                case 3 -> d.readInt();
                case 4 -> d.readLong();
                case 5 -> d.readFloat();
                case 6 -> d.readDouble();
                case 7 -> { byte[] b = new byte[d.readInt()]; d.readFully(b); yield b; }
                case 8 -> d.readUTF();
                case 9 -> {
                    int et = d.readByte(), n = d.readInt();
                    List<Object> l = new ArrayList<>(Math.max(0, n));
                    for (int i = 0; i < n; i++) l.add(value(et, d));
                    yield l;
                }
                case 10 -> compound(d);
                case 11 -> { int[] a = new int[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readInt(); yield a; }
                case 12 -> { long[] a = new long[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readLong(); yield a; }
                default -> throw new IOException("bad NBT tag " + t);
            };
        }

        static void skip(int t, DataInputStream d) throws IOException {
            switch (t) {
                case 1 -> d.skipNBytes(1);
                case 2 -> d.skipNBytes(2);
                case 3, 5 -> d.skipNBytes(4);
                case 4, 6 -> d.skipNBytes(8);
                case 7 -> d.skipNBytes(d.readInt());
                case 8 -> d.skipNBytes(d.readUnsignedShort());
                case 9 -> { int et = d.readByte(), n = d.readInt(); for (int i = 0; i < n; i++) skip(et, d); }
                case 10 -> { for (int ct; (ct = d.readByte()) != 0; ) { d.skipNBytes(d.readUnsignedShort()); skip(ct, d); } }
                case 11 -> d.skipNBytes(d.readInt() * 4L);
                case 12 -> d.skipNBytes(d.readInt() * 8L);
                default -> throw new IOException("bad NBT tag " + t);
            }
        }
    }

    // ─── Block colors ────────────────────────────────────────────────

    /**
     * Each block name maps to an int: 0xRRGGBB plus flags in the top byte. Colors are close to
     * Minecraft's own map colors; grass, leaves and water take their biome's tint.
     */
    static final class Blocks {
        static final int AIR = 1 << 24, WATER = 2 << 24, GRASS = 3 << 24, FOLIAGE = 4 << 24;
        private static final int SOLID = 0;
        private static final Map<String, Integer> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

        static boolean isClear(int info) { return info == AIR; }
        static boolean isWater(int info) { return (info & 0xFF000000) == WATER; }

        static int color(int info, String biome) {
            int kind = info & 0xFF000000, rgb = info & 0xFFFFFF;
            if (kind == GRASS)   return rgb == 0 ? Biomes.grass(biome) : darken(Biomes.grass(biome), rgb / 100.0);
            if (kind == FOLIAGE) return rgb == 0 ? Biomes.foliage(biome) : darken(Biomes.foliage(biome), rgb / 100.0);
            if (kind == WATER)   return Biomes.water(biome);
            return rgb;
        }

        static int info(String fullName) {
            return CACHE.computeIfAbsent(fullName, Blocks::lookup);
        }

        private static final String[] DYES = {
            "light_blue", "light_gray", "white", "orange", "magenta", "yellow", "lime", "pink", "gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black"};
        private static final int[] DYE_RGB = {
            0x3AAFD9, 0x8E8E86, 0xE9ECEC, 0xF07613, 0xBD44B3, 0xF8C527, 0x70B919, 0xED8DAC, 0x3E4447,
            0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA12722, 0x141519};

        private static final String[] WOODS = {
            "dark_oak", "pale_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry",
            "bamboo", "crimson", "warped"};
        private static final int[] PLANKS = {
            0x42291A, 0xE3D9D9, 0xA2834F, 0x735531, 0xC0AF79, 0xA0734D, 0xA85A32, 0x763631, 0xE3B3AD,
            0xC2AF52, 0x653147, 0x2B6963};
        private static final int[] BARK = {
            0x3C2E1A, 0x4C433E, 0x6B5432, 0x3B2812, 0xD8D7D2, 0x56441B, 0x676157, 0x544328, 0x371C23,
            0x5B8C2A, 0x5C1A1E, 0x3A3A4D};

        private static final Map<String, Integer> EXACT = new HashMap<>();
        private static void put(int rgb, String... names) { for (String n : names) EXACT.put(n, rgb); }
        static {
            put(AIR, "air", "cave_air", "void_air", "light", "barrier", "structure_void", "glow_lichen",
                "cobweb", "string", "tripwire", "tripwire_hook", "ladder", "iron_bars", "chain", "fire",
                "soul_fire", "redstone_wire", "lever", "flower_pot", "end_rod", "lightning_rod", "scaffolding",
                "sculk_vein", "resin_clump", "firefly_bush", "leaf_litter");
            put(WATER, "water", "bubble_column", "kelp", "kelp_plant", "seagrass", "tall_seagrass");
            put(GRASS, "grass_block");
            put(GRASS | 88, "short_grass", "grass", "tall_grass", "fern", "large_fern", "bush");
            put(FOLIAGE, "oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves");
            put(FOLIAGE | 85, "vine");
            put(0x80A755, "birch_leaves");          put(0x619961, "spruce_leaves");
            put(0xE8B4CF, "cherry_leaves");         put(0x5E7D2E, "azalea_leaves", "azalea");
            put(0x6E8A3A, "flowering_azalea_leaves", "flowering_azalea"); put(0x7D8478, "pale_oak_leaves");
            put(0x208030, "lily_pad");              put(0xD45A12, "lava");
            put(0x91B7FD, "ice", "frosted_ice");    put(0x8DB4FA, "packed_ice"); put(0x74A7FD, "blue_ice");
            put(0xF5F9FB, "snow", "snow_block", "powder_snow");
            put(0xDBD3A0, "sand", "suspicious_sand"); put(0xBE6621, "red_sand");
            put(0x837E7E, "gravel", "suspicious_gravel"); put(0x866043, "dirt", "coarse_dirt", "rooted_dirt");
            put(0x5B3F18, "podzol");                put(0x6F6265, "mycelium"); put(0x3C3A3D, "mud");
            put(0xA0A6B3, "clay");                  put(0x8F6744, "farmland"); put(0x947A41, "dirt_path");
            put(0x7D7D7D, "stone", "smooth_stone", "infested_stone");
            put(0x9A6A57, "granite", "polished_granite"); put(0xBDBDBD, "diorite", "polished_diorite");
            put(0x888888, "andesite", "polished_andesite"); put(0x7A7A7A, "cobblestone");
            put(0x505055, "deepslate", "cobbled_deepslate", "basalt", "polished_basalt", "smooth_basalt");
            put(0x6C6D66, "tuff");                  put(0xDFE0DC, "calcite");
            put(0x596E2D, "moss_block", "moss_carpet"); put(0xA8AFA3, "pale_moss_block", "pale_moss_carpet");
            put(0x555555, "bedrock");               put(0x14121E, "obsidian", "crying_obsidian");
            put(0x6F3634, "netherrack");            put(0x7B0B0B, "nether_wart_block");
            put(0x167E86, "warped_wart_block");     put(0x831F1F, "crimson_nylium");
            put(0x2B6963, "warped_nylium");         put(0x51402F, "soul_sand", "soul_soil");
            put(0x2A2328, "blackstone", "gilded_blackstone"); put(0x8E3F1F, "magma_block");
            put(0xFBDA74, "glowstone");             put(0x2C1518, "nether_bricks");
            put(0xF09A4B, "shroomlight");           put(0x5E4A44, "ancient_debris");
            put(0xDBDEA0, "end_stone");             put(0xA97CA9, "purpur_block", "purpur_pillar");
            put(0x5E3F5E, "chorus_plant", "chorus_flower");
            put(0x92C066, "sugar_cane");            put(0x5A7E2B, "cactus");
            put(0xC57718, "pumpkin", "carved_pumpkin", "jack_o_lantern"); put(0x7FA02A, "melon");
            put(0xA88F0F, "hay_block");             put(0x5B8C2A, "bamboo");
            put(0xF2D72E, "dandelion");             put(0xC0221E, "poppy"); put(0x2AA6D9, "blue_orchid");
            put(0xB06CD9, "allium");                put(0xE0E4EF, "azure_bluet", "white_tulip");
            put(0xC23A2A, "red_tulip");             put(0xE47A26, "orange_tulip"); put(0xE8A2C6, "pink_tulip");
            put(0xE5E5DD, "oxeye_daisy");           put(0x4F6FD8, "cornflower"); put(0xEDEDED, "lily_of_the_valley");
            put(0xF3C72C, "sunflower");             put(0xC48FC3, "lilac"); put(0xA4262E, "rose_bush");
            put(0xE5B6D6, "peony");                 put(0x262626, "wither_rose"); put(0xF0A9CA, "pink_petals");
            put(0x957051, "brown_mushroom_block");  put(0xC72D2B, "red_mushroom_block");
            put(0xCBC4B9, "mushroom_stem");         put(0x966557, "bricks", "brick_stairs", "brick_slab", "brick_wall");
            put(0x985E43, "terracotta");            put(0xC8DCE0, "glass", "glass_pane", "tinted_glass");
            put(0x0D1D24, "sculk", "sculk_sensor", "sculk_shrieker", "sculk_catalyst");
            put(0x8562BF, "amethyst_block", "budding_amethyst", "amethyst_cluster");
            put(0x866B5C, "dripstone_block", "pointed_dripstone"); put(0x4A3B27, "mangrove_roots", "muddy_mangrove_roots");
            put(0xECE6DF, "quartz_block", "smooth_quartz", "quartz_pillar", "quartz_bricks");
            put(0x5DA196, "prismarine");            put(0x6FB6A8, "prismarine_bricks"); put(0x3A5B4F, "dark_prismarine");
            put(0x7FB238, "slime_block");           put(0xE5A02B, "honey_block", "honeycomb_block");
            put(0x5C9E31, "wheat", "carrots", "potatoes", "beetroots", "sweet_berry_bush", "torchflower_crop",
                "pitcher_crop", "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling",
                "acacia_sapling", "dark_oak_sapling", "cherry_sapling", "mangrove_propagule", "dead_bush",
                "big_dripleaf", "small_dripleaf", "spore_blossom", "hanging_roots", "cave_vines", "cave_vines_plant");
        }

        private static int lookup(String full) {
            String n = full.startsWith("minecraft:") ? full.substring(10) : full;
            Integer exact = EXACT.get(n);
            if (exact != null) return exact;

            // Small things you'd see through from above
            if (n.contains("torch") || n.contains("rail") || n.contains("button") || n.contains("pressure_plate")
                    || n.endsWith("_sign") || n.endsWith("_banner") || n.endsWith("_head") || n.endsWith("_skull")
                    || n.contains("candle") || n.endsWith("_pane") && !n.contains("stained") || n.endsWith("_bars")
                    || n.contains("lantern") || n.endsWith("_chain") || n.contains("potted_") || n.endsWith("_button"))
                return AIR;

            if (n.contains("ore")) {
                if (n.startsWith("deepslate")) return 0x505055;
                if (n.startsWith("nether")) return 0x6F3634;
                return 0x7D7D7D;
            }
            if (n.contains("redstone")) return 0xA21412;   // before the "stone" rules below
            if (n.contains("copper")) {
                if (n.contains("oxidized")) return 0x52A38A;
                if (n.contains("weathered")) return 0x6CA087;
                if (n.contains("exposed")) return 0xA17E68;
                return 0xC06B4F;
            }
            for (int i = 0; i < DYES.length; i++) {
                if (!n.startsWith(DYES[i] + "_")) continue;
                if (n.contains("terracotta")) return mix(DYE_RGB[i], 0x985E43, 0.55);
                if (n.contains("glass")) return mix(DYE_RGB[i], 0xC8DCE0, 0.35);
                return DYE_RGB[i];   // wool, concrete, carpet, beds, shulker boxes…
            }
            for (int i = 0; i < WOODS.length; i++) {
                if (!n.contains(WOODS[i])) continue;
                if (n.contains("leaves")) return FOLIAGE;
                if (n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_stem") || n.endsWith("_hyphae")
                        || n.contains("stripped")) return BARK[i];
                return PLANKS[i];  // planks, stairs, slabs, fences, doors…
            }
            if (n.contains("deepslate")) return 0x505055;
            if (n.contains("blackstone")) return 0x2A2328;
            if (n.contains("nether_brick")) return 0x2C1518;
            if (n.contains("end_stone")) return 0xDBDEA0;
            if (n.contains("purpur")) return 0xA97CA9;
            if (n.contains("quartz")) return 0xECE6DF;
            if (n.contains("prismarine")) return 0x5DA196;
            if (n.contains("red_sandstone")) return 0xBE6621;
            if (n.contains("sandstone")) return 0xDBD3A0;
            if (n.contains("mossy")) return 0x6E7A5A;
            if (n.contains("brick")) return n.contains("mud") ? 0x89684F : n.contains("stone") ? 0x7A7A7A : 0x966557;
            if (n.contains("granite")) return 0x9A6A57;
            if (n.contains("diorite")) return 0xBDBDBD;
            if (n.contains("andesite")) return 0x888888;
            if (n.contains("tuff")) return 0x6C6D66;
            if (n.contains("cobblestone") || n.contains("stone")) return 0x7D7D7D;
            if (n.contains("coral")) return n.contains("dead") ? 0x857F78 : 0xC8508A;
            if (n.contains("mushroom") || n.contains("fungus")) return 0x957051;
            if (n.contains("tulip") || n.contains("flower") || n.contains("orchid")) return 0xD07AA0;
            if (n.contains("iron")) return 0xA7A7A7;
            if (n.contains("gold")) return 0xF9D849;
            if (n.contains("diamond")) return 0x5CDBD5;
            if (n.contains("emerald")) return 0x17DD62;
            if (n.contains("lapis")) return 0x1F4FA6;
            if (n.contains("redstone")) return 0xA21412;
            if (n.contains("snow")) return 0xF5F9FB;
            if (n.contains("ice")) return 0x91B7FD;
            if (n.contains("sculk")) return 0x0D1D24;
            if (n.contains("moss")) return 0x596E2D;
            return 0x8A8A8A;
        }
    }

    // ─── Biome tints ─────────────────────────────────────────────────

    static final class Biomes {
        private record Tint(int grass, int foliage, int water) {}
        private static final Tint DEFAULT = new Tint(0x91BD59, 0x77AB2F, 0x3F76E4);
        private static final Map<String, Tint> TINTS = new HashMap<>();
        private static void put(Tint t, String... ids) { for (String id : ids) TINTS.put("minecraft:" + id, t); }
        static {
            put(new Tint(0x79C05A, 0x59AE30, 0x3F76E4), "forest", "flower_forest");
            put(new Tint(0x88BB67, 0x6BA941, 0x3F76E4), "birch_forest", "old_growth_birch_forest");
            put(new Tint(0x507A32, 0x59AE30, 0x3F76E4), "dark_forest");
            put(new Tint(0x86B783, 0x68A464, 0x3F76E4), "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga");
            put(new Tint(0x80B497, 0x60A17B, 0x3D57D6), "snowy_plains", "snowy_taiga", "ice_spikes", "grove",
                "snowy_slopes", "jagged_peaks", "frozen_peaks", "snowy_beach");
            put(new Tint(0x8AB689, 0x6DA36B, 0x3F76E4), "windswept_hills", "windswept_forest",
                "windswept_gravelly_hills", "stony_shore");
            put(new Tint(0x59C93C, 0x30BB0B, 0x3F76E4), "jungle", "bamboo_jungle");
            put(new Tint(0x64C73F, 0x3EB80F, 0x3F76E4), "sparse_jungle");
            put(new Tint(0xBFB755, 0xAEA42A, 0x3F76E4), "savanna", "savanna_plateau", "windswept_savanna", "desert");
            put(new Tint(0x90814D, 0x9E814D, 0x3F76E4), "badlands", "eroded_badlands", "wooded_badlands");
            put(new Tint(0x6A7039, 0x6A7039, 0x617B64), "swamp");
            put(new Tint(0x6A7039, 0x8DB127, 0x3A7A6A), "mangrove_swamp");
            put(new Tint(0x55C93F, 0x2BBB0F, 0x3F76E4), "mushroom_fields");
            put(new Tint(0xB6DB61, 0xB6DB61, 0x5DB7EF), "cherry_grove");
            put(new Tint(0x83BB6D, 0x63A948, 0x0E4ECF), "meadow");
            put(new Tint(0x9ABE4B, 0x77AB2F, 0x3F76E4), "stony_peaks");
            put(new Tint(0x778272, 0x878D76, 0x76889D), "pale_garden");
            put(new Tint(0x8EB971, 0x71A74D, 0x43D5EE), "warm_ocean");
            put(new Tint(0x8EB971, 0x71A74D, 0x45ADF2), "lukewarm_ocean", "deep_lukewarm_ocean");
            put(new Tint(0x8EB971, 0x71A74D, 0x3D57D6), "cold_ocean", "deep_cold_ocean");
            put(new Tint(0x80B497, 0x60A17B, 0x3938C9), "frozen_ocean", "deep_frozen_ocean", "frozen_river");
        }
        private static Tint of(String biome) { return biome == null ? DEFAULT : TINTS.getOrDefault(biome, DEFAULT); }
        static int grass(String biome)   { return of(biome).grass; }
        static int foliage(String biome) { return of(biome).foliage; }
        static int water(String biome)   { return of(biome).water; }
    }
}
