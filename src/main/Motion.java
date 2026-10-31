package main;

import javafx.animation.*;
import javafx.beans.value.WritableValue;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Small, reusable UI animations (entrances, pops, ripples, value tweens). */
final class Motion {
    private Motion() {}

    /** Fast start, long soft landing — the "expensive app" feel. */
    static final Interpolator EASE_OUT = Interpolator.SPLINE(0.16, 1, 0.3, 1);
    static final Interpolator EASE_IN_OUT = Interpolator.SPLINE(0.65, 0, 0.35, 1);

    private static final String KEY = "motion.anim";

    /** Fades {@code n} in while it slides into place from ({@code dx}, {@code dy}). */
    static void enter(Node n, double dx, double dy, double ms, double delayMs) {
        stopRunning(n);
        n.setOpacity(0);
        n.setTranslateX(dx);
        n.setTranslateY(dy);
        Timeline t = new Timeline(new KeyFrame(Duration.millis(ms),
            new KeyValue(n.opacityProperty(), 1, EASE_OUT),
            new KeyValue(n.translateXProperty(), 0, EASE_OUT),
            new KeyValue(n.translateYProperty(), 0, EASE_OUT)));
        t.setDelay(Duration.millis(delayMs));
        start(n, t);
    }

    /** A page or panel coming into view. */
    static void enter(Node n) { enter(n, 0, 12, 380, 0); }

    /** Several nodes entering one after another, {@code stepMs} apart. */
    static void stagger(List<? extends Node> nodes, double dx, double dy, double startMs, double stepMs) {
        for (int i = 0; i < nodes.size(); i++) enter(nodes.get(i), dx, dy, 520, startMs + i * stepMs);
    }

    /** A quick scale bump to {@code peak} and back, e.g. when a counter changes. */
    static void pop(Node n, double peak) {
        stopRunning(n);
        Timeline t = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(n.scaleXProperty(), 1),    new KeyValue(n.scaleYProperty(), 1)),
            new KeyFrame(Duration.millis(110), new KeyValue(n.scaleXProperty(), peak, EASE_OUT), new KeyValue(n.scaleYProperty(), peak, EASE_OUT)),
            new KeyFrame(Duration.millis(380), new KeyValue(n.scaleXProperty(), 1, EASE_IN_OUT), new KeyValue(n.scaleYProperty(), 1, EASE_IN_OUT)));
        start(n, t);
    }

    /** Animates any number property (progress, layoutY, …) to {@code to}. */
    static void tween(WritableValue<Number> v, double to, double ms) {
        Animation old = TWEENS.remove(v);
        if (old != null) old.stop(); // retarget from wherever it is now
        Timeline t = new Timeline(new KeyFrame(Duration.millis(ms), new KeyValue(v, to, EASE_OUT)));
        t.setOnFinished(e -> TWEENS.remove(v, t));
        TWEENS.put(v, t);
        t.play();
    }
    private static final Map<WritableValue<Number>, Animation> TWEENS = new WeakHashMap<>();

    /** An endless ring that grows out of a status dot and fades — call play()/stop() on the result. */
    static Animation ripple(Node ring) {
        ring.setOpacity(0);
        Timeline t = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(ring.scaleXProperty(), 1), new KeyValue(ring.scaleYProperty(), 1),
                new KeyValue(ring.opacityProperty(), 0.55)),
            new KeyFrame(Duration.millis(1700),
                new KeyValue(ring.scaleXProperty(), 3.2, EASE_OUT), new KeyValue(ring.scaleYProperty(), 3.2, EASE_OUT),
                new KeyValue(ring.opacityProperty(), 0, EASE_OUT)));
        t.setCycleCount(Animation.INDEFINITE);
        return t;
    }

    /** A dialog's content settling in: fade + slight zoom. */
    static void dialogIn(Node pane) {
        pane.setScaleX(0.965);
        pane.setScaleY(0.965);
        pane.setOpacity(0);
        new Timeline(new KeyFrame(Duration.millis(260),
            new KeyValue(pane.opacityProperty(), 1, EASE_OUT),
            new KeyValue(pane.scaleXProperty(), 1, EASE_OUT),
            new KeyValue(pane.scaleYProperty(), 1, EASE_OUT))).play();
    }

    /** Ends a running enter()/pop() on {@code n} at once, leaving it fully in place. */
    static void settle(Node n) {
        if (!(n.getProperties().get(KEY) instanceof Animation)) return;
        stopRunning(n);
        n.setOpacity(1);
        n.setTranslateX(0); n.setTranslateY(0);
        n.setScaleX(1);     n.setScaleY(1);
    }

    // One running animation per node, so a quick second call doesn't fight the first
    private static void start(Node n, Animation a) {
        n.getProperties().put(KEY, a);
        a.setOnFinished(e -> n.getProperties().remove(KEY, a));
        a.play();
    }

    private static void stopRunning(Node n) {
        if (n.getProperties().remove(KEY) instanceof Animation a) a.stop();
    }
}
