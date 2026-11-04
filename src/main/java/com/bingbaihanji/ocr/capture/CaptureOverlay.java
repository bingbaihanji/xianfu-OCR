package com.bingbaihanji.ocr.capture;

import com.bingbaihanji.ocr.theme.Theme;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;

// 单显示器截图覆盖层：暗化底图 + 选区亮图 + 十字线 + 放大镜 + 尺寸标签 + 8 手柄
public class CaptureOverlay {

    // 选区确认（rect 为 overlay 逻辑坐标；null 表示整屏）/ 取消
    public interface Callbacks {
        void onConfirm(CaptureOverlay overlay, Rectangle2D rect);

        void onCancel();
    }

    private static final double HANDLE_SIZE = 8;
    private static final double HANDLE_TOL = 6;
    private static final double MAG_ZOOM = 8;

    private final ScreenCaptureService.MonitorCapture capture;
    private final Callbacks callbacks;
    private final Stage stage = new Stage();

    private final Image fxImage;
    private final ImageView darkView = new ImageView();
    private final ImageView brightView = new ImageView();
    private final Rectangle clipRect = new Rectangle();
    private final Rectangle selBorder = new Rectangle();
    private final Line crossH = new Line();
    private final Line crossV = new Line();
    private final Label sizeLabel = new Label();
    private final Label coordLabel = new Label();
    private final Canvas magnifier = new Canvas(128, 96);
    private final Label rgbLabel = new Label();
    private final Pane uiLayer = new Pane();
    private final List<Rectangle> handles = new ArrayList<>();

    private Rectangle2D selection;
    private CaptureGeometry.Handle dragHandle;
    private double pressX;
    private double pressY;
    private Rectangle2D pressRect;

    public CaptureOverlay(ScreenCaptureService.MonitorCapture capture, Callbacks callbacks) {
        this.capture = capture;
        this.callbacks = callbacks;
        this.fxImage = SwingFXUtils.toFXImage(capture.image(), null);
        build();
    }

    public void show() {
        stage.show();
    }

    public void close() {
        stage.close();
    }

    public ScreenCaptureService.MonitorCapture capture() {
        return capture;
    }

    public Rectangle2D selection() {
        return selection;
    }

    // 确认当前选区（无选区则整屏）
    public void confirm() {
        callbacks.onConfirm(this, selection);
    }

    // ------------------------------------------------------------------ 构建

