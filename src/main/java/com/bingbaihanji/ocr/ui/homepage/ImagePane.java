package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.ocr.geom.BoxLayout;
import com.bingbaihanji.ocr.theme.Theme;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

// 左侧图片区：等比显示 + 滚轮以光标为锚点缩放 + 拖拽平移 + 识别框叠加/行高亮联动
public class ImagePane extends StackPane {

    private static final double PAD = 16;
    private static final double MIN_SCALE = 0.2;
    private static final double MAX_SCALE = 8;

    private final ImageView imageView = new ImageView();
    private final Pane boxLayer = new Pane();
    private final StackPane content = new StackPane(imageView, boxLayer);
    private final Label placeholder = new Label("拖入图片或按 Ctrl+V 粘贴");
    private final Label sizeHint = new Label("");

    private final List<OcrLine> lines = new ArrayList<>();
    private final List<Rectangle> boxes = new ArrayList<>();

    private BufferedImage source;
    private double baseScale = 1;
    private double zoom = 1;
    private double panX;
    private double panY;
    private int highlighted = -1;
    private IntConsumer onLineClick = i -> {
    };
    private double dragStartX;
    private double dragStartY;
    private double dragStartPanX;
    private double dragStartPanY;

    public ImagePane() {
        setId("image-pane");
        placeholder.getStyleClass().add("label-dim");
        sizeHint.getStyleClass().add("label-dim");
        getChildren().addAll(content, placeholder, sizeHint);
        StackPane.setAlignment(placeholder, Pos.CENTER);
        StackPane.setAlignment(sizeHint, Pos.BOTTOM_LEFT);
        StackPane.setMargin(sizeHint, new Insets(8));

        content.setManaged(false);
        content.setVisible(false);

        widthProperty().addListener((o, a, b) -> layoutImage());
        heightProperty().addListener((o, a, b) -> layoutImage());

        addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPress);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onDrag);
    }

    // 设置显示图片（null 等价于清空）
    public void setImage(BufferedImage img) {
        this.source = img;
        if (img == null) {
            clear();
            return;
        }
        Image fx = SwingFXUtils.toFXImage(img, null);
        imageView.setImage(fx);
        placeholder.setVisible(false);
        content.setVisible(true);
        sizeHint.setText(img.getWidth() + " × " + img.getHeight());
        zoom = 1;
        panX = 0;
        panY = 0;
        layoutImage();
    }

    // 清空图片与识别框
    public void clear() {
        source = null;
        imageView.setImage(null);
        boxLayer.getChildren().clear();
        boxes.clear();
        lines.clear();
        highlighted = -1;
        placeholder.setVisible(true);
        content.setVisible(false);
        sizeHint.setText("");
    }

    // 设置识别框（图片坐标系）
    public void setLines(List<OcrLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
        redrawBoxes();
    }

    // 高亮第 index 个识别框（-1 清除）
    public void highlightLine(int index) {
        highlighted = index;
        for (int i = 0; i < boxes.size(); i++) {
            boolean on = i == index;
            boxes.get(i).setStroke(on ? Color.web(Theme.ACCENT) : Color.web(Theme.ACCENT, 0.55));
            boxes.get(i).setStrokeWidth(on ? 3 : 1.5);
        }
    }

    public void clearHighlight() {
        highlightLine(-1);
    }

    // 点击识别框回调
    public void setOnLineClick(IntConsumer handler) {
        this.onLineClick = handler == null ? i -> {
        } : handler;
    }

    // ------------------------------------------------------------------ 布局

    private void layoutImage() {
        if (source == null) {
            return;
        }
        double availW = Math.max(1, getWidth() - 2 * PAD);
        double availH = Math.max(1, getHeight() - 2 * PAD);
        double iw = source.getWidth();
        double ih = source.getHeight();
        baseScale = Math.min(availW / iw, availH / ih);
        double dispW = iw * baseScale;
        double dispH = ih * baseScale;
        imageView.setFitWidth(dispW);
        imageView.setFitHeight(dispH);
        // 框叠加层钉死为显示尺寸：与 ImageView 同原点同大小，识别框才能与像素对齐
        boxLayer.setPrefSize(dispW, dispH);
        boxLayer.setMinSize(dispW, dispH);
        boxLayer.setMaxSize(dispW, dispH);
        content.setPrefSize(dispW, dispH);
        content.setMinSize(dispW, dispH);
        content.setMaxSize(dispW, dispH);
        content.resize(dispW, dispH);
        placeContent();
        applyTransform();
        redrawBoxes();
    }

    // content 是 unmanaged 节点，父布局不会为它居中——手工摆放（缩放/平移走变换，不影响布局位置）
    private void placeContent() {
        content.relocate((getWidth() - content.getWidth()) / 2, (getHeight() - content.getHeight()) / 2);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (source != null) {
            placeContent();
        }
    }

    private void applyTransform() {
        content.setScaleX(zoom);
        content.setScaleY(zoom);
        content.setTranslateX(panX);
        content.setTranslateY(panY);
    }

    private void redrawBoxes() {
        boxLayer.getChildren().clear();
        boxes.clear();
        for (OcrLine line : lines) {
            Rectangle2D b = BoxLayout.bounds(line.getPoints());
            double w = Math.max(2, b.getWidth() * baseScale);
            double h = Math.max(2, b.getHeight() * baseScale);
            Rectangle r = new Rectangle(b.getMinX() * baseScale, b.getMinY() * baseScale, w, h);
            r.setFill(Color.web(Theme.ACCENT, 0.08));
            r.setStroke(Color.web(Theme.ACCENT, 0.55));
            r.setStrokeWidth(1.5);
            final int idx = boxes.size();
            r.setOnMouseClicked(e -> {
                onLineClick.accept(idx);
                e.consume();
            });
            boxes.add(r);
            boxLayer.getChildren().add(r);
        }
        if (highlighted >= 0) {
            highlightLine(highlighted);
        }
    }

    // ------------------------------------------------------------------ 交互

    private void onScroll(ScrollEvent e) {
        if (source == null) {
            return;
        }
        double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
        double newZoom = Math.min(MAX_SCALE, Math.max(MIN_SCALE, zoom * factor));
        if (newZoom == zoom) {
            return;
        }
        // 以光标处内容点为锚点：pan += (c - P) × (z - z')；P 为内容中心（Node 缩放原点）
        Point2D c = content.sceneToLocal(e.getSceneX(), e.getSceneY());
        double px = content.getLayoutBounds().getWidth() / 2;
        double py = content.getLayoutBounds().getHeight() / 2;
        panX += (c.getX() - px) * (zoom - newZoom);
        panY += (c.getY() - py) * (zoom - newZoom);
        zoom = newZoom;
        applyTransform();
        e.consume();
    }

    private void onPress(MouseEvent e) {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
        dragStartPanX = panX;
        dragStartPanY = panY;
    }

    private void onDrag(MouseEvent e) {
        if (source == null || !e.isPrimaryButtonDown()) {
            return;
        }
        panX = dragStartPanX + (e.getSceneX() - dragStartX);
        panY = dragStartPanY + (e.getSceneY() - dragStartY);
        applyTransform();
        e.consume();
    }
}
