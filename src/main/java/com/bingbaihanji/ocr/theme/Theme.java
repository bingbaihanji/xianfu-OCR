package com.bingbaihanji.ocr.theme;

import javafx.scene.Scene;

// 主题常量与样式装载（全部颜色/圆角在 dark.css 中以 looked-up colors 统一定义）
public final class Theme {

    public static final String ACCENT = "#5AB0FF";
    public static final String DANGER = "#E06C75";

    private Theme() {
    }

    // 把暗色样式表挂到场景（幂等，重复调用只会挂一次）
    public static void apply(Scene scene) {
        String css = Theme.class.getResource("/css/dark.css").toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }
}
