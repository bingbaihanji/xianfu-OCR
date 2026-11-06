package com.bingbaihanji.ocr.capture;

import com.bingbaihanji.ocr.theme.Theme;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
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
// 选区本身由所有覆盖层共用一份（物理像素坐标），本类只负责本屏的视图与交互
public class CaptureOverlay {

    // 共享选区（物理像素坐标）：读写一份，供全部覆盖层渲染同一选区
    public interface SelectionStore {

        // 当前选区，null 表示尚未框选
        Rectangle2D selection();

        // 更新选区并刷新全部覆盖层
        void setSelection(Rectangle2D deviceRect);

        // 选区可拖动范围（全部显示器物理像素并集）
        Rectangle2D deviceUnion();
    }

    public interface Callbacks {

        // 确认选区：deviceRect 为物理像素矩形（非 null）
        void onConfirm(Rectangle2D deviceRect);

        // 取消截图（ESC / 右键触发）
        void onCancel();

        // 键盘事件：返回 true 表示已消费
        boolean onKey(KeyCode code);
    }

    // 单个缩放手柄的绘制边长
    private static final double HANDLE_SIZE = 8;
    // 手柄命中测试容差，使手柄比视觉尺寸更容易被点中
    private static final double HANDLE_TOL = 6;
    // 放大镜放大倍数
    private static final double MAG_ZOOM = 8;
    // 小于该像素视为「只是点击」而非框选，丢弃
    private static final double MIN_SEL = 2;

    // 本显示器的抓屏结果（BufferedImage + Screen），提供屏幕/图像坐标换算
    private final ScreenCaptureService.MonitorCapture capture;
    // 共享选区
    private final SelectionStore store;
    // 确认 / 取消回调
    private final Callbacks callbacks;
    // 透明、置顶、无边框的全屏覆盖窗口
    private final Stage stage = new Stage();

    // 抓屏图转换后的 JavaFX Image
    private final Image fxImage;
    // 底层：整屏暗化的 ImageView（作为非选区底色）
    private final ImageView darkView = new ImageView();
    // 中层：整屏原图 ImageView，被 clipRect 裁剪后只显示选区
    private final ImageView brightView = new ImageView();
    // brightView 的裁剪矩形（即"选区亮洞"），坐标为本屏逻辑坐标
    private final Rectangle clipRect = new Rectangle();
    // 选区边框矩形
    private final Rectangle selBorder = new Rectangle();
    // 十字准线：水平线
    private final Line crossH = new Line();
    // 十字准线：垂直线
    private final Line crossV = new Line();
    // 选区图像像素尺寸标签（如 "1920 × 1080"）
    private final Label sizeLabel = new Label();
    // 光标处图像坐标标签
    private final Label coordLabel = new Label();
    // 放大镜画布
    private final Canvas magnifier = new Canvas(128, 96);
    // 光标处 RGB 颜色标签
    private final Label rgbLabel = new Label();
    // 承载所有交互 UI（十字线/边框/标签/放大镜/手柄/工具条）的顶层容器
    private final Pane uiLayer = new Pane();
    // 工具条挂载槽（工具条随选区跨屏换挂到对应覆盖层）
    private final Pane toolbarSlot = new Pane();
    // 8 个缩放手柄（NW、N、NE、W、E、SW、S、SE）
    private final List<Rectangle> handles = new ArrayList<>();

    // 当前拖拽锚点手柄
    private CaptureGeometry.Handle dragHandle;
    // 鼠标按下点的物理坐标（拖拽增量基准）
    private double pressDeviceX;
    private double pressDeviceY;
    // 鼠标按下时的选区快照（物理坐标，拖拽计算基准，避免累计误差）
    private Rectangle2D pressRect;
    // 是否正在拉出新选区（按下到抬起之间）
    private boolean rubberBanding;

    // 构造覆盖层：保存抓屏与回调，并完成界面构建
    public CaptureOverlay(ScreenCaptureService.MonitorCapture capture, SelectionStore store, Callbacks callbacks) {
        this.capture = capture;
        this.store = store;
        this.callbacks = callbacks;
        this.fxImage = SwingFXUtils.toFXImage(capture.image(), null);
        build();
    }

