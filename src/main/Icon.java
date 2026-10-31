package main;

import javafx.scene.Group;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.util.Map;
import java.util.Set;

/**
 * A small line icon drawn from SVG path data on a 24×24 grid (Lucide-style strokes), so icons are
 * crisp at any size and recolor through CSS instead of being emoji. Usable from FXML:
 * {@code <Icon name="console" size="18"/>}. Style it with {@code .icon { -fx-stroke: ...; }}
 * (filled icons such as "play" use -fx-fill).
 */
public final class Icon extends Group {

    private static final Map<String, String> PATHS = Map.ofEntries(
        Map.entry("console",  "M4 17l6-6-6-6 M12 19h8"),
        Map.entry("chat",     "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"),
        Map.entry("users",    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8z "
                            + "M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75"),
        Map.entry("shield",   "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4"),
        Map.entry("activity", "M22 12h-4l-3 9L9 3l-3 9H2"),
        Map.entry("clock",    "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z M12 6v6l4 2"),
        Map.entry("alert",    "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z "
                            + "M12 9v4 M12 17h.01"),
        Map.entry("plug",     "M12 22v-5 M9 8V2 M15 8V2 M18 8v5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V8z"),
        Map.entry("zap",      "M13 2L3 14h9l-1 8 10-12h-9l1-8z"),
        Map.entry("settings", "M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-5 M12 20H3 M14 2v4 M8 10v4 M16 18v4"),
        Map.entry("play",     "M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z"),
        Map.entry("stop",     "M7 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"),
        Map.entry("restart",  "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8 M21 3v5h-5"),
        Map.entry("refresh",  "M3 12a9 9 0 0 1 15.36-6.36L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-15.36 6.36L3 16 M3 21v-5h5"),
        Map.entry("search",   "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M21 21l-4.35-4.35"),
        Map.entry("send",     "M22 2L11 13 M22 2l-7 20-4-9-9-4 20-7z"),
        Map.entry("minimize", "M5 12h14"),
        Map.entry("maximize", "M6 5h12a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"),
        Map.entry("close",    "M6 6l12 12 M18 6L6 18"),
        Map.entry("plus",     "M12 5v14 M5 12h14"),
        Map.entry("trash",    "M3 6h18 M8 6V4h8v2 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"),
        Map.entry("folder",   "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
        Map.entry("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3"),
        Map.entry("save",     "M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z M17 21v-8H7v8 M7 3v5h8"),
        Map.entry("server",   "M4 3h16a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z "
                            + "M4 13h16a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1z M7 7h.01 M7 17h.01"),
        Map.entry("gauge",    "M12 14l4-4 M3.34 19a10 10 0 1 1 17.32 0"),
        Map.entry("globe",    "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z M2 12h20 "
                            + "M12 2a15 15 0 0 1 4 10a15 15 0 0 1-4 10a15 15 0 0 1-4-10a15 15 0 0 1 4-10z"),
        Map.entry("archive",  "M3 4h18v4H3z M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8 M10 12h4"),
        Map.entry("timer",    "M10 2h4 M12 14l3-3 M12 6a8 8 0 1 0 0 16a8 8 0 1 0 0-16z"),
        Map.entry("type",     "M4 7V4h16v3 M9 20h6 M12 4v16"),
        Map.entry("ban",      "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z M4.93 4.93l14.14 14.14"),
        Map.entry("check",    "M20 6L9 17l-5-5"),
        Map.entry("eraser",   "M7 21l-4.3-4.3a1 1 0 0 1 0-1.4l10-10a1 1 0 0 1 1.4 0l5.6 5.6a1 1 0 0 1 0 1.4L11 21z M22 21H7 M5 11l9 9"),
        Map.entry("cpu",      "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z "
                            + "M9 9h6v6H9z M9 1v3 M15 1v3 M9 20v3 M15 20v3 M20 9h3 M20 14h3 M1 9h3 M1 14h3"),
        Map.entry("memory",   "M2 7h20v10H2z M6 11v2 M10 11v2 M14 11v2 M18 11v2")
    );
    private static final Set<String> FILLED = Set.of("play", "stop");

    private final SVGPath path = new SVGPath();
    private final Group   canvas;
    private String name = "";
    private double size = 18;

    public Icon() {
        // The transparent 24×24 box keeps every icon the same size and centred, whatever its shape
        Rectangle box = new Rectangle(24, 24);
        box.setFill(null);
        box.setStroke(null);
        canvas = new Group(box, path);
        getChildren().add(canvas);
        getStyleClass().add("icon-box");
        path.getStyleClass().add("icon");
        setMouseTransparent(true);
        applySize();
    }

    public Icon(String name, double size) {
        this();
        setSize(size);
        setName(name);
    }

    public String getName() { return name; }

    public void setName(String name) {
        this.name = name;
        path.setContent(PATHS.getOrDefault(name, ""));
        path.getStyleClass().remove("filled");
        if (FILLED.contains(name)) path.getStyleClass().add("filled");
    }

    public double getSize() { return size; }

    public void setSize(double size) {
        this.size = size;
        applySize();
    }

    private void applySize() {
        double s = size / 24.0;
        canvas.setScaleX(s);
        canvas.setScaleY(s);
        // Scaling pivots on the centre; shift so the icon's top-left stays at (0,0) for layout
        canvas.setTranslateX((size - 24) / 2);
        canvas.setTranslateY((size - 24) / 2);
    }
}
