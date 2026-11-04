package com.bingbaihanji.model.ocr.result;

// 多边形外接矩形计算，供 OcrLine / OcrBox 共用
final class BoxGeometry {

    // 私有构造，禁止实例化工具类
    private BoxGeometry() {
    }

    // 计算指定维度（0=x, 1=y）的最小坐标
    private static float minCoord(float[] points, int dim) {
        float min = Float.MAX_VALUE;
        for (int i = dim; i < points.length; i += 2) {
            min = Math.min(min, points[i]);
        }
        return min;
    }

    // 计算指定维度（0=x, 1=y）的最大坐标
    private static float maxCoord(float[] points, int dim) {
        float max = -Float.MAX_VALUE;
        for (int i = dim; i < points.length; i += 2) {
            max = Math.max(max, points[i]);
        }
        return max;
    }

    // 外接矩形最小 x 坐标
    static int x(float[] points) {
        return Math.round(minCoord(points, 0));
    }

    // 外接矩形最小 y 坐标
    static int y(float[] points) {
        return Math.round(minCoord(points, 1));
    }

    // 外接矩形宽度
    static int width(float[] points) {
        return Math.round(maxCoord(points, 0) - minCoord(points, 0));
    }

    // 外接矩形高度
    static int height(float[] points) {
        return Math.round(maxCoord(points, 1) - minCoord(points, 1));
    }
}
