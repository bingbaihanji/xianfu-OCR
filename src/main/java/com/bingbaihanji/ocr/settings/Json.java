package com.bingbaihanji.ocr.settings;

import java.util.LinkedHashMap;
import java.util.Map;

// 轻量 JSON 读写：仅支持「扁平对象」——键-值对，值限字符串/数值/布尔。
// 满足 settings.json 持久化需求，避免为配置引入第三方 JSON 库。
public final class Json {

    private Json() {
    }

    // 序列化为 JSON 字符串；null 值键被跳过；不支持的类型抛 IllegalArgumentException
    public static String write(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            appendValue(sb, v);
        }
        return sb.append('}').toString();
    }

    // 解析扁平 JSON 对象；语法错误抛 IllegalArgumentException
    public static Map<String, Object> parse(String text) {
        return new Parser(text).parseObject();
    }

    // 转义 JSON 字符串内容
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, Object v) {
        if (v instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Double) {
            sb.append(v);
        } else {
            throw new IllegalArgumentException("不支持的值类型: " + v.getClass().getName());
        }
    }

    // 递归下降解析器（本项目只用到对象 + 原始值）
    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        Map<String, Object> parseObject() {
            Map<String, Object> out = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                next();
                return out;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                skipWs();
                out.put(key, readValue());
                skipWs();
                char c = next();
                if (c == '}') {
                    return out;
                }
                if (c != ',') {
                    throw err("期望 ',' 或 '}'，实际为 '" + c + "'");
                }
            }
        }

        private Object readValue() {
            char c = peek();
            return switch (c) {
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                default -> readNumber();
            };
        }

        private Object readNumber() {
            int start = pos;
            if (peek() == '-') {
                next();
            }
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    next();
                } else {
                    break;
                }
            }
            String s = text.substring(start, pos);
            try {
                return s.contains(".") || s.contains("e") || s.contains("E")
                        ? (Object) Double.parseDouble(s)
                        : (Object) Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw err("非法数字: " + s);
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, pos)) {
                throw err("非法字面量");
            }
            pos += literal.length();
            return value;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw err("\\u 转义不完整");
                        }
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw err("非法转义: \\" + e);
                }
            }
        }

        private void skipWs() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw err("意外结束");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            char actual = next();
            if (actual != c) {
                throw err("期望 '" + c + "'，实际为 '" + actual + "'");
            }
        }

        private IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON 解析失败 (pos=" + pos + "): " + msg);
        }
    }
}
