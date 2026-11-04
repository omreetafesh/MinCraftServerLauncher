package main;

import javafx.scene.paint.Color;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The launcher's color themes. Each one is a handful of base colors; everything else (glass panels,
 * soft highlights, hover overlays) is derived from them, then handed to the UI as a small stylesheet
 * that overrides the tokens at the top of style.css. Code-drawn things (cursor glow, aurora, map,
 * logo) take their colors from here too.
 *
 * Status colors keep their meaning in every theme: "Online" is always green, warnings amber, errors red.
 */
final class Theme {

    final String id, name, vibe;
    final boolean light;
    private final Map<String, String> base;
    /**
     * Ambient light (Effects): the card spotlight's color, the cursor's halo and bright core and how
     * strong they are, and the three drifting aurora blobs. A light theme needs a light, bright halo:
     * a dark color "glowing" over white only darkens it into a gray smudge.
     */
    final Color glow, halo, glowCore;
    final double glowAlpha;
    final Color[] aurora;
    final double[] auroraAlpha;
    /** Hue the logo's block is turned to (degrees), or NaN to keep it emerald. */
    final double logoHue;

    private Theme(String id, String name, String vibe, boolean light, Map<String, String> base,
                  String halo, double glowAlpha, String glowCore, String[] aurora, double[] auroraAlpha,
                  double logoHue) {
        this.id = id; this.name = name; this.vibe = vibe; this.light = light; this.base = base;
        this.glow = Color.web(base.get("accent"));
        this.halo = Color.web(halo != null ? halo : base.get("accent"));
        this.glowAlpha = glowAlpha;
        this.glowCore = Color.web(glowCore);
        this.aurora = Arrays.stream(aurora).map(Color::web).toArray(Color[]::new);
        this.auroraAlpha = auroraAlpha;
        this.logoHue = logoHue;
    }

    // ─── The themes ──────────────────────────────────────────────────

    static final List<Theme> ALL = List.of(
        dark("emerald", "Emerald", "Midnight blue with emerald light")
            .surfaces("#0b0e14", "#0e1219", "#131922", "#1a212d", "#232c3b", "#1f2733", "#2c3746")
            .text("#e8ecf3", "#a3adbe", "#687387")
            .accent("#34d399", "#10b981", "#4ade80", "#03140d")
            .set("log-info", "#8fb3d9").set("code-text", "#93c5fd")
            .ambient("#6ee7b7", new String[]{"#10b981", "#3b82f6", "#8b5cf6"}, 0.13, 0.09, 0.07)
            .logo(Double.NaN),

        dark("prismarine", "Prismarine", "Deep ocean teal, like an ocean monument")
            .surfaces("#071116", "#09161b", "#0e1d23", "#13272e", "#1b333c", "#172a31", "#234049")
            .text("#e3f2f4", "#9dbcc2", "#5f7f86")
            .accent("#22d3ee", "#06b6d4", "#67e8f9", "#021a20")
            .set("log-info", "#8ab9d1").set("code-text", "#7dd3fc")
            .ambient("#a5f3fc", new String[]{"#06b6d4", "#2563eb", "#14b8a6"}, 0.14, 0.10, 0.08)
            .logo(188),

        dark("amethyst", "Amethyst", "The End's indigo night and purpur glow")
            .surfaces("#0d0b16", "#110e1c", "#171327", "#1f1a33", "#2a2342", "#231d36", "#33294d")
            .text("#eeeaf7", "#b1a8c8", "#766b91")
            .accent("#a78bfa", "#8b5cf6", "#c4b5fd", "#12082a")
            .set("violet", "#f0abfc").set("log-info", "#a9b1db").set("code-text", "#c4b5fd")
            .ambient("#ddd6fe", new String[]{"#8b5cf6", "#d946ef", "#3b82f6"}, 0.15, 0.09, 0.07)
            .logo(262),

        dark("nether", "Nether", "Warm charcoal lit by embers")
            .surfaces("#110b0a", "#150e0c", "#1d1412", "#271a17", "#33221e", "#2a1d19", "#3d2a24")
            .text("#f5ebe7", "#c5aca3", "#8b6f65")
            .accent("#fb923c", "#ea580c", "#fdba74", "#1f0a02")
            .set("warn", "#fcd34d").set("log-info", "#d5b9aa").set("code-text", "#fdba74")
            .ambient("#fed7aa", new String[]{"#ea580c", "#dc2626", "#f59e0b"}, 0.14, 0.10, 0.07)
            .logo(25),

        dark("cherry", "Cherry Grove", "Dark plum under falling blossoms")
            .surfaces("#120b10", "#160e14", "#1e141b", "#281b24", "#34232f", "#2b1d27", "#3f2a38")
            .text("#f8eaf2", "#caabbc", "#8d6b7f")
            .accent("#f472b6", "#db2777", "#f9a8d4", "#2a0616")
            .set("violet", "#c4b5fd").set("log-info", "#d7b9c9").set("code-text", "#f9a8d4")
            .ambient("#fbcfe8", new String[]{"#db2777", "#c084fc", "#fb7185"}, 0.13, 0.09, 0.07)
            .logo(328),

        light("quartz", "Quartz", "Bright and clean, for daytime")
            .surfaces("#eef1f5", "#f6f8fa", "#ffffff", "#f1f4f8", "#e6ebf1", "#e1e6ed", "#ccd4de")
            .text("#111827", "#4b5563", "#8a94a6")
            .accent("#047857", "#065f46", "#059669", "#ffffff")
            .set("ok", "#047857").set("info", "#2563eb").set("warn", "#b45309").set("warn-2", "#d97706")
            .set("danger", "#dc2626").set("danger-2", "#b91c1c").set("violet", "#7c3aed")
            .set("log-error", "#e11d48").set("log-info", "#475569").set("code-text", "#1d4ed8")
            .set("map-bg", "#dfe5ec")
            .ambient("#a7f3d0", new String[]{"#10b981", "#3b82f6", "#a78bfa"}, 0.10, 0.07, 0.06)
            .halo("#34d399", 0.10)   // bright mint, laid over the panels as a soft tint (see Effects)
            .logo(Double.NaN)
    );

