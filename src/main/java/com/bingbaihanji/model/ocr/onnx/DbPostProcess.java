package com.bingbaihanji.model.ocr.onnx;

import com.bingbaihanji.model.ocr.exception.OcrModelException;
import com.bingbaihanji.model.ocr.result.OcrBox;
import com.bingbaihanji.model.ocr.util.GeometryUtils;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.RotatedRect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.CV_32FC2;
import static org.bytedeco.opencv.global.opencv_core.CV_32SC2;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_core.mean;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

// DB 检测后处理，对应 Python db_postprocess.DBPostProcess（box_type=quad）
public final class DbPostProcess {

    // 最短边阈值（概率图坐标系），对应 Python self.min_size
    private static final int MIN_SIZE = 3;

    // 外扩后二次拟合的最短边阈值，对应 Python self.min_size + 2
    private static final int MIN_EXPANDED_SIDE = MIN_SIZE + 2;

    // 原图坐标系下的最小宽高，对应 predict_det.filter_tag_det_res 里的 3
    private static final int MIN_DEST_SIDE = 3;

    // 最多候选框数的兜底值，实际由构造参数决定（各模型套件的 inference.yml 不同）
    private static final int DEFAULT_MAX_CANDIDATES = 1000;

    // JTS 几何工厂，供多边形外扩复用
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    // 二值化阈值，对应 Python DBPostProcess.thresh
    private final double thresh;

    // 候选框均值分数下限，对应 Python box_thresh
    private final double boxThresh;

    // 多边形外扩比例，对应 Python unclip_ratio
    private final double unclipRatio;

    // 二值化后是否做 2×2 膨胀，对应 Python dilation_kernel 非空
    private final boolean useDilation;

    // 最多候选框数，对应 Python max_candidates
    private final int maxCandidates;

    // 用检测后处理参数构造实例（maxCandidates 取套件默认）
    public DbPostProcess(double thresh, double boxThresh, double unclipRatio, boolean useDilation) {
        this(thresh, boxThresh, unclipRatio, useDilation, DEFAULT_MAX_CANDIDATES);
    }

    // 用检测后处理参数构造实例
    public DbPostProcess(double thresh, double boxThresh, double unclipRatio,
                         boolean useDilation, int maxCandidates) {
        this.thresh = thresh;
        this.boxThresh = boxThresh;
        this.unclipRatio = unclipRatio;
        this.useDilation = useDilation;
        this.maxCandidates = maxCandidates;
    }

    // 求轮廓最小外接旋转矩形：四角点重排为 左上 → 右上 → 右下 → 左下，sside 为最短边
    private static MiniBox getMiniBoxes(Mat contour) {
        if (contour.empty() || contour.rows() < 3) {
            return null;
        }
        RotatedRect rr = minAreaRect(contour);
        double sside = Math.min(rr.size().width(), rr.size().height());
        float[] corners = GeometryUtils.rotatedRectCorners(rr);
        float[][] pts = {
                {corners[0], corners[1]},
                {corners[2], corners[3]},
                {corners[4], corners[5]},
                {corners[6], corners[7]}
        };
        Arrays.sort(pts, Comparator.comparingDouble(p -> p[0]));
        int i1, i2, i3, i4;
        if (pts[1][1] > pts[0][1]) {
            i1 = 0;
            i4 = 1;
        } else {
            i1 = 1;
            i4 = 0;
        }
        if (pts[3][1] > pts[2][1]) {
            i2 = 2;
            i3 = 3;
        } else {
            i2 = 3;
            i3 = 2;
        }
        float[] box = {
                pts[i1][0], pts[i1][1],
                pts[i2][0], pts[i2][1],
                pts[i3][0], pts[i3][1],
                pts[i4][0], pts[i4][1]
        };
        return new MiniBox(box, sside);
    }

