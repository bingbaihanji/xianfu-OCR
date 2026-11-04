package com.bingbaihanji.model.ocr.api;

import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.exception.OcrException;
import com.bingbaihanji.model.ocr.result.OcrBox;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.result.RecText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

// OCR 服务抽象基类，通过模板方法统一处理参数校验、计时、日志与异常包装
public abstract class AbstractOcrService implements OcrService {

    // 子类共享的日志器
    private static final Logger log = LoggerFactory.getLogger(AbstractOcrService.class);

    // 最终方法：统一入口，负责参数校验、计时、异常包装，子类只需实现 doRecognize
    @Override
    public final OcrResult recognize(byte[] image, OcrConfig config) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(config, "config");
        long start = System.nanoTime();
        try {
            return doRecognize(image, config);
        } catch (OcrException e) {
            throw e;
        } catch (Throwable t) {
            throw new OcrException("OCR 识别失败, model=" + config.getModel(), t);
        } finally {
            long cost = (System.nanoTime() - start) / 1_000_000;
            log.debug("ocr recognize model={}, bytes={}, cost={}ms",
                    config.getModel(), image.length, cost);
        }
    }

    // 抽象方法：子类实现具体推理逻辑（输入已归一化为字节数组）
    protected abstract OcrResult doRecognize(byte[] image, OcrConfig config);

    // 最终方法：统一检测入口，负责参数校验、计时、异常包装
    @Override
    public final List<OcrBox> detect(byte[] image, OcrConfig config) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(config, "config");
        long start = System.nanoTime();
        try {
            return doDetect(image, config);
        } catch (OcrException e) {
            throw e;
        } catch (Throwable t) {
            throw new OcrException("OCR 检测失败, model=" + config.getModel(), t);
        } finally {
            long cost = (System.nanoTime() - start) / 1_000_000;
            log.debug("ocr detect model={}, bytes={}, cost={}ms",
                    config.getModel(), image.length, cost);
        }
    }

    // 抽象方法：子类实现具体检测逻辑
    protected abstract List<OcrBox> doDetect(byte[] image, OcrConfig config);

    // 最终方法：统一识别入口（已裁剪文字块），负责参数校验、计时、异常包装
    @Override
    public final List<RecText> recognizeCrops(List<byte[]> crops, OcrConfig config) {
        Objects.requireNonNull(crops, "crops");
        Objects.requireNonNull(config, "config");
        long start = System.nanoTime();
        try {
            return doRecognizeCrops(crops, config);
        } catch (OcrException e) {
            throw e;
        } catch (Throwable t) {
            throw new OcrException("OCR 裁剪识别失败, model=" + config.getModel(), t);
        } finally {
            long cost = (System.nanoTime() - start) / 1_000_000;
            log.debug("ocr recognizeCrops model={}, n={}, cost={}ms",
                    config.getModel(), crops.size(), cost);
        }
    }

    // 抽象方法：子类实现具体裁剪识别逻辑
    protected abstract List<RecText> doRecognizeCrops(List<byte[]> crops, OcrConfig config);

    // 最终方法：统一可视化入口，负责参数校验与异常包装
    @Override
    public final BufferedImage visualize(BufferedImage image, OcrResult result) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(result, "result");
        try {
            return doVisualize(image, result);
        } catch (OcrException e) {
            throw e;
        } catch (Throwable t) {
            throw new OcrException("OCR 可视化失败", t);
        }
    }

    // 抽象方法：子类实现具体可视化逻辑
    protected abstract BufferedImage doVisualize(BufferedImage image, OcrResult result);
}