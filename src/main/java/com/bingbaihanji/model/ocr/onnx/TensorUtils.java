package com.bingbaihanji.model.ocr.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import com.bingbaihanji.model.ocr.exception.OcrModelException;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

import java.nio.FloatBuffer;
import java.util.Arrays;

import static org.bytedeco.opencv.global.opencv_core.CV_32FC1;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

// 张量转换工具：BGR Mat → 归一化 CHW float[] → OnnxTensor，以及输出读取
public final class TensorUtils {

    // 私有构造，禁止实例化工具类
    private TensorUtils() {
    }

    /**
     * 等比缩放到高度 imgH，右侧 0 填充到 maxW，归一化到 [-1,1]。
     * 对应 Python predict_rec.resize_norm_img 的 SVTR_LCNet 分支，
     * 以及 predict_cls.resize_norm_img。
     *
     * @param bgr            源图 BGR（三通道）
     * @param imgH           目标高度，必须为正
     * @param maxW           输出宽度
     * @param truncateToMaxW true 时等比缩放宽度超过 maxW 则截断到 maxW；
     *                       false 时调用方必须保证 {@code maxW >= ceil(imgH * w / h)}，
     *                       否则会抛 IllegalArgumentException（而不是越界写坏数组）
     * @return 行优先 [3 * imgH * maxW]，布局 CHW（平面 0=B, 1=G, 2=R）
     */
    public static float[] resizeNormPad(Mat bgr, int imgH, int maxW, boolean truncateToMaxW) {
        if (bgr == null || bgr.empty()) {
            throw new IllegalArgumentException("bgr 不能为空");
        }
        if (bgr.channels() != 3) {
            throw new IllegalArgumentException("bgr 必须为三通道 BGR, channels=" + bgr.channels());
        }
        if (imgH <= 0 || maxW <= 0) {
            throw new IllegalArgumentException("imgH/maxW 必须为正, imgH=" + imgH + ", maxW=" + maxW);
        }
        int h = bgr.rows();
        int w = bgr.cols();
        double ratio = w / (double) h;
        int resizedW = (int) Math.ceil(imgH * ratio);
        if (resizedW <= 0) {
            throw new IllegalArgumentException("缩放后宽度非正, h=" + h + ", w=" + w + ", imgH=" + imgH);
        }
        if (truncateToMaxW) {
            resizedW = Math.min(resizedW, maxW);
        } else if (resizedW > maxW) {
            throw new IllegalArgumentException(
                    "truncateToMaxW=false 时 maxW 不能小于缩放宽度, maxW=" + maxW
                            + ", resizedW=" + resizedW);
        }
        Mat resized = new Mat();
        try {
            resize(bgr, resized, new Size(resizedW, imgH), 0, 0, INTER_LINEAR);
            byte[] raw = new byte[resizedW * imgH * 3];
            resized.data().get(raw);
            float[] out = new float[3 * imgH * maxW];
            for (int c = 0; c < 3; c++) {
                int plane = c * imgH * maxW;
                for (int y = 0; y < imgH; y++) {
                    int srcRow = y * resizedW * 3;
                    int dstRow = plane + y * maxW;
                    for (int x = 0; x < resizedW; x++) {
                        int v = raw[srcRow + x * 3 + c] & 0xFF;
                        out[dstRow + x] = (v / 255.0f - 0.5f) / 0.5f;
                    }
                }
            }
            return out;
        } finally {
            resized.release();
        }
    }

    // 打包成 ONNX 输入张量
    public static OnnxTensor toTensor(OrtEnvironment env, float[] data, long[] shape) {
        try {
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);
        } catch (OrtException e) {
            throw new OcrModelException("构造输入张量失败, shape=" + Arrays.toString(shape), e);
        }
    }

    // 把 OnnxTensor 的 float 输出摊平成 float[]
    public static float[] readFloats(OnnxTensor tensor) {
        FloatBuffer fb = tensor.getFloatBuffer();
        float[] out = new float[fb.remaining()];
        fb.get(out);
        return out;
    }

    // 把 float[] 装成 CV_32FC1 Mat（供 DbPostProcess 读概率图用）。
    // 注意：内部 FloatPointer 由返回的 Mat 持有引用，Mat 释放前不要手动释放该指针
    static Mat toFloatMat(float[] data, int rows, int cols) {
        FloatPointer fp = new FloatPointer(data);
        return new Mat(rows, cols, CV_32FC1, fp);
    }
}
