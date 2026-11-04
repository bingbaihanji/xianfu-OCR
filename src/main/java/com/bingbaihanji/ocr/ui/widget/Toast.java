package com.bingbaihanji.ocr.ui.widget;

import com.bingbaihanji.ocr.theme.FxAnim;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

// 轻提示：顶部居中滑入，2 秒后淡出
public class Toast extends StackPane {

    private final Label label = new Label();
    private Timeline hideTimer;

    public Toast() {
        getStyleClass().add("toast");
        setVisible(false);
        setMouseTransparent(true);
        setPadding(new Insets(8, 16, 8, 16));
        label.setWrapText(true);
        getChildren().add(label);
    }

    public void show(String message) {
        show(message, false);
    }

    public void showError(String message) {
        show(message, true);
    }

    private void show(String message, boolean error) {
        label.setText(message);
        getStyleClass().setAll(error ? "toast-error" : "toast");
        setVisible(true);
        setOpacity(1);
        FxAnim.slideIn(this, 180);
        if (hideTimer != null) {
            hideTimer.stop();
        }
        hideTimer = new Timeline(new KeyFrame(Duration.millis(2000),
                e -> FxAnim.fadeOut(this, 200, () -> setVisible(false))));
        hideTimer.play();
    }
}
