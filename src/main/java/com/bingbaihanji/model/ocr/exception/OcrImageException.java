package com.bingbaihanji.model.ocr.exception;

// 图片输入、解码、编码、读取相关异常
public class OcrImageException extends OcrException {

    // 仅携带消息构造异常
    public OcrImageException(String message) {
        super(message);
    }

    // 携带消息与根因构造异常
    public OcrImageException(String message, Throwable cause) {
        super(message, cause);
    }
}