package com.bingbaihanji.model.ocr.api;

import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.enums.OcrModel;
import com.bingbaihanji.model.ocr.result.OcrBox;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.result.RecText;
import com.bingbaihanji.model.ocr.util.ImageIoUtils;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// OCR 识别服务接口，对外暴露统一识别入口，实现类应为无状态且线程安全
public interface OcrService extends AutoCloseable {

    // 核心抽象方法：按字节数组完成一次识别（子类实现具体推理）
    OcrResult recognize(byte[] image, OcrConfig config);

    // 便捷方法：BufferedImage 输入 + 完整配置，内部归一化为 PNG 字节
    default OcrResult recognize(BufferedImage image, OcrConfig config) {
        return recognize(ImageIoUtils.toPngBytes(image), config);
    }

    // 便捷方法：InputStream 输入 + 完整配置，内部全量读取为字节
    default OcrResult recognize(InputStream image, OcrConfig config) {
        return recognize(ImageIoUtils.readAll(image), config);
    }

    // 便捷方法：字节数组输入 + 默认配置
    default OcrResult recognize(byte[] image) {
        return recognize(image, OcrConfig.defaults());
    }

    // 便捷方法：BufferedImage 输入 + 默认配置
    default OcrResult recognize(BufferedImage image) {
        return recognize(image, OcrConfig.defaults());
    }

    // 便捷方法：InputStream 输入 + 默认配置
    default OcrResult recognize(InputStream image) {
        return recognize(image, OcrConfig.defaults());
    }

    // 便捷方法：字节数组输入 + 仅指定模型
    default OcrResult recognize(byte[] image, OcrModel model) {
        return recognize(image, OcrConfig.of(model));
    }

    // 便捷方法：BufferedImage 输入 + 仅指定模型
    default OcrResult recognize(BufferedImage image, OcrModel model) {
        return recognize(image, OcrConfig.of(model));
    }

    // 便捷方法：InputStream 输入 + 仅指定模型
    default OcrResult recognize(InputStream image, OcrModel model) {
        return recognize(image, OcrConfig.of(model));
    }

    // 便捷方法：批量识别，默认串行逐张执行
    default List<OcrResult> recognizeBatch(List<byte[]> images, OcrConfig config) {
        Objects.requireNonNull(images, "images");
        return images.stream().map(img -> recognize(img, config)).collect(Collectors.toList());
    }

    // 只检测，返回文本框（按 sortedBoxes 规则排序：上→下、左→右）
    List<OcrBox> detect(byte[] image, OcrConfig config);

    // 便捷方法：BufferedImage 输入 + 完整配置
    default List<OcrBox> detect(BufferedImage image, OcrConfig config) {
        return detect(ImageIoUtils.toPngBytes(image), config);
    }

    // 便捷方法：字节数组输入 + 默认配置
    default List<OcrBox> detect(byte[] image) {
        return detect(image, OcrConfig.defaults());
    }

    // 便捷方法：BufferedImage 输入 + 默认配置
    default List<OcrBox> detect(BufferedImage image) {
        return detect(image, OcrConfig.defaults());
    }

    // 只识别，输入是已裁剪的文字块。内部执行 cls（若启用）+ rec，
    // 不经过 det，不做 minConfidence 过滤（由调用方决定）。
    // 传 BufferedImage 列表时先经 ImageIoUtils.toPngBytes 逐个转换（泛型擦除无法重载）
    List<RecText> recognizeCrops(List<byte[]> crops, OcrConfig config);

    // 可视化：把识别结果画回原图并拼接文字面板
    BufferedImage visualize(BufferedImage image, OcrResult result);

    // 释放服务持有的资源（会话、线程池等），默认空实现
    @Override
    default void close() {
    }
}