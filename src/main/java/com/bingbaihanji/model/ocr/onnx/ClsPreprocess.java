package com.bingbaihanji.model.ocr.onnx;

import org.bytedeco.opencv.opencv_core.Mat;

// 方向分类预处理，输入形状固定 3,48,192
public final class ClsPreprocess {

    // 分类模型输入通道数
    public static final int IMG_C = 3;

    // 分类模型输入高度
    public static final int IMG_H = 48;

    // 分类模型输入宽度
    public static final int IMG_W = 192;

    // 私有构造，禁止实例化
    private ClsPreprocess() {
    }

    // 等比缩放到高 48，右侧 0 填充到 192
    public static float[] run(Mat bgr) {
        return TensorUtils.resizeNormPad(bgr, IMG_H, IMG_W, true);
    }
}
