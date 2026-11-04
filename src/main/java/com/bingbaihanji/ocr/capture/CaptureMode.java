package com.bingbaihanji.ocr.capture;

// 截图模式：框选确认后的去向
public enum CaptureMode {

    // 截图识别：出工具条（识别/贴图/复制图片/取消）
    RECOGNIZE,

    // 截图贴图：确认后直接贴图，跳过工具条
    PIN
}
