package main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import java.util.function.BooleanSupplier;
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
        primaryStage.setTitle("Minecraft Server Launcher");

        for (int size : new int[]{16, 32, 48, 64, 128, 256})
            primaryStage.getIcons().add(bufToFx(AppIcon.draw(size)));

        // Fit the window on small screens (e.g. 1366x768 laptops) instead of spilling under the taskbar
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1240, screen.getWidth()), h = Math.min(780, screen.getHeight());
        Scene scene = new Scene(root, w, h);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(Math.min(MIN_W, screen.getWidth()));
        primaryStage.setMinHeight(Math.min(MIN_H, screen.getHeight()));
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            controller.handleWindowClose();
        });

        controller.setStage(primaryStage);
        enableEdgeResize(primaryStage, scene, controller::isWindowMaximized);
        primaryStage.show();
        root.requestFocus(); // nothing focused at start, so no input shows a focus ring
    }

    // ── Resizing an undecorated window ───────────────────────────────
    // With StageStyle.UNDECORATED the OS draws no border, so there was nothing to grab to resize.
    // These filters turn a thin band along every edge/corner into resize handles.

    private static final int EDGE = 6, MIN_W = 1070, MIN_H = 560; // MIN_W: the title bar's buttons need ~1050 px

    static void enableEdgeResize(Stage stage, Scene scene, BooleanSupplier isMaximized) {
        Cursor[] mode = {Cursor.DEFAULT};
        double[] start = new double[6]; // mouse x, y, stage x, y, w, h

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            mode[0] = isMaximized.getAsBoolean() ? Cursor.DEFAULT
                    : edgeCursor(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight());
            scene.setCursor(mode[0]);
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (mode[0] == Cursor.DEFAULT || !e.isPrimaryButtonDown()) return;
            start[0] = e.getScreenX(); start[1] = e.getScreenY();
            start[2] = stage.getX();   start[3] = stage.getY();
            start[4] = stage.getWidth(); start[5] = stage.getHeight();
            e.consume(); // don't let the title bar start a window drag
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (mode[0] == Cursor.DEFAULT) return;
            Cursor c = mode[0];
            double dx = e.getScreenX() - start[0], dy = e.getScreenY() - start[1];
            double minW = stage.getMinWidth(), minH = stage.getMinHeight();
            if (c == Cursor.E_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.SE_RESIZE)
                stage.setWidth(Math.max(minW, start[4] + dx));
            if (c == Cursor.W_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE) {
                double nw = Math.max(minW, start[4] - dx);
                stage.setX(start[2] + start[4] - nw); stage.setWidth(nw);
            }
            if (c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE)
                stage.setHeight(Math.max(minH, start[5] + dy));
            if (c == Cursor.N_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.NW_RESIZE) {
                double nh = Math.max(minH, start[5] - dy);
                stage.setY(start[3] + start[5] - nh); stage.setHeight(nh);
            }
            e.consume();
        });
    }

    private static Cursor edgeCursor(double x, double y, double w, double h) {
        boolean l = x < EDGE, r = x > w - EDGE, t = y < EDGE, b = y > h - EDGE;
        if (t && l) return Cursor.NW_RESIZE;
        if (t && r) return Cursor.NE_RESIZE;
        if (b && l) return Cursor.SW_RESIZE;
        if (b && r) return Cursor.SE_RESIZE;
        if (l) return Cursor.W_RESIZE;
        if (r) return Cursor.E_RESIZE;
        if (t) return Cursor.N_RESIZE;
        if (b) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
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
