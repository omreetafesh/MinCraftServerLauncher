package main;

import javafx.animation.AnimationTimer;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * The Map page: a pannable, zoomable top-down map of the server's world (rendered by
 * {@link WorldMap} on background threads) with live player markers on top.
 * Drag to move, scroll to zoom, double-click to zoom in.
 */
final class MapView extends StackPane {

    private static final double MIN_SCALE = 1 / 16.0, MAX_SCALE = 8;   // screen pixels per block
    private static final double FULL_RES_FROM = 0.35;                    // below this the low-res tiles suffice
    private static final int    MAX_FULL_TILES = 30;
    private Color bg = Theme.DEFAULT.mapBackground(), grid = Theme.DEFAULT.mapGrid();   // follow the theme

    private final Canvas canvas = new Canvas();
    private final Pane markerLayer = new Pane();
    private final ToggleGroup dimGroup = new ToggleGroup();
    private final Label coords = new Label(), progressText = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final HBox progressBox = new HBox(8, progress, progressText);
    private final VBox emptyBox = new VBox(8);
    private final Label emptyTitle = new Label(), emptySub = new Label();
    private final Function<String, Image> heads;

    // View: block at the centre of the canvas, and zoom. Targets are eased towards each frame.
    private double centerX, centerZ, scale = 0.5;
    private double targetX, targetZ, targetScale = 0.5;
    private double anchorWX = Double.NaN, anchorWZ, anchorSX, anchorSY;   // zoom keeps this point under the cursor
    private double dragX, dragY;
    private boolean dirty = true;

    // World source
    private File serverDir;
    private String level = "world";
    private WorldMap.Dim dim = WorldMap.Dim.OVERWORLD;
    private File regionDir;
    private volatile int generation;   // bumped when the world/dimension changes, so stale renders are dropped

