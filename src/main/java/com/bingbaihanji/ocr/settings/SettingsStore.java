package com.bingbaihanji.ocr.settings;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import com.bingbaihanji.ocr.hotkey.HotkeyBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

// 设置持久化：<jarDir>/config/settings.json；损坏时备份 settings.bad.json 并回退默认值
public final class SettingsStore {

    private static final Logger log = LoggerFactory.getLogger(SettingsStore.class);

    private SettingsStore() {
    }

    // 配置文件位置（jar 同目录 config/ 下）
    public static Path file(Path jarDir) {
        return jarDir.resolve("config").resolve("settings.json");
    }

    // 读取设置；文件缺失返回默认值，损坏则备份并返回默认值
    public static AppSettings load(Path jarDir) {
        Path f = file(jarDir);
        if (!Files.isRegularFile(f)) {
            return new AppSettings();
        }
        try {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            return fromMap(Json.parse(text));
        } catch (IOException | RuntimeException e) {
            log.warn("配置读取失败，回退默认值: {}", e.toString());
            backupBad(f);
            return new AppSettings();
        }
    }

    // 保存设置（自动建 config/ 目录）
    public static void save(Path jarDir, AppSettings s) throws IOException {
        Path f = file(jarDir);
        Files.createDirectories(f.getParent());
        Files.writeString(f, Json.write(toMap(s)), StandardCharsets.UTF_8);
    }

    private static void backupBad(Path f) {
        try {
            Path bad = f.resolveSibling("settings.bad.json");
            Files.move(f, bad, StandardCopyOption.REPLACE_EXISTING);
            log.warn("损坏配置已备份到: {}", bad);
        } catch (IOException e) {
            log.warn("备份损坏配置失败: {}", e.toString());
        }
    }

    static Map<String, Object> toMap(AppSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", s.getModel().getCode());
        m.put("minConfidence", s.getMinConfidence());
        m.put("maxSideLen", s.getMaxSideLen());
        m.put("enableRotate", s.isEnableRotate());
        m.put("rememberWindow", s.isRememberWindow());
        if (!Double.isNaN(s.getWindowX())) {
            m.put("windowX", s.getWindowX());
        }
        if (!Double.isNaN(s.getWindowY())) {
            m.put("windowY", s.getWindowY());
        }
        m.put("windowWidth", s.getWindowWidth());
        m.put("windowHeight", s.getWindowHeight());
        m.put("windowMaximized", s.isWindowMaximized());
        m.put("clearClipboardOnExit", s.isClearClipboardOnExit());
        m.put("hotkey.captureRecognize", s.getHotkeyCaptureRecognize());
        m.put("hotkey.capturePin", s.getHotkeyCapturePin());
        m.put("hotkey.toggleWindow", s.getHotkeyToggleWindow());
        m.put("lastOpenDir", s.getLastOpenDir());
        m.put("lastExportDir", s.getLastExportDir());
        return m;
    }

    static AppSettings fromMap(Map<String, Object> m) {
        AppSettings s = new AppSettings();
        s.setModel(OcrModel.fromCode(str(m, "model", "")).orElse(OcrModel.PPOCR_V5));
        s.setMinConfidence(num(m, "minConfidence", 0.5));
        s.setMaxSideLen((int) num(m, "maxSideLen", 960));
        s.setEnableRotate(bool(m, "enableRotate", false));
        s.setRememberWindow(bool(m, "rememberWindow", true));
        s.setWindowX(num(m, "windowX", Double.NaN));
        s.setWindowY(num(m, "windowY", Double.NaN));
        s.setWindowWidth(num(m, "windowWidth", 1100));
        s.setWindowHeight(num(m, "windowHeight", 720));
        s.setWindowMaximized(bool(m, "windowMaximized", false));
        s.setClearClipboardOnExit(bool(m, "clearClipboardOnExit", false));
        s.setHotkeyCaptureRecognize(str(m, "hotkey.captureRecognize", HotkeyBinding.DEFAULT_CAPTURE_RECOGNIZE));
        s.setHotkeyCapturePin(str(m, "hotkey.capturePin", HotkeyBinding.DEFAULT_CAPTURE_PIN));
        s.setHotkeyToggleWindow(str(m, "hotkey.toggleWindow", HotkeyBinding.DEFAULT_TOGGLE_WINDOW));
        s.setLastOpenDir(str(m, "lastOpenDir", ""));
        s.setLastExportDir(str(m, "lastExportDir", ""));
        return s;
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private static double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }
}
