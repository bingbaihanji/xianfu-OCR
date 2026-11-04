package com.bingbaihanji.ocr.theme;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

// 统一动效：淡入淡出/滑入/弹出，150-250ms 缓出
public final class FxAnim {

    private FxAnim() {
    }

    public static void fadeIn(Node node) {
        fadeIn(node, 180);
    }

    public static void fadeIn(Node node, double millis) {
        FadeTransition t = new FadeTransition(Duration.millis(millis), node);
        t.setFromValue(0);
        t.setToValue(1);
        t.play();
    }

    // 淡出后执行回调（如隐藏节点）
    public static void fadeOut(Node node, double millis, Runnable after) {
        FadeTransition t = new FadeTransition(Duration.millis(millis), node);
        t.setFromValue(node.getOpacity());
        t.setToValue(0);
        if (after != null) {
            t.setOnFinished(e -> after.run());
        }
        t.play();
    }

    // 自上而下滑入 + 淡入（Toast 用）
    public static void slideIn(Node node, double millis) {
        TranslateTransition slide = new TranslateTransition(Duration.millis(millis), node);
        slide.setFromY(-12);
        slide.setToY(0);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        new ParallelTransition(slide, fade).play();
    }

    // 轻微放大弹入 + 淡入（对话框/工具条用）
    public static void popIn(Node node, double millis) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(millis), node);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1);
        scale.setToY(1);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        new ParallelTransition(scale, fade).play();
    }
}
