import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.Random;

/**
 * Generates the installer art in the app's "midnight / emerald" theme, using the app's own logo
 * (src/main/AppIcon.java):
 *   wizard_back_*.png   full-window aurora background — the same three glows the launcher draws
 *   wizard_image_*.png  transparent logo + wordmark for the Welcome / Finished pages
 *   wizard_icon_*.png   transparent logo for the header of the other pages
 * Several sizes of each, so Inno Setup can pick a sharp one at any DPI.
 * Compile: javac -d tools\bin tools\MakeWizardArt.java src\main\AppIcon.java
 * Run:     java -cp tools\bin MakeWizardArt dist 1.1.0
 */
public class MakeWizardArt {

    // Same tokens as style.css
    static final Color BG       = new Color(0x0b0e14);
    static final Color TEXT     = new Color(0xe8ecf3);
    static final Color TEXT_2   = new Color(0xa3adbe);
    static final Color TEXT_3   = new Color(0x687387);
    static final Color ACCENT   = new Color(0x34d399);

    // installer.iss uses WizardSizePercent=130, which grows the page and banner areas (not the icon)
    static final double SIZE = 1.30;
    // Image areas at 100% DPI with default settings (Inno Setup docs), and the DPI steps to cover
    static final double[] DPI = {1.0, 1.25, 1.5, 1.75, 2.0, 2.5};

    static String version = "1.1.0";   // build.bat passes its APP_VERSION

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "dist";
        if (args.length > 1) version = args[1];
        File dir = new File(out);
        dir.mkdirs();
        // Old single-size art from earlier builds
        new File(dir, "wizard_banner.bmp").delete();
        new File(dir, "wizard_icon.bmp").delete();

        // The background is a soft blur, so two sizes scale cleanly to every DPI (about 1 MB for both)
        for (double d : new double[]{1.0, 2.0})
            ImageIO.write(background((int) (596 * d * SIZE), (int) (432 * d * SIZE)), "PNG",
                new File(dir, "wizard_back_" + (int) (d * 100) + ".png"));
        for (double d : DPI) {
            int tag = (int) Math.round(d * 100);
            ImageIO.write(banner((int) (202 * d * SIZE), (int) (386 * d * SIZE)), "PNG",
                new File(dir, "wizard_image_" + tag + ".png"));
            ImageIO.write(smallIcon((int) Math.round(58 * d)), "PNG",
                new File(dir, "wizard_icon_" + tag + ".png"));
        }
        System.out.println("OK");
    }

    // ── Aurora background (whole wizard) ──────────────────────────────────

    // Same glows as Effects.java: color, alpha, center (fraction of width/height), radius (fraction of width)
    static final double[][] GLOWS = {
        {0x10b981, .20, .17, .25, .62},   // emerald, behind the logo
        {0x3b82f6, .11, .92, .95, .58},   // blue, bottom right
        {0x8b5cf6, .08, .82, .02, .46},   // violet, top right
    };

    static BufferedImage background(int W, int H) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Random noise = new Random(7);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double r = BG.getRed(), g = BG.getGreen(), b = BG.getBlue();
                for (double[] gl : GLOWS) {
                    double t = Math.hypot(x - gl[2] * W, y - gl[3] * H) / (gl[4] * W);
                    if (t >= 1) continue;
                    double f = Math.pow(1 - t * t, 3) * gl[1];   // smooth falloff, no visible edge
                    int c = (int) gl[0];
                    r += (((c >> 16) & 255) - r) * f;
                    g += (((c >> 8) & 255) - g) * f;
                    b += ((c & 255) - b) * f;
                }
                // A little dither so the 8-bit gradient doesn't band
                double n = noise.nextDouble() - 0.5;
                img.setRGB(x, y, (clamp(r + n) << 16) | (clamp(g + n) << 8) | clamp(b + n));
            }
        }
        return img;
    }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    // ── Welcome / Finished banner (transparent, sits on the aurora) ───────

    static BufferedImage banner(int W, int H) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rg(img);
        double k = W / 262.0;          // designed at 262 wide (100% DPI × 1.3)
        g.scale(k, k);
        double w = 262, h = H / k;

        // Logo with a soft emerald halo
        int logo = 116;
        double cy = 118;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2, cy), 120,
            new float[]{0f, .45f, 1f},
            new Color[]{new Color(52, 211, 153, 70), new Color(52, 211, 153, 22), new Color(52, 211, 153, 0)}));
        g.fill(new Ellipse2D.Double(w / 2 - 120, cy - 120, 240, 240));
        drawImage(g, main.AppIcon.draw((int) Math.round(logo * k)), (w - logo) / 2, cy - logo / 2.0, logo);

        // Wordmark
        double y = cy + logo / 2.0 + 46;
        g.setFont(bestFont("Segoe UI Semibold,Segoe UI,Arial", Font.BOLD, 23));
        g.setColor(TEXT);
        drawC(g, "Server Launcher", w, y);
        g.setFont(bestFont("Segoe UI,Arial", Font.PLAIN, 12));
        g.setColor(TEXT_2);
        drawC(g, "for Minecraft Java Edition", w, y + 21);

        // Fading emerald divider
        double sepY = y + 42;
        g.setPaint(new LinearGradientPaint((float) 46, (float) sepY, (float) (w - 46), (float) sepY,
            new float[]{0f, .5f, 1f},
            new Color[]{new Color(52, 211, 153, 0), ACCENT, new Color(52, 211, 153, 0)}));
        g.fill(new Rectangle2D.Double(46, sepY, w - 92, 1.5));

        // Features
        String[] feats = {
            "One-click start and stop",
            "Live console and players",
            "Finds the right Java",
            "Backups and restarts",
        };
        g.setFont(bestFont("Segoe UI,Arial", Font.PLAIN, 12));
        double fy = sepY + 32, fx = 56;
        for (String f : feats) {
            g.setColor(new Color(52, 211, 153, 50));
            g.fill(new Ellipse2D.Double(fx - 3, fy - 10.5, 11, 11));
            g.setColor(ACCENT);
            g.fill(new Ellipse2D.Double(fx, fy - 7.5, 5, 5));
            g.setColor(TEXT_2);
            g.drawString(f, (float) (fx + 16), (float) fy);
            fy += 23;
        }

        // Version
        g.setFont(bestFont("Segoe UI,Arial", Font.PLAIN, 10));
        g.setColor(TEXT_3);
        drawC(g, "v" + version, w, h - 14);

        g.dispose();
        return img;
    }

    // ── Header icon (transparent) ─────────────────────────────────────────

    static BufferedImage smallIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rg(img);
        int pad = Math.max(1, size / 14);
        g.drawImage(main.AppIcon.draw(size - 2 * pad), pad, pad, null);
        g.dispose();
        return img;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Draws a pre-rendered (device-pixel) image at logical position/size under a scaled transform. */
    static void drawImage(Graphics2D g, BufferedImage im, double x, double y, double size) {
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.scale(size / im.getWidth(), size / im.getHeight());
        g.drawImage(im, at, null);
    }

    static void drawC(Graphics2D g, String text, double W, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) ((W - fm.stringWidth(text)) / 2), (float) y);
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
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,       RenderingHints.VALUE_ANTIALIAS_ON);
        // Grayscale text AA: LCD subpixel AA leaves colored fringes on a transparent image
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,  RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,          RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,      RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }
}
