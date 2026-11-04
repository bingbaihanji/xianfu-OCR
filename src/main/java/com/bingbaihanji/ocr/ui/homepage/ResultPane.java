package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.model.ocr.result.OcrResult;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

// 右侧结果面板：可编辑文本 + 逐行明细（悬停/点击与左图识别框联动）
public class ResultPane extends VBox {

    // 逐行明细项：序号、文本、置信度
    public record LineItem(int index, String text, double confidence) {
    }

    private final TextArea textArea = new TextArea();
    private final ListView<LineItem> list = new ListView<>();
    private final Button copyButton = new Button("复制");

    private IntConsumer onLineHover = i -> {
    };
    private Consumer<String> onLineClick = s -> {
    };

    public ResultPane() {
        setSpacing(8);
        setPadding(new Insets(4));

        Label textLabel = new Label("识别文本");
        textLabel.getStyleClass().add("label-dim");
        HBox header = new HBox(8, textLabel, copyButton);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        textArea.setPromptText("识别结果将显示在这里，可直接编辑；导出以编辑后内容为准");
        textArea.setWrapText(true);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        Label listLabel = new Label("逐行明细（悬停定位识别框，点击复制该行）");
        listLabel.getStyleClass().add("label-dim");

        list.setPrefHeight(180);
        list.setCellFactory(v -> new ListCell<>() {
            {
                hoverProperty().addListener((o, a, b) -> {
                    if (b && getItem() != null) {
                        onLineHover.accept(getItem().index());
                    } else if (!b) {
                        onLineHover.accept(-1);
                    }
                });
                setOnMouseClicked(e -> {
                    if (getItem() != null) {
                        onLineClick.accept(getItem().text());
                    }
                });
            }

            @Override
            protected void updateItem(LineItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label text = new Label((item.index() + 1) + ". " + item.text());
                    text.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(text, Priority.ALWAYS);
                    Label conf = new Label(String.format("%.2f", item.confidence()));
                    conf.getStyleClass().add("label-dim");
                    HBox row = new HBox(8, text, conf);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        getChildren().addAll(header, textArea, listLabel, list);
    }

    // 填充识别结果
    public void setResult(OcrResult result) {
        textArea.setText(result.getText());
        List<LineItem> items = new ArrayList<>();
        List<OcrLine> lines = result.getLines();
        for (int i = 0; i < lines.size(); i++) {
            OcrLine l = lines.get(i);
            items.add(new LineItem(i, l.getText(), l.getConfidence()));
        }
        list.setItems(FXCollections.observableArrayList(items));
    }

    // 清空
    public void clear() {
        textArea.clear();
        list.setItems(FXCollections.observableArrayList());
    }

    // 当前（可能被编辑过的）文本，导出以此为准
    public String currentText() {
        return textArea.getText();
    }

    // 选中并滚动到第 index 行（点识别框联动）
    public void selectLine(int index) {
        if (index >= 0 && index < list.getItems().size()) {
            list.getSelectionModel().select(index);
            list.scrollTo(index);
        }
    }

    public void setOnCopy(Runnable handler) {
        copyButton.setOnAction(e -> handler.run());
    }

    public void setOnLineHover(IntConsumer handler) {
        this.onLineHover = handler == null ? i -> {
        } : handler;
    }

    public void setOnLineClick(Consumer<String> handler) {
        this.onLineClick = handler == null ? s -> {
        } : handler;
    }
}
