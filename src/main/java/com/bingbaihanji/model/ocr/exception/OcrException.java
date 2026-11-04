package com.bingbaihanji.model.ocr.exception;

// OCR 统一异常基类，所有 OCR 相关异常都继承自它
public class OcrException extends RuntimeException {

    // 仅携带消息构造异常
    public OcrException(String message) {
        super(message);
    }

    // 携带消息与根因构造异常
    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}