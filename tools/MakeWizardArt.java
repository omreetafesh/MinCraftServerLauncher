import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import java.io.File;

public class MakeWizardArt {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "dist";
        new File(out).mkdirs();
        ImageIO.write(banner(400, 600), "BMP", new File(out, "wizard_banner.bmp"));
        ImageIO.write(smallIcon(55, 55),  "BMP", new File(out, "wizard_icon.bmp"));
        System.out.println("OK");
    }

    // ── 400×600 left-panel banner ─────────────────────────────────────────

    static BufferedImage banner(int W, int H) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rg(img);

        // Background: near-black with a very faint green tint
        g.setPaint(new GradientPaint(0, 0, new Color(0x0a120a), W, H, new Color(0x070a07)));
        g.fillRect(0, 0, W, H);

        // Dot-matrix overlay (matrix style)
        for (int y = 10; y < H; y += 16) {
            for (int x = 10; x < W; x += 16) {
                double cx = x - W / 2.0, cy = y - H * 0.38;
                double d  = Math.sqrt(cx * cx + cy * cy);
                int bri   = (int)(28 * Math.max(0, 1 - d / 260));
                g.setColor(new Color(0, bri + 6, 0));
                g.fillRect(x, y, 2, 2);
            }
        }

        // ── Grass block (large, centred upper portion) ────────────────────
        int blockSz = 230;
        int bx = (W - blockSz) / 2;
        int by = 55;
        drawBlock(g, bx, by, blockSz);

        // Soft glow shadow beneath block
        int gCX = W / 2, gCY = by + blockSz - 4;
        for (int r = 60; r > 0; r--) {
            int alpha = (int)(22.0 * (61 - r) / 60.0);
            g.setColor(new Color(0, Math.min(255, alpha * 4), 0, Math.min(255, alpha)));
            g.fillOval(gCX - r * 2, gCY - r / 3, r * 4, r * 2 / 3);
        }

        // ── Separator (fade-in green line) ────────────────────────────────
        int sepY = by + blockSz + 26;
        drawFadeLine(g, W, sepY, new Color(0x00dd33), 2);

        // ── "MINECRAFT" heading ───────────────────────────────────────────
        g.setFont(bestFont("Impact,Arial Black,Arial", Font.BOLD, 46));
        g.setColor(Color.WHITE);
        drawC(g, "MINECRAFT", W, sepY + 55);

        // ── "SERVER LAUNCHER" subtitle ────────────────────────────────────
        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.setColor(new Color(0x00ff41));
        drawC(g, "SERVER LAUNCHER", W, sepY + 84);

        // ── Second thin separator ─────────────────────────────────────────
        int sep2Y = sepY + 100;
        drawFadeLine(g, W, sep2Y, new Color(0x00882a), 1);

        // ── Feature bullets ───────────────────────────────────────────────
        String[] feats = {
            "No Java install required",
            "Live console & command input",
            "RAM / TPS / player charts",
            "Multi-profile server management",
            "Auto JAR downloader",
        };
        g.setFont(new Font("Consolas", Font.PLAIN, 13));
        int fy = sep2Y + 26;
        int indentX = W / 2 - 100;
        for (String f : feats) {
            g.setColor(new Color(0x00cc33));
            g.fillOval(indentX, fy - 9, 7, 7);
            g.setColor(new Color(0x999999));
            g.drawString(f, indentX + 16, fy);
            fy += 22;
        }

        // ── Version number at very bottom ─────────────────────────────────
        g.setFont(new Font("Consolas", Font.PLAIN, 11));
        g.setColor(new Color(0x2a5a2a));
        drawC(g, "v1.0.0", W, H - 14);

        // ── Bottom accent bar ─────────────────────────────────────────────
        g.setColor(new Color(0x1a3a1a));
        g.fillRect(0, H - 6, W, 6);
        g.setColor(new Color(0x00aa33));
        g.fillRect(0, H - 6, W, 2);

        g.dispose();
        return img;
    }

    // ── 55×55 small corner icon ───────────────────────────────────────────

    static BufferedImage smallIcon(int W, int H) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rg(img);
        g.setColor(new Color(0x0e1a0e));
        g.fillRect(0, 0, W, H);
        drawBlock(g, 5, 5, W - 10);
        g.dispose();
        return img;
    }

    // ── Isometric grass block ─────────────────────────────────────────────

    static void drawBlock(Graphics2D g, int ox, int oy, int S) {
        float sc = S / 256f;

        int nx = ox + p(128,sc), ny = oy + p(34,sc);
        int ex = ox + p(212,sc), ey = oy + p(78,sc);
        int sx = ox + p(128,sc), sy = oy + p(122,sc);
        int wx = ox + p(44,sc),  wy = oy + p(78,sc);
        int blx = ox + p(44,sc),  bly = oy + p(166,sc);
        int bcx = ox + p(128,sc), bcy = oy + p(210,sc);
        int brx = ox + p(212,sc), bry = oy + p(166,sc);

        int[] topX  = {nx, ex, sx, wx};
        int[] topY  = {ny, ey, sy, wy};
        int[] leftX = {wx, sx, bcx, blx};
        int[] leftY = {wy, sy, bcy, bly};
        int[] rightX= {sx, ex, brx, bcx};
        int[] rightY= {sy, ey, bry, bcy};

        // Right face — shadow side
        g.setPaint(new GradientPaint((sx+ex)/2f, sy, new Color(0x5e3d22),
                                     (sx+ex)/2f, bcy, new Color(0x2e1c0d)));
        g.fillPolygon(rightX, rightY, 4);

        // Left face — lit side
        g.setPaint(new GradientPaint((wx+sx)/2f, wy, new Color(0xb07848),
                                     (wx+sx)/2f, bcy, new Color(0x7a5030)));
        g.fillPolygon(leftX, leftY, 4);

        // Top face — grass
        g.setPaint(new GradientPaint(nx, ny, new Color(0x7ed94e),
                                     sx, sy, new Color(0x58ae2a)));
        g.fillPolygon(topX, topY, 4);

        // Grass strips
        int gH = p(16, sc);
        g.setColor(new Color(0x4e9224));
        g.fillPolygon(new int[]{wx,sx,sx,wx}, new int[]{wy,sy,sy+gH,wy+gH}, 4);
        g.setColor(new Color(0x3c7018));
        g.fillPolygon(new int[]{sx,ex,ex,sx}, new int[]{sy,ey,ey+gH,sy+gH}, 4);

        // Outlines
        float sw = Math.max(1.5f, 2.8f * sc);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolygon(topX, topY, 4);
        g.drawLine(wx, wy, blx, bly);
        g.drawLine(ex, ey, brx, bry);
        g.drawLine(sx, sy, bcx, bcy);
        g.drawLine(blx, bly, bcx, bcy);
        g.drawLine(brx, bry, bcx, bcy);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    static void drawFadeLine(Graphics2D g, int W, int y, Color mid, int h) {
        g.setPaint(new GradientPaint(0, y, new Color(0,0,0), W/2f, y, mid));
        g.fillRect(0, y, W/2, h);
        g.setPaint(new GradientPaint(W/2f, y, mid, W, y, new Color(0,0,0)));
        g.fillRect(W/2, y, W/2, h);
    }

    static void drawC(Graphics2D g, String text, int W, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (W - fm.stringWidth(text)) / 2, y);
    }

    static Font bestFont(String csv, int style, int size) {
        for (String name : csv.split(",")) {
            Font f = new Font(name.trim(), style, size);
            if (!f.getFamily().equals("Dialog")) return f;
        }
        return new Font("Arial", style, size);
    }

    static Graphics2D rg(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    static int p(int v, float sc) { return Math.round(v * sc); }
}
