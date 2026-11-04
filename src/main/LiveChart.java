package main;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.*;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * A live time-series chart drawn on a canvas: a smooth line with a soft gradient fill, real clock
 * times along the bottom, unit-labelled values up the side, an optional reference line (a target or a
 * limit), colors that turn amber / red as a value gets worse, a pulsing dot on the newest reading
 * and a crosshair with the exact value and time under the mouse. With no data it says so, instead
 * of showing empty axes. In sparkline mode it's just the line, for the summary tiles.
 */
final class LiveChart extends Region {

    /** Readings over time, oldest first, in a fixed-size ring (about 2 h at one every 2 s). */
    static final class Series {
        private final long[] t;
        private final double[] v;
        private int start, size;

        Series(int capacity) { t = new long[capacity]; v = new double[capacity]; }

        void add(long timeMillis, double value) {
            int i = (start + size) % t.length;
            t[i] = timeMillis; v[i] = value;
            if (size < t.length) size++; else start = (start + 1) % t.length;
        }
        int size() { return size; }
        long time(int i) { return t[(start + i) % t.length]; }
        double value(int i) { return v[(start + i) % t.length]; }
        double last() { return size == 0 ? Double.NaN : value(size - 1); }
        long lastTime() { return size == 0 ? 0 : time(size - 1); }
    }

    /** How good a value is: higher is better (TPS) or lower is better (tick time). */
    record Thresholds(double warn, double bad, boolean higherIsBetter) {}

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Font LABEL = Font.font("Segoe UI", 10.5);
    private static final Font TIP_VALUE = Font.font("Segoe UI", FontWeight.BOLD, 13);
    private static final Font TIP_TIME = Font.font("Segoe UI", 11);
    private static final Font EMPTY_TITLE = Font.font("Segoe UI", FontWeight.BOLD, 13);
    private static final Font EMPTY_SUB = Font.font("Segoe UI", 12);

    private final Canvas canvas = new Canvas();
    private final Series series;
    private final boolean sparkline;

    // Look and behaviour (set by the page)
    private DoubleFunction<String> axisFormat = v -> String.format("%.0f", v);
    private DoubleFunction<String> valueFormat = v -> String.format("%.1f", v);
    private double yMin = 0, yMax = Double.NaN, minSpan = 1;
    private boolean integers, stepped;
    private Thresholds thresholds;
    private DoubleSupplier reference;            // NaN = none
    private java.util.function.Supplier<String> referenceLabel = () -> "";
    private long expectedGapMs = 2000;           // a longer pause between readings breaks the line
    private long windowMs = 5 * 60_000L;
    /** When the history starts (shared by the page's charts, so their time axes line up). */
    private java.util.function.LongSupplier start = () -> Long.MIN_VALUE;
    private static final long MIN_SPAN_MS = 10_000;
    private static final long[] TICKS = {5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000,
                                         900_000, 1_200_000, 1_800_000, 3_600_000};
    private String emptyTitle = "No data yet", emptySub = "";
    private Theme theme = Theme.DEFAULT;

    private double hoverX = Double.NaN;

    LiveChart(Series series, boolean sparkline) {
        this.series = series;
        this.sparkline = sparkline;
        getChildren().add(canvas);
        getStyleClass().add(sparkline ? "sparkline" : "live-chart");
        if (!sparkline) {
            setOnMouseMoved(e -> hoverX = e.getX());
            setOnMouseExited(e -> hoverX = Double.NaN);
        }
    }

    // ─── Configuration ───────────────────────────────────────────────

    LiveChart axis(DoubleFunction<String> axisFormat, DoubleFunction<String> valueFormat) {
        this.axisFormat = axisFormat; this.valueFormat = valueFormat; return this;
    }
    /** Fixed range; pass NaN as max to fit the data (never narrower than {@code minSpan}). */
    LiveChart range(double min, double max, double minSpan) { yMin = min; yMax = max; this.minSpan = minSpan; return this; }
    LiveChart integers() { integers = true; return this; }
    LiveChart stepped() { stepped = true; return this; }
    LiveChart thresholds(Thresholds t) { thresholds = t; return this; }
    LiveChart reference(DoubleSupplier value, String label) { return reference(value, () -> label); }
    LiveChart reference(DoubleSupplier value, java.util.function.Supplier<String> label) { reference = value; referenceLabel = label; return this; }
    LiveChart gap(long expectedMs) { expectedGapMs = expectedMs; return this; }
    LiveChart empty(String title, String sub) { emptyTitle = title; emptySub = sub; return this; }