    private void build() {
        Screen screen = capture.screen();
        Rectangle2D bounds = screen.getBounds();

        darkView.setImage(fxImage);
        darkView.setFitWidth(bounds.getWidth());
        darkView.setFitHeight(bounds.getHeight());
        darkView.setPreserveRatio(false);
        darkView.setEffect(new javafx.scene.effect.ColorAdjust(0, 0, -0.35, 0));

        brightView.setImage(fxImage);
        brightView.setFitWidth(bounds.getWidth());
        brightView.setFitHeight(bounds.getHeight());
        brightView.setPreserveRatio(false);
        clipRect.setVisible(false);
        brightView.setClip(clipRect);

        selBorder.setVisible(false);
        selBorder.setFill(Color.TRANSPARENT);
        selBorder.setStroke(Color.web(Theme.ACCENT));
        selBorder.setStrokeWidth(1.5);

        crossH.setStroke(Color.web(Theme.ACCENT, 0.7));
        crossV.setStroke(Color.web(Theme.ACCENT, 0.7));
        crossH.setVisible(false);
        crossV.setVisible(false);

        sizeLabel.getStyleClass().add("capture-hint");
        coordLabel.getStyleClass().add("capture-hint");
        rgbLabel.getStyleClass().add("capture-hint");
        sizeLabel.setVisible(false);
        coordLabel.setVisible(false);
        rgbLabel.setVisible(false);
        magnifier.getStyleClass().add("capture-magnifier");

        for (int i = 0; i < 8; i++) {
            Rectangle h = new Rectangle(HANDLE_SIZE, HANDLE_SIZE);
            h.setFill(Color.web(Theme.ACCENT));
            h.setStroke(Color.WHITE);
            h.setStrokeWidth(1);
            h.setVisible(false);
            handles.add(h);
        }

        uiLayer.getChildren().addAll(crossH, crossV, selBorder, sizeLabel,
                magnifier, coordLabel, rgbLabel);
        uiLayer.getChildren().addAll(handles);

        StackPane root = new StackPane(darkView, brightView, uiLayer);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.TRANSPARENT);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        root.setOnMousePressed(this::onPress);
        root.setOnMouseDragged(this::onDrag);
        root.setOnMouseMoved(this::onMove);
        root.setOnMouseClicked(this::onClick);

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    // ------------------------------------------------------------------ 事件

    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            callbacks.onCancel();
            e.consume();
        } else if (e.getCode() == KeyCode.ENTER) {
            confirm();
            e.consume();
        }
    }

    private void onPress(MouseEvent e) {
        stage.requestFocus();
        if (e.getButton() == MouseButton.SECONDARY) {
            callbacks.onCancel();
            return;
        }
        pressX = e.getX();
        pressY = e.getY();
        if (selection == null) {
            dragHandle = CaptureGeometry.Handle.SE;
            pressRect = new Rectangle2D(pressX, pressY, 0, 0);
        } else {
            dragHandle = CaptureGeometry.hitHandle(selection, e.getX(), e.getY(), HANDLE_TOL);
            pressRect = selection;
            if (dragHandle == CaptureGeometry.Handle.OUTSIDE) {
                dragHandle = CaptureGeometry.Handle.SE;
                pressRect = new Rectangle2D(pressX, pressY, 0, 0);
            }
        }
    }

    private void onDrag(MouseEvent e) {
        if (dragHandle == null) {
            return;
        }
        Rectangle2D r = CaptureGeometry.drag(pressRect, dragHandle, e.getX() - pressX, e.getY() - pressY);
        updateSelection(r);
    }

    private void onMove(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();
        crossH.setVisible(true);
        crossV.setVisible(true);
        crossH.setStartX(0);
        crossH.setEndX(uiLayer.getWidth());
        crossH.setStartY(y);
        crossH.setEndY(y);
        crossV.setStartY(0);
        crossV.setEndY(uiLayer.getHeight());
        crossV.setStartX(x);
        crossV.setEndX(x);
        showMagnifier(x, y);
        if (selection == null) {
            stage.getScene().setCursor(Cursor.CROSSHAIR);
        } else {
            stage.getScene().setCursor(cursorFor(
                    CaptureGeometry.hitHandle(selection, x, y, HANDLE_TOL)));
        }
    }

    private void onClick(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) {
            return;
        }
        if (selection != null && selection.contains(e.getX(), e.getY())) {
            confirm();
        } else {
            // 双击选区外 → 捕获整个当前显示器
            callbacks.onConfirm(this, null);
        }
    }

    // ------------------------------------------------------------------ 选区渲染

    private void updateSelection(Rectangle2D r) {
        selection = r;
        clipRect.setVisible(true);
        clipRect.setX(r.getMinX());
        clipRect.setY(r.getMinY());
        clipRect.setWidth(r.getWidth());
        clipRect.setHeight(r.getHeight());

        selBorder.setVisible(true);
        selBorder.setX(r.getMinX());
        selBorder.setY(r.getMinY());
        selBorder.setWidth(r.getWidth());
        selBorder.setHeight(r.getHeight());

        double cx = (r.getMinX() + r.getMaxX()) / 2;
        double cy = (r.getMinY() + r.getMaxY()) / 2;
        double[][] pos = {
                {r.getMinX() - HANDLE_SIZE / 2, r.getMinY() - HANDLE_SIZE / 2},
                {cx - HANDLE_SIZE / 2, r.getMinY() - HANDLE_SIZE / 2},
                {r.getMaxX() - HANDLE_SIZE / 2, r.getMinY() - HANDLE_SIZE / 2},
                {r.getMinX() - HANDLE_SIZE / 2, cy - HANDLE_SIZE / 2},
                {r.getMaxX() - HANDLE_SIZE / 2, cy - HANDLE_SIZE / 2},
                {r.getMinX() - HANDLE_SIZE / 2, r.getMaxY() - HANDLE_SIZE / 2},
                {cx - HANDLE_SIZE / 2, r.getMaxY() - HANDLE_SIZE / 2},
                {r.getMaxX() - HANDLE_SIZE / 2, r.getMaxY() - HANDLE_SIZE / 2}};
        for (int i = 0; i < handles.size(); i++) {
            handles.get(i).setVisible(true);
            handles.get(i).setX(pos[i][0]);
            handles.get(i).setY(pos[i][1]);
        }

        // 尺寸标签（图像像素尺寸）
        int[] imgSize = imageSize(r);
        sizeLabel.setText(imgSize[0] + " × " + imgSize[1]);
        sizeLabel.setVisible(true);
        double ly = r.getMinY() - 28 < 0 ? r.getMaxY() + 6 : r.getMinY() - 28;
        sizeLabel.relocate(r.getMinX(), ly);
    }

    private int[] imageSize(Rectangle2D r) {
        Rectangle2D ir = capture.toImageRect(r);
        return new int[]{(int) Math.round(ir.getWidth()), (int) Math.round(ir.getHeight())};
    }

    // ------------------------------------------------------------------ 放大镜

    private void showMagnifier(double x, double y) {
        double[] p = capture.toImagePoint(x, y);
        double imgX = clamp(p[0], 0, capture.image().getWidth());
        double imgY = clamp(p[1], 0, capture.image().getHeight());

        double srcW = magnifier.getWidth() / MAG_ZOOM;
        double srcH = magnifier.getHeight() / MAG_ZOOM;
        double sx = clamp(imgX - srcW / 2, 0, Math.max(0, capture.image().getWidth() - srcW));
        double sy = clamp(imgY - srcH / 2, 0, Math.max(0, capture.image().getHeight() - srcH));

        GraphicsContext g = magnifier.getGraphicsContext2D();
        g.setImageSmoothing(false);
        g.clearRect(0, 0, magnifier.getWidth(), magnifier.getHeight());
        g.drawImage(fxImage,
                sx * fxImage.getWidth() / capture.image().getWidth(),
                sy * fxImage.getHeight() / capture.image().getHeight(),
                srcW * fxImage.getWidth() / capture.image().getWidth(),
                srcH * fxImage.getHeight() / capture.image().getHeight(),
                0, 0, magnifier.getWidth(), magnifier.getHeight());
        // 中心十字
        g.setStroke(Color.web(Theme.ACCENT, 0.9));
        g.strokeLine(magnifier.getWidth() / 2, 0, magnifier.getWidth() / 2, magnifier.getHeight());
        g.strokeLine(0, magnifier.getHeight() / 2, magnifier.getWidth(), magnifier.getHeight() / 2);

        int ix = (int) imgX;
        int iy = (int) imgY;
        coordLabel.setText("(" + ix + ", " + iy + ")");
        rgbLabel.setText(rgbAt(ix, iy));
        coordLabel.setVisible(true);
        rgbLabel.setVisible(true);

        // 放大镜跟随光标（右下，越界翻左上）
        double mx = x + 18;
        double my = y + 18;
        if (mx + magnifier.getWidth() + 8 > uiLayer.getWidth()) {
            mx = x - magnifier.getWidth() - 18;
        }
        if (my + magnifier.getHeight() + 40 > uiLayer.getHeight()) {
            my = y - magnifier.getHeight() - 40;
        }
        magnifier.relocate(mx, my);
        coordLabel.relocate(mx, my + magnifier.getHeight() + 2);
        rgbLabel.relocate(mx, my + magnifier.getHeight() + 18);
    }

    private String rgbAt(int x, int y) {
        int w = capture.image().getWidth();
        int h = capture.image().getHeight();
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return "RGB(-)";
        }
        int rgb = capture.image().getRGB(x, y);
        return "RGB(" + ((rgb >> 16) & 0xFF) + "," + ((rgb >> 8) & 0xFF) + "," + (rgb & 0xFF) + ")";
    }

    // ------------------------------------------------------------------ 工具

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Cursor cursorFor(CaptureGeometry.Handle h) {
        return switch (h) {
            case N, S -> Cursor.V_RESIZE;
            case E, W -> Cursor.H_RESIZE;
            case NE, SW -> Cursor.NE_RESIZE;
            case NW, SE -> Cursor.NW_RESIZE;
            case INSIDE -> Cursor.MOVE;
            default -> Cursor.CROSSHAIR;
        };
    }

    // overlay 局部坐标 → 场景坐标（工具条定位用）
    public Point2D localToScreen(double x, double y) {
        Point2D p = uiLayer.localToScreen(x, y);
        return p == null ? new Point2D(stage.getX() + x, stage.getY() + y) : p;
    }
}
