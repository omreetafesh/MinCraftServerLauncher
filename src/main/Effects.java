package main;

import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.Set;

/**
 * The window's ambient lighting, drawn on two mouse-transparent layers around the UI:
 * <ul>
 *   <li><b>back</b> (behind the translucent panels): slowly drifting aurora blobs, and a soft glow
 *       that trails the cursor and fades out once the mouse rests or leaves the window;</li>
 *   <li><b>front</b> (over the UI): a spotlight that lights up the border of the card under the cursor.</li>
 * </ul>
 * One AnimationTimer drives it all and stops itself when nothing is moving, so an idle launcher
 * costs nothing beyond the slow aurora (redrawn at ~20 fps), which can be switched off.
 */
final class Effects {

    private Color accent = Theme.DEFAULT.glow;   // the spotlight's color (follows the theme)
    /** Style classes of the panels the spotlight attaches to. */
    private static final Set<String> CARD_CLASSES =
        Set.of("card", "rail", "command-bar", "props-section", "stats-card", "profile-card");

    private static final long IDLE_NS = 650_000_000L;   // the glow starts fading this long after the last move
    private static final long AMBIENT_FRAME_NS = 83_000_000L; // ~12 fps: the blobs drift a few px per second

    private final Pane back, front;

    private final Glow halo = glow(240);                    // big, lags behind the cursor
    private final Glow core = glow(80);                     // small, hugs the cursor
    private final Glow[] blobs = { glow(520), glow(460), glow(400) };   // the drifting aurora
    // Lissajous paths for the blobs: centre x/y (fraction of the window), amplitude x/y, speed x/y, phase
    private static final double[][] PATHS = {
        {0.22, 0.30, 0.20, 0.18, 0.050, 0.037, 0.0},
        {0.82, 0.72, 0.18, 0.20, 0.041, 0.053, 2.1},
        {0.55, 0.10, 0.25, 0.12, 0.033, 0.046, 4.2},
    };

    private final Region spot = new Region();
    private final Rectangle spotClip = new Rectangle();

    private boolean enabled = true, paused = false, inside = false, running = false;
    private double mx = -1000, my = -1000;   // cursor, in scene coordinates
    private double hx, hy, cx, cy;           // halo / core centres, in back-layer coordinates
    private double glow, spotLevel;          // 0..1
    private Region card;                     // the card under the cursor (null = none)
    private Region lastSpotCard;
    private double[] lastSpot = {};
    private long lastMove, lastFrame, lastAmbient;
    private final long startNs = System.nanoTime();

    private final AnimationTimer timer = new AnimationTimer() {
        @Override public void handle(long now) { tick(now); }
    };

