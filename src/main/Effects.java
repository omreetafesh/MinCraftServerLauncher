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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.Set;

/**
 * The window's ambient lighting, drawn on two mouse-transparent layers around the UI:
 * <ul>
 *   <li><b>back</b> (behind the translucent panels): slowly drifting aurora blobs, and a soft emerald
 *       glow that trails the cursor and fades out once the mouse rests or leaves the window;</li>
 *   <li><b>front</b> (over the UI): a spotlight that lights up the border of the card under the cursor.</li>
 * </ul>
 * One AnimationTimer drives it all and stops itself when nothing is moving, so an idle launcher
 * costs nothing beyond the slow aurora (redrawn at ~20 fps), which can be switched off.
 */
final class Effects {

    private static final Color ACCENT = Color.web("#34d399");
    /** Style classes of the panels the spotlight attaches to. */
    private static final Set<String> CARD_CLASSES =
        Set.of("card", "rail", "command-bar", "props-section", "stats-card", "profile-card");

    private static final long IDLE_NS = 650_000_000L;   // the glow starts fading this long after the last move
    private static final long AMBIENT_FRAME_NS = 83_000_000L; // ~12 fps: the blobs drift a few px per second

    private final Pane back, front;

    private final Circle halo = glow(240, ACCENT, 0.22);                 // big, lags behind the cursor
    private final Circle core = glow(80, Color.web("#6ee7b7"), 0.22);    // small, hugs the cursor
    private final Circle[] blobs = {
        glow(520, Color.web("#10b981"), 0.13),
        glow(460, Color.web("#3b82f6"), 0.09),
        glow(400, Color.web("#8b5cf6"), 0.07),
    };
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
            new Stop(0, ACCENT.deriveColor(0, 1, 1, 0.75)), new Stop(1, Color.TRANSPARENT));
        RadialGradient fill = new RadialGradient(0, 0, lx, ly, 320, false, CycleMethod.NO_CYCLE,
            new Stop(0, ACCENT.deriveColor(0, 1, 1, 0.065)), new Stop(1, Color.TRANSPARENT));
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

    /**
     * A circle centred on (0,0) whose fill fades from {@code alpha} to nothing along (1-t²)³ — a curve
     * with no kinks, sampled finely, so the dark gradient doesn't show visible rings.
     */
    private static Circle glow(double radius, Color c, double alpha) {
        Circle g = new Circle(radius);
        g.setManaged(false);
        int n = 16;
        Stop[] stops = new Stop[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, f = Math.pow(1 - t * t, 3);
            stops[i] = new Stop(t, c.deriveColor(0, 1, 1, alpha * f));
        }
        g.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE, stops));
        // Rendered once into a texture; moving / fading it is then a cheap copy, not a new gradient
        g.setCache(true);
        g.setCacheHint(javafx.scene.CacheHint.SPEED);
        return g;
    }
}
