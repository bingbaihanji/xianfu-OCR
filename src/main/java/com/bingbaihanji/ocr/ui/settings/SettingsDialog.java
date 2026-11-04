package com.bingbaihanji.ocr.ui.settings;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import com.bingbaihanji.ocr.hotkey.HotkeyBinding;
import com.bingbaihanji.ocr.settings.AppSettings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// 设置对话框：识别 / 快捷键 / 窗口 / 关于 四页
public class SettingsDialog extends Stage {

    private final AppSettings edited;
    private final Consumer<AppSettings> onApply;
    private final Label conflictLabel = new Label();
    private List<HotkeyRow> hotkeyRows;
    private HotkeyRow recording;

    public SettingsDialog(Window owner, AppSettings current, Consumer<AppSettings> onApply) {
        this.edited = current.copy();
        this.onApply = onApply;
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.EXTENDED);
        HeaderBar.setPrefButtonHeight(this, 0);
        setTitle("设置");
        setMinWidth(620);
        setMinHeight(420);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: -fx-bg;");

        HBox topBar = new HBox(createHeaderBar());
        topBar.setPadding(new Insets(4, 8, 8, 8));
        HBox.setHgrow(topBar.getChildren().get(0), Priority.ALWAYS);
        root.setTop(topBar);

        // 左侧导航
        ToggleGroup navGroup = new ToggleGroup();
        ToggleButton navRecognize = navButton("识别", navGroup, true);
        ToggleButton navHotkeys = navButton("快捷键", navGroup, false);
        ToggleButton navWindow = navButton("窗口", navGroup, false);
        ToggleButton navAbout = navButton("关于", navGroup, false);
        VBox nav = new VBox(4, navRecognize, navHotkeys, navWindow, navAbout);
        nav.setPadding(new Insets(8));
        nav.setPrefWidth(110);

        // 右侧面板
        StackPane cards = new StackPane();
        VBox recognizeCard = buildRecognizeCard();
        VBox hotkeysCard = buildHotkeysCard();
        VBox windowCard = buildWindowCard();
        VBox aboutCard = buildAboutCard();
        cards.getChildren().addAll(recognizeCard, hotkeysCard, windowCard, aboutCard);
        hotkeysCard.setVisible(false);
        windowCard.setVisible(false);
        aboutCard.setVisible(false);

        navGroup.selectedToggleProperty().addListener((o, a, b) -> {
            recognizeCard.setVisible(b == navRecognize);
            hotkeysCard.setVisible(b == navHotkeys);
            windowCard.setVisible(b == navWindow);
            aboutCard.setVisible(b == navAbout);
        });

        HBox center = new HBox(nav, cards);
        HBox.setHgrow(cards, Priority.ALWAYS);
        root.setCenter(center);

