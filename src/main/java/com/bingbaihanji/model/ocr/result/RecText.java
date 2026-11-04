package com.bingbaihanji.model.ocr.result;

import java.util.Objects;

// OCR 纯识别结果，仅携带文本与置信度，不含坐标
public final class RecText {

    private final String text;

    private final double confidence;

    // 构造识别结果
    public RecText(String text, double confidence) {
        this.text = Objects.requireNonNull(text, "text");
        this.confidence = confidence;
    }

    // 截断过长文本，供 toString 使用
    private static String truncate(String s) {
        return s.length() <= 32 ? s : s.substring(0, 32) + "...";
    }

    // 获取文本内容
    public String getText() {
        return text;
    }

    // 获取置信度，范围 0.0 ~ 1.0
    public double getConfidence() {
        return confidence;
    }

    // 返回识别结果的可读字符串（截断文本，避免日志过长）
    @Override
    public String toString() {
        return "RecText{text='" + truncate(text) + "', confidence=" + confidence + "}";
    }

    // 基于文本与置信度判断相等性
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecText that)) {
            return false;
        }
        return Double.compare(that.confidence, confidence) == 0 && text.equals(that.text);
    }

    // 计算哈希值
    @Override
    public int hashCode() {
        return Objects.hash(text, confidence);
    }
}
