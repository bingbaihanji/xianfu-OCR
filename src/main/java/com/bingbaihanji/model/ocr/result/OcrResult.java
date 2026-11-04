package com.bingbaihanji.model.ocr.result;

import com.bingbaihanji.model.ocr.enums.OcrModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// OCR 结构化识别结果，包含整体文本、置信度、逐行明细、模型信息、耗时与阶段耗时
public final class OcrResult {

    private final String text;

    private final double confidence;

    private final List<OcrLine> lines;

    private final OcrModel model;

    private final String modelVersion;

    private final long elapsedMillis;

    private final Map<String, Long> stageTimings;

    // 私有构造，统一经由 Builder 创建，保证不可变
    private OcrResult(Builder b) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(b.lines));
        this.text = b.text != null ? b.text : joinLines(this.lines);
        this.confidence = b.confidence;
        this.model = b.model;
        this.modelVersion = b.modelVersion;
        this.elapsedMillis = b.elapsedMillis;
        this.stageTimings = b.stageTimings == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(b.stageTimings);
    }

    // 拼接所有行文本作为默认整体文本
    private static String joinLines(List<OcrLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (OcrLine line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.getText());
        }
        return sb.toString();
    }

    // 创建结果 Builder
    public static Builder builder() {
        return new Builder();
    }

    // 获取整体文本
    public String getText() {
        return text;
    }

    // 获取整体置信度，范围 0.0 ~ 1.0
    public double getConfidence() {
        return confidence;
    }

    // 获取逐行识别明细（只读）
    public List<OcrLine> getLines() {
        return lines;
    }

    // 获取所使用的识别模型
    public OcrModel getModel() {
        return model;
    }

    // 获取实际执行识别的引擎版本
    public String getModelVersion() {
        return modelVersion;
    }

    // 获取整体识别耗时（毫秒）
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    // 获取各阶段耗时明细（预处理/推理/后处理等）
    public Map<String, Long> getStageTimings() {
        return stageTimings;
    }

    // 返回结果摘要字符串（截断文本，避免日志过长）
    @Override
    public String toString() {
        String preview = text.length() <= 64 ? text : text.substring(0, 64) + "...";
        return "OcrResult{model=" + model + ", lines=" + lines.size()
                + ", confidence=" + String.format("%.3f", confidence)
                + ", elapsed=" + elapsedMillis + "ms, text='" + preview + "'}";
    }

    // 结果 Builder，逐项设置并构建不可变 OcrResult
    public static final class Builder {

        private String text;

        private double confidence;

        private List<OcrLine> lines = Collections.emptyList();

        private OcrModel model;

        private String modelVersion;

        private long elapsedMillis;

        private Map<String, Long> stageTimings;

        // 设置整体文本（不设置时由 lines 自动拼接）
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        // 设置整体置信度
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        // 设置逐行明细
        public Builder lines(List<OcrLine> lines) {
            this.lines = lines == null ? Collections.emptyList() : lines;
            return this;
        }

        // 设置识别模型
        public Builder model(OcrModel model) {
            this.model = model;
            return this;
        }

        // 设置引擎版本
        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        // 设置整体耗时（毫秒）
        public Builder elapsedMillis(long elapsedMillis) {
            this.elapsedMillis = elapsedMillis;
            return this;
        }

        // 设置阶段耗时明细
        public Builder stageTimings(Map<String, Long> stageTimings) {
            this.stageTimings = stageTimings;
            return this;
        }

        // 构建不可变 OcrResult 实例
        public OcrResult build() {
            return new OcrResult(this);
        }
    }
}