    /**
     * @param shell the window's root; mouse movement is watched there
     * @param back  layer behind the UI
     * @param front layer above the UI
     */
    Effects(Region shell, Pane back, Pane front) {
        this.back = back;
        this.front = front;
        for (Pane p : new Pane[]{back, front}) {
            p.setMouseTransparent(true);
            p.setMinSize(0, 0);
            p.setPrefSize(0, 0); // never let the layers' contents size the window
        }
        // The layers paint outside their children's area, so clip each to the window
        for (Pane p : new Pane[]{back, front}) {
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(p.widthProperty());
            clip.heightProperty().bind(p.heightProperty());
            p.setClip(clip);
        }
        back.getChildren().addAll(blobs);
        back.getChildren().addAll(halo, core);
        setTheme(Theme.DEFAULT);
        halo.setOpacity(0);
        core.setOpacity(0);

        spot.setManaged(false);
        spot.setOpacity(0);
        spot.setClip(spotClip);
        front.getChildren().add(spot);

        shell.addEventFilter(MouseEvent.MOUSE_MOVED,   this::onMouse);
        shell.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouse);
        shell.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, e -> {
            if (e.getTarget() == shell) { inside = false; card = null; wake(); }
        });
        placeBlobs(0);
        wake();
    }

    private Theme theme;

    /** Recolors the glow, the aurora and the spotlight. */
    void setTheme(Theme t) {
        if (t == theme) return;   // the glows are images: only redraw them when something changed
        theme = t;
        accent = t.glow;
        // A light theme can't be "lit" from behind its near-opaque white panels: what little shows
        // through spans so few screen shades that it bands into rings. There the cursor glow is a
        // faint tint laid over the panels instead (last layer drawn, so its dither survives).
        Pane layer = t.light ? front : back;
        if (halo.getParent() != layer) {
            ((Pane) halo.getParent()).getChildren().removeAll(halo, core);
            if (t.light) layer.getChildren().addAll(0, List.of(halo, core));   // under the spotlight
            else layer.getChildren().addAll(halo, core);
        }
        paint(halo, t.halo, t.glowAlpha);
        paint(core, t.glowCore, t.glowAlpha);
        for (int i = 0; i < blobs.length; i++) paint(blobs[i], t.aurora[i], t.auroraAlpha[i]);
        lastSpot = new double[0];   // repaint the spotlight in the new color
    }

    /** Turns all effects on or off (Settings → Performance → Interface effects). */
    void setEnabled(boolean on) {
        enabled = on;
        back.setVisible(on);
        front.setVisible(on);
        if (on) wake(); else stop();
    }

    /** Paused while the window is minimised or hidden in the tray — nobody can see it then. */
    void setPaused(boolean p) {
        paused = p;
        if (p) stop(); else wake();
    }

    private void onMouse(MouseEvent e) {
        if (!enabled) return;
        boolean wasInside = inside;
        inside = true;
        mx = e.getSceneX();
        my = e.getSceneY();
        lastMove = System.nanoTime();
        if (!wasInside) { // appear where the cursor is instead of flying in from the old spot
            Point2D p = back.sceneToLocal(mx, my);
            if (glow < 0.05) { hx = cx = p.getX(); hy = cy = p.getY(); }
        }
        card = cardAt(e.getPickResult().getIntersectedNode());
        wake();
    }

    private static Region cardAt(Node n) {
        for (; n != null; n = n.getParent())
            if (n instanceof Region r && !java.util.Collections.disjoint(r.getStyleClass(), CARD_CLASSES)) return r;
        return null;
    }

    private void wake() {
        if (!enabled || paused || running) return;
        running = true;
        lastFrame = 0;
        timer.start();
    }

    private void stop() {
        running = false;
        timer.stop();
    }

    // ── One frame ────────────────────────────────────────────────────

    private void tick(long now) {
        double dt = lastFrame == 0 ? 1 / 60.0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;

        // Ambient blobs: slow, so 20 fps is plenty — and it keeps an idle window cheap to redraw
        if (now - lastAmbient >= AMBIENT_FRAME_NS) {
            lastAmbient = now;
            placeBlobs((now - startNs) / 1e9);
        }

        // Cursor glow: rises quickly while the mouse moves, fades slowly once it rests
        boolean moving = inside && now - lastMove < IDLE_NS;
        if (!moving && glow == 0 && spotLevel == 0 && card == null) return; // idle: only the aurora moves
        glow = approach(glow, moving ? 1 : 0, dt, moving ? 9 : 1.6);
        Point2D target = back.sceneToLocal(mx, my);
        hx = approach(hx, target.getX(), dt, 6);   hy = approach(hy, target.getY(), dt, 6);
        cx = approach(cx, target.getX(), dt, 16);  cy = approach(cy, target.getY(), dt, 16);
        halo.setTranslateX(hx); halo.setTranslateY(hy); halo.setOpacity(glow);
        core.setTranslateX(cx); core.setTranslateY(cy); core.setOpacity(glow);

        // Card spotlight: stays softly lit while hovering, brighter while the mouse moves
        double wanted = card != null && card.getScene() != null && card.isVisible() ? 0.45 + 0.55 * glow : 0;
        spotLevel = approach(spotLevel, wanted, dt, wanted > spotLevel ? 10 : 4);
        if (card != null && wanted > 0) drawSpot(card);
        spot.setOpacity(spotLevel);

        // Fully faded: snap to 0 so the layers stop being redrawn for invisible changes
        if (glow < 0.004 && spotLevel < 0.004 && !moving) {
            glow = spotLevel = 0;
            halo.setOpacity(0); core.setOpacity(0); spot.setOpacity(0);
        }
    }

    private void placeBlobs(double t) {
        double w = Math.max(1, back.getWidth()), h = Math.max(1, back.getHeight());
        for (int i = 0; i < blobs.length; i++) {
            double[] p = PATHS[i];
            blobs[i].setTranslateX(w * (p[0] + p[2] * Math.sin(t * p[4] * Math.PI * 2 / 3 + p[6])));
            blobs[i].setTranslateY(h * (p[1] + p[3] * Math.cos(t * p[5] * Math.PI * 2 / 3 + p[6])));
        }
    }

    /** Positions the spotlight over {@code c} and lights its border around the cursor. */
    private void drawSpot(Region c) {
        Bounds b = front.sceneToLocal(c.localToScene(c.getLayoutBounds()));
        if (b == null || b.getWidth() <= 0) return;
        spot.resizeRelocate(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());

        // A card scrolled half out of view must not glow outside its scroll pane
        Bounds vis = b;
        for (Parent p = c.getParent(); p != null; p = p.getParent())
            if (p instanceof ScrollPane sp) {
                Bounds v = front.sceneToLocal(sp.localToScene(sp.getLayoutBounds()));
                vis = intersect(vis, v);
            }
        spotClip.setX(vis.getMinX() - b.getMinX());
        spotClip.setY(vis.getMinY() - b.getMinY());
        spotClip.setWidth(Math.max(0, vis.getWidth()));
        spotClip.setHeight(Math.max(0, vis.getHeight()));

        Point2D m = front.sceneToLocal(mx, my);
        double lx = m.getX() - b.getMinX(), ly = m.getY() - b.getMinY();
        double[] key = {b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight(), lx, ly, vis.getWidth(), vis.getHeight()};
        if (java.util.Arrays.equals(key, lastSpot) && c == lastSpotCard) return; // unchanged: skip the redraw
        lastSpot = key;
        lastSpotCard = c;
        CornerRadii radii = radiiOf(c);
        RadialGradient edge = new RadialGradient(0, 0, lx, ly, 190, false, CycleMethod.NO_CYCLE,
            new Stop(0, accent.deriveColor(0, 1, 1, 0.75)), new Stop(1, Color.TRANSPARENT));
        RadialGradient fill = new RadialGradient(0, 0, lx, ly, 320, false, CycleMethod.NO_CYCLE,
            new Stop(0, accent.deriveColor(0, 1, 1, 0.065)), new Stop(1, Color.TRANSPARENT));
        spot.setBackground(new Background(new BackgroundFill(fill, radii, null)));
        spot.setBorder(new Border(new BorderStroke(edge, BorderStrokeStyle.SOLID, radii, new BorderWidths(1))));
    }

    private static CornerRadii radiiOf(Region c) {
        Background bg = c.getBackground();
        if (bg != null && !bg.getFills().isEmpty()) return bg.getFills().get(0).getRadii();
        return new CornerRadii(12);
    }

    private static Bounds intersect(Bounds a, Bounds b) {
        double x1 = Math.max(a.getMinX(), b.getMinX()), y1 = Math.max(a.getMinY(), b.getMinY());
        double x2 = Math.min(a.getMaxX(), b.getMaxX()), y2 = Math.min(a.getMaxY(), b.getMaxY());
        return new javafx.geometry.BoundingBox(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    /** Frame-rate independent easing towards a target ({@code rate} ≈ how many times per second). */
    private static double approach(double from, double to, double dt, double rate) {
        return from + (to - from) * (1 - Math.exp(-dt * rate));
    }

    /** A glow centred on (0,0); {@link #paint} draws it. An image, so moving / fading it is a cheap copy. */
    private static final class Glow extends ImageView {
        final int radius;
        Glow(int radius) {
            this.radius = radius;
            setManaged(false);
            setLayoutX(-radius);   // so translateX/Y place its centre
            setLayoutY(-radius);
            setSmooth(false);      // drawn 1:1; never let scaling blur the dither
        }
    }

    private static Glow glow(int radius) { return new Glow(radius); }

    /**
     * Draws a glow in {@code c}, fading from {@code alpha} to nothing along (1-t²)³ — a curve with no
     * kinks. It's dithered: the glow shines through the panels only faintly, so it spans just a dozen
     * or so screen shades, and without a little noise each shade shows as a ring (most of all on a
     * light theme). The noise is under one shade on screen, so it's invisible; only the rings go.
     */
    private static void paint(Glow g, Color c, double alpha) {
        int r = g.radius, size = r * 2;
        int rgb = ((int) Math.round(c.getRed() * 255) << 16) | ((int) Math.round(c.getGreen() * 255) << 8)
                | (int) Math.round(c.getBlue() * 255);
        int[] px = new int[size * size];
        int seed = 0x9E3779B9;
        double peak = alpha * 255;
        for (int y = 0; y < size; y++) {
            double dy = (y + 0.5 - r) / r, dy2 = dy * dy;
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5 - r) / r, t2 = dx * dx + dy2;
                if (t2 >= 1) continue;
                double f = (1 - t2) * (1 - t2) * (1 - t2);
                // Triangular noise of ±3 alpha steps (xorshift: fast and good enough for dither),
                // faded out at the very edge so it can't leave a speckled rim
                seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
                double n1 = (seed & 0xFFFF) / 65535.0, n2 = ((seed >>> 16) & 0xFFFF) / 65535.0;
                double a = peak * f + (n1 - n2) * 3 * Math.min(1, f * 12);
                int ai = (int) Math.round(a);
                if (ai <= 0) continue;
                px[y * size + x] = (Math.min(255, ai) << 24) | rgb;
            }
        }
        WritableImage img = new WritableImage(size, size);
        img.getPixelWriter().setPixels(0, 0, size, size, PixelFormat.getIntArgbInstance(), px, 0, size);
        g.setImage(img);
    }
}
