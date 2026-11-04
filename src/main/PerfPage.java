package main;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The Performance page: summary tiles for what matters right now (TPS, tick time, memory, CPU,
 * players), each with a 5-minute sparkline, and below them full charts with a 5 min / 30 min / 2 h
 * range. Every chart says in one line what it measures and what "good" looks like.
 * All numbers are the server's own (not the whole PC's) and keep their history across restarts,
 * with a gap where the server was off.
 */
final class PerfPage {

    private static final int HISTORY = 3700;   // ~2 h at one reading every 2 s

    // History
    final LiveChart.Series tps = new LiveChart.Series(HISTORY), mspt = new LiveChart.Series(HISTORY),
        heap = new LiveChart.Series(HISTORY), cpu = new LiveChart.Series(HISTORY), players = new LiveChart.Series(HISTORY);
    private double heapMax = Double.NaN, heapTaken = Double.NaN, p95 = Double.NaN, p99 = Double.NaN;
    private int maxPlayers = 20, peakPlayers, threads;
    private long historyStart = Long.MIN_VALUE;   // first reading of any kind: where the charts begin
    private long gcCount = -1;
    private boolean up, memoryUnavailable, tpsUnsupported;   // up: the server is running

    private final List<LiveChart> charts = new ArrayList<>(), sparks = new ArrayList<>();
    private final Tile tpsTile, tickTile, memTile, cpuTile, playerTile;
    private final LiveChart tpsChart, tickChart, memChart, cpuChart, playerChart;
    private final Label tpsNow = new Label("–"), tickNow = new Label("–"), memNow = new Label("–"),
        cpuNow = new Label("–"), playersNow = new Label("–");
    private boolean active;

    private final AnimationTimer frame = new AnimationTimer() {
        private long last, lastTiles;
        @Override public void handle(long now) {
            if (now - last < 33_000_000L) return;   // ~30 fps is plenty for a slow scroll
            last = now;
            long t = System.currentTimeMillis();
            if (t - lastTiles > 1000) { lastTiles = t; refreshTiles(); }   // so stale numbers clear
            for (LiveChart c : charts) c.draw(t);
            for (LiveChart c : sparks) c.draw(t);
        }
    };

