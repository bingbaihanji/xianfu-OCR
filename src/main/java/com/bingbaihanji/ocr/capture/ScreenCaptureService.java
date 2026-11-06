package com.bingbaihanji.ocr.capture;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// 多显示器静态捕获：先捕获全部屏幕再显示遮罩（保证截到真实桌面而非遮罩层）
// 跨屏共享选区统一使用「物理像素坐标」（Windows 虚拟桌面），各屏 DPI 不同也连续可比
public final class ScreenCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ScreenCaptureService.class);

    private ScreenCaptureService() {
    }

    // 单显示器捕获结果：图像 + 对应 JavaFX Screen + 物理像素边界 + 是否主屏
    public record MonitorCapture(BufferedImage image, Screen screen, Rectangle2D deviceBounds, boolean primary) {

        // 逻辑坐标 → 图像像素比例（即该屏 DPI 缩放，宽高分别换算）
        public double scaleX() {
            return image.getWidth() / screen.getBounds().getWidth();
        }

        public double scaleY() {
            return image.getHeight() / screen.getBounds().getHeight();
        }

        // 选区（overlay 逻辑坐标）→ 图像像素坐标；相对换算，天然兼容各屏不同 DPI 缩放
        public Rectangle2D toImageRect(Rectangle2D local) {
            double sx = scaleX();
            double sy = scaleY();
            return new Rectangle2D(
                    local.getMinX() * sx,
                    local.getMinY() * sy,
                    local.getWidth() * sx,
                    local.getHeight() * sy);
        }

        // overlay 逻辑坐标点 → 图像像素坐标（放大镜取样用）
        public double[] toImagePoint(double x, double y) {
            return new double[]{x * scaleX(), y * scaleY()};
        }

        // overlay 逻辑坐标 → 物理屏幕坐标（跨屏共享选区用）
        public Rectangle2D toDevice(Rectangle2D local) {
            double sx = scaleX();
            double sy = scaleY();
            return new Rectangle2D(
                    deviceBounds.getMinX() + local.getMinX() * sx,
                    deviceBounds.getMinY() + local.getMinY() * sy,
                    local.getWidth() * sx,
                    local.getHeight() * sy);
        }

        // 物理屏幕坐标 → overlay 逻辑坐标
        public Rectangle2D toLocal(Rectangle2D device) {
            double sx = scaleX();
            double sy = scaleY();
            return new Rectangle2D(
                    (device.getMinX() - deviceBounds.getMinX()) / sx,
                    (device.getMinY() - deviceBounds.getMinY()) / sy,
                    device.getWidth() / sx,
                    device.getHeight() / sy);
        }

        // overlay 逻辑坐标点 → 物理屏幕坐标
        public double[] toDevicePoint(double x, double y) {
            return new double[]{deviceBounds.getMinX() + x * scaleX(), deviceBounds.getMinY() + y * scaleY()};
        }

        // 物理坐标点是否落在本显示器内
        public boolean containsDevice(double x, double y) {
            return x >= deviceBounds.getMinX() && x < deviceBounds.getMaxX()
                    && y >= deviceBounds.getMinY() && y < deviceBounds.getMaxY();
        }
    }

    // 捕获所有显示器（主屏排前，与 AWT 设备按序配对）
    public static List<MonitorCapture> captureAll() throws AWTException {
        Screen primaryScreen = Screen.getPrimary();
        List<Screen> fxScreens = new ArrayList<>(Screen.getScreens());
        fxScreens.remove(primaryScreen);
        fxScreens.add(0, primaryScreen);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<GraphicsDevice> devices = new ArrayList<>(List.of(ge.getScreenDevices()));
        GraphicsDevice primaryDevice = ge.getDefaultScreenDevice();
        devices.remove(primaryDevice);
        devices.add(0, primaryDevice);

        int n = Math.min(fxScreens.size(), devices.size());
        if (fxScreens.size() != devices.size()) {
            log.warn("JavaFX 屏幕数({})与 AWT 设备数({})不一致，按序配对前 {} 个",
                    fxScreens.size(), devices.size(), n);
        }

        List<MonitorCapture> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            GraphicsDevice device = devices.get(i);
            java.awt.Rectangle bounds = device.getDefaultConfiguration().getBounds();
            Robot robot = new Robot(device);
            BufferedImage img = robot.createScreenCapture(bounds);
            // 物理像素边界：原点取 AWT 设备原点，尺寸取图像实际像素，保证与图像 1:1 对应
            Rectangle2D deviceBounds = new Rectangle2D(bounds.x, bounds.y, img.getWidth(), img.getHeight());
            out.add(new MonitorCapture(img, fxScreens.get(i), deviceBounds, i == 0));
            log.info("已捕获显示器 #{}: {}×{} device=({},{}) {}×{}", i, img.getWidth(), img.getHeight(),
                    bounds.x, bounds.y, img.getWidth(), img.getHeight());
        }
        return out;
    }

    // 全部显示器物理像素并集：共享选区可自由拖动的范围
    public static Rectangle2D deviceUnion(List<MonitorCapture> monitors) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (MonitorCapture m : monitors) {
            Rectangle2D b = m.deviceBounds();
            minX = Math.min(minX, b.getMinX());
            minY = Math.min(minY, b.getMinY());
            maxX = Math.max(maxX, b.getMaxX());
            maxY = Math.max(maxY, b.getMaxY());
        }
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    // 物理坐标点所在显示器；不在任何屏内则取距离最近的一个（工具条/贴图落点兜底）
    public static MonitorCapture deviceAt(List<MonitorCapture> monitors, double x, double y) {
        MonitorCapture best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (MonitorCapture m : monitors) {
            if (m.containsDevice(x, y)) {
                return m;
            }
            Rectangle2D b = m.deviceBounds();
            double dx = Math.max(b.getMinX() - x, Math.max(0, x - b.getMaxX()));
            double dy = Math.max(b.getMinY() - y, Math.max(0, y - b.getMaxY()));
            double dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = m;
            }
        }
        return best;
    }

    // 物理矩形落在某屏内的可见部分（无交集返回 null）
    public static Rectangle2D intersect(Rectangle2D a, Rectangle2D b) {
        double x1 = Math.max(a.getMinX(), b.getMinX());
        double y1 = Math.max(a.getMinY(), b.getMinY());
        double x2 = Math.min(a.getMaxX(), b.getMaxX());
        double y2 = Math.min(a.getMaxY(), b.getMaxY());
        if (x2 <= x1 || y2 <= y1) {
            return null;
        }
        return new Rectangle2D(x1, y1, x2 - x1, y2 - y1);
    }
}
