package com.bingbaihanji.ocr.capture;

import com.bingbaihanji.ocr.theme.FxAnim;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

// 截图确认后的小工具条：识别 / 贴图 / 复制图片 / 取消
public class CaptureToolbar {

    // 动作回调（识别交主窗口；贴图/复制由控制器完成）
    public interface Actions {
        void onRecognize();

        void onPin();

        void onCopy();

        void onCancel();
    }

    private static final double W = 300;
    private static final double H = 44;

    private final Stage stage = new Stage();

    public CaptureToolbar(Actions actions) {
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

        HBox bar = new HBox(6, recognize, pin, copy, cancel);
        bar.getStyleClass().add("capture-toolbar");

        Scene scene = new Scene(bar, W, H);
        scene.setFill(Color.TRANSPARENT);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
    }

    // 贴选区右下方显示；越出屏幕则翻到上方
    public void show(Rectangle2D selScreenRect, Rectangle2D screenBounds) {
        double x = selScreenRect.getMaxX() - W;
        double y = selScreenRect.getMaxY() + 8;
        if (y + H > screenBounds.getMaxY()) {
            y = selScreenRect.getMinY() - H - 8;
        }
        x = Math.max(screenBounds.getMinX() + 4, Math.min(x, screenBounds.getMaxX() - W - 4));
        y = Math.max(screenBounds.getMinY() + 4, y);
        stage.setX(x);
        stage.setY(y);
        stage.show();
        FxAnim.popIn(stage.getScene().getRoot(), 150);
    }

    public void close() {
        stage.close();
    }
}
