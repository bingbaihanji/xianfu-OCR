package com.bingbaihanji.ocr.capture;

import com.bingbaihanji.ocr.theme.FxAnim;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

// 贴图窗口：无边框置顶，拖动移动、滚轮缩放（20%~400%）、双击关闭
// 交互对齐 Snipaste：Esc 关闭、Ctrl+C 复制；右键菜单 复制/另存为/显示工具条/关闭
public class PinWindow {

    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 4.0;

    private final Stage stage = new Stage();
    private final ImageView view = new ImageView();
    private final StackPane root = new StackPane(view);
    // 贴图原图（复制/另存为用），与显示缩放无关
    private final BufferedImage image;
    private final Image fxImage;
    // 另存为回调（弹文件对话框，用户自选保存位置）
    private final Consumer<BufferedImage> onSaveAs;
    private final double baseW;
    private final double baseH;
    // 悬浮工具条（由右键菜单「显示工具条」开关）
    private final HBox toolbar = new HBox(6);
    private final CheckMenuItem toolbarToggle = new CheckMenuItem("显示工具条");
    private double zoom = 1;
    private double dragStartX;
    private double dragStartY;
    private double dragStageX;
    private double dragStageY;

    // logicalW/logicalH 为期望的屏幕逻辑尺寸（与 x/y 同一 JavaFX 屏幕坐标系）
    // 不能直接用 image 像素宽高开窗：HiDPI 屏上截图是设备像素，会放大 N 倍
    public PinWindow(BufferedImage image, double x, double y, double logicalW, double logicalH,
                     Consumer<BufferedImage> onSaveAs) {
        this.image = image;
        this.onSaveAs = onSaveAs;
        this.fxImage = SwingFXUtils.toFXImage(image, null);
        view.setImage(fxImage);
        this.baseW = Math.max(1, logicalW);
        this.baseH = Math.max(1, logicalH);
        view.setFitWidth(baseW);
        view.setFitHeight(baseH);

        root.getStyleClass().add("pin-window");
        root.setStyle("-fx-background-color: transparent;");
        buildToolbar();
        root.getChildren().add(toolbar);
        StackPane.setAlignment(toolbar, Pos.BOTTOM_CENTER);
        toolbar.setVisible(false);

        Scene scene = new Scene(root, baseW, baseH);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);

        root.setOnMousePressed(this::onPress);
        root.setOnMouseDragged(this::onDrag);
        root.setOnMouseClicked(this::onClick);
        root.setOnScroll(this::onScroll);
        root.setOnMouseEntered(e -> root.getStyleClass().add("pin-hover"));
        root.setOnMouseExited(e -> root.getStyleClass().remove("pin-hover"));
        // Pane 没有 setContextMenu，自己接 ContextMenuEvent 弹菜单
        ContextMenu menu = buildMenu();
        root.setOnContextMenuRequested(e -> {
            menu.show(root, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
        stage.setX(x);
        stage.setY(y);
    }

    public void show() {
        stage.show();
    }

    public void close() {
        stage.close();
    }

    // ------------------------------------------------------------------ 快捷键

    // Esc 关闭贴图；Ctrl+C 复制贴图（贴图获得焦点后生效，点一下贴图即可获取焦点）
    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            stage.close();
            e.consume();
        } else if (e.getCode() == KeyCode.C && e.isShortcutDown()) {
            copyToClipboard();
            e.consume();
        }
    }

    // ------------------------------------------------------------------ 右键菜单

    private ContextMenu buildMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem copy = new MenuItem("复制");
        copy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        copy.setOnAction(e -> copyToClipboard());

        MenuItem save = new MenuItem("另存为");
        save.setOnAction(e -> onSaveAs.accept(image));

        toolbarToggle.setOnAction(e -> setToolbarVisible(toolbarToggle.isSelected()));

        MenuItem close = new MenuItem("关闭");
        close.setOnAction(e -> stage.close());

        menu.getItems().addAll(copy, save, toolbarToggle, new SeparatorMenuItem(), close);
        return menu;
    }

    // ------------------------------------------------------------------ 工具条

    private void buildToolbar() {
        Button copy = new Button("复制");
        copy.setTooltip(new Tooltip("复制贴图（Ctrl+C）"));
        copy.setOnAction(e -> copyToClipboard());

        Button save = new Button("另存为");
        save.setOnAction(e -> onSaveAs.accept(image));

        Button reset = new Button("1:1");
        reset.setTooltip(new Tooltip("还原为 100% 缩放"));
        reset.setOnAction(e -> zoomTo(1, stage.getWidth() / 2, stage.getHeight() / 2));

        Button close = new Button("关闭");
        close.setTooltip(new Tooltip("关闭贴图（Esc）"));
        close.setOnAction(e -> stage.close());

        toolbar.getChildren().addAll(copy, save, reset, close);
        toolbar.getStyleClass().add("capture-toolbar");
        // 限制为偏好尺寸，否则 StackPane 会把工具条拉满整个贴图
        toolbar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // 吃掉鼠标事件：按钮自身照常响应，但不让事件冒泡到根节点被当成拖动
        toolbar.addEventHandler(MouseEvent.ANY, MouseEvent::consume);
        toolbar.addEventHandler(ScrollEvent.ANY, ScrollEvent::consume);
    }

    private void setToolbarVisible(boolean show) {
        toolbarToggle.setSelected(show);
        toolbar.setVisible(show);
        if (show) {
            FxAnim.popIn(toolbar, 150);
        }
    }

    // ------------------------------------------------------------------ 交互

    private void onPress(MouseEvent e) {
        // 点一下贴图拿到键盘焦点，Esc / Ctrl+C 才能生效
        stage.requestFocus();
        if (e.getButton() == MouseButton.PRIMARY) {
            dragStartX = e.getScreenX();
            dragStartY = e.getScreenY();
            dragStageX = stage.getX();
            dragStageY = stage.getY();
        }
    }

    private void onDrag(MouseEvent e) {
        if (e.isPrimaryButtonDown()) {
            stage.setX(dragStageX + (e.getScreenX() - dragStartX));
            stage.setY(dragStageY + (e.getScreenY() - dragStartY));
        }
    }

    private void onClick(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            stage.close();
        }
    }

    // 滚轮缩放：以光标处为锚点
    private void onScroll(ScrollEvent e) {
        zoomTo(zoom * (e.getDeltaY() > 0 ? 1.1 : 1 / 1.1), e.getX(), e.getY());
        e.consume();
    }

    // 缩放到指定倍数，保持 (cx, cy) 窗口内坐标下的像素不动
    private void zoomTo(double newZoom, double cx, double cy) {
        newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        if (newZoom == zoom) {
            return;
        }
        double k = newZoom / zoom;
        zoom = newZoom;
        view.setFitWidth(baseW * zoom);
        view.setFitHeight(baseH * zoom);
        stage.setWidth(baseW * zoom);
        stage.setHeight(baseH * zoom);
        stage.setX(stage.getX() + cx * (1 - k));
        stage.setY(stage.getY() + cy * (1 - k));
    }

    private void copyToClipboard() {
        ClipboardContent cc = new ClipboardContent();
        cc.putImage(fxImage);
        Clipboard.getSystemClipboard().setContent(cc);
    }
}
