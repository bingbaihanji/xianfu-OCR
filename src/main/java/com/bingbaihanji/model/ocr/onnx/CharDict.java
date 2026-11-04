package com.bingbaihanji.model.ocr.onnx;

import com.bingbaihanji.model.ocr.exception.OcrModelException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

// CTC 字典，index 0 固定为 blank
public final class CharDict {

    private final String[] characters;

    private final boolean reverse;

    // 私有构造
    private CharDict(String[] characters, boolean reverse) {
        this.characters = characters;
        this.reverse = reverse;
    }

    /**
     * 从输入流装载字典。<b>本方法会关闭传入的输入流</b>（内部用 try-with-resources 包裹），
     * 调用方不应再自行关闭，也不要复用该流。
     * <p>
     * 行内容原样保留：readLine 已去掉行终止符，这里刻意不再 strip。
     * 字典里存在全角空格 U+3000 这类“空白字符即字符”的条目
     * （ppocrv5_dict.txt 第一行就是 U+3000），stripTrailing 会把它清成空串。
     *
     * @param in           字典文本，每行一个字符
     * @param useSpaceChar 是否在末尾追加空格字符
     * @param sourcePath   原始资源路径，仅用于 arabic 判定与错误信息
     */
    public static CharDict load(InputStream in, boolean useSpaceChar, String sourcePath) {
        Objects.requireNonNull(in, "in");
        List<String> chars = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                chars.add(line);
            }
        } catch (IOException e) {
            throw new OcrModelException("读取字典失败: " + sourcePath, e);
        }
        if (useSpaceChar) {
            chars.add(" ");
        }
        boolean reverse = sourcePath != null
                && sourcePath.toLowerCase(Locale.ROOT).contains("arabic");
        // CTC blank 占 index 0
        String[] characters = new String[chars.size() + 1];
        characters[0] = "blank";
        for (int i = 0; i < chars.size(); i++) {
            characters[i + 1] = chars.get(i);
        }
        return new CharDict(characters, reverse);
    }

    // 字符总数（含 blank）
    public int size() {
        return characters.length;
    }

    // 按索引取字符，越界返回 null
    public String get(int index) {
        return index >= 0 && index < characters.length ? characters[index] : null;
    }

    // 是否为阿拉伯语（识别结果需反转连续拉丁片段）
    public boolean isReverse() {
        return reverse;
    }
}
