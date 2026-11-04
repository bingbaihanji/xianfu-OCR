package com.bingbaihanji.ocr.capture;

import javafx.geometry.Rectangle2D;

// 选区几何：归一化/手柄命中/拖拽移动缩放（纯函数）
public final class CaptureGeometry {

    // 8 个调整手柄 + 区域内/外
    public enum Handle {
        N, S, E, W, NE, NW, SE, SW, INSIDE, OUTSIDE
    }

    private CaptureGeometry() {
    }

    // 两点归一化为正矩形
    public static Rectangle2D normalize(double x1, double y1, double x2, double y2) {
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        return new Rectangle2D(x, y, Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    // 手柄命中判定（tol 为容差像素）
    public static Handle hitHandle(Rectangle2D rect, double x, double y, double tol) {
        double l = rect.getMinX();
        double r = rect.getMaxX();
        double t = rect.getMinY();
        double b = rect.getMaxY();
        boolean nearL = Math.abs(x - l) <= tol;
        boolean nearR = Math.abs(x - r) <= tol;
        boolean nearT = Math.abs(y - t) <= tol;
        boolean nearB = Math.abs(y - b) <= tol;
        boolean inX = x >= l - tol && x <= r + tol;
        boolean inY = y >= t - tol && y <= b + tol;
        if (nearT && nearL) {
            return Handle.NW;
        }
        if (nearT && nearR) {
            return Handle.NE;
        }
        if (nearB && nearL) {
            return Handle.SW;
        }
        if (nearB && nearR) {
            return Handle.SE;
        }
        if (nearT && inX) {
            return Handle.N;
        }
        if (nearB && inX) {
            return Handle.S;
        }
        if (nearL && inY) {
            return Handle.W;
        }
        if (nearR && inY) {
            return Handle.E;
        }
        if (inX && inY) {
            return Handle.INSIDE;
        }
        return Handle.OUTSIDE;
    }

    // 按手柄拖拽：anchor 为拖拽起点矩形，(dx,dy) 为已拖距离；最小尺寸 1px
    public static Rectangle2D drag(Rectangle2D anchor, Handle handle, double dx, double dy) {
        double l = anchor.getMinX();
        double t = anchor.getMinY();
        double r = anchor.getMaxX();
        double b = anchor.getMaxY();
        switch (handle) {
            case INSIDE -> {
                return new Rectangle2D(l + dx, t + dy, anchor.getWidth(), anchor.getHeight());
            }
            case N -> {
                t = Math.min(t + dy, b - 1);
            }
            case S -> {
                b = Math.max(b + dy, t + 1);
            }
            case W -> {
                l = Math.min(l + dx, r - 1);
            }
            case E -> {
                r = Math.max(r + dx, l + 1);
            }
            case NW -> {
                t = Math.min(t + dy, b - 1);
                l = Math.min(l + dx, r - 1);
            }
            case NE -> {
                t = Math.min(t + dy, b - 1);
                r = Math.max(r + dx, l + 1);
            }
            case SW -> {
                b = Math.max(b + dy, t + 1);
                l = Math.min(l + dx, r - 1);
            }
            case SE -> {
                b = Math.max(b + dy, t + 1);
                r = Math.max(r + dx, l + 1);
            }
            default -> {
                return anchor;
            }
        }
        return normalize(l, t, r, b);
    }
}
