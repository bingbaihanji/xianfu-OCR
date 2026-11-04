package com.bingbaihanji.ocr.geom;

import javafx.geometry.Rectangle2D;

// 识别框坐标映射：图片坐标系 → 显示坐标系（纯函数）
public final class BoxLayout {

    private BoxLayout() {
    }

    // 点集外接矩形（图片坐标系）
    public static Rectangle2D bounds(float[] points) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    // 映射点集：显示坐标 = 图片坐标 × scale + offset
    public static double[] map(float[] points, double scale, double offX, double offY) {
        double[] out = new double[points.length];
        for (int i = 0; i < points.length; i += 2) {
            out[i] = points[i] * scale + offX;
            out[i + 1] = points[i + 1] * scale + offY;
        }
        return out;
    }

    // 外接矩形映射到显示坐标系
    public static Rectangle2D mapBounds(float[] points, double scale, double offX, double offY) {
        Rectangle2D b = bounds(points);
        return new Rectangle2D(
                b.getMinX() * scale + offX,
                b.getMinY() * scale + offY,
                b.getWidth() * scale,
                b.getHeight() * scale);
    }
}
