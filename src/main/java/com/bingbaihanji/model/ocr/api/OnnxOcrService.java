package com.bingbaihanji.model.ocr.api;

import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.config.OcrProperties;
import com.bingbaihanji.model.ocr.engine.OcrEngine;
import com.bingbaihanji.model.ocr.engine.OnnxOcrEngine;
import com.bingbaihanji.model.ocr.result.OcrBox;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.result.RecText;

import java.awt.image.BufferedImage;
import java.util.List;

// 基于 ONNX 引擎的 OCR 服务门面，将 OcrEngine 适配为 OcrService
public class OnnxOcrService extends AbstractOcrService {

    // 底层推理引擎
    private final OcrEngine engine;

    // 使用自定义引擎构造（便于测试或替换后端）
    public OnnxOcrService(OcrEngine engine) {
        this.engine = engine;
    }

    // 使用运行属性构造默认 ONNX 引擎
    public OnnxOcrService(OcrProperties props) {
        this(new OnnxOcrEngine(props));
    }

    // 委托引擎执行实际识别
    @Override
    protected OcrResult doRecognize(byte[] image, OcrConfig config) {
        return engine.recognize(image, config);
    }

    // 委托引擎执行检测
    @Override
    protected List<OcrBox> doDetect(byte[] image, OcrConfig config) {
        return engine.detect(image, config);
    }

    // 委托引擎执行裁剪识别
    @Override
    protected List<RecText> doRecognizeCrops(List<byte[]> crops, OcrConfig config) {
        return engine.recognizeCrops(crops, config);
    }

    // 委托引擎执行可视化
    @Override
    protected BufferedImage doVisualize(BufferedImage image, OcrResult result) {
        return engine.visualize(image, result);
    }

    // 关闭服务并释放引擎资源
    @Override
    public void close() {
        engine.close();
    }
}
