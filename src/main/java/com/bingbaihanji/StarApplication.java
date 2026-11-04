package com.bingbaihanji;

import com.bingbaihanji.ocr.ui.XianFuOCRApplication;
import javafx.application.Application;

/**
 * GUI 启动器。
 * <p>
 * 不在 {@link XianFuOCRApplication}（Application 子类）上直接写 main——
 * classpath 模式下 JavaFX 启动器会做模块检查并报「缺少 JavaFX 运行时组件」；
 * 由非 Application 子类的入口调用 {@code Application.launch} 可绕过该检查。
 *
 * @author bingbaihanji
 * @date 2026-11-04 11:16:00
 */
public class StarApplication {

    public static void main(String[] args) {
        Application.launch(XianFuOCRApplication.class, args);
    }
}