    private static final class Entry {
        int rx, rz; long modified; boolean empty;
        WritableImage low, full;
        boolean fullRequested;
    }
    private final Map<Long, Entry> tiles = new HashMap<>();
    private final Set<Long> rendering = new HashSet<>();   // regions queued or being drawn right now
    // Regions that changed while on screen stay in memory, so the next save redraws only its chunks
    private final Map<Long, WorldMap.Region> live = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, WorldMap.Region> e) { return size() > 12; }
    });
    private final LinkedHashMap<Long, Entry> fullLru = new LinkedHashMap<>(16, 0.75f, true);
    private int queued, done;

    private final ThreadPoolExecutor renderPool = pool("map-render",
        Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 4)));
    private final ThreadPoolExecutor ioPool = pool("map-io", 1);

    // Players: where they are, and where their marker is drawn (eased towards it)
    // Positions arrive about twice a second. Markers replay them slightly in the past, along a smooth
    // curve through the recent samples, so they move continuously instead of hopping (the way games
    // show other players). A jump longer than this between two samples is a real teleport: snap.
    private static final double TELEPORT_BLOCKS = 120;
    private static final int    MAX_SAMPLES = 6;

    private static final class Marker {
        WorldMap.Dim dim; double x, z, shownX, shownZ; boolean placed;
        final ArrayDeque<double[]> samples = new ArrayDeque<>();   // {seconds, x, z}, oldest first
        double interval = 0.5;                                      // typical seconds between samples
        double playT = Double.NaN;                                  // the moment of the path being shown
        Node node; Animation ripple; Tooltip tip;
    }
    private boolean active;
    private final Map<String, Marker> players = new LinkedHashMap<>();

    private final AnimationTimer timer = new AnimationTimer() {
        private long last;
        @Override public void handle(long now) {
            double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
            last = now;
            tick(dt, now / 1e9);
        }
        @Override public void stop() { super.stop(); last = 0; }
    };

    MapView(Function<String, Image> heads) {
        this.heads = heads;
        getStyleClass().add("map-view");
        setMinSize(0, 0);

        Pane canvasHolder = new Pane(canvas);
        canvasHolder.setMinSize(0, 0);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> dirty = true);
        canvas.heightProperty().addListener((o, a, b) -> dirty = true);
        markerLayer.setPickOnBounds(false);
        markerLayer.setMinSize(0, 0);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(26); clip.setArcHeight(26);
        setClip(clip);

        getChildren().addAll(canvasHolder, markerLayer, buildToolbar(), buildZoomBar(), buildStatusBar(), buildEmpty());
        installMouse(canvasHolder);
    }

    private static ThreadPoolExecutor pool(String name, int threads) {
        ThreadPoolExecutor p = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);   // never compete with the Minecraft server
                return t;
            });
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    // ─── Chrome ──────────────────────────────────────────────────────

    private Node buildToolbar() {
        HBox bar = new HBox(2);
        bar.getStyleClass().add("map-toolbar");
        for (WorldMap.Dim d : WorldMap.Dim.values()) {
            ToggleButton b = new ToggleButton(d.label);
            b.getStyleClass().add("map-seg");
            b.setToggleGroup(dimGroup);
            b.setUserData(d);
            b.setSelected(d == dim);
            b.setOnAction(e -> {
                b.setSelected(true);   // can't un-select the dimension you're on
                setDimension(d);
            });
            bar.getChildren().add(b);
        }
        bar.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        StackPane.setAlignment(bar, Pos.TOP_LEFT);
        StackPane.setMargin(bar, new Insets(12));
        return bar;
    }

    private Node buildZoomBar() {
        Button in = iconButton("plus", "Zoom in", () -> zoomBy(2, getWidth() / 2, getHeight() / 2));
        Button out = iconButton("minus", "Zoom out", () -> zoomBy(0.5, getWidth() / 2, getHeight() / 2));
        Button find = iconButton("locate", "Show all players", this::fitPlayers);
        Button refresh = iconButton("refresh", "Check for newly explored areas", this::rescan);
        VBox bar = new VBox(2, in, out, new Separator(), find, refresh);
        bar.getStyleClass().add("map-toolbar");
        bar.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        StackPane.setAlignment(bar, Pos.TOP_RIGHT);
        StackPane.setMargin(bar, new Insets(12));
        return bar;
    }

    private static Button iconButton(String icon, String tip, Runnable action) {
        Button b = new Button(null, new Icon(icon, 16));
        b.getStyleClass().addAll("icon-button", "map-button");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Node buildStatusBar() {
        coords.getStyleClass().add("map-coords");
        progress.getStyleClass().add("map-progress");
        progress.setPrefWidth(90);
        progressText.getStyleClass().add("map-progress-text");
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setVisible(false);
        progressBox.setManaged(false);
        HBox bar = new HBox(14, coords, progressBox);
        bar.getStyleClass().add("map-status");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        bar.setMouseTransparent(true);
        StackPane.setAlignment(bar, Pos.BOTTOM_LEFT);
        StackPane.setMargin(bar, new Insets(12));
        coords.setText("Move the mouse over the map");
        return bar;
    }

    private Node buildEmpty() {
        emptyTitle.getStyleClass().add("empty-title");
        emptySub.getStyleClass().add("empty-sub");
        emptySub.setWrapText(true);
        emptySub.setMaxWidth(320);
        emptyBox.getChildren().addAll(new Icon("map", 34), emptyTitle, emptySub);
        emptyBox.getStyleClass().add("empty-state");
        emptyBox.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        emptyBox.setMouseTransparent(true);
        emptyBox.setVisible(false);
        return emptyBox;
    }

    private void showEmpty(String title, String sub) {
        emptyTitle.setText(title);
        emptySub.setText(sub);
        emptyBox.setVisible(title != null);
    }

    // ─── Mouse ───────────────────────────────────────────────────────

    private void installMouse(Pane target) {
        target.setOnMousePressed(e -> {
            dragX = e.getX(); dragY = e.getY();
            anchorWX = Double.NaN;
            target.setCursor(javafx.scene.Cursor.CLOSED_HAND);
        });
        target.setOnMouseReleased(e -> target.setCursor(javafx.scene.Cursor.DEFAULT));
        target.setOnMouseDragged(e -> {
            double dx = (e.getX() - dragX) / scale, dz = (e.getY() - dragY) / scale;
            dragX = e.getX(); dragY = e.getY();
            centerX -= dx; centerZ -= dz;
            targetX = centerX; targetZ = centerZ;
            targetScale = scale;
            dirty = true;
            updateCoords(e.getX(), e.getY());
        });
        target.setOnMouseMoved(e -> updateCoords(e.getX(), e.getY()));
        target.setOnMouseExited(e -> coords.setText(dim.label));
        target.setOnScroll(e -> {
            if (e.getDeltaY() == 0) return;
            zoomBy(Math.pow(1.0018, e.getDeltaY()), e.getX(), e.getY());
        });
        target.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) zoomBy(2, e.getX(), e.getY());
        });
    }

    private void zoomBy(double factor, double sx, double sy) {
        double newTarget = clamp(targetScale * factor, MIN_SCALE, MAX_SCALE);
        if (newTarget == targetScale) return;
        anchorWX = worldX(sx); anchorWZ = worldZ(sy);
        anchorSX = sx; anchorSY = sy;
        targetScale = newTarget;
        // Where the centre ends up once the zoom settles (the anchor stays put)
        targetX = anchorWX - (sx - getWidth() / 2) / targetScale;
        targetZ = anchorWZ - (sy - getHeight() / 2) / targetScale;
    }

    private void updateCoords(double sx, double sy) {
        coords.setText(String.format("X %,d   Z %,d", (long) Math.floor(worldX(sx)), (long) Math.floor(worldZ(sy))));
    }

    private double worldX(double sx) { return centerX + (sx - getWidth() / 2) / scale; }
    private double worldZ(double sy) { return centerZ + (sy - getHeight() / 2) / scale; }
    private double screenX(double wx) { return (wx - centerX) * scale + getWidth() / 2; }
    private double screenY(double wz) { return (wz - centerZ) * scale + getHeight() / 2; }

    // ─── Lifecycle ───────────────────────────────────────────────────

    /** Colors of the unexplored background and the grid (the rest of the map is styled by CSS). */
    void setTheme(Theme t) {
        bg = t.mapBackground();
        grid = t.mapGrid();
        dirty = true;
    }

    /** The page became visible / hidden: animate and poll only while it's on screen. */
    void setActive(boolean active) {
        this.active = active;
        if (active) {
            timer.start();
            dirty = true;
            for (Marker m : players.values()) if (m.ripple != null) m.ripple.play();
        } else {
            timer.stop();
            stopWatching();
            for (Marker m : players.values()) if (m.ripple != null) m.ripple.stop();
        }
    }

    /** Points the map at a server folder; re-scans only if it changed. */
    void setWorld(File serverDir, String level) {
        if (Objects.equals(serverDir, this.serverDir) && Objects.equals(level, this.level) && regionDir != null) {
            rescan();
            return;
        }
        this.serverDir = serverDir;
        this.level = level;
        reload(true);
    }

    WorldMap.Dim dimension() { return dim; }

    void setDimension(WorldMap.Dim d) {
        if (d == dim) return;
        dim = d;
        for (Toggle t : dimGroup.getToggles()) t.setSelected(t.getUserData() == d);
        coords.setText(d.label);
        reload(true);
        refreshMarkers();
    }

    private void reload(boolean recenter) {
        generation++;
        renderPool.getQueue().clear();
        ioPool.getQueue().clear();
        tiles.clear();
        rendering.clear();
        live.clear();
        fullLru.clear();
        queued = done = 0;
        regionDir = serverDir == null ? null : dim.regionDir(serverDir, level);
        if (recenter) {
            // Start where the players are, else at 0,0
            Marker first = players.values().stream().filter(m -> m.dim == dim).findFirst().orElse(null);
            centerX = targetX = first != null ? first.x : 0;
            centerZ = targetZ = first != null ? first.z : 0;
            anchorWX = Double.NaN;
        }
        rescan();
        dirty = true;
    }

    /** Looks for new or changed region files and renders them (cheap when nothing changed). */
    void rescan() {
        if (serverDir == null) {
            showEmpty("No server folder", "Choose your server folder in Settings and its world will show up here.");
            return;
        }
        if (regionDir == null) regionDir = dim.regionDir(serverDir, level);
        if (regionDir == null) {
            showEmpty(dim == WorldMap.Dim.OVERWORLD ? "No world yet" : "Nobody has been to " + dim.label + " yet",
                dim == WorldMap.Dim.OVERWORLD
                    ? "Start the server once to create the world, then come back here."
                    : "It shows up here once a player has explored it and the server has saved.");
            return;
        }
        showEmpty(null, null);
        if (active) watch(regionDir);
        File[] files = regionDir.listFiles((d, n) -> n.endsWith(".mca"));
        if (files == null) return;

        // Nearest to what you're looking at first
        double cx = centerX / WorldMap.SIZE, cz = centerZ / WorldMap.SIZE;
        List<File> todo = new ArrayList<>();
        for (File f : files) {
            int[] rc = WorldMap.regionCoords(f.getName());
            if (rc == null) continue;
            long k = key(rc[0], rc[1]);
            if (rendering.contains(k)) continue;   // already on its way (a change meanwhile is caught when it lands)
            Entry e = tiles.get(k);
            if (e == null || e.modified != f.lastModified()) todo.add(f);
        }
        todo.sort(Comparator.comparingDouble(f -> {
            int[] rc = WorldMap.regionCoords(f.getName());
            return Math.hypot(rc[0] + 0.5 - cx, rc[1] + 0.5 - cz);
        }));
        if (todo.isEmpty()) return;
        if (renderPool.getQueue().isEmpty() && done >= queued) queued = done = 0;
        queued += todo.size();
        updateProgress();

        int gen = generation;
        File cache = WorldMap.cacheDir(regionDir);
        WorldMap.Dim d = dim;
        for (File f : todo) {
            int[] rc = WorldMap.regionCoords(f.getName());
            long k = key(rc[0], rc[1]);
            boolean changedOnScreen = tiles.containsKey(k);   // already drawn, and the server saved it again
            rendering.add(k);
            renderPool.execute(() -> {
                if (gen != generation) return;
                long mod = f.lastModified();
                File cacheFile = new File(cache, "r." + rc[0] + "." + rc[1] + ".bin");
                WorldMap.Region kept = live.get(k);
                WorldMap.Tile t = kept != null || changedOnScreen ? null : WorldMap.readCachedLow(cacheFile, rc[0], rc[1], mod);
                WorldMap.Region r = kept;
                if (t == null) {
                    // A region seen changing is kept in memory: then only its re-saved chunks are redrawn
                    if (r == null) r = new WorldMap.Region();
                    try { WorldMap.update(r, f, d); } catch (Exception ex) { r.incomplete = true; }
                    // Being built on (or a chunk was caught mid-write): keep it for quick updates
                    if (changedOnScreen || r.incomplete) live.put(k, r);
                    int[] full = r.pixels();
                    // The disk cache is written on a full draw; a half-read region is cached as stale
                    if (kept == null) WorldMap.writeCache(cacheFile, r.incomplete ? 0 : mod, full);
                    t = new WorldMap.Tile(rc[0], rc[1], mod, full, full == null ? null : WorldMap.downsample(full));
                }
                WorldMap.Tile tile = t;
                WorldMap.Region region = r;
                Platform.runLater(() -> {
                    if (gen != generation) return;
                    rendering.remove(k);
                    accept(tile);
                    done++;
                    updateProgress();
                    if (f.lastModified() != tile.sourceModified()) {
                        rescan(); // the server saved this region again while we were drawing it
                    } else if (region != null && region.incomplete && region.retries++ < 4) {
                        // A chunk was mid-write: read just that chunk again in a moment
                        javafx.animation.PauseTransition later = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
                        later.setOnFinished(e -> {
                            Entry en = tiles.get(k);
                            if (en != null && gen == generation) { en.modified = -1; rescan(); }
                        });
                        later.play();
                    } else if (region != null && !region.incomplete) {
                        region.retries = 0;
                    }
                });
            });
        }
    }

    private void updateProgress() {
        // Only for real work: a region or two redrawn after a save shouldn't flash a progress bar
        boolean busy = queued >= 3 && done < queued;
        progressBox.setVisible(busy);
        progressBox.setManaged(busy);
        if (busy) {
            progress.setProgress((double) done / queued);
            progressText.setText("Drawing map · " + done + " of " + queued);
        }
    }

    private void accept(WorldMap.Tile t) {
        long k = key(t.rx(), t.rz());
        Entry e = new Entry();
        e.rx = t.rx(); e.rz = t.rz(); e.modified = t.sourceModified();
        e.empty = t.low() == null;
        fullLru.remove(k);   // a re-render replaces the old tile
        if (!e.empty) {
            e.low = image(t.low(), WorldMap.LOW_SIZE);
            if (t.full() != null) {
                e.full = image(t.full(), WorldMap.SIZE);
                rememberFull(k, e);
            }
        }
        tiles.put(k, e);
        dirty = true;
    }

    private void rememberFull(long k, Entry e) {
        fullLru.put(k, e);
        while (fullLru.size() > MAX_FULL_TILES) {
            Iterator<Map.Entry<Long, Entry>> it = fullLru.entrySet().iterator();
            Entry old = it.next().getValue();
            it.remove();
            old.full = null;
            old.fullRequested = false;
        }
    }

    private void requestFull(long k, Entry e) {
        if (e.fullRequested || regionDir == null) return;
        e.fullRequested = true;
        int gen = generation;
        File cacheFile = new File(WorldMap.cacheDir(regionDir), "r." + e.rx + "." + e.rz + ".bin");
        ioPool.execute(() -> {
            int[] px = gen == generation ? WorldMap.readCachedFull(cacheFile) : null;
            if (px == null) return;
            Platform.runLater(() -> {
                if (gen != generation || tiles.get(k) != e) return;
                e.full = image(px, WorldMap.SIZE);
                rememberFull(k, e);
                dirty = true;
            });
        });
    }

    private static WritableImage image(int[] px, int size) {
        WritableImage img = new WritableImage(size, size);
        img.getPixelWriter().setPixels(0, 0, size, size, PixelFormat.getIntArgbInstance(), px, 0, size);
        return img;
    }

    private static long key(int rx, int rz) { return ((long) rx << 32) | (rz & 0xFFFFFFFFL); }

    // ─── Live updates ────────────────────────────────────────────────
    // The server writes a changed chunk to its region file within about 0.1 s (but the same chunk at
    // most once per 10 s — the launcher's save-all covers that, see LauncherController). Windows sends
    // no change notification for a file the server keeps open, so the map checks the files' modified
    // times itself, four times a second (about a millisecond of work), and redraws what changed.

    private static final long WATCH_EVERY_MS = 250;
    private volatile Thread watcher;
    private File watchedDir;
    private volatile long lastChange = Long.MIN_VALUE / 2;   // System.nanoTime() when a region file last changed

    /** Seconds since the world on screen last changed on disk (huge if it hasn't while watched). */
    double secondsSinceChange() { return (System.nanoTime() - lastChange) / 1e9; }

    private void watch(File dir) {
        if (watcher != null && dir.equals(watchedDir)) return;
        stopWatching();
        watchedDir = dir;
        Thread t = new Thread(() -> watchLoop(dir), "map-watch");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        watcher = t;
        t.start();
    }

    private void watchLoop(File dir) {
        Map<String, Long> seen = new HashMap<>();
        boolean first = true;
        while (watcher == Thread.currentThread()) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".mca"));
            boolean changed = false;
            if (files != null) {
                for (File f : files) {
                    long mod = f.lastModified();
                    Long old = seen.put(f.getName(), mod);
                    if (old == null ? !first : old != mod) changed = true;
                }
            }
            first = false;
            if (changed) {
                lastChange = System.nanoTime();
                Platform.runLater(this::rescan);
            }
            try { Thread.sleep(WATCH_EVERY_MS); } catch (InterruptedException e) { return; }
        }
    }

    private void stopWatching() {
        Thread t = watcher;
        watcher = null;
        watchedDir = null;
        if (t != null) t.interrupt();
    }

    // ─── Players ─────────────────────────────────────────────────────

    /** A player's latest position (from the server). */
    void updatePlayer(String name, WorldMap.Dim d, double x, double z) {
        Marker m = players.computeIfAbsent(name, n -> createMarker(n));
        boolean firstFix = !m.placed;
        double now = System.nanoTime() / 1e9;
        boolean jump = firstFix || d != m.dim || Math.hypot(x - m.x, z - m.z) > TELEPORT_BLOCKS;
        if (jump) {
            m.samples.clear();
            m.playT = Double.NaN;
            m.shownX = x; m.shownZ = z;
        } else if (!m.samples.isEmpty()) {
            double gap = now - m.samples.peekLast()[0];
            if (gap > 0.1 && gap < 5) m.interval += (gap - m.interval) * 0.3;
        }
        m.samples.addLast(new double[]{now, x, z});
        while (m.samples.size() > MAX_SAMPLES) m.samples.removeFirst();
        m.dim = d; m.x = x; m.z = z;
        m.placed = true;
        refreshMarkers();
        // First fix ever and the map is still at the default spot: go to the player
        if (firstFix && d == dim && players.values().stream().filter(p -> p.placed).count() == 1
                && centerX == 0 && centerZ == 0) {
            centerX = targetX = x; centerZ = targetZ = z;
            dirty = true;
        }
    }

    /** Drops markers of players who left. */
    void retainPlayers(Collection<String> online) {
        players.entrySet().removeIf(en -> {
            if (online.contains(en.getKey())) return false;
            Marker m = en.getValue();
            if (m.ripple != null) m.ripple.stop();
            markerLayer.getChildren().remove(m.node);
            return true;
        });
    }

    /** Centres the map on a player (switching dimension if needed). */
    void focus(String name) {
        Marker m = players.get(name);
        if (m == null || !m.placed || m.dim == null) return;
        if (m.dim != dim) setDimension(m.dim);
        anchorWX = Double.NaN;
        targetX = m.x; targetZ = m.z;
        targetScale = Math.max(targetScale, 2);
    }

    /** Zooms to fit every player in this dimension. */
    private void fitPlayers() {
        List<Marker> here = players.values().stream().filter(m -> m.placed && m.dim == dim).toList();
        if (here.isEmpty()) {
            // Maybe everyone is in another dimension
            players.values().stream().filter(m -> m.placed && m.dim != null).findFirst().ifPresent(m -> focus(name(m)));
            return;
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Marker m : here) {
            minX = Math.min(minX, m.x); maxX = Math.max(maxX, m.x);
            minZ = Math.min(minZ, m.z); maxZ = Math.max(maxZ, m.z);
        }
        anchorWX = Double.NaN;
        targetX = (minX + maxX) / 2; targetZ = (minZ + maxZ) / 2;
        double span = Math.max(maxX - minX, maxZ - minZ) + 160;
        targetScale = clamp(Math.min(getWidth(), getHeight()) * 0.8 / span, MIN_SCALE, 2);
    }

    private String name(Marker m) {
        for (var en : players.entrySet()) if (en.getValue() == m) return en.getKey();
        return null;
    }

    private Marker createMarker(String name) {
        Marker m = new Marker();
        Region ring = new Region();
        ring.getStyleClass().add("map-marker-ring");
        ring.setOpacity(0);
        ImageView head = new ImageView(heads.apply(name));
        head.setFitWidth(26); head.setFitHeight(26);
        head.setSmooth(false);
        Rectangle clip = new Rectangle(26, 26);
        clip.setArcWidth(8); clip.setArcHeight(8);
        head.setClip(clip);
        StackPane face = new StackPane(head);
        face.getStyleClass().add("map-marker-head");
        face.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);   // hug the head, don't stretch to the name's width
        StackPane pin = new StackPane(ring, face);
        Label label = new Label(name);
        label.getStyleClass().add("map-marker-name");
        label.setMinWidth(USE_PREF_SIZE);                 // never "…"
        VBox box = new VBox(3, pin, label);
        box.setFillWidth(false);
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().add("map-marker");
        box.setOnMouseClicked(e -> focus(name));
        m.tip = new Tooltip(name);
        Tooltip.install(box, m.tip);
        box.setManaged(false);
        m.node = box;
        m.ripple = Motion.ripple(ring);
        markerLayer.getChildren().add(box);
        if (active) m.ripple.play();
        return m;
    }

    private void refreshMarkers() {
        for (var en : players.entrySet()) {
            Marker m = en.getValue();
            boolean show = m.placed && m.dim == dim;
            m.node.setVisible(show);
            if (show) m.tip.setText(String.format("%s%nX %,d   Z %,d", en.getKey(), (long) m.x, (long) m.z));
        }
        dirty = true;
    }

    // ─── Frame ───────────────────────────────────────────────────────

    private void tick(double dt, double now) {
        double k = 1 - Math.exp(-dt * 12);
        if (Math.abs(targetScale - scale) > scale * 1e-4) {
            scale = Math.exp(Math.log(scale) + (Math.log(targetScale) - Math.log(scale)) * k);
            if (!Double.isNaN(anchorWX)) {
                // Keep the point under the cursor fixed while zooming
                centerX = anchorWX - (anchorSX - getWidth() / 2) / scale;
                centerZ = anchorWZ - (anchorSY - getHeight() / 2) / scale;
            }
            dirty = true;
        } else if (scale != targetScale) {
            scale = targetScale;
            dirty = true;
        }
        if (Double.isNaN(anchorWX) || scale == targetScale) {
            if (Math.abs(targetX - centerX) * scale > 0.05 || Math.abs(targetZ - centerZ) * scale > 0.05) {
                centerX += (targetX - centerX) * k;
                centerZ += (targetZ - centerZ) * k;
                dirty = true;
            } else if (centerX != targetX || centerZ != targetZ) {
                centerX = targetX; centerZ = targetZ;
                dirty = true;
            }
            if (scale == targetScale) anchorWX = Double.NaN;
        }

        // Markers replay their recent path two updates behind (plus room for a late one), so the stretch
        // being drawn already has a point after it and a new update never bends it mid-way. The playback
        // clock runs at real-time speed and only eases ±15% faster or slower to keep that distance.
        for (Marker m : players.values()) {
            if (!m.placed || m.samples.isEmpty()) continue;
            double target = now - m.interval * 2 - 0.15;
            if (Double.isNaN(m.playT) || Math.abs(target - m.playT) > 2) m.playT = target;
            else m.playT += dt * (1 + clamp((target - m.playT) * 0.5, -0.15, 0.15));
            double[] p = pathAt(m, m.playT);
            if (Math.abs(p[0] - m.shownX) > 1e-4 || Math.abs(p[1] - m.shownZ) > 1e-4) {
                m.shownX = p[0];
                m.shownZ = p[1];
                dirty = true;
            }
        }

        if (dirty) {
            dirty = false;
            draw();
        }
    }

    /**
     * Where a marker is at time t: a smooth curve through the samples around t whose velocity at each
     * sample comes from its neighbours and their real timing (so uneven update gaps don't change the
     * speed), holding at the ends when t is outside the recorded path.
     */
    private static double[] pathAt(Marker m, double t) {
        double[][] s = m.samples.toArray(new double[0][]);
        int n = s.length;
        if (n == 1 || t <= s[0][0]) return new double[]{s[0][1], s[0][2]};
        if (t >= s[n - 1][0]) return new double[]{s[n - 1][1], s[n - 1][2]};
        int i = 0;
        while (i < n - 2 && t >= s[i + 1][0]) i++;
        double[] a = s[Math.max(0, i - 1)], p1 = s[i], p2 = s[i + 1], b = s[Math.min(n - 1, i + 2)];
        double h = Math.max(1e-6, p2[0] - p1[0]), u = (t - p1[0]) / h;
        double[] out = new double[2];
        for (int k = 1; k <= 2; k++) {
            double v1 = (p2[k] - a[k]) / Math.max(1e-6, p2[0] - a[0]);   // blocks per second at p1
            double v2 = (b[k] - p1[k]) / Math.max(1e-6, b[0] - p1[0]);   // … and at p2
            out[k - 1] = hermite(p1[k], v1 * h, p2[k], v2 * h, u);
        }
        return out;
    }

    private static double hermite(double p1, double m1, double p2, double m2, double u) {
        double u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * p1 + (u3 - 2 * u2 + u) * m1 + (-2 * u3 + 3 * u2) * p2 + (u3 - u2) * m2;
    }

    private void draw() {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(bg);
        g.fillRect(0, 0, w, h);

        double regionPx = WorldMap.SIZE * scale;
        int rx0 = (int) Math.floor(worldX(0) / WorldMap.SIZE), rx1 = (int) Math.floor(worldX(w) / WorldMap.SIZE);
        int rz0 = (int) Math.floor(worldZ(0) / WorldMap.SIZE), rz1 = (int) Math.floor(worldZ(h) / WorldMap.SIZE);
        boolean wantFull = scale >= FULL_RES_FROM;
        g.setImageSmoothing(scale < 1);

        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                long k = key(rx, rz);
                Entry e = tiles.get(k);
                // Whole pixels on both edges, so neighbouring tiles never show a seam
                double x0 = Math.floor(screenX(rx * (double) WorldMap.SIZE));
                double y0 = Math.floor(screenY(rz * (double) WorldMap.SIZE));
                double x1 = Math.floor(screenX((rx + 1) * (double) WorldMap.SIZE));
                double y1 = Math.floor(screenY((rz + 1) * (double) WorldMap.SIZE));
                if (e == null || e.empty) continue;
                if (wantFull && e.full == null) requestFull(k, e);
                if (wantFull && e.full != null) {
                    fullLru.get(k); // mark as recently used
                    g.drawImage(e.full, x0, y0, x1 - x0, y1 - y0);
                } else if (e.low != null) {
                    g.drawImage(e.low, x0, y0, x1 - x0, y1 - y0);
                }
            }
        }

        // Faint grid: regions when zoomed out, chunks when zoomed in
        double step = scale >= 3 ? 16 : WorldMap.SIZE;
        if (step * scale >= 24) {
            g.setStroke(grid);
            g.setLineWidth(1);
            for (double gx = Math.floor(worldX(0) / step) * step; gx <= worldX(w); gx += step) {
                double sx = Math.floor(screenX(gx)) + 0.5;
                g.strokeLine(sx, 0, sx, h);
            }
            for (double gz = Math.floor(worldZ(0) / step) * step; gz <= worldZ(h); gz += step) {
                double sy = Math.floor(screenY(gz)) + 0.5;
                g.strokeLine(0, sy, w, sy);
            }
        }

        // Player markers: the head's centre sits on the player
        for (Marker m : players.values()) {
            if (!m.placed || m.dim != dim) continue;
            Region n = (Region) m.node;
            if (n.getWidth() == 0) n.applyCss(); // a brand-new marker: style it before measuring
            n.autosize();
            double nw = n.prefWidth(-1);
            // Not rounded to whole pixels: a slow walker would visibly step one pixel at a time
            n.relocate(screenX(m.shownX) - nw / 2, screenY(m.shownZ) - 15);
        }
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Stops background work (window closing). */
    void shutdown() {
        timer.stop();
        stopWatching();
        renderPool.shutdownNow();
        ioPool.shutdownNow();
    }
}
