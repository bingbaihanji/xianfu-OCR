package com.bingbaihanji.model.ocr.enums;

import lombok.Getter;

import java.util.Optional;

// 本模块支持识别的 ONNX 模型枚举，标识具体使用哪个模型文件
@Getter
public enum OcrModel {

    // 默认模型，由实现方自行决定实际加载的模型文件
    DEFAULT("default", "默认 ONNX 模型"),

    // PaddleOCR V4 系列 ONNX 模型
    PPOCR_V4("ppocr_v4", "PaddleOCR V4 ONNX 模型"),

    // PaddleOCR V5 系列 ONNX 模型
    PPOCR_V5("ppocr_v5", "PaddleOCR V5 ONNX 模型"),

    // PaddleOCR V6 系列 ONNX 模型（默认指向 small 档，见 model/ppocrv6/README.md）
    PPOCR_V6("ppocr_v6", "PaddleOCR V6 ONNX 模型"),

    // PaddleOCR V6 tiny 档（速度优先，字典覆盖较小）
    PPOCR_V6_TINY("ppocr_v6_tiny", "PaddleOCR V6 tiny 档"),

    // PaddleOCR V6 medium 档（精度优先，CPU 上 rec 明显更慢）
    PPOCR_V6_MEDIUM("ppocr_v6_medium", "PaddleOCR V6 medium 档");

    // 获取模型编码（用于序列化、配置、日志）

    private final String code;

    // 获取模型描述

    private final String description;

    // 构造模型枚举值
    OcrModel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    // 根据编码反查模型枚举（大小写不敏感，未命中返回 Optional.empty）
    public static Optional<OcrModel> fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return Optional.empty();
        }
        for (OcrModel m : values()) {
            if (m.code.equalsIgnoreCase(code)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    // 返回模型编码作为字符串表示
    @Override
    public String toString() {
        return code;
    }
}