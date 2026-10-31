package main;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The launcher's logo: an emerald "server block" on a dark rounded tile, in the same colors as
 * the UI theme. Plain Java 2D (no JavaFX), so the build tools draw the exact same art into the
 * Windows icon (tools/MakeIcon) and the installer images (tools/MakeWizardArt).
 *
 * Everything is drawn on a 256×256 grid and scaled. Small sizes (≤ 32 px: taskbar, tray, title
 * bar) drop the fine details and enlarge the block so it stays crisp.
 */
public final class AppIcon {

    private AppIcon() {}

    // Theme colors (see the tokens at the top of style.css)
    private static final Color TILE_TOP    = new Color(0x1d2634);
    private static final Color TILE_BOTTOM = new Color(0x0b0f15);
    private static final Color TOP_LIGHT   = new Color(0xa7f3d0);
    private static final Color TOP_DARK    = new Color(0x34d399);
    private static final Color LEFT_TOP    = new Color(0x10b981);
    private static final Color LEFT_BOTTOM = new Color(0x047857);
    private static final Color RIGHT_TOP   = new Color(0x047857);
    private static final Color RIGHT_BOT   = new Color(0x053b2d);

    /** The full icon: tile + block. */
    public static BufferedImage draw(int size) {
        return render(size, true);
    }

    /** Just the block on a transparent background (for placing on other artwork). */
    public static BufferedImage drawMark(int size) {
        return render(size, false);
    }

    private static BufferedImage render(int size, boolean withTile) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.scale(size / 256.0, size / 256.0);

