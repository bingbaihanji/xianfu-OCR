package com.bingbaihanji.ocr.ui;

import com.bingbaihanji.model.cli.NativeBootstrap;
import com.bingbaihanji.ocr.service.RecognitionService;
import com.bingbaihanji.ocr.settings.AppSettings;
import com.bingbaihanji.ocr.settings.SettingsStore;
import com.bingbaihanji.ocr.theme.FxAnim;
import com.bingbaihanji.ocr.theme.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HeaderBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

// 应用入口：装配设置/识别服务/主窗口，管理生命周期
public class XianFuOCRApplication extends Application {

    static {
        // StageStyle.EXTENDED/HeaderBar 是 JavaFX 25 的预览特性。
        // 注意：JavaFX 25.0.3 的检查只认 JVM 启动参数 -Djavafx.enablePreview=true
        // （IDEA 运行配置需自行添加该 VM 选项）；此处设置属性为旧版 JavaFX 的兜底。
        System.setProperty("javafx.enablePreview", "true");
    }

    private static final Logger log = LoggerFactory.getLogger(XianFuOCRApplication.class);

    private Path jarDir;
    private AppSettings settings;
    private RecognitionService recognition;
    private AppController controller;
    private boolean shutDown;

    @Override
    public void init() throws Exception {
        super.init();
        // 原生库引导（必须早于任何 ONNX/OpenCV 调用；init 在 FX 线程外执行）
        NativeBootstrap.bootstrap();
        jarDir = NativeBootstrap.jarDir();
        settings = SettingsStore.load(jarDir);
        recognition = RecognitionService.createDefault(jarDir);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.EXTENDED);
        HeaderBar.setPrefButtonHeight(primaryStage, 0);

        controller = new AppController(primaryStage, recognition, settings, jarDir);

        Scene scene = new Scene(controller.getView(),
                settings.getWindowWidth(), settings.getWindowHeight());
        Theme.apply(scene);

        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icon/logo.png")));
        primaryStage.setOnCloseRequest(e -> shutdown());
        controller.restoreWindowState();

        primaryStage.show();
        FxAnim.fadeIn(controller.getView());
        controller.start();
    }

    @Override
    public void stop() {
        shutdown();
    }

    // 幂等收尾：存窗口状态与配置、释放识别资源
    private void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        if (controller != null) {
            controller.persistWindowState();
            controller.dispose();
        }
        if (settings != null && jarDir != null) {
            try {
                SettingsStore.save(jarDir, settings);
            } catch (IOException e) {
                log.warn("保存设置失败: {}", e.toString());
            }
        }
        if (settings != null && settings.isClearClipboardOnExit()) {
            Clipboard.getSystemClipboard().clear();
        }
        if (recognition != null) {
            recognition.close();
        }
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}
