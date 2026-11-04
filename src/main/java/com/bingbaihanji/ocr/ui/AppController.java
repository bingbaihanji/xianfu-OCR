package com.bingbaihanji.ocr.ui;

import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.ocr.capture.CaptureController;
import com.bingbaihanji.ocr.capture.CaptureMode;
import com.bingbaihanji.ocr.hotkey.GlobalHotkey;
import com.bingbaihanji.ocr.hotkey.HotkeyBinding;
import com.bingbaihanji.ocr.service.AnnotatedImageRenderer;
import com.bingbaihanji.ocr.service.RecognitionService;
import com.bingbaihanji.ocr.settings.AppSettings;
import com.bingbaihanji.ocr.settings.SettingsStore;
import com.bingbaihanji.ocr.ui.homepage.Homepage;
import com.bingbaihanji.ocr.ui.menu.AppMenuBar;
import com.bingbaihanji.ocr.ui.settings.SettingsDialog;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

// 应用控制器：装配主窗口事件与识别流
public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    // 全局热键 id
    private static final int HOTKEY_CAPTURE_RECOGNIZE = 1;
    private static final int HOTKEY_CAPTURE_PIN = 2;
    private static final int HOTKEY_TOGGLE_WINDOW = 3;

    private final Stage stage;
    private final RecognitionService recognition;
    private final AppSettings settings;
    private final Path jarDir;
    private final Homepage view;

    private BufferedImage currentImage;
    private OcrResult lastResult;
    private GlobalHotkey globalHotkey;
    private CaptureController activeCapture;

    public AppController(Stage stage, RecognitionService recognition, AppSettings settings, Path jarDir) {
        this.stage = stage;
        this.recognition = recognition;
        this.settings = settings;
        this.jarDir = jarDir;
        AppMenuBar menuBar = new AppMenuBar(
                this::openImage, this::exportText, this::saveAnnotated, this::openSettings, stage::close);
        this.view = new Homepage(menuBar);
        wire();
    }

    public Homepage getView() {
        return view;
    }

    // 启动后调用：注册全局热键 + 后台预热当前模型
    public void start() {
        globalHotkey = new GlobalHotkey(this::onGlobalHotkey);
        registerHotkeys();
        BufferedImage warm = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        recognition.submit(warm, buildConfig(), r -> {
        }, t -> view.toast().showError("模型预热失败: " + t.getMessage()));
    }

    // 退出前释放全局热键与进行中的截图
    public void dispose() {
        if (activeCapture != null) {
            activeCapture.close();
            activeCapture = null;
        }
        if (globalHotkey != null) {
            globalHotkey.close();
            globalHotkey = null;
        }
    }

    // 退出前保存窗口状态到 settings
    public void persistWindowState() {
        if (!settings.isRememberWindow()) {
            return;
        }
        settings.setWindowX(stage.getX());
        settings.setWindowY(stage.getY());
        settings.setWindowWidth(stage.getWidth());
        settings.setWindowHeight(stage.getHeight());
        settings.setWindowMaximized(stage.isMaximized());
    }

    // 启动时恢复窗口状态（在 show 之前调用）
    public void restoreWindowState() {
        if (!settings.isRememberWindow()) {
            return;
        }
        double x = settings.getWindowX();
        double y = settings.getWindowY();
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            stage.setX(x);
            stage.setY(y);
        }
        stage.setWidth(settings.getWindowWidth());
        stage.setHeight(settings.getWindowHeight());
        if (settings.isWindowMaximized()) {
            stage.setMaximized(true);
        }
    }

    // ------------------------------------------------------------------ 截图与全局热键

    // 启动截图（进行中时忽略重复触发）
    public void openCapture(CaptureMode mode) {
        if (activeCapture != null) {
            return;
        }
        CaptureController c = new CaptureController(mode,
                img -> showImage(img, "截图"),
                () -> activeCapture = null);
        activeCapture = c;
        try {
            c.start();
        } catch (RuntimeException e) {
            activeCapture = null;
            c.close();
            view.toast().showError(e.getMessage());
        }
    }

    private void onGlobalHotkey(int id) {
        switch (id) {
            case HOTKEY_CAPTURE_RECOGNIZE -> openCapture(CaptureMode.RECOGNIZE);
            case HOTKEY_CAPTURE_PIN -> openCapture(CaptureMode.PIN);
            case HOTKEY_TOGGLE_WINDOW -> toggleWindow();
            default -> {
            }
        }
    }

    private void toggleWindow() {
        if (stage.isShowing() && stage.isFocused()) {
            stage.setIconified(true);
        } else {
            stage.setIconified(false);
            stage.show();
            stage.toFront();
        }
    }

    private void registerHotkeys() {
        registerOne(HOTKEY_CAPTURE_RECOGNIZE, settings.getHotkeyCaptureRecognize());
        registerOne(HOTKEY_CAPTURE_PIN, settings.getHotkeyCapturePin());
        registerOne(HOTKEY_TOGGLE_WINDOW, settings.getHotkeyToggleWindow());
    }

    private void registerOne(int id, String combo) {
        try {
            if (!globalHotkey.register(id, HotkeyBinding.parse(combo))) {
                view.toast().showError("热键被占用: " + combo);
            }
        } catch (RuntimeException e) {
            view.toast().showError("热键注册失败: " + combo);
        }
    }

    // ------------------------------------------------------------------ 装配

    private void wire() {
        view.screenshotButton().setOnAction(e -> openCapture(CaptureMode.RECOGNIZE));
        view.openButton().setOnAction(e -> openImage());
        view.resultPane().setOnCopy(this::copyText);
        view.resultPane().setOnLineHover(i -> {
            if (i >= 0) {
                view.imagePane().highlightLine(i);
            } else {
                view.imagePane().clearHighlight();
            }
        });
        view.resultPane().setOnLineClick(this::copyLine);
        view.imagePane().setOnLineClick(view.resultPane()::selectLine);

        view.addEventFilter(KeyEvent.KEY_PRESSED, this::handlePasteHotkey);
        view.rootPane().setOnDragOver(this::handleDragOver);
        view.rootPane().setOnDragDropped(this::handleDragDropped);
    }

    // Ctrl+V 剪贴板有图则粘贴并识别；无图放行给文本控件
    private void handlePasteHotkey(KeyEvent e) {
        if (!e.isShortcutDown() || e.getCode() != KeyCode.V) {
            return;
        }
        Clipboard cb = Clipboard.getSystemClipboard();
        if (!cb.hasImage()) {
            return;
        }
        Image img = cb.getImage();
        if (img == null) {
            return;
        }
        e.consume();
        showImage(SwingFXUtils.fromFXImage(img, null), "剪贴板图片");
    }

    private void handleDragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (db.hasFiles() || db.hasImage()) {
            e.acceptTransferModes(TransferMode.COPY);
        }
        e.consume();
    }

    private void handleDragDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        boolean ok = false;
        if (db.hasFiles()) {
            for (File f : db.getFiles()) {
                if (isImageFile(f.getName())) {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) {
                            showImage(img, f.getName());
                            ok = true;
                            break;
                        }
                    } catch (IOException ex) {
                        view.toast().showError("读取失败: " + ex.getMessage());
                    }
                }
            }
        } else if (db.hasImage() && db.getImage() != null) {
            showImage(SwingFXUtils.fromFXImage(db.getImage(), null), "拖入图片");
            ok = true;
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    // ------------------------------------------------------------------ 识别流

    private void showImage(BufferedImage img, String source) {
        currentImage = img;
        lastResult = null;
        view.imagePane().setImage(img);
        view.resultPane().clear();
        log.info("载入图片: {} ({}×{})", source, img.getWidth(), img.getHeight());
        recognize();
    }

    private void recognize() {
        if (currentImage == null) {
            return;
        }
        view.statusBar().setRunning();
        recognition.submit(currentImage, buildConfig(), this::onResult, this::onRecognizeError);
    }

    private OcrConfig buildConfig() {
        return OcrConfig.builder()
                .model(settings.getModel())
                .minConfidence(settings.getMinConfidence())
                .maxSideLen(settings.getMaxSideLen())
                .enableRotate(settings.isEnableRotate())
                .build();
    }

    private void onResult(OcrResult r) {
        lastResult = r;
        view.imagePane().setLines(r.getLines());
        view.resultPane().setResult(r);
        view.statusBar().setIdle();
        view.statusBar().setResultInfo(r.getModel(), r.getLines().size(), r.getElapsedMillis());
        if (r.getLines().isEmpty()) {
            view.toast().show("未识别到文字");
        }
    }

    private void onRecognizeError(Throwable t) {
        log.error("识别失败", t);
        view.statusBar().setError(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        view.toast().showError("识别失败: " + t.getMessage());
    }

    // ------------------------------------------------------------------ 文件

    private void openImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("打开图片");
        fc.getExtensionFilters().add(imageFilter());
        File init = existingDir(settings.getLastOpenDir());
        if (init != null) {
            fc.setInitialDirectory(init);
        }
        File f = fc.showOpenDialog(stage);
        if (f == null) {
            return;
        }
        settings.setLastOpenDir(f.getParentFile().getAbsolutePath());
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                throw new IOException("无法解码图片: " + f.getName());
            }
            showImage(img, f.getName());
        } catch (IOException e) {
            view.toast().showError("打开失败: " + e.getMessage());
        }
    }

    private void exportText() {
        String text = view.resultPane().currentText();
        if (text == null || text.isBlank()) {
            view.toast().show("暂无识别结果可导出");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("导出文本");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件 (*.txt)", "*.txt"));
        fc.setInitialFileName("识别结果.txt");
        File init = existingDir(settings.getLastExportDir());
        fc.setInitialDirectory(init != null ? init : exportsDir().toFile());
        File f = fc.showSaveDialog(stage);
        if (f == null) {
            return;
        }
        try {
            Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
            settings.setLastExportDir(f.getParentFile().getAbsolutePath());
            view.toast().show("已导出: " + f.getName());
        } catch (IOException e) {
            view.toast().showError("导出失败: " + e.getMessage());
        }
    }

    private void saveAnnotated() {
        if (currentImage == null || lastResult == null) {
            view.toast().show("暂无可保存的识别结果");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("保存标注图");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片 (*.png)", "*.png"));
        fc.setInitialFileName("标注图.png");
        File init = existingDir(settings.getLastExportDir());
        fc.setInitialDirectory(init != null ? init : exportsDir().toFile());
        File f = fc.showSaveDialog(stage);
        if (f == null) {
            return;
        }
        try {
            BufferedImage out = AnnotatedImageRenderer.render(currentImage, lastResult);
            ImageIO.write(out, "png", f);
            settings.setLastExportDir(f.getParentFile().getAbsolutePath());
            view.toast().show("已保存: " + f.getName());
        } catch (IOException e) {
            view.toast().showError("保存失败: " + e.getMessage());
        }
    }

    private void openSettings() {
        new SettingsDialog(stage, settings, updated -> {
            settings.copyFrom(updated);
            try {
                SettingsStore.save(jarDir, settings);
                registerHotkeys();
                view.toast().show("设置已保存");
            } catch (IOException e) {
                view.toast().showError("设置保存失败: " + e.getMessage());
            }
        }).showAndWait();
    }

    // ------------------------------------------------------------------ 剪贴板

    private void copyText() {
        String text = view.resultPane().currentText();
        if (text == null || text.isBlank()) {
            view.toast().show("暂无可复制的文本");
            return;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
        view.toast().show("已复制全文");
    }

    private void copyLine(String line) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(line);
        Clipboard.getSystemClipboard().setContent(cc);
        view.toast().show("已复制该行");
    }

    // ------------------------------------------------------------------ 工具

    private Path exportsDir() {
        Path dir = jarDir.resolve("exports");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建导出目录失败: {}", e.toString());
        }
        return dir;
    }

    private static File existingDir(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File f = new File(path);
        return f.isDirectory() ? f : null;
    }

    private static FileChooser.ExtensionFilter imageFilter() {
        return new FileChooser.ExtensionFilter(
                "图片文件 (*.png, *.jpg, *.jpeg, *.bmp, *.gif, *.webp)",
                "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp");
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".gif") || lower.endsWith(".webp");
    }
}
