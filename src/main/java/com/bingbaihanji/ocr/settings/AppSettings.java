package com.bingbaihanji.ocr.settings;

import com.bingbaihanji.model.ocr.enums.OcrModel;

// 应用设置（可变）：识别参数、窗口状态与最近目录；序列化经 SettingsStore
public class AppSettings {

    private OcrModel model = OcrModel.PPOCR_V5;
    private double minConfidence = 0.5;
    private int maxSideLen = 960;
    private boolean enableRotate = false;

    private boolean rememberWindow = true;
    private double windowX = Double.NaN;
    private double windowY = Double.NaN;
    private double windowWidth = 1100;
    private double windowHeight = 720;
    private boolean windowMaximized = false;

    private boolean clearClipboardOnExit = false;

    private String lastOpenDir = "";
    private String lastExportDir = "";

    public OcrModel getModel() {
        return model;
    }

    public void setModel(OcrModel model) {
        this.model = model;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public int getMaxSideLen() {
        return maxSideLen;
    }

    public void setMaxSideLen(int maxSideLen) {
        this.maxSideLen = maxSideLen;
    }

    public boolean isEnableRotate() {
        return enableRotate;
    }

    public void setEnableRotate(boolean enableRotate) {
        this.enableRotate = enableRotate;
    }

    public boolean isRememberWindow() {
        return rememberWindow;
    }

    public void setRememberWindow(boolean rememberWindow) {
        this.rememberWindow = rememberWindow;
    }

    // 未记录位置时为 NaN
    public double getWindowX() {
        return windowX;
    }

    public void setWindowX(double windowX) {
        this.windowX = windowX;
    }

    public double getWindowY() {
        return windowY;
    }

    public void setWindowY(double windowY) {
        this.windowY = windowY;
    }

    public double getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(double windowWidth) {
        this.windowWidth = windowWidth;
    }

    public double getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(double windowHeight) {
        this.windowHeight = windowHeight;
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }

    public boolean isClearClipboardOnExit() {
        return clearClipboardOnExit;
    }

    public void setClearClipboardOnExit(boolean clearClipboardOnExit) {
        this.clearClipboardOnExit = clearClipboardOnExit;
    }

    public String getLastOpenDir() {
        return lastOpenDir;
    }

    public void setLastOpenDir(String lastOpenDir) {
        this.lastOpenDir = lastOpenDir == null ? "" : lastOpenDir;
    }

    public String getLastExportDir() {
        return lastExportDir;
    }

    public void setLastExportDir(String lastExportDir) {
        this.lastExportDir = lastExportDir == null ? "" : lastExportDir;
    }

    // 复制一份（设置对话框在副本上编辑）
    public AppSettings copy() {
        AppSettings c = new AppSettings();
        c.copyFrom(this);
        return c;
    }

    // 从另一份设置覆盖全部字段
    public void copyFrom(AppSettings other) {
        this.model = other.model;
        this.minConfidence = other.minConfidence;
        this.maxSideLen = other.maxSideLen;
        this.enableRotate = other.enableRotate;
        this.rememberWindow = other.rememberWindow;
        this.windowX = other.windowX;
        this.windowY = other.windowY;
        this.windowWidth = other.windowWidth;
        this.windowHeight = other.windowHeight;
        this.windowMaximized = other.windowMaximized;
        this.clearClipboardOnExit = other.clearClipboardOnExit;
        this.lastOpenDir = other.lastOpenDir;
        this.lastExportDir = other.lastExportDir;
    }
}
