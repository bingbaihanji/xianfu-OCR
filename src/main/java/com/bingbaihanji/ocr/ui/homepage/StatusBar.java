package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

// 状态栏：状态文案 + 模型/行数/耗时，识别中显示加载动效
public class StatusBar extends HBox {

    private final Label status = new Label("就绪");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label info = new Label("");

    public StatusBar() {
        setId("status-bar");
        setSpacing(12);
        setPadding(new Insets(6, 12, 6, 12));
        spinner.setPrefSize(16, 16);
        spinner.setMinSize(16, 16);
        spinner.setVisible(false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(status, spinner, spacer, info);
    }

    public void setRunning() {
        status.setText("识别中…");
        spinner.setVisible(true);
    }

    public void setIdle() {
        spinner.setVisible(false);
        status.setText("就绪");
    }

    public void setError(String message) {
        spinner.setVisible(false);
        status.setText("出错：" + message);
    }

    public void setResultInfo(OcrModel model, int lines, long elapsedMillis) {
        info.setText("模型: " + model + " │ " + lines + " 行 │ " + elapsedMillis + " ms");
    }
}
