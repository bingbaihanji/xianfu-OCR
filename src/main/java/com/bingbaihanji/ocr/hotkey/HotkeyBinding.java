package com.bingbaihanji.ocr.hotkey;

import javafx.scene.input.KeyCode;

import java.util.Locale;
import java.util.Objects;

// 组合键绑定：解析/规范化/显示/冲突检测 + Win32 MOD_/VK 映射（供 GlobalHotkey 使用）
public final class HotkeyBinding {

    // Win32 fsModifiers（RegisterHotKey 用）
    public static final int MOD_ALT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_SHIFT = 0x0004;
    public static final int MOD_WIN = 0x0008;
    public static final int MOD_NOREPEAT = 0x4000;

    // 三个动作的默认热键
    public static final String DEFAULT_CAPTURE_RECOGNIZE = "CTRL+ALT+A";
    public static final String DEFAULT_CAPTURE_PIN = "CTRL+ALT+X";
    public static final String DEFAULT_TOGGLE_WINDOW = "CTRL+ALT+Z";

    private final String combo; // 规范形如 CTRL+SHIFT+ALT+WIN+A

    private HotkeyBinding(String combo) {
        this.combo = combo;
    }

    // 解析组合键（大小写不敏感、顺序任意）；非法抛 IllegalArgumentException
    public static HotkeyBinding parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        boolean win = false;
        String key = null;
        for (String part : raw.toUpperCase(Locale.ROOT).split("\\+")) {
            String t = part.trim();
            switch (t) {
                case "CTRL", "CONTROL" -> ctrl = true;
                case "SHIFT" -> shift = true;
                case "ALT" -> alt = true;
                case "WIN", "META" -> win = true;
                default -> {
                    if (key != null) {
                        throw new IllegalArgumentException("多余按键: " + t);
                    }
                    if (!isKeyToken(t)) {
                        throw new IllegalArgumentException("未知按键: " + t);
                    }
                    key = t;
                }
            }
        }
        if (key == null) {
            throw new IllegalArgumentException("缺少主按键: " + raw);
        }
        StringBuilder sb = new StringBuilder();
        if (ctrl) {
            sb.append("CTRL+");
        }
        if (shift) {
            sb.append("SHIFT+");
        }
        if (alt) {
            sb.append("ALT+");
        }
        if (win) {
            sb.append("WIN+");
        }
        sb.append(key);
        return new HotkeyBinding(sb.toString());
    }

    // 从 JavaFX 按键事件构建（快捷键录制用）；不支持的键抛 IllegalArgumentException
    public static HotkeyBinding fromFx(KeyCode code, boolean ctrl, boolean shift, boolean alt, boolean win) {
        String name = code.getName();
        String key;
        if (name.length() == 1 && Character.isLetterOrDigit(name.charAt(0))) {
            key = name.toUpperCase(Locale.ROOT);
        } else if (name.matches("F\\d{1,2}")) {
            key = name.toUpperCase(Locale.ROOT);
        } else {
            throw new IllegalArgumentException("不支持的按键: " + name);
        }
        StringBuilder sb = new StringBuilder();
        if (ctrl) {
            sb.append("CTRL+");
        }
        if (shift) {
            sb.append("SHIFT+");
        }
        if (alt) {
            sb.append("ALT+");
        }
        if (win) {
            sb.append("WIN+");
        }
        sb.append(key);
        return parse(sb.toString());
    }

    static boolean isKeyToken(String t) {
        return (t.length() == 1 && Character.isLetterOrDigit(t.charAt(0)))
                || t.matches("F[1-9]|F1[0-9]|F2[0-4]");
    }

    // 规范化组合键（如 CTRL+ALT+A）
    public String combo() {
        return combo;
    }

    // 人类可读（如 Ctrl+Alt+A）
    public String display() {
        return combo.replace("CTRL", "Ctrl").replace("SHIFT", "Shift")
                .replace("ALT", "Alt").replace("WIN", "Win");
    }

    // Win32 修饰键位（不含 MOD_NOREPEAT，由 GlobalHotkey 注册时附加）
    public int modifiers() {
        int m = 0;
        if (combo.startsWith("CTRL") || combo.contains("+CTRL")) {
            m |= MOD_CONTROL;
        }
        if (combo.contains("SHIFT")) {
            m |= MOD_SHIFT;
        }
        // WIN 放在 ALT 前判断，避免 "WIN" 与其它词冲突（规范串里 WIN 只作为整段）
        for (String part : combo.split("\\+")) {
            switch (part) {
                case "ALT" -> m |= MOD_ALT;
                case "WIN" -> m |= MOD_WIN;
                default -> {
                }
            }
        }
        return m;
    }

    // Win32 虚拟键码（A-Z / 0-9 / F1-F24）
    public int virtualKey() {
        String key = combo.substring(combo.lastIndexOf('+') + 1);
        if (key.startsWith("F") && key.length() > 1) {
            return 0x70 + Integer.parseInt(key.substring(1)) - 1; // VK_F1 = 0x70
        }
        return key.charAt(0); // 大写字母/数字的 ASCII 即 VK 码
    }

    // 是否冲突（同一组合）
    public static boolean conflicts(HotkeyBinding a, HotkeyBinding b) {
        return a.combo.equals(b.combo);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HotkeyBinding h && combo.equals(h.combo);
    }

    @Override
    public int hashCode() {
        return combo.hashCode();
    }

    @Override
    public String toString() {
        return combo;
    }
}
