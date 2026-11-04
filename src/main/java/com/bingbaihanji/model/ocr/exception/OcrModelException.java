package com.bingbaihanji.model.ocr.exception;

// 模型加载、会话创建、推理执行相关异常
public class OcrModelException extends OcrException {

    // 仅携带消息构造异常
    public OcrModelException(String message) {
        super(message);
    }

    // 携带消息与根因构造异常
    public OcrModelException(String message, Throwable cause) {
        super(message, cause);
    }
}