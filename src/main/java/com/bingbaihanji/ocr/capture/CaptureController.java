package com.bingbaihanji.ocr.capture;

import com.bingbaihanji.ocr.capture.ScreenCaptureService.MonitorCapture;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// 截图编排：先捕获全部屏幕 → 全屏遮罩 → 选区 → 裁剪 → 工具条/贴图/复制
// 选区以「物理像素坐标」在全部覆盖层间共享，因此可以跨显示器自由拖动
public class CaptureController implements CaptureOverlay.SelectionStore {

    private static final Logger log = LoggerFactory.getLogger(CaptureController.class);

    private final CaptureMode mode;
    private final Consumer<BufferedImage> onRecognizeImage;
    // 贴图「另存为」：交上层弹文件对话框（用户自选保存位置）
    private final Consumer<BufferedImage> onSaveImage;
    private final Runnable onClose;
    private final List<MonitorCapture> monitors = new ArrayList<>();
    private final List<CaptureOverlay> overlays = new ArrayList<>();

    // 全部显示器物理像素并集（选区可拖动范围）
    private Rectangle2D virtualUnion;
    // 共享选区（物理像素坐标），null 表示尚未框选
    private Rectangle2D selection;
    // 是否已确认（工具条已出 / 贴图已发）
    private boolean confirmed;
    private boolean closed;

    private CaptureToolbar toolbar;
    private CaptureOverlay toolbarHost;

    // onRecognizeImage：识别模式下用户点「识别」时回调裁剪图（交主窗口展示）；
    // onSaveImage：贴图「另存为」时回调原图（交上层弹文件对话框）；
    // onClose：会话结束（取消/完成）时回调，供调用方解除「进行中」标记
    public CaptureController(CaptureMode mode, Consumer<BufferedImage> onRecognizeImage,
                             Consumer<BufferedImage> onSaveImage, Runnable onClose) {
        this.mode = mode;
        this.onRecognizeImage = onRecognizeImage;
        this.onSaveImage = onSaveImage;
        this.onClose = onClose;
    }

    // 启动截图（先捕获后显示遮罩）；失败抛 IllegalStateException
    public void start() {
        try {
            monitors.addAll(ScreenCaptureService.captureAll());
            virtualUnion = ScreenCaptureService.deviceUnion(monitors);
            for (MonitorCapture m : monitors) {
                overlays.add(new CaptureOverlay(m, this, overlayCallbacks));
            }
            for (CaptureOverlay o : overlays) {
                o.show();
            }
            log.info("截图开始: {} 个显示器, 虚拟范围 {}×{} mode={}", monitors.size(),
                    (int) virtualUnion.getWidth(), (int) virtualUnion.getHeight(), mode);
        } catch (Exception e) {
            close();
            throw new IllegalStateException("截图捕获失败: " + e.getMessage(), e);
        }
    }

