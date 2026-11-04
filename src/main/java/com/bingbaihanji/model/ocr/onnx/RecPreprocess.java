package com.bingbaihanji.model.ocr.onnx;

import org.bytedeco.opencv.opencv_core.Mat;

// 识别预处理，输入形状固定 3,48,320（对应 Python onnx_paddleocr 强制的 rec_image_shape）
public final class RecPreprocess {

    // 识别模型输入通道数
    public static final int IMG_C = 3;

    // 识别模型输入高度
    public static final int IMG_H = 48;

    // 识别模型基准输入宽度
    public static final int IMG_W = 320;

    // 私有构造，禁止实例化
    private RecPreprocess() {
    }

    /**
     * 批内动态宽度：imgW = int(48 * maxWhRatio)，等比缩放后右侧 0 填充。
     * 对应 Python TextRecognizer.resize_norm_img 的 SVTR_LCNet 分支。
     *
     * @param bgr        文字块 BGR
     * @param maxWhRatio 批内最大宽高比；下限 320/48 由本方法强制，调用方不必自行兜底
     */
    public static float[] run(Mat bgr, double maxWhRatio) {
        int imgW = Math.max((int) (IMG_H * maxWhRatio), IMG_W);
        return TensorUtils.resizeNormPad(bgr, IMG_H, imgW, true);
    }
}