    static final Theme DEFAULT = ALL.get(0);

    static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return DEFAULT;
    }

    // ─── What the UI reads ───────────────────────────────────────────

    /** A base color by token name (without the leading '-'). */
    Color color(String token) { return Color.web(tokens().get(token)); }

    private String stylesheet;

    /** The stylesheet for this theme: every token of style.css, as a data: URL. */
    String stylesheet() {
        if (stylesheet != null) return stylesheet;
        StringBuilder css = new StringBuilder(".root, .dialog-pane {\n");
        tokens().forEach((k, v) -> css.append("    -").append(k).append(": ").append(v).append(";\n"));
        css.append("}\n");
        return stylesheet = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(css.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The map's empty background and grid lines. */
    Color mapBackground() { return color("map-bg"); }
    Color mapGrid() { return light ? Color.rgb(15, 23, 42, 0.06) : Color.rgb(255, 255, 255, 0.035); }

    /** The app logo, its block recolored to this theme. */
    BufferedImage logo(int size) {
        BufferedImage img = AppIcon.draw(size);
        if (Double.isNaN(logoHue)) return img;
        float hue = (float) (logoHue / 360.0);
        float[] hsb = new float[3];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24;
                if (a == 0) continue;
                java.awt.Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, hsb);
                if (hsb[1] < 0.25) continue;   // the dark tile stays as it is; only the block changes
                int rgb = java.awt.Color.HSBtoRGB(hue, hsb[1], hsb[2]) & 0xFFFFFF;
                img.setRGB(x, y, (a << 24) | rgb);
            }
        }
        return img;
    }

    // ─── Deriving every token ────────────────────────────────────────

    private Map<String, String> tokens;

    private Map<String, String> tokens() {
        if (tokens != null) return tokens;
        Map<String, String> t = new LinkedHashMap<>(base);
        Color bg = Color.web(t.get("bg")), side = Color.web(t.get("bg-side")), surface = Color.web(t.get("surface"));
        // Glass panels: translucent, chosen so that over the plain background each looks exactly
        // like its solid color (the ambient light shows through only where it's lit)
        t.put("glass", over(surface, bg, light ? 0.80 : 0.70));
        t.put("glass-side", over(side, bg, light ? 0.85 : 0.75));
        t.put("inset", over(bg, surface, 0.50));              // = -bg when laid inside a card
        String ink = light ? "15, 23, 42" : "255, 255, 255";   // hovers darken in light themes
        t.put("hover",       "rgba(" + ink + ", " + (light ? 0.05 : 0.045) + ")");
        t.put("hover-faint", "rgba(" + ink + ", " + (light ? 0.03 : 0.025) + ")");
        t.put("hover-2",     "rgba(" + ink + ", " + (light ? 0.06 : 0.05) + ")");
        t.put("swatch-line", "rgba(" + ink + ", " + (light ? 0.18 : 0.14) + ")");
        t.put("overlay-line", "rgba(" + ink + ", " + (light ? 0.08 : 0.06) + ")");
        t.put("overlay",        rgba(bg, light ? 0.88 : 0.82));
        t.put("overlay-strong", rgba(bg, light ? 0.92 : 0.85));
        t.putIfAbsent("map-bg", hex(bg.deriveColor(0, 1, 0.62, 1)));

        Color accent = Color.web(t.get("accent"));
        t.put("accent-soft",  rgba(accent, light ? 0.10 : 0.12));
        t.put("accent-faint", rgba(accent, light ? 0.08 : 0.10));
        t.put("accent-line",  rgba(accent, 0.28));
        t.put("accent-glow",  rgba(accent, 0.35));
        for (String s : new String[]{"ok", "info", "warn", "danger"}) {
            Color c = Color.web(t.get(s));
            t.put(s + "-soft",  rgba(c, light ? 0.10 : 0.12));
            t.put(s + "-faint", rgba(c, 0.08));
            t.put(s + "-line",  rgba(c, light ? 0.30 : 0.22));
            t.put(s + "-hover", rgba(c, light ? 0.16 : 0.22));
        }
        t.put("danger-line", rgba(Color.web(t.get("danger")), 0.35));
        t.put("warn-line", rgba(Color.web(t.get("warn")), 0.35));
        t.put("info-text", light ? t.get("info") : hex(Color.web(t.get("info")).interpolate(Color.WHITE, 0.35)));
        Color violet = Color.web(t.get("violet"));
        t.put("violet-glow", rgba(violet, 0.35));
        t.put("violet-fade", rgba(violet, 0.02));
        return tokens = t;
    }

    /** The color that, laid over {@code under} at {@code alpha}, shows exactly {@code target}. */
    private static String over(Color target, Color under, double alpha) {
        double r = (target.getRed() - (1 - alpha) * under.getRed()) / alpha;
        double g = (target.getGreen() - (1 - alpha) * under.getGreen()) / alpha;
        double b = (target.getBlue() - (1 - alpha) * under.getBlue()) / alpha;
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.2f)", c255(r), c255(g), c255(b), alpha);
    }

    private static String rgba(Color c, double a) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.2f)",
            c255(c.getRed()), c255(c.getGreen()), c255(c.getBlue()), a);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c255(c.getRed()), c255(c.getGreen()), c255(c.getBlue()));
    }

    private static int c255(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }

    // ─── Builder ─────────────────────────────────────────────────────

    private static Builder dark(String id, String name, String vibe)  { return new Builder(id, name, vibe, false); }
    private static Builder light(String id, String name, String vibe) { return new Builder(id, name, vibe, true); }

    private static final class Builder {
        final String id, name, vibe; final boolean light;
        final Map<String, String> m = new LinkedHashMap<>();
        String core, halo; String[] aurora; double[] alpha;
        double glowAlpha = 0.22;

        Builder(String id, String name, String vibe, boolean light) {
            this.id = id; this.name = name; this.vibe = vibe; this.light = light;
            // Shared meanings; a theme overrides them only to keep them readable on its colors
            m.put("ok", "#34d399");
            m.put("info", "#60a5fa");
            m.put("warn", "#fbbf24");
            m.put("warn-2", "#f59e0b");
            m.put("danger", "#f87171");
            m.put("danger-2", "#ef4444");
            m.put("violet", "#a78bfa");
            m.put("log-error", "#fb7185");
        }
        Builder surfaces(String bg, String side, String s1, String s2, String s3, String line, String line2) {
            m.put("bg", bg); m.put("bg-side", side); m.put("surface", s1); m.put("surface-2", s2);
            m.put("surface-3", s3); m.put("line", line); m.put("line-2", line2);
            return this;
        }
        Builder text(String t1, String t2, String t3) { m.put("text", t1); m.put("text-2", t2); m.put("text-3", t3); return this; }
        Builder accent(String a, String a2, String hover, String ink) {
            m.put("accent", a); m.put("accent-2", a2); m.put("accent-hover", hover); m.put("accent-ink", ink);
            return this;
        }
        Builder set(String k, String v) { m.put(k, v); return this; }
        Builder ambient(String core, String[] aurora, double... alpha) { this.core = core; this.aurora = aurora; this.alpha = alpha; return this; }
        /** The cursor halo's own color and strength (default: the accent at 0.22). */
        Builder halo(String color, double alpha) { this.halo = color; this.glowAlpha = alpha; return this; }
        Theme logo(double hue) { return new Theme(id, name, vibe, light, m, halo, glowAlpha, core, aurora, alpha, hue); }
    }
}