        // 底部按钮
        Button ok = new Button("确定");
        ok.getStyleClass().add("primary");
        ok.setOnAction(e -> {
            onApply.accept(edited);
            close();
        });
        Button apply = new Button("应用");
        apply.setOnAction(e -> onApply.accept(edited));
        Button cancel = new Button("取消");
        cancel.setOnAction(e -> close());
        HBox buttons = new HBox(8, new Region(), ok, apply, cancel);
        buttons.setPadding(new Insets(8));
        buttons.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(buttons.getChildren().get(0), Priority.ALWAYS);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 680, 460);
        scene.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleRecordingKey);
        setScene(scene);
    }

    // ------------------------------------------------------------------ 面板

    private VBox buildRecognizeCard() {
        ComboBox<OcrModel> modelBox = new ComboBox<>(FXCollections.observableArrayList(OcrModel.values()));
        modelBox.setValue(edited.getModel());
        modelBox.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(OcrModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDescription());
            }
        });
        modelBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(OcrModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDescription());
            }
        });
        modelBox.valueProperty().addListener((o, a, b) -> edited.setModel(b));

        Slider confidence = new Slider(0, 1, edited.getMinConfidence());
        confidence.setMajorTickUnit(0.1);
        confidence.setMinorTickCount(0);
        Label confValue = new Label(String.format("%.2f", edited.getMinConfidence()));
        confValue.getStyleClass().add("label-dim");
        confidence.valueProperty().addListener((o, a, b) -> {
            edited.setMinConfidence(b.doubleValue());
            confValue.setText(String.format("%.2f", b.doubleValue()));
        });

        Spinner<Integer> maxSide = new Spinner<>();
        maxSide.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(32, 4096, edited.getMaxSideLen(), 64));
        maxSide.setEditable(true);
        maxSide.valueProperty().addListener((o, a, b) -> edited.setMaxSideLen(b));

        CheckBox rotate = new CheckBox("启用方向分类（cls，纠正倒置文字）");
        rotate.setSelected(edited.isEnableRotate());
        rotate.selectedProperty().addListener((o, a, b) -> edited.setEnableRotate(b));

        GridPane grid = formGrid();
        grid.addRow(0, new Label("识别模型"), modelBox);
        grid.addRow(1, new Label("最低置信度"), new HBox(8, confidence, confValue));
        grid.addRow(2, new Label("预处理最大边长"), maxSide);
        grid.addRow(3, new Label("方向分类"), rotate);

        Label hint = new Label("识别参数在下一次识别时生效，无需重启。");
        hint.getStyleClass().add("label-dim");

        VBox box = new VBox(12, grid, hint);
        box.setPadding(new Insets(16));
        VBox.setVgrow(grid, Priority.ALWAYS);
        return box;
    }

    private VBox buildWindowCard() {
        CheckBox remember = new CheckBox("记住主窗口位置与大小");
        remember.setSelected(edited.isRememberWindow());
        remember.selectedProperty().addListener((o, a, b) -> edited.setRememberWindow(b));

        CheckBox clearClipboard = new CheckBox("退出时清空系统剪贴板");
        clearClipboard.setSelected(edited.isClearClipboardOnExit());
        clearClipboard.selectedProperty().addListener((o, a, b) -> edited.setClearClipboardOnExit(b));

        VBox box = new VBox(12, remember, clearClipboard);
        box.setPadding(new Insets(16));
        return box;
    }

    private VBox buildAboutCard() {
        Label name = new Label("显符OCR 1.0");
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label engine = new Label("识别引擎：PaddleOCR ONNX 套件（ppocr v4 / v5 / v6 tiny·small·medium）");
        Label privacy = new Label("完全本地推理，不上传任何数据。");
        Label models = new Label("模型文件内置并支持 jar 同目录 model/ 外部覆盖。");
        engine.getStyleClass().add("label-dim");
        privacy.getStyleClass().add("label-dim");
        models.getStyleClass().add("label-dim");
        VBox box = new VBox(8, name, engine, privacy, models);
        box.setPadding(new Insets(16));
        return box;
    }

    private VBox buildHotkeysCard() {
        Label hint = new Label("点击「录制」后按下新组合键（Esc 取消）。与其他动作冲突会提示并保留原值。");
        hint.getStyleClass().add("label-dim");
        hint.setWrapText(true);

        hotkeyRows = new ArrayList<>(List.of(
                new HotkeyRow("截图识别", edited::getHotkeyCaptureRecognize, edited::setHotkeyCaptureRecognize),
                new HotkeyRow("截图贴图", edited::getHotkeyCapturePin, edited::setHotkeyCapturePin),
                new HotkeyRow("显示/隐藏主窗口", edited::getHotkeyToggleWindow, edited::setHotkeyToggleWindow)));

        VBox rows = new VBox(8);
        for (HotkeyRow row : hotkeyRows) {
            Label name = new Label(row.name);
            name.setMinWidth(120);
            row.button = new Button();
            row.button.setMinWidth(170);
            row.button.setOnAction(e -> startRecording(row));
            refreshHotkeyButton(row);
            HBox line = new HBox(12, name, row.button);
            line.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(line);
        }

        conflictLabel.getStyleClass().add("label-conflict");
        conflictLabel.setWrapText(true);

        Button defaults = new Button("恢复默认");
        defaults.setOnAction(e -> {
            edited.setHotkeyCaptureRecognize(HotkeyBinding.DEFAULT_CAPTURE_RECOGNIZE);
            edited.setHotkeyCapturePin(HotkeyBinding.DEFAULT_CAPTURE_PIN);
            edited.setHotkeyToggleWindow(HotkeyBinding.DEFAULT_TOGGLE_WINDOW);
            hotkeyRows.forEach(this::refreshHotkeyButton);
            conflictLabel.setText("");
        });

        VBox box = new VBox(12, hint, rows, conflictLabel, defaults);
        box.setPadding(new Insets(16));
        return box;
    }

    // ------------------------------------------------------------------ 快捷键录制

    private void startRecording(HotkeyRow row) {
        stopRecording();
        recording = row;
        row.button.setText("请按下组合键…");
        row.button.getStyleClass().add("recording");
        conflictLabel.setText("");
    }

    private void stopRecording() {
        if (recording != null) {
            recording.button.getStyleClass().remove("recording");
            refreshHotkeyButton(recording);
            recording = null;
        }
    }

    private void handleRecordingKey(KeyEvent e) {
        if (recording == null) {
            return;
        }
        e.consume();
        if (e.getCode() == KeyCode.ESCAPE) {
            stopRecording();
            return;
        }
        try {
            HotkeyBinding binding = HotkeyBinding.fromFx(e.getCode(),
                    e.isControlDown(), e.isShiftDown(), e.isAltDown(), e.isMetaDown());
            String combo = binding.combo();
            for (HotkeyRow other : hotkeyRows) {
                if (other != recording && other.get.get().equals(combo)) {
                    conflictLabel.setText("与「" + other.name + "」冲突: " + binding.display());
                    return;
                }
            }
            recording.set.accept(combo);
            conflictLabel.setText("");
            stopRecording();
        } catch (IllegalArgumentException ex) {
            conflictLabel.setText(ex.getMessage() + "（Esc 取消录制）");
        }
    }

    private void refreshHotkeyButton(HotkeyRow row) {
        try {
            row.button.setText(HotkeyBinding.parse(row.get.get()).display());
        } catch (RuntimeException e) {
            row.button.setText(row.get.get());
        }
    }

    // 一行快捷键设置：动作名、当前值读写、按钮
    private static final class HotkeyRow {

        final String name;
        final Supplier<String> get;
        final Consumer<String> set;
        Button button;

        HotkeyRow(String name, Supplier<String> get, Consumer<String> set) {
            this.name = name;
            this.get = get;
            this.set = set;
        }
    }

    // ------------------------------------------------------------------ 工具

    private HeaderBar createHeaderBar() {
        HeaderBar bar = new HeaderBar();
        Label title = new Label("设置");
        bar.setCenter(title);
        HeaderBar.setDragType(title, HeaderDragType.DRAGGABLE_SUBTREE);
        Button closeBtn = new Button("✕");
        HeaderBar.setButtonType(closeBtn, HeaderButtonType.CLOSE);
        bar.setTrailing(closeBtn);
        return bar;
    }

    private static ToggleButton navButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setSelected(selected);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        return grid;
    }
}
