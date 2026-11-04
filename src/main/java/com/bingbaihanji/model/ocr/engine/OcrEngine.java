package com.bingbaihanji.model.ocr.engine;

import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.result.OcrBox;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.result.RecText;

import java.awt.image.BufferedImage;
import java.util.List;

// OCR 引擎 SPI，定义具体推理引擎必须实现的契约，便于替换不同后端
public interface OcrEngine extends AutoCloseable {

    // 按字节数组图片执行一次识别
    OcrResult recognize(byte[] image, OcrConfig config);

    // 按字节数组图片只做文本检测。结果必须已按 sortedBoxes 规则排序（上→下、左→右），
    // 与完整识别流程使用同一排序，保证 detect 与 recognize 的框顺序一致
    List<OcrBox> detect(byte[] image, OcrConfig config);

    // 对已裁剪文字块只做识别。内部执行 cls（若启用）+ rec，不经过 det，
    // 不做 minConfidence 过滤（由调用方决定），与 Python ocr(det=False) 一致
    List<RecText> recognizeCrops(List<byte[]> crops, OcrConfig config);

    // 把识别结果画回原图，并在右侧拼接文字面板（对应 Python draw_ocr）
    BufferedImage visualize(BufferedImage image, OcrResult result);

    // 释放引擎持有的会话等资源
    @Override
    void close();
}