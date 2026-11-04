package com.bingbaihanji.model.ocr.util;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.RotatedRect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.BORDER_REPLICATE;
import static org.bytedeco.opencv.global.opencv_core.CV_32FC2;
import static org.bytedeco.opencv.global.opencv_core.ROTATE_90_COUNTERCLOCKWISE;
import static org.bytedeco.opencv.global.opencv_core.rotate;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_CUBIC;
import static org.bytedeco.opencv.global.opencv_imgproc.getPerspectiveTransform;
import static org.bytedeco.opencv.global.opencv_imgproc.warpPerspective;

// OpenCV 几何操作工具，承接 Python utils.py 的 get_rotate_crop_image / sorted_boxes，
// 以及 np.round、cv2.boxPoints 的等价实现
public final class GeometryUtils {

    // 私有构造，禁止实例化工具类
    private GeometryUtils() {
    }

    /**
     * Python np.round / round 语义：四舍六入五成双（banker's rounding）。
     * Java 的 Math.round 是四舍五入，两者在 x.5 处结果不同，必须区分。
     * 例如 round(2.5)：Python 得 2，Java 得 3。
     */
    public static int pyRound(double v) {
        double floor = Math.floor(v);
        double diff = v - floor;
        if (diff > 0.5) {
            return (int) Math.ceil(v);
        }
        if (diff < 0.5) {
            return (int) floor;
        }
        long f = (long) floor;
        return (int) ((f & 1L) == 0 ? f : f + 1);
    }

    // 两点欧氏距离（onnx 包的 DbPostProcess 也要用，故为 public）
    public static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 文本框排序：先按左上角 y 再按 x（上→下、左→右），
     * 再对同一行（y 差 &lt; 10）内左到右冒泡纠偏。对应 Python predict_system.sorted_boxes。
     *
     * @param dtBoxes 每项为 [x0,y0,x1,y1,x2,y2,x3,y3]
     * @return 新列表；元素数组与入参共享引用（不复制）；入参列表不被修改
     */
    public static List<float[]> sortedBoxes(List<float[]> dtBoxes) {
        List<float[]> boxes = new ArrayList<>(dtBoxes);
        boxes.sort((a, b) -> {
            int c = Float.compare(a[1], b[1]);
            return c != 0 ? c : Float.compare(a[0], b[0]);
        });
        int n = boxes.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = i; j >= 0; j--) {
                float[] next = boxes.get(j + 1);
                float[] cur = boxes.get(j);
                if (Math.abs(next[1] - cur[1]) < 10 && next[0] < cur[0]) {
                    boxes.set(j, next);
                    boxes.set(j + 1, cur);
                } else {
                    break;
                }
            }
        }
        return boxes;
    }

    /**
     * 把四边形框透视拉正裁剪出来。对应 Python utils.get_rotate_crop_image。
     * 若裁剪结果高宽比 &gt;= 1.5 则逆时针旋转 90 度。
     * <p>
     * 返回的 Mat 包装原生内存，由调用方负责 {@code release()}；入参 img 不被修改、不被释放。
     * 若框退化（cropW/cropH &lt;= 0）则返回 {@code img.clone()}，同样由调用方负责释放。
     *
     * @param img    源图（BGR Mat）
     * @param points [x0,y0,x1,y1,x2,y2,x3,y3]
     */
    public static Mat getRotateCropImage(Mat img, float[] points) {
        if (points == null || points.length < 8) {
            throw new IllegalArgumentException("points 至少包含 4 个点（8 个浮点数）");
        }
        float[] p0 = {points[0], points[1]};
        float[] p1 = {points[2], points[3]};
        float[] p2 = {points[4], points[5]};
        float[] p3 = {points[6], points[7]};
        int cropW = (int) Math.max(dist(p0, p1), dist(p2, p3));
        int cropH = (int) Math.max(dist(p0, p3), dist(p1, p2));
        if (cropW <= 0 || cropH <= 0) {
            return img.clone();
        }

        FloatPointer srcData = new FloatPointer(
                points[0], points[1], points[2], points[3],
                points[4], points[5], points[6], points[7]);
        FloatPointer dstData = new FloatPointer(
                0f, 0f, (float) cropW, 0f, (float) cropW, (float) cropH, 0f, (float) cropH);
        Mat src = new Mat(4, 1, CV_32FC2, srcData);
        Mat dst = new Mat(4, 1, CV_32FC2, dstData);
        Mat m = null;
        Mat crop = null;
        Mat result = null;
        try {
            m = getPerspectiveTransform(src, dst);
            crop = new Mat();
            warpPerspective(img, crop, m, new Size(cropW, cropH),
                    INTER_CUBIC, BORDER_REPLICATE, Scalar.all(0));
            if (crop.rows() * 1.0 / crop.cols() >= 1.5) {
                result = new Mat();
                rotate(crop, result, ROTATE_90_COUNTERCLOCKWISE);
            } else {
                result = crop;
            }
            return result;
        } finally {
            // 只释放中间量；result（crop 或旋转结果）绝不能在这里释放
            if (crop != null && crop != result) {
                crop.release();
            }
            if (m != null) {
                m.release();
            }
            src.release();
            dst.release();
            srcData.close();
            dstData.close();
        }
    }

    /**
     * 计算 RotatedRect 的四个角点，等价 cv2.boxPoints。
     * 公式取自 OpenCV cv::RotatedRect::points(Point2f[])。
     *
     * @return [x0,y0,x1,y1,x2,y2,x3,y3]
     */
    public static float[] rotatedRectCorners(RotatedRect rr) {
        double angle = Math.toRadians(rr.angle());
        // a = sin(θ)·0.5，b = cos(θ)·0.5；沿用 cv::RotatedRect::points 内部命名，便于对照源码
        float a = (float) (Math.sin(angle) * 0.5);
        float b = (float) (Math.cos(angle) * 0.5);
        float cx = rr.center().x();
        float cy = rr.center().y();
        float w = rr.size().width();
        float h = rr.size().height();

        float x0 = cx - a * h - b * w;
        float y0 = cy + b * h - a * w;
        float x1 = cx + a * h - b * w;
        float y1 = cy - b * h - a * w;
        float x2 = 2 * cx - x0;
        float y2 = 2 * cy - y0;
        float x3 = 2 * cx - x1;
        float y3 = 2 * cy - y1;
        return new float[]{x0, y0, x1, y1, x2, y2, x3, y3};
    }
}
