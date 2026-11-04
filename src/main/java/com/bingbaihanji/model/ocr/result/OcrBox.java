package com.bingbaihanji.model.ocr.result;

import java.util.Arrays;
import java.util.Objects;

// OCR 检测框明细，仅携带多边形包围盒坐标，不含识别文本
public final class OcrBox {

    private final float[] points; // [x0,y0,x1,y1,x2,y2,x3,y3]

    // 使用四个角点坐标构造检测框
    public OcrBox(float[] points) {
        Objects.requireNonNull(points, "points");
        if (points.length < 4 || (points.length & 1) != 0) {
            throw new IllegalArgumentException("points 必须为至少 2 个点的偶数长度数组");
        }
        this.points = points.clone();
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

    // 返回检测框的可读字符串
    @Override
    public String toString() {
        return "OcrBox{box=(" + getX() + "," + getY() + "," + getWidth() + "," + getHeight() + ")}";
    }

    // 基于坐标判断相等性
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OcrBox that)) {
            return false;
        }
        return Arrays.equals(points, that.points);
    }

    // 计算哈希值
    @Override
    public int hashCode() {
        return Arrays.hashCode(points);
    }
}
