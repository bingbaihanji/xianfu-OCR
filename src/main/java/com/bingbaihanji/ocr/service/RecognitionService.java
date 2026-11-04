package com.bingbaihanji.ocr.service;

import com.bingbaihanji.model.ocr.api.OnnxOcrService;
import com.bingbaihanji.model.ocr.api.OcrService;
import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.config.OcrProperties;
import com.bingbaihanji.model.ocr.result.OcrResult;
import javafx.application.Platform;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

// 异步识别服务：单线程串行推理 + 新任务替代旧任务 + 可注入 UI 线程投递
public final class RecognitionService implements AutoCloseable {

    private final OcrService ocr;
    private final Consumer<Runnable> uiRunner;
    private final ExecutorService exec;
    private final AtomicLong seq = new AtomicLong();

    // 生产构造：回调投递到 JavaFX 应用线程
    public RecognitionService(OcrService ocr) {
        this(ocr, Platform::runLater);
    }

    // 测试构造：uiRunner 可注入直接执行的实现
    public RecognitionService(OcrService ocr, Consumer<Runnable> uiRunner) {
        this.ocr = Objects.requireNonNull(ocr, "ocr");
        this.uiRunner = Objects.requireNonNull(uiRunner, "uiRunner");
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ocr-infer");
            t.setDaemon(true);
            return t;
        });
    }

    // 默认装配：内置模型套件（外部 model/ 目录可覆盖，见 OcrProperties.withBundledModels）
    public static RecognitionService createDefault(Path jarDir) {
        return new RecognitionService(new OnnxOcrService(OcrProperties.withBundledModels(jarDir)));
    }

    // 提交识别；最后一次提交为准，旧任务即使完成其回调也不再触发
    public void submit(BufferedImage image, OcrConfig config,
                       Consumer<OcrResult> onSuccess, Consumer<Throwable> onError) {
        long id = seq.incrementAndGet();
        exec.execute(() -> {
            try {
                OcrResult result = ocr.recognize(image, config);
                if (id == seq.get()) {
                    uiRunner.accept(() -> onSuccess.accept(result));
                }
            } catch (Throwable t) {
                if (id == seq.get()) {
                    uiRunner.accept(() -> onError.accept(t));
                }
            }
        });
    }

    @Override
    public void close() {
        exec.shutdownNow();
        ocr.close();
    }
}
