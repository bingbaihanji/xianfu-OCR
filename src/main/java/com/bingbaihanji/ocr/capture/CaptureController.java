package com.bingbaihanji.ocr.capture;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// 截图编排：先捕获全部屏幕 → 全屏遮罩 → 选区 → 裁剪 → 工具条/贴图/复制
public class CaptureController {

    private static final Logger log = LoggerFactory.getLogger(CaptureController.class);

    private final CaptureMode mode;
    private final Consumer<BufferedImage> onRecognizeImage;
    private final Runnable onClose;
    private final List<CaptureOverlay> overlays = new ArrayList<>();

    private CaptureToolbar toolbar;
    private BufferedImage pendingCrop;
    private Rectangle2D pendingSelScreenRect;

    // onRecognizeImage：识别模式下用户点「识别」时回调裁剪图（交主窗口展示）；
    // onClose：会话结束（取消/完成）时回调，供调用方解除「进行中」标记
    public CaptureController(CaptureMode mode, Consumer<BufferedImage> onRecognizeImage, Runnable onClose) {
        this.mode = mode;
        this.onRecognizeImage = onRecognizeImage;
        this.onClose = onClose;
    }

    // 启动截图（先捕获后显示遮罩）；失败抛 IllegalStateException
    public void start() {
        try {
            List<ScreenCaptureService.MonitorCapture> monitors = ScreenCaptureService.captureAll();
            for (ScreenCaptureService.MonitorCapture m : monitors) {
                overlays.add(new CaptureOverlay(m, overlayCallbacks));
            }
            for (CaptureOverlay o : overlays) {
                o.show();
            }
        } catch (Exception e) {
            close();
            throw new IllegalStateException("截图捕获失败: " + e.getMessage(), e);
        }
    }

    // 关闭全部截图相关窗口（幂等）
    public void close() {
        for (CaptureOverlay o : overlays) {
            o.close();
        }
        overlays.clear();
        if (toolbar != null) {
            toolbar.close();
            toolbar = null;
        }
        onClose.run();
    }

    // ------------------------------------------------------------------ 回调

    private final CaptureOverlay.Callbacks overlayCallbacks = new CaptureOverlay.Callbacks() {
        @Override
        public void onConfirm(CaptureOverlay overlay, Rectangle2D rect) {
            Rectangle2D bounds = overlay.capture().screen().getBounds();
            Rectangle2D sel = rect != null ? rect : new Rectangle2D(0, 0, bounds.getWidth(), bounds.getHeight());
            Rectangle2D imgRect = overlay.capture().toImageRect(sel);
            pendingCrop = crop(overlay.capture().image(), imgRect);
            Point2D tl = overlay.localToScreen(sel.getMinX(), sel.getMinY());
            pendingSelScreenRect = new Rectangle2D(tl.getX(), tl.getY(), sel.getWidth(), sel.getHeight());
            for (CaptureOverlay o : overlays) {
                o.close();
            }
            overlays.clear();
            log.info("截图完成: {}×{} mode={}", pendingCrop.getWidth(), pendingCrop.getHeight(), mode);
            if (mode == CaptureMode.PIN) {
                pin();
                close();
            } else {
                showToolbar(bounds);
            }
        }

        @Override
        public void onCancel() {
            close();
        }
    };

    private void showToolbar(Rectangle2D screenBounds) {
        toolbar = new CaptureToolbar(new CaptureToolbar.Actions() {
            @Override
            public void onRecognize() {
                BufferedImage img = pendingCrop;
                close();
                onRecognizeImage.accept(img);
            }

            @Override
            public void onPin() {
                close();
                pin();
            }

            @Override
            public void onCopy() {
                ClipboardContent cc = new ClipboardContent();
                cc.putImage(SwingFXUtils.toFXImage(pendingCrop, null));
                Clipboard.getSystemClipboard().setContent(cc);
                close();
            }

            @Override
            public void onCancel() {
                close();
            }
        });
        toolbar.show(pendingSelScreenRect, screenBounds);
    }

    private void pin() {
        new PinWindow(pendingCrop, pendingSelScreenRect.getMinX(), pendingSelScreenRect.getMinY()).show();
    }

    // ------------------------------------------------------------------ 工具

    private static BufferedImage crop(BufferedImage src, Rectangle2D r) {
        int x = (int) Math.floor(r.getMinX());
        int y = (int) Math.floor(r.getMinY());
        int w = (int) Math.ceil(r.getWidth());
        int h = (int) Math.ceil(r.getHeight());
        x = Math.max(0, Math.min(x, src.getWidth() - 1));
        y = Math.max(0, Math.min(y, src.getHeight() - 1));
        w = Math.max(1, Math.min(w, src.getWidth() - x));
        h = Math.max(1, Math.min(h, src.getHeight() - y));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, w, h, x, y, x + w, y + h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
