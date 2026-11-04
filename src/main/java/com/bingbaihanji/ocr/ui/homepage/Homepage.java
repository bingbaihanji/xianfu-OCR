package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.ocr.ui.menu.AppMenuBar;
import com.bingbaihanji.ocr.ui.widget.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.InputStream;

// 主窗口：HeaderBar 标题栏 + 菜单/工具行 + 左图右文 + 状态栏 + Toast 覆盖层
public class Homepage extends StackPane {

    private static final double ICON_SIZE = 24;
    private static final double CONTROL_SPACING = 10;
    private static final String TITLE = "显符OCR";

    private static final String ICON_LOGO = "/icon/logo.png";
    private static final String ICON_MIN = "/icon/min.png";
    private static final String ICON_MAX = "/icon/max.png";
    private static final String ICON_CLOSE = "/icon/close.png";

    private final BorderPane rootPane = new BorderPane();
    private final ImagePane imagePane = new ImagePane();
    private final ResultPane resultPane = new ResultPane();
    private final StatusBar statusBar = new StatusBar();
    private final Toast toast = new Toast();
    private final Button screenshotButton = new Button("截图识别");
    private final Button openButton = new Button("打开图片");

    public Homepage(AppMenuBar menuBar) {
        setId("homepage");

        HBox topBar = new HBox(createHeaderBar());
        topBar.setPadding(new Insets(4, 8, 8, 8));
        HBox.setHgrow(topBar.getChildren().get(0), Priority.ALWAYS);

        HBox toolRow = new HBox(menuBar, screenshotButton, openButton);
        toolRow.setAlignment(Pos.CENTER_LEFT);
        toolRow.setPadding(new Insets(0, 8, 8, 8));
        HBox.setHgrow(menuBar, Priority.ALWAYS);

        SplitPane split = new SplitPane(imagePane, resultPane);
        split.setDividerPositions(0.5);

        StackPane center = new StackPane(split, toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(12));

        rootPane.setTop(new VBox(topBar, toolRow));
        rootPane.setCenter(center);
        rootPane.setBottom(statusBar);
        getChildren().add(rootPane);
    }

    // ------------------------------------------------------------------ 访问器（AppController 装配用）

    public BorderPane rootPane() {
        return rootPane;
    }

    public ImagePane imagePane() {
        return imagePane;
    }

    public ResultPane resultPane() {
        return resultPane;
    }

    public StatusBar statusBar() {
        return statusBar;
    }

    public Toast toast() {
        return toast;
    }

    public Button openButton() {
        return openButton;
    }

    public Button screenshotButton() {
        return screenshotButton;
    }

    // ------------------------------------------------------------------ 构建

    private HeaderBar createHeaderBar() {
        HeaderBar headerBar = new HeaderBar();

        Label title = new Label(TITLE);
        headerBar.setCenter(title);
        HeaderBar.setDragType(title, HeaderDragType.DRAGGABLE_SUBTREE);

        headerBar.setLeading(createIconView(ICON_LOGO));
        headerBar.setTrailing(createWindowControls());

        return headerBar;
    }

    private HBox createWindowControls() {
        HBox controls = new HBox(CONTROL_SPACING);
        controls.getChildren().addAll(
                createControlButton(ICON_MIN, HeaderButtonType.ICONIFY),
                createControlButton(ICON_MAX, HeaderButtonType.MAXIMIZE),
                createControlButton(ICON_CLOSE, HeaderButtonType.CLOSE));
        return controls;
    }

    private Button createControlButton(String iconPath, HeaderButtonType type) {
        Button button = new Button();
        button.setGraphic(createIconView(iconPath));
        HeaderBar.setButtonType(button, type);
        return button;
    }

    // ------------------------------------------------------------------ 工具

    private ImageView createIconView(String resourcePath) {
        ImageView view = new ImageView(loadImage(resourcePath));
        view.setFitWidth(ICON_SIZE);
        view.setFitHeight(ICON_SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    private Image loadImage(String resourcePath) {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("图标资源未找到: " + resourcePath);
        }
        return new Image(in);
    }
}
