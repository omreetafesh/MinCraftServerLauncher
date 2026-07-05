import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Standalone utility — no module system needed.
 * Generates icon.ico (multi-resolution) from the same grass-block art used in the app.
 * Compile: javac tools\MakeIcon.java -d tools\bin
 * Run:     java -cp tools\bin MakeIcon icon.ico
 */
public class MakeIcon {

    public static void main(String[] args) throws Exception {
        String outPath = args.length > 0 ? args[0] : "icon.ico";

        int[] sizes = {16, 32, 48, 256};
        byte[][] pngs = new byte[sizes.length][];

        System.out.println("Rendering grass block at " + sizes.length + " sizes...");
        for (int i = 0; i < sizes.length; i++) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            ImageIO.write(drawGrassBlock(sizes[i]), "png", buf);
            pngs[i] = buf.toByteArray();
        }

        // Write ICO file (little-endian, PNG-compressed entries — Vista+ compatible)
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outPath))) {
            le16(os, 0);            // reserved
            le16(os, 1);            // type = icon
            le16(os, sizes.length); // number of images

            // Directory entries (16 bytes each)
            int dataOffset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int dim = (sizes[i] == 256) ? 0 : sizes[i]; // 0 means 256 in ICO spec
                os.write(dim);                  // width
                os.write(dim);                  // height
                os.write(0);                    // color count (0 = true-color)
                os.write(0);                    // reserved
                le16(os, 1);                    // color planes
                le16(os, 32);                   // bits per pixel
                le32(os, pngs[i].length);       // size of image data
                le32(os, dataOffset);           // file offset of image data
                dataOffset += pngs[i].length;
            }

            // Embedded PNG blobs
            for (byte[] png : pngs) os.write(png);
        }

        System.out.println("[OK] " + outPath);
    }

    // ── Little-endian helpers ────────────────────────────────────────────

    static void le16(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
    }

    static void le32(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
        os.write((v >> 16) & 0xFF);
        os.write((v >> 24) & 0xFF);
    }

    // ── Grass block renderer (copied from main.Main) ──────────────────────

    static BufferedImage drawGrassBlock(int S) {
        BufferedImage buf = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,   RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        float sc = S / 256f;

        for (int r = 8; r > 0; r--) {
            int alpha = (int)(18 * (9 - r) / 8f);
            g.setColor(new Color(0, 200, 60, alpha));
            g.fillRoundRect(-(int)(r*sc), -(int)(r*sc),
                             S + (int)(2*r*sc), S + (int)(2*r*sc),
                             (int)(48*sc), (int)(48*sc));
        }
        g.setPaint(new GradientPaint(0, 0, new Color(0x242424), 0, S, new Color(0x141414)));
        g.fillRoundRect(0, 0, S, S, (int)(44*sc), (int)(44*sc));

        int nx = p(128,sc), ny = p(34,sc);
        int ex = p(212,sc), ey = p(78,sc);
        int sx = p(128,sc), sy = p(122,sc);
        int wx = p(44,sc),  wy = p(78,sc);
        int blx = p(44,sc),  bly = p(166,sc);
        int bcx = p(128,sc), bcy = p(210,sc);
        int brx = p(212,sc), bry = p(166,sc);

        int[] topX  = {nx, ex, sx, wx};
        int[] topY  = {ny, ey, sy, wy};
        int[] leftX = {wx, sx, bcx, blx};
        int[] leftY = {wy, sy, bcy, bly};
        int[] rightX = {sx, ex, brx, bcx};
        int[] rightY = {sy, ey, bry, bcy};

        g.setPaint(new GradientPaint((sx+ex)/2f, sy, new Color(0x5e3d22),
                                      (sx+ex)/2f, bcy, new Color(0x301c0d)));
        g.fillPolygon(rightX, rightY, 4);
        g.setColor(rgba(0x1a0b04, 110));
        dirtMark(g,sc,145,136,7); dirtMark(g,sc,168,122,5);
        dirtMark(g,sc,155,158,6); dirtMark(g,sc,178,148,4);
        dirtMark(g,sc,148,174,5); dirtMark(g,sc,170,168,6);
        dirtMark(g,sc,140,190,4); dirtMark(g,sc,162,188,5);

        g.setPaint(new GradientPaint((wx+sx)/2f, wy, new Color(0xb07848),
                                      (wx+sx)/2f, bcy, new Color(0x7a5030)));
        g.fillPolygon(leftX, leftY, 4);
        g.setColor(rgba(0x3e2010, 110));
        dirtMark(g,sc,72,132,7); dirtMark(g,sc,96,148,5);
        dirtMark(g,sc,64,163,6); dirtMark(g,sc,104,166,4);
        dirtMark(g,sc,76,178,5); dirtMark(g,sc,110,182,6);
        dirtMark(g,sc,58,188,4); dirtMark(g,sc,90,196,5);

        g.setPaint(new GradientPaint(nx, ny, new Color(0x7ed94e), sx, sy, new Color(0x58ae2a)));
        g.fillPolygon(topX, topY, 4);
        g.setColor(rgba(0x9fe870, 80));
        blotch(g,sc,90,58,18,10); blotch(g,sc,138,52,14,8);
        blotch(g,sc,160,72,16,9); blotch(g,sc,110,90,20,10);
        g.setColor(rgba(0x4a9020, 70));
        blotch(g,sc,75,72,14,8); blotch(g,sc,150,60,12,7);
        blotch(g,sc,120,100,16,8);

        int gH = p(16, sc);
        g.setColor(rgba(0x4e9224, 200));
        g.fillPolygon(new int[]{wx,sx,sx,wx}, new int[]{wy,sy,sy+gH,wy+gH}, 4);
        g.setColor(rgba(0x78cc44, 100));
        g.setStroke(new BasicStroke(Math.max(1f, 1.5f*sc)));
        g.drawLine(wx, wy, sx, sy);

        g.setColor(rgba(0x3c7018, 200));
        g.fillPolygon(new int[]{sx,ex,ex,sx}, new int[]{sy,ey,ey+gH,sy+gH}, 4);
        g.setColor(rgba(0x58a030, 80));
        g.drawLine(sx, sy, ex, ey);

        float sw = Math.max(1.5f, 2.8f*sc);
        g.setColor(rgba(0x000000, 210));
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolygon(topX, topY, 4);
        g.drawLine(wx, wy, blx, bly);
        g.drawLine(ex, ey, brx, bry);
        g.drawLine(sx, sy, bcx, bcy);
        g.drawLine(blx, bly, bcx, bcy);
        g.drawLine(brx, bry, bcx, bcy);

        g.setStroke(new BasicStroke(Math.max(1.5f, 4f*sc)));
        g.setColor(rgba(0x00ff41, 55));
        g.drawRoundRect(p(4,sc), p(4,sc), S-p(8,sc), S-p(8,sc), p(38,sc), p(38,sc));

        g.dispose();
        return buf;
    }

    static void dirtMark(Graphics2D g, float sc, int bx, int by, int size) {
        g.fillRect(p(bx,sc), p(by,sc), p(size,sc), p(size,sc));
    }

    static void blotch(Graphics2D g, float sc, int bx, int by, int w, int h) {
        g.fillOval(p(bx,sc), p(by,sc), p(w,sc), p(h,sc));
    }

    static int p(int v, float sc) { return Math.round(v * sc); }

    static Color rgba(int hex, int a) {
        return new Color((hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF, a);
    }
}
