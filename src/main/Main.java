package main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("Launcher.fxml"));
        Parent root = loader.load();
        LauncherController controller = loader.getController();

        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setTitle("Minecraft Server Dashboard");

        for (int size : new int[]{16, 32, 48, 64, 128, 256})
            primaryStage.getIcons().add(bufToFx(drawGrassBlock(size)));

        primaryStage.setScene(new Scene(root, 1150, 720));
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            controller.handleWindowClose();
        });

        controller.setStage(primaryStage);
        primaryStage.show();
    }

    // ── Shared icon renderer ─────────────────────────────────────────

    static java.awt.image.BufferedImage drawGrassBlock(int S) {
        java.awt.image.BufferedImage buf =
            new java.awt.image.BufferedImage(S, S, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = buf.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                           java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                           java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        float sc = S / 256f;

        // ── Background ───────────────────────────────────────────────
        // Outer dark glow ring (subtle)
        for (int r = 8; r > 0; r--) {
            int alpha = (int)(18 * (9 - r) / 8f);
            g.setColor(new java.awt.Color(0, 200, 60, alpha));
            g.fillRoundRect(-(int)(r*sc), -(int)(r*sc),
                             S + (int)(2*r*sc), S + (int)(2*r*sc),
                             (int)(48*sc), (int)(48*sc));
        }
        // Main dark rounded background
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x242424),
                                               0, S, new java.awt.Color(0x141414)));
        g.fillRoundRect(0, 0, S, S, (int)(44*sc), (int)(44*sc));

        // ── Isometric cube vertices (at 256-pixel scale) ─────────────
        //   Top face (rhombus):  N, E, S, W
        //   Left face (parallelogram):  W, S, BC, BL
        //   Right face (parallelogram): S, E, BR, BC
        int nx = p(128,sc), ny = p(34,sc);
        int ex = p(212,sc), ey = p(78,sc);
        int sx = p(128,sc), sy = p(122,sc);
        int wx = p(44,sc),  wy = p(78,sc);
        int blx = p(44,sc),  bly = p(166,sc);   // BL = W + (0, 88)
        int bcx = p(128,sc), bcy = p(210,sc);   // BC = S + (0, 88)
        int brx = p(212,sc), bry = p(166,sc);   // BR = E + (0, 88)

        int[] topX  = {nx, ex, sx, wx};
        int[] topY  = {ny, ey, sy, wy};
        int[] leftX = {wx, sx, bcx, blx};
        int[] leftY = {wy, sy, bcy, bly};
        int[] rightX = {sx, ex, brx, bcx};
        int[] rightY = {sy, ey, bry, bcy};

        // ── RIGHT face — shadow side ─────────────────────────────────
        g.setPaint(new java.awt.GradientPaint(
            (sx+ex)/2f, sy, new java.awt.Color(0x5e3d22),
            (sx+ex)/2f, bcy, new java.awt.Color(0x301c0d)));
        g.fillPolygon(rightX, rightY, 4);

        // Dirt detail marks — right face
        g.setColor(rgba(0x1a0b04, 110));
        dirtMark(g, sc, 145,136,  7); dirtMark(g, sc, 168,122, 5);
        dirtMark(g, sc, 155,158,  6); dirtMark(g, sc, 178,148, 4);
        dirtMark(g, sc, 148,174,  5); dirtMark(g, sc, 170,168, 6);
        dirtMark(g, sc, 140,190,  4); dirtMark(g, sc, 162,188, 5);

        // ── LEFT face — lit side ─────────────────────────────────────
        g.setPaint(new java.awt.GradientPaint(
            (wx+sx)/2f, wy, new java.awt.Color(0xb07848),
            (wx+sx)/2f, bcy, new java.awt.Color(0x7a5030)));
        g.fillPolygon(leftX, leftY, 4);

        // Dirt detail marks — left face
        g.setColor(rgba(0x3e2010, 110));
        dirtMark(g, sc, 72,132, 7);  dirtMark(g, sc, 96,148, 5);
        dirtMark(g, sc, 64,163, 6);  dirtMark(g, sc, 104,166, 4);
        dirtMark(g, sc, 76,178, 5);  dirtMark(g, sc, 110,182, 6);
        dirtMark(g, sc, 58,188, 4);  dirtMark(g, sc, 90,196, 5);

        // ── TOP face — grass ─────────────────────────────────────────
        // Base grass fill
        g.setPaint(new java.awt.GradientPaint(
            nx, ny, new java.awt.Color(0x7ed94e),
            sx, sy, new java.awt.Color(0x58ae2a)));
        g.fillPolygon(topX, topY, 4);

        // Lighter highlight blotches on top (grass texture)
        g.setColor(rgba(0x9fe870, 80));
        blotch(g, sc, 90, 58,  18, 10);
        blotch(g, sc, 138, 52, 14,  8);
        blotch(g, sc, 160, 72, 16,  9);
        blotch(g, sc, 110, 90, 20, 10);
        g.setColor(rgba(0x4a9020, 70));
        blotch(g, sc, 75,  72, 14,  8);
        blotch(g, sc, 150, 60, 12,  7);
        blotch(g, sc, 120, 100, 16, 8);

        // ── Grass side strip on left face top ───────────────────────
        int gH = p(16, sc);
        g.setColor(rgba(0x4e9224, 200));
        g.fillPolygon(
            new int[]{wx, sx, sx, wx},
            new int[]{wy, sy, sy+gH, wy+gH}, 4);
        // thin lighter edge on top of grass strip
        g.setColor(rgba(0x78cc44, 100));
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, 1.5f*sc)));
        g.drawLine(wx, wy, sx, sy);

        // ── Grass side strip on right face top ──────────────────────
        g.setColor(rgba(0x3c7018, 200));
        g.fillPolygon(
            new int[]{sx, ex, ex, sx},
            new int[]{sy, ey, ey+gH, sy+gH}, 4);
        g.setColor(rgba(0x58a030, 80));
        g.drawLine(sx, sy, ex, ey);

        // ── Edge outlines ────────────────────────────────────────────
        float sw = Math.max(1.5f, 2.8f*sc);
        g.setColor(rgba(0x000000, 210));
        g.setStroke(new java.awt.BasicStroke(sw,
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        // Top face
        g.drawPolygon(topX, topY, 4);
        // Vertical pillars
        g.drawLine(wx, wy, blx, bly);
        g.drawLine(ex, ey, brx, bry);
        g.drawLine(sx, sy, bcx, bcy);
        // Bottom edges
        g.drawLine(blx, bly, bcx, bcy);
        g.drawLine(brx, bry, bcx, bcy);

        // ── Subtle green glow border around background ───────────────
        g.setStroke(new java.awt.BasicStroke(Math.max(1.5f, 4f*sc)));
        g.setColor(rgba(0x00ff41, 55));
        g.drawRoundRect(p(4,sc), p(4,sc), S-p(8,sc), S-p(8,sc),
                        p(38,sc), p(38,sc));

        g.dispose();
        return buf;
    }

    private static void dirtMark(java.awt.Graphics2D g, float sc, int bx, int by, int size) {
        g.fillRect(p(bx,sc), p(by,sc), p(size,sc), p(size,sc));
    }

    private static void blotch(java.awt.Graphics2D g, float sc,
                                int bx, int by, int w, int h) {
        g.fillOval(p(bx,sc), p(by,sc), p(w,sc), p(h,sc));
    }

    private static int p(int v, float sc) {
        return Math.round(v * sc);
    }

    private static java.awt.Color rgba(int hex, int a) {
        return new java.awt.Color((hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF, a);
    }

    // ── Convert AWT BufferedImage → JavaFX WritableImage ─────────────

    static WritableImage bufToFx(java.awt.image.BufferedImage buf) {
        int W = buf.getWidth(), H = buf.getHeight();
        WritableImage wi = new WritableImage(W, H);
        PixelWriter pw = wi.getPixelWriter();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int argb = buf.getRGB(x, y);
                pw.setColor(x, y, Color.rgb(
                    (argb >> 16) & 0xff,
                    (argb >>  8) & 0xff,
                     argb        & 0xff,
                    ((argb >> 24) & 0xff) / 255.0));
            }
        return wi;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
