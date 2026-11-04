package com.bingbaihanji.ocr.capture;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.image.BufferedImage;

// 贴图窗口：无边框置顶，拖动移动、滚轮缩放（20%~400%）、双击关闭
public class PinWindow {

    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 4.0;

    private final Stage stage = new Stage();
    private final ImageView view = new ImageView();
    private final StackPane root = new StackPane(view);
    private final double baseW;
    private final double baseH;
    private double zoom = 1;
    private double dragStartX;
    private double dragStartY;
    private double dragStageX;
    private double dragStageY;

    public PinWindow(BufferedImage image, double x, double y) {
        Image fx = SwingFXUtils.toFXImage(image, null);
        view.setImage(fx);
        this.baseW = image.getWidth();
        this.baseH = image.getHeight();
        view.setFitWidth(baseW);
        view.setFitHeight(baseH);

        root.getStyleClass().add("pin-window");
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, baseW, baseH);
        scene.setFill(Color.TRANSPARENT);

        root.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragStartX = e.getScreenX();
                dragStartY = e.getScreenY();
                dragStageX = stage.getX();
                dragStageY = stage.getY();
            }
        });
        root.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                stage.setX(dragStageX + (e.getScreenX() - dragStartX));
                stage.setY(dragStageY + (e.getScreenY() - dragStartY));
            }
        });
        root.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                stage.close();
            }
        });
        root.setOnScroll(this::onScroll);
        root.setOnMouseEntered(e -> root.getStyleClass().add("pin-hover"));
        root.setOnMouseExited(e -> root.getStyleClass().remove("pin-hover"));

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

    // 滚轮缩放：以光标处为锚点
    private void onScroll(ScrollEvent e) {
        double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (newZoom == zoom) {
            return;
        }
        double cx = e.getX();
        double cy = e.getY();
        double k = newZoom / zoom;
        zoom = newZoom;
        view.setFitWidth(baseW * zoom);
        view.setFitHeight(baseH * zoom);
        stage.setWidth(baseW * zoom);
        stage.setHeight(baseH * zoom);
        // 保持光标下像素不动
        stage.setX(stage.getX() + cx * (1 - k));
        stage.setY(stage.getY() + cy * (1 - k));
        e.consume();
    }
}
