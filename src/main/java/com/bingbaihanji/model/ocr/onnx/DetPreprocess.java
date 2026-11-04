package com.bingbaihanji.model.ocr.onnx;

import com.bingbaihanji.model.ocr.util.GeometryUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

// 检测预处理：DetResizeForTest(type0, limit 由 maxSide 决定 max|min) + NormalizeImage(hwc) + ToCHW
public final class DetPreprocess {

    // ImageNet 归一化参数，对应 Python predict_det 的 NormalizeImage 配置
    private static final float SCALE = 1.0f / 255.0f;

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};

    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    // 私有构造，禁止实例化
    private DetPreprocess() {
    }

    /**
     * 执行检测预处理。
     *
     * @param bgr          源图 BGR
     * @param limitSideLen det_limit_side_len，通常 960
     * @param maxSide      true 对应 det_limit_type=max
     */
    public static Out run(Mat bgr, int limitSideLen, boolean maxSide) {
        if (bgr == null || bgr.empty()) {
            throw new IllegalArgumentException("bgr 不能为空");
        }
        // copyTo 到 ROI 时若类型不符会重新分配目标，导致补零区变成整块零图；
        // 循环里按三通道交错读也会读错。这里前置校验，把静默出错变成明确报错
        if (bgr.type() != CV_8UC3) {
            throw new IllegalArgumentException("bgr 必须为 CV_8UC3 BGR, type=" + bgr.type());
        }
        int srcH = bgr.rows();
        int srcW = bgr.cols();

        // Python DetResizeForTest: h + w < 64 时补到至少 32x32。
        // 必须显式补零：new Mat(rows, cols, type) 在 OpenCV 里是未初始化内存，
        // 而 Python 的 image_padding 用的是 np.zeros
        Mat work = bgr;
        Mat padded = null;
        try {
            if (srcH + srcW < 64) {
                int ph = Math.max(32, srcH);
                int pw = Math.max(32, srcW);
                padded = new Mat(ph, pw, CV_8UC3, Scalar.all(0));
                bgr.copyTo(padded.rowRange(0, srcH).colRange(0, srcW));
                work = padded;
            }
            int h = work.rows();
            int w = work.cols();

            double ratio;
            if (maxSide) {
                ratio = Math.max(h, w) > limitSideLen
                        ? (double) limitSideLen / Math.max(h, w) : 1.0;
            } else {
                ratio = Math.min(h, w) < limitSideLen
                        ? (double) limitSideLen / Math.min(h, w) : 1.0;
            }
            int resizeH = (int) (h * ratio);
            int resizeW = (int) (w * ratio);
            // 必须是 32 的倍数且至少 32。用 pyRound 对齐 Python 的 round()
            resizeH = Math.max(GeometryUtils.pyRound(resizeH / 32.0) * 32, 32);
            resizeW = Math.max(GeometryUtils.pyRound(resizeW / 32.0) * 32, 32);

            Mat resized = new Mat();
            resize(work, resized, new Size(resizeW, resizeH), 0, 0, INTER_LINEAR);
            try {
                byte[] raw = new byte[resizeW * resizeH * 3];
                resized.data().get(raw);
                float[] chw = new float[3 * resizeH * resizeW];
                for (int c = 0; c < 3; c++) {
                    int plane = c * resizeH * resizeW;
                    for (int y = 0; y < resizeH; y++) {
                        int srcRow = y * resizeW * 3;
                        int dstRow = plane + y * resizeW;
                        for (int x = 0; x < resizeW; x++) {
                            int v = raw[srcRow + x * 3 + c] & 0xFF;
                            // c=0 读 B 通道、c=1 读 G、c=2 读 R；
                            // MEAN/STD 按同一下标取用，与 Python order='hwc' 的 (1,1,3) 行为一致
                            chw[dstRow + x] = (v * SCALE - MEAN[c]) / STD[c];
                        }
                    }
                }
                return new Out(chw, srcH, srcW, resizeH, resizeW);
            } finally {
                resized.release();
            }
        } finally {
            if (padded != null) {
                padded.release();
            }
        }
    }

    // 预处理结果：CHW 数据 + 原图尺寸 + 缩放后尺寸
    public record Out(float[] chw, int srcH, int srcW, int dstH, int dstW) {
    }
}
