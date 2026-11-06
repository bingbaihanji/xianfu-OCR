package com.bingbaihanji.ocr.capture;

import com.bingbaihanji.ocr.theme.FxAnim;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;

// 截图确认后的小工具条：识别 / 贴图 / 复制图片 / 取消
// 作为覆盖层内嵌节点使用（不独立开窗），避免置顶层级与键盘焦点被抢走
public class CaptureToolbar extends HBox {

    // 动作回调（识别交主窗口；贴图/复制由控制器完成）
    public interface Actions {

        void onRecognize();

        void onPin();

        void onCopy();

        void onCancel();
    }

    private static final double HEIGHT = 44;

    public CaptureToolbar(Actions actions) {
        super(6);
        Button recognize = new Button("识别");
        recognize.getStyleClass().add("primary");
        recognize.setOnAction(e -> actions.onRecognize());
        Button pin = new Button("贴图");
        pin.setTooltip(new Tooltip("钉在屏幕上"));
        pin.setOnAction(e -> actions.onPin());
        Button copy = new Button("复制图片");
        copy.setOnAction(e -> actions.onCopy());
        Button cancel = new Button("取消");
        cancel.setOnAction(e -> actions.onCancel());

        getChildren().addAll(recognize, pin, copy, cancel);
        getStyleClass().add("capture-toolbar");

        // 吃掉全部鼠标事件：按钮自身照常响应，但不让事件继续冒泡到覆盖层根节点
        // （否则点工具条会被当成"点空白处重新框选"）
        addEventHandler(MouseEvent.ANY, MouseEvent::consume);
        addEventHandler(ScrollEvent.ANY, ScrollEvent::consume);
    }

    // 显示时的弹出动效
    public void animateIn() {
        FxAnim.popIn(this, 150);
    }

    // 工具条高度（贴边翻转定位用）
    public static double barHeight() {
        return HEIGHT;
    }
}