    // 框内概率均值，对应 Python box_score_fast
    private static double boxScoreFast(Mat predMat, int h, int w, float[] box) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i += 2) {
            minX = Math.min(minX, box[i]);
            maxX = Math.max(maxX, box[i]);
            minY = Math.min(minY, box[i + 1]);
            maxY = Math.max(maxY, box[i + 1]);
        }
        int xmin = (int) Math.max(0, Math.min(Math.floor(minX), w - 1));
        int xmax = (int) Math.max(0, Math.min(Math.ceil(maxX), w - 1));
        int ymin = (int) Math.max(0, Math.min(Math.floor(minY), h - 1));
        int ymax = (int) Math.max(0, Math.min(Math.ceil(maxY), h - 1));
        if (xmax < xmin || ymax < ymin) {
            return 0;
        }
        int maskH = ymax - ymin + 1;
        int maskW = xmax - xmin + 1;
        Mat mask = new Mat(maskH, maskW, CV_8UC1, Scalar.all(0));
        try {
            // 坐标平移到 ROI 局部系后截断为 int（Python astype int32）再填充
            IntPointer poly = new IntPointer(
                    (int) box[0] - xmin, (int) box[1] - ymin,
                    (int) box[2] - xmin, (int) box[3] - ymin,
                    (int) box[4] - xmin, (int) box[5] - ymin,
                    (int) box[6] - xmin, (int) box[7] - ymin);
            Mat polyMat = new Mat(4, 1, CV_32SC2, poly);
            try {
                fillPoly(mask, new MatVector(polyMat), Scalar.all(1));
            } finally {
                polyMat.release();
            }
            Mat rowView = predMat.rowRange(ymin, ymax + 1);
            try {
                Mat roi = rowView.colRange(xmin, xmax + 1);
                try {
                    Scalar m = mean(roi, mask);
                    return m.get(0);
                } finally {
                    roi.release();
                }
            } finally {
                rowView.release();
            }
        } finally {
            mask.release();
        }
    }

    // 多边形外扩，语义等同 pyclipper JT_ROUND + ET_CLOSEDPOLYGON
    private static float[] unclip(float[] box, double unclipRatio) {
        Coordinate[] coords = {
                new Coordinate(box[0], box[1]),
                new Coordinate(box[2], box[3]),
                new Coordinate(box[4], box[5]),
                new Coordinate(box[6], box[7]),
                new Coordinate(box[0], box[1])
        };
        try {
            LinearRing ring = GEOMETRY_FACTORY.createLinearRing(coords);
            Polygon poly = GEOMETRY_FACTORY.createPolygon(ring);
            double area = poly.getArea();
            double perimeter = poly.getLength();
            if (perimeter <= 0 || area <= 0) {
                return box.clone();
            }
            // 外扩距离 = 面积 × unclipRatio / 周长；buffer 默认 ROUND 连接，对应 pyclipper JT_ROUND
            double distance = area * unclipRatio / perimeter;
            Geometry expanded = poly.buffer(distance, 8);
            if (expanded.isEmpty()) {
                return box.clone();
            }
            Geometry largest = largestPolygon(expanded);
            if (largest == null) {
                return box.clone();
            }
            // 只取外环：getCoordinates() 会把内环顶点也拼进来，污染点集
            Coordinate[] shell = largest instanceof Polygon p
                    ? p.getExteriorRing().getCoordinates()
                    : largest.getCoordinates();
            float[] out = new float[shell.length * 2];
            for (int i = 0; i < shell.length; i++) {
                out[i * 2] = (float) shell[i].x;
                out[i * 2 + 1] = (float) shell[i].y;
            }
            return out;
        } catch (RuntimeException e) {
            throw new OcrModelException("DB unclip 多边形外扩失败", e);
        }
    }

    // 取面积最大的多边形，没有任何几何时返回 null
    private static Geometry largestPolygon(Geometry g) {
        int n = g.getNumGeometries();
        if (n == 0) {
            return null;
        }
        Geometry best = g.getGeometryN(0);
        for (int i = 1; i < n; i++) {
            Geometry cur = g.getGeometryN(i);
            if (cur.getArea() > best.getArea()) {
                best = cur;
            }
        }
        return best;
    }

    // 概率图坐标 → 原图坐标，对应 Python boxes_from_bitmap 的缩放（上界含 destW/destH，后续 clipDetRes 再减 1）
    private static float[] scaleToDest(float[] box, int predW, int predH, int destW, int destH) {
        float[] out = box.clone();
        for (int i = 0; i < out.length; i += 2) {
            out[i] = GeometryUtils.pyRound(out[i] / predW * destW);
            out[i] = Math.max(0, Math.min(out[i], destW));
            out[i + 1] = GeometryUtils.pyRound(out[i + 1] / predH * destH);
            out[i + 1] = Math.max(0, Math.min(out[i + 1], destH));
        }
        return out;
    }

    // 顺时针整理四点为 左上 → 右上 → 右下 → 左下，对应 predict_det.order_points_clockwise
    private static float[] orderPointsClockwise(float[] pts) {
        float[][] p = {
                {pts[0], pts[1]}, {pts[2], pts[3]},
                {pts[4], pts[5]}, {pts[6], pts[7]}
        };
        // sum = x + y：最小为左上，最大为右下
        double[] sum = new double[4];
        // diff = y - x：值越小越靠右上（x 大 y 小），越大越靠左下
        double[] diff = new double[4];
        for (int i = 0; i < 4; i++) {
            sum[i] = p[i][0] + p[i][1];
            diff[i] = p[i][1] - p[i][0];
        }
        int minSum = 0, maxSum = 0;
        for (int i = 1; i < 4; i++) {
            if (sum[i] < sum[minSum]) {
                minSum = i;
            }
            if (sum[i] > sum[maxSum]) {
                maxSum = i;
            }
        }
        // 剩余两点中 diff 最小者为右上，最大者为左下
        int minDiff = -1, maxDiff = -1;
        for (int i = 0; i < 4; i++) {
            if (i == minSum || i == maxSum) {
                continue;
            }
            if (minDiff < 0 || diff[i] < diff[minDiff]) {
                minDiff = i;
            }
            if (maxDiff < 0 || diff[i] > diff[maxDiff]) {
                maxDiff = i;
            }
        }
        return new float[]{
                p[minSum][0], p[minSum][1],
                p[minDiff][0], p[minDiff][1],
                p[maxSum][0], p[maxSum][1],
                p[maxDiff][0], p[maxDiff][1]
        };
    }

    // 裁剪到图像范围，对应 predict_det.clip_det_res（保留浮点）
    private static float[] clipDetRes(float[] pts, int imgH, int imgW) {
        float[] out = pts.clone();
        for (int i = 0; i < out.length; i += 2) {
            out[i] = Math.max(0, Math.min(out[i], imgW - 1));
            out[i + 1] = Math.max(0, Math.min(out[i + 1], imgH - 1));
        }
        return out;
    }

    /**
     * 从概率图提取文本框。
     *
     * @param pred   行优先 [predH * predW] 的概率图（模型输出 maps[0,0,:,:]）
     * @param predH  概率图高
     * @param predW  概率图宽
     * @param destH  目标（原图）高
     * @param destW  目标（原图）宽
     * @return 原图坐标系下的检测框，已按 order_points_clockwise 整理并过滤
     */
    public List<OcrBox> call(float[] pred, int predH, int predW, int destH, int destW) {
        // 1. 二值化：概率 > thresh 记 1，否则 0
        byte[] maskBytes = new byte[predH * predW];
        for (int i = 0; i < maskBytes.length; i++) {
            maskBytes[i] = (byte) (pred[i] > thresh ? 1 : 0);
        }
        // 所有原生 Mat 都在本 try 内分配，保证任意一步抛异常都能被 finally 释放
        Mat mask = null;
        Mat predMat = null;
        try {
            mask = new Mat(predH, predW, CV_8UC1, new BytePointer(maskBytes));
            if (useDilation) {
                // 2. 可选膨胀：2×2 全 1 核，对应 Python self.dilation_kernel
                Mat kernel = new Mat(2, 2, CV_8UC1, new BytePointer(new byte[]{1, 1, 1, 1}));
                Mat dilated = new Mat();
                try {
                    dilate(mask, dilated, kernel);
                } catch (RuntimeException e) {
                    // 失败时 dilated 还没被挂到 mask 上，必须单独释放
                    dilated.release();
                    throw e;
                } finally {
                    kernel.release();
                }
                mask.release();
                mask = dilated;
            }
            predMat = TensorUtils.toFloatMat(pred, predH, predW);

            // 3. 查找轮廓（findContours 可能改写输入，故传掩码副本）
            MatVector contours = new MatVector();
            Mat hierarchy = new Mat();
            Mat contourInput = mask.clone();
            try {
                findContours(contourInput, contours, hierarchy, RETR_LIST, CHAIN_APPROX_SIMPLE);
            } finally {
                contourInput.release();
                hierarchy.release();
            }

            List<float[]> raw = new ArrayList<>();
            int numContours = (int) Math.min(contours.size(), maxCandidates);
            for (int i = 0; i < numContours; i++) {
                Mat contour = contours.get(i);
                MiniBox mini = getMiniBoxes(contour);
                if (mini == null || mini.sside() < MIN_SIZE) {
                    continue;
                }
                double score = boxScoreFast(predMat, predH, predW, mini.box());
                if (score < boxThresh) {
                    continue;
                }
                float[] expanded = unclip(mini.box(), unclipRatio);
                if (expanded.length < 8) {
                    continue;
                }
                Mat expandedMat = new Mat(expanded.length / 2, 1, CV_32FC2, new FloatPointer(expanded));
                try {
                    MiniBox after = getMiniBoxes(expandedMat);
                    if (after == null || after.sside() < MIN_EXPANDED_SIDE) {
                        continue;
                    }
                    raw.add(scaleToDest(after.box(), predW, predH, destW, destH));
                } finally {
                    expandedMat.release();
                }
            }

            // 4. 顺时针整理 + 裁剪 + 过滤过小框，对应 predict_det.filter_tag_det_res
            List<OcrBox> out = new ArrayList<>();
            for (float[] box : raw) {
                float[] pts = orderPointsClockwise(box);
                pts = clipDetRes(pts, destH, destW);
                float[] p0 = {pts[0], pts[1]};
                float[] p1 = {pts[2], pts[3]};
                float[] p3 = {pts[6], pts[7]};
                int rectWidth = (int) GeometryUtils.dist(p0, p1);
                int rectHeight = (int) GeometryUtils.dist(p0, p3);
                if (rectWidth <= MIN_DEST_SIDE || rectHeight <= MIN_DEST_SIDE) {
                    continue;
                }
                out.add(new OcrBox(pts));
            }
            return out;
        } finally {
            if (predMat != null) {
                predMat.release();
            }
            if (mask != null) {
                mask.release();
            }
        }
    }

    // minAreaRect 四角点按 x 排序后重排为 左上 → 右上 → 右下 → 左下
    private record MiniBox(float[] box, double sside) {
    }
}
