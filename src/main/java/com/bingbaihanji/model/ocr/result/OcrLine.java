package com.bingbaihanji.model.ocr.result;

import java.util.Arrays;
import java.util.Objects;

// OCR 单行/单块识别明细，携带文本、置信度与多边形包围盒坐标
public final class OcrLine {

    private final String text;

    private final double confidence;

    private final float[] points; // [x0,y0,x1,y1,x2,y2,x3,y3]

    // 使用四个角点坐标构造识别行（推荐 PaddleOCR 等输出多边形的场景）
    public OcrLine(String text, double confidence, float[] points) {
        this.text = Objects.requireNonNull(text, "text");
        Objects.requireNonNull(points, "points");
        if (points.length < 4 || (points.length & 1) != 0) {
            throw new IllegalArgumentException("points 必须为至少 2 个点的偶数长度数组");
        }
        this.confidence = confidence;
        this.points = points.clone();
    }

    // 使用轴对齐矩形构造识别行（便捷构造，自动展开为四点）
    public OcrLine(String text, double confidence, int x, int y, int width, int height) {
        this(text, confidence,
                new float[]{x, y, x + width, y, x + width, y + height, x, y + height});
    }

    // 截断过长文本，供 toString 使用
    private static String truncate(String s) {
        return s.length() <= 32 ? s : s.substring(0, 32) + "...";
    }

    // 获取文本内容
    public String getText() {
        return text;
    }

    // 获取置信度，范围 0.0 ~ 1.0
    public double getConfidence() {
        return confidence;
    }

    // 获取多边形角点坐标（返回防御性拷贝）
    public float[] getPoints() {
        return points.clone();
    }

    // 获取外接矩形最小 x 坐标
    public int getX() {
        return BoxGeometry.x(points);
    }

    // 获取外接矩形最小 y 坐标
    public int getY() {
        return BoxGeometry.y(points);
    }

    // 获取外接矩形宽度
    public int getWidth() {
        return BoxGeometry.width(points);
    }

    // 获取外接矩形高度
    public int getHeight() {
        return BoxGeometry.height(points);
    }

    // 返回识别行的可读字符串（截断文本，避免日志过长）
    @Override
    public String toString() {
        return "OcrLine{text='" + truncate(text) + "', confidence=" + confidence
                + ", box=(" + getX() + "," + getY() + "," + getWidth() + "," + getHeight() + ")}";
    }

    // 基于文本、置信度、坐标判断相等性
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OcrLine that)) {
            return false;
        }
        return Double.compare(that.confidence, confidence) == 0
                && text.equals(that.text)
                && Arrays.equals(points, that.points);
    }

    // 计算哈希值
    @Override
    public int hashCode() {
        int result = Objects.hash(text, confidence);
        result = 31 * result + Arrays.hashCode(points);
        return result;
    }
}