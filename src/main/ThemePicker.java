package main;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The theme picker: a panel that drops down from the header's palette button, with a card per theme.
 * Each card shows a miniature of the launcher drawn in that theme's own colors, so you can compare
 * them before choosing. Picking one applies it straight away; click outside or press Esc to close.
 */
final class ThemePicker {

    private static final double PREVIEW_W = 188, PREVIEW_H = 82;

    private final StackPane host;
    private final Supplier<Theme> current;
    private final Consumer<Theme> onPick;
    private final Pane layer = new Pane();
    private final VBox panel = new VBox(12);
    private final List<VBox> cards = new ArrayList<>();
    // Esc closes it. Listened for on the whole window while open: key presses go to whichever
    // control has focus, never to the panel itself.
    private final javafx.event.EventHandler<KeyEvent> escape = e -> {
        if (e.getCode() == KeyCode.ESCAPE) { close(); e.consume(); }
    };

    /**
     * @param host    the window's root; the panel floats above everything in it
     * @param current the theme in use
     * @param onPick  called when a card is clicked
     */
    ThemePicker(StackPane host, Supplier<Theme> current, Consumer<Theme> onPick) {
        this.host = host;
        this.current = current;
        this.onPick = onPick;

        Label title = new Label("Theme");
        title.getStyleClass().add("theme-title");
        Label sub = new Label("Changes apply right away");
        sub.getStyleClass().add("theme-sub");
        VBox head = new VBox(1, title, sub);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("theme-grid");
        for (int i = 0; i < Theme.ALL.size(); i++) {
            VBox card = card(Theme.ALL.get(i));
            cards.add(card);
            grid.add(card, i % 2, i / 2);
        }
        panel.getChildren().addAll(head, grid);
        panel.getStyleClass().add("theme-popover");
        panel.setManaged(false);

        // Clicks outside the panel close it
        Region catcher = new Region();
        catcher.prefWidthProperty().bind(layer.widthProperty());
        catcher.prefHeightProperty().bind(layer.heightProperty());
        catcher.setOnMousePressed(e -> close());
        layer.getChildren().addAll(catcher, panel);
        host.widthProperty().addListener((o, a, b) -> close());
        host.heightProperty().addListener((o, a, b) -> close());
    }

    boolean isOpen() { return layer.getParent() != null; }

    void toggle(Node anchor) {
        if (isOpen()) close(); else open(anchor);
    }

