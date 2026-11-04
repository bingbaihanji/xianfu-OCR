package com.bingbaihanji.model.ocr.config;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// OCR 识别配置，承载模型、语言、置信度阈值、最大边长、方向分类等常用参数及扩展参数
@Getter
public final class OcrConfig {

    // 获取识别模型

    private final OcrModel model;

    // 获取语言标识，可为 null（表示由模型文件决定）

    private final String language;

    // 获取最低置信度阈值，低于该值的行会被过滤

    private final double minConfidence;

    // 获取预处理最大边长（det_limit_side_len，det_limit_type 固定为 max）。
    // 注意：引擎把 0 或负数视为「用默认值 960」，目前没有「完全不缩放」的表达方式

    private final int maxSideLen;

    // 是否启用方向分类（cls）

    private final boolean enableRotate;

    // 获取扩展参数（只读）

    private final Map<String, Object> options;

    // 私有构造，统一经由 Builder 创建，保证不可变
    private OcrConfig(Builder b) {
        this.model = Objects.requireNonNull(b.model, "model");
        this.language = b.language;
        this.minConfidence = b.minConfidence;
        this.maxSideLen = b.maxSideLen;
        this.enableRotate = b.enableRotate;
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(b.options));
    }

    // 创建默认配置
    public static OcrConfig defaults() {
        return builder().build();
    }

    // 创建仅指定模型的配置
    public static OcrConfig of(OcrModel model) {
        return builder().model(model).build();
    }

    // 创建配置 Builder
    public static Builder builder() {
        return new Builder();
    }

    // 返回配置摘要
    @Override
    public String toString() {
        return "OcrConfig{model=" + model + ", language=" + language
                + ", minConfidence=" + minConfidence + ", maxSideLen=" + maxSideLen
                + ", enableRotate=" + enableRotate + ", options=" + options + "}";
    }

    // 配置 Builder
    public static final class Builder {

        private final Map<String, Object> options = new LinkedHashMap<>();

        private OcrModel model = OcrModel.DEFAULT;

        private String language;

        private double minConfidence = 0.5;

        private int maxSideLen = 960;

        private boolean enableRotate = false;

        // 设置识别模型
        public Builder model(OcrModel model) {
            this.model = model;
            return this;
        }

        // 设置语言标识（null 表示由模型文件决定）
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        // 设置最低置信度阈值
        public Builder minConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
            return this;
        }

        // 设置预处理最大边长
        public Builder maxSideLen(int maxSideLen) {
            this.maxSideLen = maxSideLen;
            return this;
        }

        // 设置是否启用方向分类
        public Builder enableRotate(boolean enableRotate) {
            this.enableRotate = enableRotate;
            return this;
        }

        // 追加扩展参数（引擎专属参数，实验性用途）
        public Builder option(String key, Object value) {
            this.options.put(key, value);
            return this;
        }

        // 构建不可变 OcrConfig 实例
        public OcrConfig build() {
            return new OcrConfig(this);
        }
    }
}