    void setWindow(long ms) { windowMs = ms; }
    LiveChart startAt(java.util.function.LongSupplier historyStart) { start = historyStart; return this; }
    void setTheme(Theme t) { theme = t; }

    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
    }

    // ─── Drawing ─────────────────────────────────────────────────────

    /** Draws the chart as of {@code now} (the page redraws every frame, so time scrolls smoothly). */
    void draw(long now) {
        double w = canvas.getWidth(), h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        if (w < 20 || h < 20) return;

        Color text3 = theme.color("text-3"), line = theme.color("line"), accent = theme.color("accent");
        double padL = sparkline ? 1 : 50, padR = sparkline ? 4 : 14, padT = sparkline ? 4 : 14, padB = sparkline ? 2 : 26;
        double pw = w - padL - padR, ph = h - padT - padB;
        // Shown span: the chosen range, or — while there isn't that much history yet — just the
        // history there is, so the line starts at the left edge instead of a sliver at "now"
        long earliest = start.getAsLong();
        long span = earliest == Long.MIN_VALUE || earliest >= now ? windowMs
                  : Math.min(windowMs, Math.max(MIN_SPAN_MS, now - earliest));
        long from = now - span;

        // Readings in view (plus one just before it, so the line enters from the left edge)
        int first = series.size();
        for (int i = series.size() - 1; i >= 0; i--) {
            if (series.time(i) < from) { first = i; break; }
            first = i;
        }
        boolean any = false;
        for (int i = first; i < series.size(); i++) if (series.time(i) >= from) { any = true; break; }
        if (!any) {
            if (!sparkline) drawEmpty(g, w, h, text3);
            return;
        }

        // Value range: fixed, or fitted to what's in view (and to the reference line)
        double lo = yMin, hi = yMax;
        if (Double.isNaN(hi)) {
            double max = Double.NEGATIVE_INFINITY;
            for (int i = first; i < series.size(); i++) max = Math.max(max, series.value(i));
            double ref = reference != null ? reference.getAsDouble() : Double.NaN;
            if (!Double.isNaN(ref)) max = Math.max(max, ref);
            hi = niceCeil(Math.max(lo + minSpan, max * 1.15), lo);
        }
        double yLo = lo, yHi = hi;
        java.util.function.DoubleUnaryOperator Y = v -> padT + ph - (Math.max(yLo, Math.min(yHi, v)) - yLo) / (yHi - yLo) * ph;
        java.util.function.LongToDoubleFunction X = t -> padL + (t - from) / (double) span * pw;

        if (!sparkline) drawAxes(g, padL, padT, pw, ph, lo, hi, from, now, text3, line, Y);

        // Colors: the line by how good each value is (or the theme accent); the area under it is one
        // soft tint in the current state's color, fading out downwards
        Paint stroke = thresholds != null ? valueGradient(padT, ph, lo, hi, 1.0) : accent;
        Color fillTop = colorFor(series.last(), accent).deriveColor(0, 1, 1, sparkline ? 0.22 : 0.26);

        g.save();
        g.beginPath();
        g.rect(padL, padT - 6, pw + 6, ph + 12);
        g.clip();

        // The line, split where the server was off (no readings for a while)
        int segStart = first;
        for (int i = first + 1; i <= series.size(); i++) {
            boolean breakHere = i == series.size() || series.time(i) - series.time(i - 1) > expectedGapMs * 3;
            if (!breakHere) continue;
            drawSegment(g, segStart, i - 1, X, Y, stroke, fillTop, padT, ph, lo, hi);
            segStart = i;
        }

        // The newest reading: a dot with a slow pulse, when it's recent
        int lastI = series.size() - 1;
        if (now - series.time(lastI) < expectedGapMs * 3) {
            double lx = X.applyAsDouble(series.time(lastI)), ly = Y.applyAsDouble(series.value(lastI));
            Color c = colorFor(series.value(lastI), accent);
            double pulse = (now % 2000) / 2000.0;
            g.setFill(c.deriveColor(0, 1, 1, 0.35 * (1 - pulse)));
            double pr = (sparkline ? 3 : 4) + pulse * (sparkline ? 6 : 9);
            g.fillOval(lx - pr, ly - pr, pr * 2, pr * 2);
            double r = sparkline ? 2.5 : 3.5;
            g.setFill(c);
            g.fillOval(lx - r, ly - r, r * 2, r * 2);
        }
        g.restore();

        if (!sparkline && !Double.isNaN(hoverX) && hoverX >= padL && hoverX <= padL + pw)
            drawHover(g, first, from, X, Y, padT, ph, w, accent, text3);
    }

    private void drawSegment(GraphicsContext g, int a, int b, java.util.function.LongToDoubleFunction X,
                             java.util.function.DoubleUnaryOperator Y, Paint stroke, Color fillTop,
                             double padT, double ph, double lo, double hi) {
        int n = b - a + 1;
        if (n < 1) return;
        // Many readings for few pixels (a long window): average them per pixel column
        double[] xs = new double[n], ys = new double[n];
        int m = 0;
        double bucketX = Double.NaN, sumY = 0; int count = 0;
        for (int i = a; i <= b; i++) {
            double x = X.applyAsDouble(series.time(i)), y = Y.applyAsDouble(series.value(i));
            if (!stepped && !Double.isNaN(bucketX) && Math.floor(x) == Math.floor(bucketX)) { sumY += y; count++; continue; }
            if (count > 0) { xs[m] = bucketX; ys[m] = sumY / count; m++; }
            bucketX = x; sumY = y; count = 1;
        }
        if (count > 0) { xs[m] = bucketX; ys[m] = sumY / count; m++; }

        double base = padT + ph;
        if (m == 1) {   // a single reading: a short dash so it's visible
            g.setStroke(stroke); g.setLineWidth(sparkline ? 1.6 : 2.2);
            g.strokeLine(xs[0] - 2, ys[0], xs[0] + 2, ys[0]);
            return;
        }
        // Area under the line
        g.beginPath();
        tracePath(g, xs, ys, m);
        g.lineTo(xs[m - 1], base);
        g.lineTo(xs[0], base);
        g.closePath();
        // Fades out within about half the chart's height below the line's highest point, so a steady
        // line near the top (TPS at 20) is a soft glow, not a solid block
        double top = ys[0];
        for (int i = 1; i < m; i++) top = Math.min(top, ys[i]);
        double fadeTo = Math.min(base, top + Math.max(30, ph * 0.5));
        g.setFill(new LinearGradient(0, top, 0, fadeTo, false, CycleMethod.NO_CYCLE,
            new Stop(0, fillTop), new Stop(1, fillTop.deriveColor(0, 1, 1, 0))));
        g.fill();
        // The line itself
        g.beginPath();
        tracePath(g, xs, ys, m);
        g.setStroke(stroke);
        g.setLineWidth(sparkline ? 1.6 : 2.2);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.stroke();
    }

    /** A smooth curve that never overshoots the readings (monotone cubic), or steps for counts. */
    private void tracePath(GraphicsContext g, double[] x, double[] y, int n) {
        g.moveTo(x[0], y[0]);
        if (stepped) {
            for (int i = 1; i < n; i++) { g.lineTo(x[i], y[i - 1]); g.lineTo(x[i], y[i]); }
            return;
        }
        double[] d = new double[n - 1], m = new double[n];
        for (int i = 0; i < n - 1; i++) d[i] = (y[i + 1] - y[i]) / Math.max(1e-9, x[i + 1] - x[i]);
        m[0] = d[0]; m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2;
        for (int i = 0; i < n - 1; i++) {   // Fritsch–Carlson: keep it monotone between readings
            if (d[i] == 0) { m[i] = m[i + 1] = 0; continue; }
            double a = m[i] / d[i], b = m[i + 1] / d[i], s = a * a + b * b;
            if (s > 9) { double t = 3 / Math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i]; }
        }
        for (int i = 0; i < n - 1; i++) {
            double h = x[i + 1] - x[i];
            g.bezierCurveTo(x[i] + h / 3, y[i] + m[i] * h / 3, x[i + 1] - h / 3, y[i + 1] - m[i + 1] * h / 3, x[i + 1], y[i + 1]);
        }
    }

    private void drawAxes(GraphicsContext g, double padL, double padT, double pw, double ph, double lo, double hi,
                          long from, long now, Color text3, Color line,
                          java.util.function.DoubleUnaryOperator Y) {
        g.setFont(LABEL);
        g.setLineWidth(1);
        // Values: 3–5 round steps, labelled with their unit
        double step = niceStep((hi - lo) / Math.max(2, Math.min(4, ph / 42)));
        if (integers) step = Math.max(1, Math.round(step));
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        for (double v = lo; v <= hi + step * 0.001; v += step) {
            double y = Math.round(Y.applyAsDouble(v)) + 0.5;
            g.setStroke(line);
            g.setLineDashes(v == lo ? null : new double[]{3, 4});
            g.strokeLine(padL, y, padL + pw, y);
            g.setFill(text3);
            g.fillText(axisFormat.apply(v), padL - 10, y);
        }
        g.setLineDashes(null);

        // Reference line: a target or a limit, labelled
        if (reference != null) {
            double ref = reference.getAsDouble();
            if (!Double.isNaN(ref) && ref >= lo && ref <= hi) {
                double y = Math.round(Y.applyAsDouble(ref)) + 0.5;
                Color c = theme.color("text-2").deriveColor(0, 1, 1, 0.55);
                g.setStroke(c);
                g.setLineDashes(6, 5);
                g.strokeLine(padL, y, padL + pw, y);
                g.setLineDashes(null);
                g.setFill(theme.color("text-2"));
                g.setTextAlign(TextAlignment.RIGHT);
                g.setTextBaseline(VPos.BOTTOM);
                g.fillText(referenceLabel.get(), padL + pw, y - 3);
            }
        }

        // Clock times along the bottom, on round times: the smallest step that leaves room between
        // labels (seconds while the history is short, minutes later)
        long span = now - from;
        boolean seconds = span < 4 * 60_000L;
        int room = (int) Math.max(1, pw / (seconds ? 78 : 62));
        long tick = TICKS[TICKS.length - 1];
        for (long t : TICKS) if (span / t <= room) { tick = t; break; }
        if (!seconds) tick = Math.max(tick, 60_000L);
        DateTimeFormatter fmt = tick < 60_000L ? HHMMSS : HHMM;
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        g.setFill(text3);
        for (long t = (from / tick + 1) * tick; t <= now; t += tick) {
            double x = padL + (t - from) / (double) span * pw;
            if (x < padL + 22 || x > padL + pw - 60) continue;   // keep clear of the edges and of "now"
            g.fillText(fmt.format(Instant.ofEpochMilli(t)), x, padT + ph + 8);
        }
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText("now", padL + pw, padT + ph + 8);
    }

    private void drawHover(GraphicsContext g, int first, long from, java.util.function.LongToDoubleFunction X,
                           java.util.function.DoubleUnaryOperator Y, double padT, double ph, double w,
                           Color accent, Color text3) {
        int best = -1; double bestD = Double.MAX_VALUE;
        for (int i = first; i < series.size(); i++) {
            if (series.time(i) < from) continue;
            double d = Math.abs(X.applyAsDouble(series.time(i)) - hoverX);
            if (d < bestD) { bestD = d; best = i; }
        }
        if (best < 0 || bestD > 40) return;
        double x = X.applyAsDouble(series.time(best)), y = Y.applyAsDouble(series.value(best));
        Color c = colorFor(series.value(best), accent);

        g.setStroke(text3.deriveColor(0, 1, 1, 0.6));
        g.setLineWidth(1);
        g.setLineDashes(2, 3);
        g.strokeLine(Math.round(x) + 0.5, padT, Math.round(x) + 0.5, padT + ph);
        g.setLineDashes(null);
        g.setFill(theme.color("bg"));
        g.fillOval(x - 5.5, y - 5.5, 11, 11);
        g.setFill(c);
        g.fillOval(x - 4, y - 4, 8, 8);

        // Tooltip: the value, and when
        String value = valueFormat.apply(series.value(best));
        String time = HHMMSS.format(Instant.ofEpochMilli(series.time(best)));
        double tw = Math.max(textWidth(value, TIP_VALUE), textWidth(time, TIP_TIME)) + 22, th = 42;
        double tx = x + 14 + tw > w - 4 ? x - 14 - tw : x + 14;
        double ty = Math.max(padT, Math.min(y - th / 2, padT + ph - th));
        g.setFill(theme.color("surface-3").deriveColor(0, 1, 1, 0.96));
        g.fillRoundRect(tx, ty, tw, th, 12, 12);
        g.setStroke(theme.color("line-2"));
        g.strokeRoundRect(tx + 0.5, ty + 0.5, tw - 1, th - 1, 12, 12);
        g.setFill(c);
        g.fillRoundRect(tx + 9, ty + 9, 3, 13, 2, 2);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        g.setFont(TIP_VALUE);
        g.setFill(theme.color("text"));
        g.fillText(value, tx + 17, ty + 7);
        g.setFont(TIP_TIME);
        g.setFill(text3);
        g.fillText(time, tx + 11, ty + 24);
    }

    private void drawEmpty(GraphicsContext g, double w, double h, Color text3) {
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.setFont(EMPTY_TITLE);
        g.setFill(theme.color("text-2"));
        g.fillText(emptyTitle, w / 2, h / 2 - 9);
        g.setFont(EMPTY_SUB);
        g.setFill(text3);
        g.fillText(emptySub, w / 2, h / 2 + 11);
    }

    // ─── Colors by value ─────────────────────────────────────────────

    /** The status color of a value: green / amber / red by the thresholds, else the accent. */
    Color colorFor(double v, Color accent) {
        if (thresholds == null || Double.isNaN(v)) return accent;
        boolean bad = thresholds.higherIsBetter ? v < thresholds.bad : v > thresholds.bad;
        boolean warn = thresholds.higherIsBetter ? v < thresholds.warn : v > thresholds.warn;
        return theme.color(bad ? "danger" : warn ? "warn" : "ok");
    }

    /** A vertical gradient that colors the line by value: green, through amber, to red. */
    private LinearGradient valueGradient(double padT, double ph, double lo, double hi, double alpha) {
        Color ok = theme.color("ok").deriveColor(0, 1, 1, alpha), warn = theme.color("warn").deriveColor(0, 1, 1, alpha),
              bad = theme.color("danger").deriveColor(0, 1, 1, alpha);
        double fw = (thresholds.warn - lo) / (hi - lo), fb = (thresholds.bad - lo) / (hi - lo);   // 0 = bottom
        // Gradient runs top → bottom, so a position p from the bottom is offset 1 - p
        double ow = clamp01(1 - fw), ob = clamp01(1 - fb);
        Color top = thresholds.higherIsBetter ? ok : bad, bottom = thresholds.higherIsBetter ? bad : ok;
        double first = Math.min(ow, ob), second = Math.max(ow, ob);
        return new LinearGradient(0, padT, 0, padT + ph, false, CycleMethod.NO_CYCLE,
            new Stop(0, top), new Stop(Math.max(0, first - 0.02), top), new Stop(first, warn),
            new Stop(second, warn), new Stop(Math.min(1, second + 0.02), bottom), new Stop(1, bottom));
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /** 1, 2, 2.5 or 5 × a power of ten, near {@code raw}. */
    static double niceStep(double raw) {
        if (raw <= 0 || Double.isNaN(raw)) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(raw))), f = raw / p;
        return (f < 1.5 ? 1 : f < 2.25 ? 2 : f < 3.5 ? 2.5 : f < 7.5 ? 5 : 10) * p;
    }

    /** The next round value at or above {@code v}, as an axis top. */
    static double niceCeil(double v, double lo) {
        double step = niceStep((v - lo) / 4);
        return lo + Math.ceil((v - lo) / step) * step;
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    private static double textWidth(String s, Font f) {
        Text t = new Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }
}