        boolean small = size <= 32;
        if (withTile) drawTile(g, small);
        drawBlock(g, small, withTile);
        g.dispose();
        return img;
    }

    private static void drawTile(Graphics2D g, boolean small) {
        double pad = small ? 0 : 8;
        Shape tile = new RoundRectangle2D.Double(pad, pad, 256 - 2 * pad, 256 - 2 * pad, 112, 112);
        g.setPaint(new GradientPaint(0, (float) pad, TILE_TOP, 0, (float) (256 - pad), TILE_BOTTOM));
        g.fill(tile);

        // Soft emerald glow behind the block
        g.setClip(tile);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(128, 150), 128,
            new float[]{0f, 1f}, new Color[]{new Color(52, 211, 153, small ? 40 : 64), new Color(52, 211, 153, 0)}));
        g.fill(tile);
        g.setClip(null);

        if (!small) {
            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(255, 255, 255, 24));
            g.draw(new RoundRectangle2D.Double(pad + 1.5, pad + 1.5, 253 - 2 * pad, 253 - 2 * pad, 109, 109));
        }
    }

    private static void drawBlock(Graphics2D g, boolean small, boolean onTile) {
        // Isometric cube: top rhombus N-E-S-W, then the two visible sides down to the bottom corners.
        // Small icons get a bigger block so it reads at 16 px.
        double k  = small ? 1.18 : (onTile ? 1.0 : 1.22);
        double cx = 128, cy = 130;
        double[] N  = pt(128, 46, k, cx, cy),  E  = pt(206, 90, k, cx, cy);
        double[] S  = pt(128, 134, k, cx, cy), W  = pt(50, 90, k, cx, cy);
        double[] BL = pt(50, 170, k, cx, cy),  BC = pt(128, 214, k, cx, cy), BR = pt(206, 170, k, cx, cy);

        if (!small) { // a soft contact shadow grounds the block
            for (int i = 6; i >= 1; i--) {
                g.setColor(new Color(0, 0, 0, 10));
                double w = 120 * k + i * 12, h = 16 + i * 3;
                g.fill(new Ellipse2D.Double(cx - w / 2, BC[1] - h / 2 + 4, w, h));
            }
        }

        // Faces: right (shadow side), left (lit side), top (brightest)
        g.setPaint(new GradientPaint(0, (float) E[1], RIGHT_TOP, 0, (float) BC[1], RIGHT_BOT));
        g.fill(poly(S, E, BR, BC));
        g.setPaint(new GradientPaint(0, (float) W[1], LEFT_TOP, 0, (float) BC[1], LEFT_BOTTOM));
        g.fill(poly(W, S, BC, BL));
        g.setPaint(new GradientPaint((float) N[0], (float) N[1], TOP_LIGHT, (float) S[0], (float) S[1], TOP_DARK));
        g.fill(poly(N, E, S, W));
        if (!small) pixelTexture(g, N, E, W);

        // Light bevels along the edges instead of dark outlines — reads modern, not pixel-art
        g.setStroke(new BasicStroke(small ? 5f : 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 110));
        g.draw(line(W, N)); g.draw(line(N, E));
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(line(W, S)); g.draw(line(S, E));
        g.setColor(new Color(255, 255, 255, 40));
        g.draw(line(S, BC));

        if (small) return;

        // Server front on the dark side: two drive slots and a glowing status LED
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(1, 30, 22, 170));
        g.draw(line(onFace(S, E, BC, 0.14, 0.34), onFace(S, E, BC, 0.56, 0.34)));
        g.draw(line(onFace(S, E, BC, 0.14, 0.60), onFace(S, E, BC, 0.56, 0.60)));
        double[] led = onFace(S, E, BC, 0.80, 0.34);
        double r = 6.5 * k;
        g.setColor(new Color(110, 231, 183, 70));
        g.fill(new Ellipse2D.Double(led[0] - r * 2.2, led[1] - r * 2.2, r * 4.4, r * 4.4));
        g.setColor(new Color(0xd1fae5));
        g.fill(new Ellipse2D.Double(led[0] - r, led[1] - r, r * 2, r * 2));
        double[] led2 = onFace(S, E, BC, 0.80, 0.60);
        g.setColor(new Color(110, 231, 183, 120));
        g.fill(new Ellipse2D.Double(led2[0] - r * 0.8, led2[1] - r * 0.8, r * 1.6, r * 1.6));
    }

    /**
     * A faint 8×8 pixel pattern on the top face — a nod to Minecraft's block textures that stays
     * subtle enough to keep the icon clean. Fixed pattern, so every build draws the same icon.
     */
    private static void pixelTexture(Graphics2D g, double[] n, double[] e, double[] w) {
        // 0 = none, 1 = lighter, 2 = darker
        String[] rows = {
            "01000020",
            "00020001",
            "20000100",
            "00100002",
            "01002000",
            "00000100",
            "20010002",
            "00200010",
        };
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                char t = rows[r].charAt(c);
                if (t == '0') continue;
                double u0 = c / 8.0, u1 = (c + 1) / 8.0, v0 = r / 8.0, v1 = (r + 1) / 8.0;
                g.setColor(t == '1' ? new Color(255, 255, 255, 46) : new Color(4, 120, 87, 60));
                g.fill(poly(onFace(n, e, w, u0, v0), onFace(n, e, w, u1, v0),
                            onFace(n, e, w, u1, v1), onFace(n, e, w, u0, v1)));
            }
        }
    }

    // ── geometry helpers ─────────────────────────────────────────────

    /** Point scaled by k around (cx, cy). */
    private static double[] pt(double x, double y, double k, double cx, double cy) {
        return new double[]{cx + (x - cx) * k, cy + (y - cy) * k};
    }

    /** Point on the face spanned from origin o along a (u: 0..1) and b (v: 0..1). */
    private static double[] onFace(double[] o, double[] a, double[] b, double u, double v) {
        return new double[]{o[0] + (a[0] - o[0]) * u + (b[0] - o[0]) * v,
                            o[1] + (a[1] - o[1]) * u + (b[1] - o[1]) * v};
    }

    private static Shape poly(double[]... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0][0], p[0][1]);
        for (int i = 1; i < p.length; i++) path.lineTo(p[i][0], p[i][1]);
        path.closePath();
        return path;
    }

    private static Line2D line(double[] a, double[] b) {
        return new Line2D.Double(a[0], a[1], b[0], b[1]);
    }
}