    PerfPage(VBox container) {
        // ── Charts ──
        tpsChart = chart(tps).range(0, 22, 1).thresholds(new LiveChart.Thresholds(18, 15, true))
            .reference(() -> 20, "Target · 20 TPS").gap(5000)
            .axis(v -> String.format("%.0f", v), v -> String.format("%.1f TPS", v));
        tickChart = chart(mspt).range(0, Double.NaN, 60).thresholds(new LiveChart.Thresholds(40, 50, false))
            .reference(() -> 50, "Lag above 50 ms").gap(5000)
            .axis(v -> String.format("%.0f ms", v), v -> String.format("%.1f ms per tick", v));
        memChart = chart(heap).range(0, Double.NaN, 0.5)
            .reference(() -> heapMax, () -> "Limit · " + size(heapMax * 1024))
            .axis(v -> num(v) + " GB", v -> size(v * 1024) + " used");
        cpuChart = chart(cpu).range(0, Double.NaN, 25).thresholds(new LiveChart.Thresholds(70, 90, false))
            .axis(v -> String.format("%.0f%%", v), v -> String.format("%.1f%% CPU", v));
        playerChart = chart(players).range(0, Double.NaN, 4).integers().stepped()
            .axis(v -> String.format("%.0f", v), v -> String.format("%.0f player%s", v, v == 1 ? "" : "s"));
        for (LiveChart c : charts) c.empty("Waiting for the server", "Start it and live numbers show up here within seconds");

        // ── Tiles ──
        tpsTile = tile("TPS", "activity", spark(tps).range(0, 22, 1).thresholds(new LiveChart.Thresholds(18, 15, true)).gap(5000));
        tickTile = tile("Tick time", "timer", spark(mspt).range(0, Double.NaN, 60).thresholds(new LiveChart.Thresholds(40, 50, false)).gap(5000));
        memTile = tile("Memory", "memory", spark(heap).range(0, Double.NaN, 0.5));
        cpuTile = tile("CPU", "cpu", spark(cpu).range(0, Double.NaN, 25));
        playerTile = tile("Players", "users", spark(players).range(0, Double.NaN, 4).stepped());
        HBox tiles = new HBox(12, tpsTile.box, tickTile.box, memTile.box, cpuTile.box, playerTile.box);
        tiles.getStyleClass().add("perf-tiles");
        for (Node n : tiles.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // ── Range ──
        Label hist = new Label("History");
        hist.getStyleClass().add("perf-section");
        HBox range = new HBox(2);
        range.getStyleClass().add("seg-group");
        ToggleGroup group = new ToggleGroup();
        long[] windows = {5 * 60_000L, 30 * 60_000L, 120 * 60_000L};
        String[] names = {"5 min", "30 min", "2 hours"};
        for (int i = 0; i < windows.length; i++) {
            ToggleButton b = new ToggleButton(names[i]);
            b.getStyleClass().add("seg");
            b.setToggleGroup(group);
            long w = windows[i];
            b.setOnAction(e -> { b.setSelected(true); for (LiveChart c : charts) c.setWindow(w); });
            if (i == 0) b.setSelected(true);
            range.getChildren().add(b);
        }
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox rangeRow = new HBox(hist, gap, range);
        rangeRow.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("perf-grid");
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);
        grid.add(card("TPS", "Ticks per second. 20 is perfect; players notice lag below about 18.", tpsNow, tpsChart), 0, 0);
        grid.add(card("Tick time", "Time the server spends on each tick. Under 50 ms keeps a steady 20 TPS.", tickNow, tickChart), 1, 0);
        grid.add(card("Memory", "Java memory in use, out of what you gave the server. A sawtooth is normal: that's garbage collection.", memNow, memChart), 0, 1);
        grid.add(card("CPU", "How much of your PC's processor the server uses (100% = every core busy).", cpuNow, cpuChart), 1, 1);
        grid.add(card("Players online", "Who's been on, and when.", playersNow, playerChart), 0, 2, 2, 1);

        container.getChildren().setAll(tiles, rangeRow, grid);
        refreshTiles();
    }

    // ─── Pieces ──────────────────────────────────────────────────────

    private LiveChart chart(LiveChart.Series s) {
        LiveChart c = new LiveChart(s, false).startAt(() -> historyStart);
        c.setMinHeight(190);
        c.setPrefHeight(210);
        charts.add(c);
        return c;
    }

    private LiveChart spark(LiveChart.Series s) {
        LiveChart c = new LiveChart(s, true).startAt(() -> historyStart);
        c.setMinHeight(34);
        c.setPrefHeight(34);
        c.setWindow(5 * 60_000L);
        sparks.add(c);
        return c;
    }

