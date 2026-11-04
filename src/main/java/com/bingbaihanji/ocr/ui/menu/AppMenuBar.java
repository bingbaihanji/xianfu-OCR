package com.bingbaihanji.ocr.ui.menu;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

// 主菜单栏：文件（打开/导出/保存/退出）、设置（设置…）
public class AppMenuBar extends MenuBar {

    public AppMenuBar(Runnable onOpen, Runnable onExport, Runnable onSave,
                      Runnable onSettings, Runnable onExit) {
        Menu file = new Menu("文件");
        file.getItems().addAll(
                item("打开图片…", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), onOpen),
                item("导出文本…", new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), onExport),
                item("保存标注图…", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), onSave),
                new SeparatorMenuItem(),
                item("退出", null, onExit));

        Menu settings = new Menu("设置");
        settings.getItems().add(
                item("设置…", new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN), onSettings));

        getMenus().addAll(file, settings);
    }

    private static MenuItem item(String text, KeyCombination accel, Runnable action) {
        MenuItem mi = new MenuItem(text);
        if (accel != null) {
            mi.setAccelerator(accel);
        }
        mi.setOnAction(e -> action.run());
        return mi;
    }
}