    void open(Node anchor) {
        refresh();
        host.getChildren().add(layer);
        layer.applyCss();
        panel.applyCss();
        panel.autosize();
        // Right-aligned under the button, kept inside the window
        Bounds b = layer.sceneToLocal(anchor.localToScene(anchor.getLayoutBounds()));
        double w = panel.prefWidth(-1), x = Math.max(8, Math.min(b.getMaxX() - w, host.getWidth() - w - 8));
        panel.relocate(x, b.getMaxY() + 8);
        panel.setTranslateY(-6);
        Motion.dialogIn(panel);
        new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(260),
            new javafx.animation.KeyValue(panel.translateYProperty(), 0, Motion.EASE_OUT))).play();
        if (host.getScene() != null) host.getScene().addEventFilter(KeyEvent.KEY_PRESSED, escape);
    }

    void close() {
        if (!isOpen()) return;
        host.getChildren().remove(layer);
        if (host.getScene() != null) host.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, escape);
    }

    /** Marks the card of the theme in use. */
    void refresh() {
        Theme now = current.get();
        for (VBox c : cards) {
            boolean on = c.getUserData() == now;
            c.getStyleClass().remove("theme-selected");
            if (on) c.getStyleClass().add("theme-selected");
            c.lookupAll(".theme-check").forEach(n -> n.setVisible(on));
        }
    }

    // ─── One card ────────────────────────────────────────────────────

    private VBox card(Theme t) {
        Label name = new Label(t.name);
        name.getStyleClass().add("theme-name");
        Label vibe = new Label(t.vibe);
        vibe.getStyleClass().add("theme-vibe");
        vibe.setWrapText(true);
        vibe.setMaxWidth(PREVIEW_W);
        VBox card = new VBox(8, preview(t), new VBox(1, name, vibe));
        card.getStyleClass().add("theme-card");
        card.setUserData(t);
        card.setOnMouseClicked(e -> {
            onPick.accept(t);
            refresh();
        });
        return card;
    }

    /** A miniature launcher in the theme's own colors: sidebar, header, a card with a button, the aurora. */
    private static Node preview(Theme t) {
        Pane p = new Pane();
        p.setMinSize(PREVIEW_W, PREVIEW_H);
        p.setPrefSize(PREVIEW_W, PREVIEW_H);
        p.setMaxSize(PREVIEW_W, PREVIEW_H);
        p.setBackground(fill(t.color("bg"), 0));

        Circle aurora = new Circle(70, new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
            new Stop(0, t.aurora[0].deriveColor(0, 1, 1, t.light ? 0.30 : 0.45)), new Stop(1, Color.TRANSPARENT)));
        aurora.relocate(PREVIEW_W - 120, PREVIEW_H - 95);
        Circle aurora2 = new Circle(55, new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
            new Stop(0, t.aurora[1].deriveColor(0, 1, 1, t.light ? 0.22 : 0.32)), new Stop(1, Color.TRANSPARENT)));
        aurora2.relocate(PREVIEW_W - 70, -40);

        // Sidebar with a logo dot and nav items, the selected one in the accent color
        Region side = box(0, 0, 46, PREVIEW_H, t.color("bg-side"), 0);
        side.setBorder(new Border(new BorderStroke(t.color("line"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
            new BorderWidths(0, 1, 0, 0))));
        Region logo = box(8, 8, 9, 9, t.color("accent"), 3);
        Region nav0 = box(6, 25, 34, 7, t.color("accent").deriveColor(0, 1, 1, 0.18), 3);
        Region nav0b = box(9, 27, 14, 3, t.color("accent"), 2);
        Region nav1 = box(9, 38, 22, 3, t.color("text-3"), 2);
        Region nav2 = box(9, 47, 26, 3, t.color("text-3"), 2);
        Region nav3 = box(9, 56, 18, 3, t.color("text-3"), 2);

        // Header: title, an "Online" pill and the accent Start button
        Region title = box(56, 9, 44, 5, t.color("text"), 2);
        Region subtitle = box(56, 17, 30, 3, t.color("text-3"), 2);
        Region online = box(PREVIEW_W - 64, 9, 22, 8, t.color("ok").deriveColor(0, 1, 1, 0.22), 4);
        Region start = box(PREVIEW_W - 38, 8, 28, 10, t.color("accent"), 3);

        // A card with two lines of text, laid over the aurora like the real glass panels
        Region card = box(54, 30, PREVIEW_W - 62, PREVIEW_H - 38, t.color("surface").deriveColor(0, 1, 1, 0.86), 6);
        card.setBorder(new Border(new BorderStroke(t.color("line"), BorderStrokeStyle.SOLID, new CornerRadii(6),
            BorderWidths.DEFAULT)));
        Region l1 = box(62, 39, 70, 3, t.color("text-2"), 2);
        Region l2 = box(62, 47, 52, 3, t.color("text-3"), 2);
        Region l3 = box(62, 55, 86, 3, t.color("text-3"), 2);
        Region bar = box(62, 64, PREVIEW_W - 78, 4, t.color("surface-3"), 2);
        Region barFill = box(62, 64, (PREVIEW_W - 78) * 0.62, 4, t.color("accent"), 2);

        // The check on the selected theme (it is the theme in use, so the app's own accent fits it)
        StackPane checkBadge = new StackPane(new Icon("check", 12));
        checkBadge.getStyleClass().add("theme-check");
        checkBadge.relocate(PREVIEW_W - 24, PREVIEW_H - 24);
        checkBadge.setVisible(false);

        p.getChildren().addAll(aurora, aurora2, side, logo, nav0, nav0b, nav1, nav2, nav3, title, subtitle,
            online, start, card, l1, l2, l3, bar, barFill, checkBadge);

        Rectangle clip = new Rectangle(PREVIEW_W, PREVIEW_H);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        p.setClip(clip);
        StackPane frame = new StackPane(p);
        frame.getStyleClass().add("theme-preview");
        frame.setPadding(Insets.EMPTY);
        frame.setAlignment(Pos.CENTER);
        return frame;
    }

    private static Region box(double x, double y, double w, double h, Color c, double radius) {
        Region r = new Region();
        r.setBackground(fill(c, radius));
        r.resizeRelocate(x, y, w, h);
        r.setManaged(false);
        return r;
    }

    private static Background fill(Color c, double radius) {
        return new Background(new BackgroundFill(c, new CornerRadii(radius), Insets.EMPTY));
    }
}