    // 显示覆盖窗口
    public void show() {
        stage.show();
    }

    // 关闭覆盖窗口
    public void close() {
        stage.close();
    }

    // 返回本覆盖层对应的抓屏结果
    public ScreenCaptureService.MonitorCapture capture() {
        return capture;
    }

    // 共享选区变更后由控制器调用，刷新本屏视图
    public void refresh() {
        renderSelection();
    }

    // 在本覆盖层挂载工具条节点（localX/localY 为本屏逻辑坐标）
    public void placeToolbar(Node node, double localX, double localY) {
        if (node.getParent() != toolbarSlot) {
            toolbarSlot.getChildren().setAll(node);
        }
        // 先算样式再量尺寸（否则按未样式化的字体量出来的宽度会偏）
        node.applyCss();
        // Pane 不会替子节点定尺寸，这里显式给到偏好尺寸
        node.resizeRelocate(localX, localY, node.prefWidth(-1), node.prefHeight(-1));
    }

    // 摘下工具条
    public void clearToolbar() {
        toolbarSlot.getChildren().clear();
    }

    // ------------------------------------------------------------------ 坐标换算

    // 本屏逻辑坐标点 → 物理屏幕坐标
    private double deviceX(double localX) {
        return capture.toDevicePoint(localX, 0)[0];
    }

    private double deviceY(double localY) {
        return capture.toDevicePoint(0, localY)[1];
    }

    // 物理屏幕矩形 → 本屏逻辑矩形
    private Rectangle2D toLocal(Rectangle2D device) {
        return capture.toLocal(device);
    }

    // ------------------------------------------------------------------ 构建

