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
public final class ScreenCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ScreenCaptureService.class);

    private ScreenCaptureService() {
    }

    // 单显示器捕获结果：图像 + 对应 JavaFX Screen + 是否主屏
    public record MonitorCapture(BufferedImage image, Screen screen, boolean primary) {

        // 选区（overlay 逻辑坐标）→ 图像像素坐标；相对换算，天然兼容各屏不同 DPI 缩放
        public Rectangle2D toImageRect(Rectangle2D overlayRect) {
            double sx = image.getWidth() / screen.getBounds().getWidth();
            double sy = image.getHeight() / screen.getBounds().getHeight();
            return new Rectangle2D(
                    overlayRect.getMinX() * sx,
                    overlayRect.getMinY() * sy,
                    overlayRect.getWidth() * sx,
                    overlayRect.getHeight() * sy);
        }

        // overlay 逻辑坐标点 → 图像像素坐标（放大镜取样用）
        public double[] toImagePoint(double x, double y) {
            double sx = image.getWidth() / screen.getBounds().getWidth();
            double sy = image.getHeight() / screen.getBounds().getHeight();
            return new double[]{x * sx, y * sy};
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
            out.add(new MonitorCapture(img, fxScreens.get(i), i == 0));
            log.info("已捕获显示器 #{}: {}×{}", i, img.getWidth(), img.getHeight());
        }
        return out;
    }
}