    private static VBox card(String title, String about, Label now, LiveChart chart) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label a = new Label(about);
        a.getStyleClass().add("perf-about");
        a.setWrapText(true);
        now.getStyleClass().add("perf-now");
        now.setMinWidth(Region.USE_PREF_SIZE);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        VBox text = new VBox(3, t, a);
        HBox.setHgrow(text, Priority.SOMETIMES);
        HBox head = new HBox(12, text, gap, now);
        head.setAlignment(Pos.TOP_LEFT);
        VBox card = new VBox(10, head, chart);
        card.getStyleClass().addAll("card", "perf-card");
        VBox.setVgrow(chart, Priority.ALWAYS);
        GridPane.setHgrow(card, Priority.ALWAYS);
        GridPane.setFillWidth(card, true);
        return card;
    }

    /** A summary tile: what, the number now, a word on how it's doing, and the last 5 minutes. */
    private static final class Tile {
        final VBox box;
        final Label value = new Label("–"), unit = new Label(), sub = new Label(), chip = new Label();
        Tile(String name, String icon, LiveChart spark) {
            Label caption = new Label(name, new Icon(icon, 14));
            caption.getStyleClass().add("tile-caption");
            chip.getStyleClass().add("tile-chip");
            chip.setVisible(false);
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox top = new HBox(6, caption, gap, chip);
            top.setAlignment(Pos.CENTER_LEFT);
            value.getStyleClass().add("tile-value");
            unit.getStyleClass().add("tile-unit");
            HBox big = new HBox(5, value, unit);
            big.setAlignment(Pos.BASELINE_LEFT);
            sub.getStyleClass().add("tile-sub");
            box = new VBox(4, top, big, sub, spark);
            box.getStyleClass().addAll("card", "perf-tile");
            box.setMinWidth(150);
        }
        void set(String v, String u, String s) { value.setText(v); unit.setText(u); sub.setText(s); }
        void chip(String text, String state) {
            chip.setVisible(text != null);
            chip.setText(text == null ? "" : text);
            chip.getStyleClass().removeAll("chip-ok", "chip-warn", "chip-bad");
            if (state != null) chip.getStyleClass().add("chip-" + state);
        }
    }

    private Tile tile(String name, String icon, LiveChart spark) { return new Tile(name, icon, spark); }

    // ─── Readings ────────────────────────────────────────────────────

    /** Memory / CPU / threads, every 2 s while the server runs (from {@link ServerStats}). */
    void onStats(ServerStats.Sample s) {
        began(s.time());

        if (!Double.isNaN(s.heapUsedMB())) {
            heap.add(s.time(), s.heapUsedMB() / 1024);
            heapMax = s.heapMaxMB() / 1024;
            heapTaken = s.heapTakenMB() / 1024;
            threads = s.threads();
            gcCount = s.gcCount();
            memoryUnavailable = false;
        } else {
            memoryUnavailable = true;
            memChart.empty("Memory details unavailable", "This server's Java doesn't allow live monitoring");
        }
        if (!Double.isNaN(s.cpu())) cpu.add(s.time(), s.cpu() * 100);
        refreshTiles();
    }

    /** From "tick query": average milliseconds per tick (and so TPS). */
    void onTick(long time, double msPerTick, double targetMs) {
        began(time);
        mspt.add(time, msPerTick);
        tps.add(time, Math.min(1000.0 / targetMs, 1000.0 / Math.max(msPerTick, 1e-6)));
        refreshTiles();
    }

    /** From "tick query": how slow the slowest ticks were. */
    void onPercentiles(double p95, double p99) {
        this.p95 = p95;
        this.p99 = p99;
        refreshTiles();
    }

    void onPlayers(long time, int count, int max) {
        began(time);
        players.add(time, count);
        maxPlayers = max;
        peakPlayers = Math.max(peakPlayers, count);
        refreshTiles();
    }

    private void began(long time) {
        if (historyStart == Long.MIN_VALUE || time < historyStart) historyStart = time;
    }

    void serverStarted() {
        up = true;
        refreshTiles();
    }

    void serverStopped() {
        up = false;
        refreshTiles();
    }

    /** The server doesn't have "tick query" (Minecraft before 1.20.3). */
    void setTpsUnsupported() {
        tpsUnsupported = true;
        tpsChart.empty("TPS isn't available", "Live TPS needs Minecraft 1.20.3 or newer");
        tickChart.empty("Tick time isn't available", "It needs Minecraft 1.20.3 or newer");
        refreshTiles();
    }

    private void refreshTiles() {
        // Once the server is down, its last readings are history (in the charts), not "now"
        double t = up ? fresh(tps, 15_000) : Double.NaN, ms = up ? fresh(mspt, 15_000) : Double.NaN,
               m = up ? fresh(heap, 8_000) : Double.NaN, c = up ? fresh(cpu, 8_000) : Double.NaN;
        String off = up ? "Starting up…" : "Server offline";

        if (!Double.isNaN(t)) {
            tpsTile.set(String.format("%.1f", t), "TPS", t >= 19.5 ? "Running at full speed" : String.format("%.0f%% of full speed", t / 20 * 100));
            if (t >= 19.5) tpsTile.chip("Excellent", "ok");
            else if (t >= 18) tpsTile.chip("Good", "ok");
            else if (t >= 15) tpsTile.chip("Laggy", "warn");
            else tpsTile.chip("Struggling", "bad");
            tpsNow.setText(String.format("%.1f TPS", t));
        } else {
            tpsTile.set("–", "", tpsUnsupported ? "Needs Minecraft 1.20.3+" : off);
            tpsTile.chip(null, null);
            tpsNow.setText("");
        }

        if (!Double.isNaN(ms)) {
            tickTile.set(num(ms), "ms", Double.isNaN(p95) ? "average per tick"
                : "slowest 5%: " + num(p95) + " ms");
            tickTile.chip(ms <= 40 ? "Healthy" : ms <= 50 ? "Busy" : "Overloaded", ms <= 40 ? "ok" : ms <= 50 ? "warn" : "bad");
            tickNow.setText(String.format("%.1f ms", ms));
        } else {
            tickTile.set("–", "", tpsUnsupported ? "Needs Minecraft 1.20.3+" : off);
            tickTile.chip(null, null);
            tickNow.setText("");
        }

        if (!Double.isNaN(m)) {
            double pct = m / heapMax * 100;
            memTile.set(num(m), "GB", "holds " + num(heapTaken) + " GB of RAM · max " + num(heapMax));
            memTile.chip(pct < 75 ? "Plenty free" : pct < 90 ? "Getting full" : "Nearly full", pct < 75 ? "ok" : pct < 90 ? "warn" : "bad");
            memNow.setText(size(m * 1024) + " used · " + size(heapTaken * 1024) + " held");
        } else {
            memTile.set("–", "", memoryUnavailable && up ? "Not available for this server" : off);
            memTile.chip(null, null);
            memNow.setText("");
        }

        if (!Double.isNaN(c)) {
            cpuTile.set(num(c), "%", threads > 0 ? "server · " + threads + " threads" : "server process");
            cpuTile.chip(null, null);
            cpuNow.setText(num(c) + "%");
        } else {
            cpuTile.set("–", "", off);
            cpuNow.setText("");
        }

        double p = up ? fresh(players, 8_000) : Double.NaN;
        if (!Double.isNaN(p)) {
            playerTile.set(String.format("%.0f", p), "/ " + maxPlayers, "peak " + peakPlayers + " this session");
            playersNow.setText(String.format("%.0f online", p));
        } else {
            playerTile.set("–", "", off);
            playersNow.setText("");
        }
    }

    /** The newest reading if it's recent, else NaN (stale numbers would mislead). */
    private static double fresh(LiveChart.Series s, long maxAgeMs) {
        return s.size() > 0 && System.currentTimeMillis() - s.lastTime() <= maxAgeMs ? s.last() : Double.NaN;
    }

    /** One decimal below 10 (0.4, 2.5), none above (37). A whole number loses its ".0". */
    static String num(double v) {
        String s = v < 10 ? String.format("%.1f", v) : String.format("%.0f", v);
        return s.endsWith(".0") || s.endsWith(",0") ? s.substring(0, s.length() - 2) : s;
    }

    /** 512 MB, 1.5 GB … */
    static String size(double mb) {
        if (mb >= 1024) {
            double g = mb / 1024;
            return (Math.abs(g - Math.round(g)) < 0.05 ? String.format("%.0f", g) : String.format("%.1f", g)) + " GB";
        }
        return String.format("%.0f MB", mb);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    void setTheme(Theme t) {
        for (LiveChart c : charts) c.setTheme(t);
        for (LiveChart c : sparks) c.setTheme(t);
    }

    /** Draws (and scrolls) only while the page is on screen. */
    void setActive(boolean on) {
        active = on;
        if (on) { refreshTiles(); frame.start(); } else frame.stop();
    }
}