    // 构建窗口内容：图层、标签、手柄、事件与 Stage
    private void build() {
        // 本显示器屏幕与边界
        Screen screen = capture.screen();
        Rectangle2D bounds = screen.getBounds();

        // 底层暗化图：铺满屏幕并加亮度-0.35 的滤镜
        darkView.setImage(fxImage);
        darkView.setFitWidth(bounds.getWidth());
        darkView.setFitHeight(bounds.getHeight());
        darkView.setPreserveRatio(false);
        darkView.setEffect(new javafx.scene.effect.ColorAdjust(0, 0, -0.35, 0));

        // 中层亮图：铺满屏幕，用 clipRect 裁剪只露出选区
        brightView.setImage(fxImage);
        brightView.setFitWidth(bounds.getWidth());
        brightView.setFitHeight(bounds.getHeight());
        brightView.setPreserveRatio(false);
        clipRect.setVisible(false);
        brightView.setClip(clipRect);

        // 选区边框：透明填充 + 强调色描边
        selBorder.setVisible(false);
        selBorder.setFill(Color.TRANSPARENT);
        selBorder.setStroke(Color.web(Theme.ACCENT));
        selBorder.setStrokeWidth(1.5);

        // 十字准线：半透明强调色，初始隐藏
        crossH.setStroke(Color.web(Theme.ACCENT, 0.7));
        crossV.setStroke(Color.web(Theme.ACCENT, 0.7));
        crossH.setVisible(false);
        crossV.setVisible(false);

        // 三个提示标签：应用样式类并初始隐藏
        sizeLabel.getStyleClass().add("capture-hint");
        coordLabel.getStyleClass().add("capture-hint");
        rgbLabel.getStyleClass().add("capture-hint");
        sizeLabel.setVisible(false);
        coordLabel.setVisible(false);
        rgbLabel.setVisible(false);
        // 放大镜画布：应用样式类并初始隐藏（否则开场就挂着一个空画布/阴影）
        magnifier.getStyleClass().add("capture-magnifier");
        magnifier.setVisible(false);

        // 创建 8 个手柄小方块：强调色填充 + 白色描边，初始隐藏
        for (int i = 0; i < 8; i++) {
            Rectangle h = new Rectangle(HANDLE_SIZE, HANDLE_SIZE);
            h.setFill(Color.web(Theme.ACCENT));
            h.setStroke(Color.WHITE);
            h.setStrokeWidth(1);
            h.setVisible(false);
            handles.add(h);
        }

        // 按层级加入 UI：十字线/边框/标签/放大镜，最后压上手柄与工具条
        uiLayer.getChildren().addAll(crossH, crossV, selBorder, sizeLabel,
                magnifier, coordLabel, rgbLabel);
        uiLayer.getChildren().addAll(handles);
        uiLayer.getChildren().add(toolbarSlot);

        // 根容器：三层叠加，透明背景
        StackPane root = new StackPane(darkView, brightView, uiLayer);
        root.setStyle("-fx-background-color: transparent;");

        // 场景：全屏透明，键盘用事件过滤器优先处理
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        // 鼠标事件统一挂在根容器上
        root.setOnMousePressed(this::onPress);
        root.setOnMouseDragged(this::onDrag);
        root.setOnMouseReleased(this::onRelease);
        root.setOnMouseMoved(this::onMove);
        // 跨屏时鼠标离开本覆盖层必须收起跟随物，否则放大镜/十字线会残留在屏幕接缝处
        root.setOnMouseEntered(e -> trackCursor(e.getX(), e.getY()));
        root.setOnMouseExited(e -> hideChrome());
        root.setOnMouseClicked(this::onClick);

        // Stage：透明无边框、置顶、位置与尺寸对齐屏幕
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    // ------------------------------------------------------------------ 事件

    // 键盘事件：交给控制器统一调度（ESC 取消，ENTER 确认/执行主操作）
    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER && store.selection() == null) {
            // 尚未框选时回车 → 捕获整个当前显示器
            Rectangle2D full = capture.toDevice(new Rectangle2D(0, 0,
                    capture.screen().getBounds().getWidth(), capture.screen().getBounds().getHeight()));
            store.setSelection(full);
            callbacks.onConfirm(full);
            e.consume();
            return;
        }
        if (callbacks.onKey(e.getCode())) {
            e.consume();
        }
    }

    // 鼠标按下：右键取消；无选区或点在选区外则开始新框选；否则拖动手柄/选区
    private void onPress(MouseEvent e) {
        // 让窗口获得键盘焦点
        stage.requestFocus();
        if (e.getButton() == MouseButton.SECONDARY) {
            callbacks.onCancel();
            return;
        }
        double dx = deviceX(e.getX());
        double dy = deviceY(e.getY());
        pressDeviceX = dx;
        pressDeviceY = dy;

        Rectangle2D sel = store.selection();
        if (sel == null) {
            dragHandle = CaptureGeometry.Handle.SE;
            pressRect = new Rectangle2D(dx, dy, 0, 0);
            rubberBanding = true;
            return;
        }
        CaptureGeometry.Handle h = CaptureGeometry.hitHandle(sel, dx, dy, HANDLE_TOL * capture.scaleX());
        if (h == CaptureGeometry.Handle.OUTSIDE) {
            // 点空白处：丢弃旧选区，重新拉框（工具条随之收起）
            store.setSelection(null);
            dragHandle = CaptureGeometry.Handle.SE;
            pressRect = new Rectangle2D(dx, dy, 0, 0);
            rubberBanding = true;
        } else {
            dragHandle = h;
            pressRect = sel;
            rubberBanding = false;
        }
    }

    // 鼠标拖拽：基于按下时的快照 + 增量计算新选区并刷新（夹紧到虚拟桌面内，可跨屏拖动）
    private void onDrag(MouseEvent e) {
        if (dragHandle == null) {
            return;
        }
        double dx = deviceX(e.getX()) - pressDeviceX;
        double dy = deviceY(e.getY()) - pressDeviceY;
        Rectangle2D r = CaptureGeometry.drag(pressRect, dragHandle, dx, dy);
        Rectangle2D union = store.deviceUnion();
        r = dragHandle == CaptureGeometry.Handle.INSIDE
                ? CaptureGeometry.clampMove(r, union)
                : CaptureGeometry.clampEdges(r, union);
        store.setSelection(r);
        // 拖拽期间没有 MOUSE_MOVED，十字线/放大镜要在这里跟手
        trackCursor(e.getX(), e.getY());
    }

    // 鼠标抬起：结束本次拖拽；拉出有效选区即视为确认（与 Snipaste 一致，无需回车）
    private void onRelease(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        boolean wasRubber = rubberBanding;
        rubberBanding = false;
        dragHandle = null;

        Rectangle2D sel = store.selection();
        if (sel == null) {
            updateChrome();
            return;
        }
        if (sel.getWidth() < MIN_SEL || sel.getHeight() < MIN_SEL) {
            // 只是点击：丢弃退化选区
            if (wasRubber) {
                store.setSelection(null);
            }
            updateChrome();
            return;
        }
        updateChrome();
        callbacks.onConfirm(sel);
    }

    // 鼠标移动：更新十字线、放大镜、坐标/RGB 标签与光标形状
    private void onMove(MouseEvent e) {
        trackCursor(e.getX(), e.getY());
    }

    // 跟随光标刷新十字线/放大镜/取样标签与光标形状（拖拽中同样调用，
    // 因为拖拽期间 JavaFX 只派发 MOUSE_DRAGGED，不会派发 MOUSE_MOVED）
    private void trackCursor(double x, double y) {
        // 光标已不在本显示器范围内：清掉跟随物再返回
        // （跨屏后本覆盖层不会再收到 MOUSE_MOVED，不清就会在屏幕接缝处留残影）
        Rectangle2D bounds = capture.screen().getBounds();
        if (x < 0 || y < 0 || x >= bounds.getWidth() || y >= bounds.getHeight()) {
            hideChrome();
            return;
        }
        if (!chromeVisible()) {
            hideChrome();
        } else {
            // 显示十字准线并跨满整个 UI 层
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
        }
        // 根据是否在选区/手柄上切换光标形状
        Rectangle2D sel = store.selection();
        if (sel == null) {
            stage.getScene().setCursor(Cursor.CROSSHAIR);
        } else {
            stage.getScene().setCursor(cursorFor(
                    CaptureGeometry.hitHandle(sel, deviceX(x), deviceY(y), HANDLE_TOL * capture.scaleX())));
        }
    }

    // 鼠标点击：左键双击选区内确认，双击选区外则捕获整个显示器
    private void onClick(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) {
            return;
        }
        Rectangle2D sel = store.selection();
        if (sel != null && sel.contains(deviceX(e.getX()), deviceY(e.getY()))) {
            callbacks.onConfirm(sel);
        } else {
            // 双击选区外 → 捕获整个当前显示器
            Rectangle2D fullLocal = new Rectangle2D(0, 0,
                    capture.screen().getBounds().getWidth(), capture.screen().getBounds().getHeight());
            Rectangle2D fullDevice = capture.toDevice(fullLocal);
            store.setSelection(fullDevice);
            callbacks.onConfirm(fullDevice);
        }
    }

    // ------------------------------------------------------------------ 选区渲染

    // 十字线/放大镜/取样标签只在「尚未选区」或「正在拉框」时出现；
    // 框选完成（鼠标抬起）后消失——与 Snipaste 一致
    private boolean chromeVisible() {
        return store.selection() == null || rubberBanding;
    }

    // 收起全部跟随物（展开只走 trackCursor，避免在旧位置重新露出）
    private void hideChrome() {
        crossH.setVisible(false);
        crossV.setVisible(false);
        magnifier.setVisible(false);
        coordLabel.setVisible(false);
        rgbLabel.setVisible(false);
    }

    // 选区状态变化后收起跟随物
    private void updateChrome() {
        if (!chromeVisible()) {
            hideChrome();
        }
    }

    // 按共享选区刷新本屏的裁剪区、边框、可见手柄与尺寸标签
    private void renderSelection() {
        Rectangle2D sel = store.selection();
        if (sel == null) {
            clipRect.setVisible(false);
            selBorder.setVisible(false);
            sizeLabel.setVisible(false);
            for (Rectangle h : handles) {
                h.setVisible(false);
            }
            updateChrome();
            return;
        }
        Rectangle2D r = toLocal(sel);

        // 更新亮图裁剪区域，使选区内部变亮
        clipRect.setVisible(true);
        clipRect.setX(r.getMinX());
        clipRect.setY(r.getMinY());
        clipRect.setWidth(r.getWidth());
        clipRect.setHeight(r.getHeight());

        // 更新选区边框位置
        selBorder.setVisible(true);
        selBorder.setX(r.getMinX());
        selBorder.setY(r.getMinY());
        selBorder.setWidth(r.getWidth());
        selBorder.setHeight(r.getHeight());

        // 8 个手柄：只画落在本显示器内的那些（选区跨屏时各屏各画各的）
        double[][] handleDevice = {
                {sel.getMinX(), sel.getMinY()},
                {(sel.getMinX() + sel.getMaxX()) / 2, sel.getMinY()},
                {sel.getMaxX(), sel.getMinY()},
                {sel.getMinX(), (sel.getMinY() + sel.getMaxY()) / 2},
                {sel.getMaxX(), (sel.getMinY() + sel.getMaxY()) / 2},
                {sel.getMinX(), sel.getMaxY()},
                {(sel.getMinX() + sel.getMaxX()) / 2, sel.getMaxY()},
                {sel.getMaxX(), sel.getMaxY()}};
        for (int i = 0; i < handles.size(); i++) {
            Rectangle h = handles.get(i);
            boolean visible = capture.containsDevice(handleDevice[i][0], handleDevice[i][1]);
            h.setVisible(visible);
            if (visible) {
                Rectangle2D hp = toLocal(new Rectangle2D(handleDevice[i][0], handleDevice[i][1], 0, 0));
                h.setX(hp.getMinX() - HANDLE_SIZE / 2);
                h.setY(hp.getMinY() - HANDLE_SIZE / 2);
            }
        }

        // 尺寸标签：显示选区总物理像素尺寸，画在选区左上角所在屏
        sizeLabel.setText((int) Math.round(sel.getWidth()) + " × " + (int) Math.round(sel.getHeight()));
        boolean labelHere = capture.containsDevice(sel.getMinX(), sel.getMinY());
        sizeLabel.setVisible(labelHere);
        if (labelHere) {
            // 优先放选区上方，越界则改放选区下方
            double ly = r.getMinY() - 28 < 0 ? r.getMaxY() + 6 : r.getMinY() - 28;
            sizeLabel.relocate(r.getMinX(), ly);
        }
    }

    // ------------------------------------------------------------------ 放大镜

    // 以光标处图像像素为中心绘制放大镜，并更新坐标/RGB 标签与跟随位置
    private void showMagnifier(double x, double y) {
        // 光标处对应的图像像素坐标，并夹紧到图像范围
        double[] p = capture.toImagePoint(x, y);
        double imgX = clamp(p[0], 0, capture.image().getWidth());
        double imgY = clamp(p[1], 0, capture.image().getHeight());

        // 放大镜覆盖的源区域大小（图像像素）
        double srcW = magnifier.getWidth() / MAG_ZOOM;
        double srcH = magnifier.getHeight() / MAG_ZOOM;
        // 源区域左上角，夹紧避免越界
        double sx = clamp(imgX - srcW / 2, 0, Math.max(0, capture.image().getWidth() - srcW));
        double sy = clamp(imgY - srcH / 2, 0, Math.max(0, capture.image().getHeight() - srcH));

        // 关闭平滑以保持像素锐利，清空后拉伸绘制源区域
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

        // 更新坐标与 RGB 标签内容
        int ix = (int) imgX;
        int iy = (int) imgY;
        coordLabel.setText("(" + ix + ", " + iy + ")");
        rgbLabel.setText(rgbAt(ix, iy));
        magnifier.setVisible(true);
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
        // 放大镜与两个标签一起定位
        magnifier.relocate(mx, my);
        coordLabel.relocate(mx, my + magnifier.getHeight() + 2);
        rgbLabel.relocate(mx, my + magnifier.getHeight() + 18);
    }

    // 取图像指定像素的 RGB 字符串，越界返回 "RGB(-)"
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

    // 数值夹紧到 [min, max]
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // 手柄方向 → 对应鼠标光标形状
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
}