    // 关闭全部截图相关窗口（幂等）
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (CaptureOverlay o : overlays) {
            o.close();
        }
        overlays.clear();
        monitors.clear();
        toolbar = null;
        toolbarHost = null;
        onClose.run();
    }

    // ------------------------------------------------------------------ 共享选区（SelectionStore）

    @Override
    public Rectangle2D selection() {
        return selection;
    }

    @Override
    public void setSelection(Rectangle2D deviceRect) {
        selection = deviceRect;
        if (deviceRect == null && confirmed) {
            // 点空白处重新开始框选：收起工具条回到选择态
            confirmed = false;
            hideToolbar();
        }
        for (CaptureOverlay o : overlays) {
            o.refresh();
        }
        if (confirmed) {
            placeToolbar();
        }
    }

    @Override
    public Rectangle2D deviceUnion() {
        return virtualUnion;
    }

    // ------------------------------------------------------------------ 回调

    private final CaptureOverlay.Callbacks overlayCallbacks = new CaptureOverlay.Callbacks() {

        @Override
        public void onConfirm(Rectangle2D deviceRect) {
            if (confirmed || closed) {
                // 已确认后的拖动只是微调选区，工具条已随 setSelection 跟随，无需重复确认
                return;
            }
            selection = deviceRect;
            confirmed = true;
            for (CaptureOverlay o : overlays) {
                o.refresh();
            }
            log.info("截图确认: {}×{} mode={}", (int) deviceRect.getWidth(), (int) deviceRect.getHeight(), mode);
            if (mode == CaptureMode.PIN) {
                pin(crop(deviceRect), deviceRect);
                close();
            } else {
                showToolbar();
            }
        }

        @Override
        public void onCancel() {
            close();
        }

        @Override
        public boolean onKey(KeyCode code) {
            return handleKey(code);
        }
    };

    // 键盘统一调度：ESC 任意阶段取消；ENTER 未确认时确认，已确认时执行主操作
    private boolean handleKey(KeyCode code) {
        if (code == KeyCode.ESCAPE) {
            close();
            return true;
        }
        if (code == KeyCode.ENTER) {
            if (confirmed) {
                recognize();
            } else if (selection != null) {
                overlayCallbacks.onConfirm(selection);
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 工具条

    private void showToolbar() {
        toolbar = new CaptureToolbar(new CaptureToolbar.Actions() {

            @Override
            public void onRecognize() {
                recognize();
            }

            @Override
            public void onPin() {
                BufferedImage img = crop(selection);
                Rectangle2D rect = selection;
                pin(img, rect);
                close();
            }

            @Override
            public void onCopy() {
                ClipboardContent cc = new ClipboardContent();
                cc.putImage(SwingFXUtils.toFXImage(crop(selection), null));
                Clipboard.getSystemClipboard().setContent(cc);
                close();
            }

            @Override
            public void onCancel() {
                close();
            }
        });
        placeToolbar();
        toolbar.animateIn();
    }

    private void hideToolbar() {
        if (toolbarHost != null) {
            toolbarHost.clearToolbar();
            toolbarHost = null;
        }
    }

    // 工具条贴在选区右下方（越界翻上方），并挂载到落点所在的覆盖层上
    private void placeToolbar() {
        if (toolbar == null || selection == null) {
            return;
        }
        double w = toolbar.prefWidth(-1);
        double h = toolbar.prefHeight(-1);

        // 选区右下角所在显示器；取不到就退化到左上角所在显示器
        MonitorCapture host = ScreenCaptureService.deviceAt(monitors, selection.getMaxX() - 1, selection.getMaxY() - 1);
        if (host == null) {
            return;
        }
        Rectangle2D localSel = host.toLocal(selection);
        Rectangle2D scr = host.screen().getBounds();

        double x = localSel.getMaxX() - w;
        double y = localSel.getMaxY() + 8;
        if (y + h > scr.getHeight()) {
            y = localSel.getMinY() - h - 8;
        }
        x = clamp(x, 0, Math.max(0, scr.getWidth() - w));
        y = clamp(y, 0, Math.max(0, scr.getHeight() - h));

        CaptureOverlay newHost = overlayOf(host);
        if (newHost == null) {
            return;
        }
        if (toolbarHost != null && toolbarHost != newHost) {
            toolbarHost.clearToolbar();
        }
        newHost.placeToolbar(toolbar, x, y);
        toolbarHost = newHost;
    }

    private void recognize() {
        BufferedImage img = crop(selection);
        close();
        onRecognizeImage.accept(img);
    }

    private CaptureOverlay overlayOf(MonitorCapture m) {
        int i = monitors.indexOf(m);
        return i < 0 || i >= overlays.size() ? null : overlays.get(i);
    }

    // ------------------------------------------------------------------ 裁剪与贴图

    // 按物理像素裁剪：各屏图像 1:1 落在自身设备边界内，跨屏选区天然无缝拼合
    private BufferedImage crop(Rectangle2D r) {
        int w = Math.max(1, (int) Math.round(r.getWidth()));
        int h = Math.max(1, (int) Math.round(r.getHeight()));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            for (MonitorCapture m : monitors) {
                Rectangle2D hit = ScreenCaptureService.intersect(r, m.deviceBounds());
                if (hit == null) {
                    continue;
                }
                int srcX = (int) Math.round(hit.getMinX() - m.deviceBounds().getMinX());
                int srcY = (int) Math.round(hit.getMinY() - m.deviceBounds().getMinY());
                int srcW = (int) Math.round(hit.getWidth());
                int srcH = (int) Math.round(hit.getHeight());
                int dstX = (int) Math.round(hit.getMinX() - r.getMinX());
                int dstY = (int) Math.round(hit.getMinY() - r.getMinY());
                g.drawImage(m.image(), dstX, dstY, dstX + srcW, dstY + srcH,
                        srcX, srcY, srcX + srcW, srcY + srcH, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    // 弹出贴图窗口；贴图窗口按「逻辑尺寸」开窗，避免 HiDPI 屏上放大 N 倍
    private void pin(BufferedImage img, Rectangle2D deviceRect) {
        MonitorCapture host = ScreenCaptureService.deviceAt(monitors, deviceRect.getMinX(), deviceRect.getMinY());
        if (host == null) {
            new PinWindow(img, 0, 0, img.getWidth(), img.getHeight(), onSaveImage).show();
            return;
        }
        Rectangle2D local = host.toLocal(deviceRect);
        Rectangle2D scr = host.screen().getBounds();
        double logicalW = deviceRect.getWidth() / host.scaleX();
        double logicalH = deviceRect.getHeight() / host.scaleY();
        new PinWindow(img, scr.getMinX() + local.getMinX(), scr.getMinY() + local.getMinY(),
                logicalW, logicalH, onSaveImage).show();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
