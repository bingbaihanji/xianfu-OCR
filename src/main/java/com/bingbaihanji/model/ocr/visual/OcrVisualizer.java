package com.bingbaihanji.model.ocr.visual;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// OCR 结果可视化，对应 Python utils.draw_ocr / sav2Img
public final class OcrVisualizer {

    private static final Logger log = LoggerFactory.getLogger(OcrVisualizer.class);

    // 框线颜色：RGB 红。Python 传 (255,0,0) 给 cv2.polylines 作用在 RGB 数组上，实际是红框
    private static final Color BOX_COLOR = new Color(255, 0, 0);

    // 框线宽度
    private static final int BOX_THICKNESS = 2;

    // 图像长边缩放到该值
    private static final int VIEW_MAX_SIDE = 600;

    // 文字面板宽度
    private static final int PANEL_WIDTH = 600;

    // 字号
    private static final int FONT_SIZE = 20;

    // 行距，对应 Python text_visual 的 gap = font_size + 5
    private static final int LINE_GAP = FONT_SIZE + 5;

    // 每行最多字符数，对应 Python 的 img_w // font_size - 4
    private static final int MAX_CHARS = PANEL_WIDTH / FONT_SIZE - 4;

    // 私有构造，禁止实例化
    private OcrVisualizer() {
    }

    /**
     * 用默认参数绘制（classpath:fonts/simfang.ttf）。
     * <p>
     * dropScore 取 0：传入的 OcrResult 已由识别流程按 minConfidence 过滤过，
     * 这里不再二次过滤，否则 minConfidence &lt; 0.5 时会「控制台有、图上没有」。
     * 需要更严的筛选请用下面带 dropScore 的重载。
     */
    public static BufferedImage draw(BufferedImage image, OcrResult result) {
        return draw(image, result, 0.0, null);
    }

    /**
     * 把识别结果画回原图并拼接右侧文字面板。
     *
     * @param image    原图
     * @param result   识别结果
     * @param dropScore 低于该置信度（或 NaN）的行不绘制
     * @param font     字体文件，null 则用 classpath:fonts/simfang.ttf，再退到 SansSerif
     * @return 新的 BufferedImage，由调用方自行处理
     */
    public static BufferedImage draw(BufferedImage image, OcrResult result, double dropScore, Path font) {
        // 1. 在原分辨率上画框
        BufferedImage boxed = deepCopy(image);
        Graphics2D g = boxed.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BOX_COLOR);
            g.setStroke(new BasicStroke(BOX_THICKNESS));
            for (OcrLine line : result.getLines()) {
                if (line.getConfidence() < dropScore || Double.isNaN(line.getConfidence())) {
                    continue;
                }
                float[] p = line.getPoints();
                int n = p.length / 2;
                int[] xs = new int[n];
                int[] ys = new int[n];
                for (int i = 0; i < n; i++) {
                    xs[i] = Math.round(p[i * 2]);
                    ys[i] = Math.round(p[i * 2 + 1]);
                }
                g.drawPolygon(xs, ys, n);
            }
        } finally {
            g.dispose();
        }

        BufferedImage view = scaleToMaxSide(boxed, VIEW_MAX_SIDE);

        // 2. 右侧面板
        Font fontObj = loadFont(font);
        BufferedImage panel = renderTextPanel(result, dropScore, view.getHeight(), fontObj);

        // 3. 横向拼接
        BufferedImage out = new BufferedImage(
                view.getWidth() + panel.getWidth(),
                Math.max(view.getHeight(), panel.getHeight()),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D og = out.createGraphics();
        try {
            og.setColor(Color.WHITE);
            og.fillRect(0, 0, out.getWidth(), out.getHeight());
            og.drawImage(view, 0, 0, null);
            og.drawImage(panel, view.getWidth(), 0, null);
        } finally {
            og.dispose();
        }
        return out;
    }

    /**
     * 保存可视化结果，对应 Python sav2Img。
     * 按目标文件扩展名选编码器：.jpg/.jpeg → JPEG，其余（含 .png）→ PNG。
     *
     * @param image  要保存的图
     * @param target 目标文件
     * @throws IllegalStateException 写文件失败或当前 JVM 缺对应编码器
     */
    public static void save(BufferedImage image, Path target) {
        String name = target.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        String format = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "jpg" : "png";
        try {
            if (!ImageIO.write(image, format, target.toFile())) {
                throw new IOException("当前 JVM 无 " + format.toUpperCase() + " 编码器");
            }
        } catch (IOException e) {
            throw new IllegalStateException("保存可视化结果失败: " + target, e);
        }
    }

    // 渲染右侧文字面板，等价 Python text_visual
    private static BufferedImage renderTextPanel(OcrResult result, double dropScore,
                                                 int pageHeight, Font font) {
        // 1. 把每条识别结果折成若干「视觉行」，分页数按视觉行算而不是按条目算
        List<String> rows = new ArrayList<>();
        int index = 0;
        for (OcrLine line : result.getLines()) {
            if (line.getConfidence() < dropScore || Double.isNaN(line.getConfidence())) {
                continue;
            }
            index++;
            String entry = index + ": " + line.getText() + "   "
                    + String.format("%.3f", line.getConfidence());
            String rest = entry;
            boolean firstLine = true;
            while (rest.length() >= MAX_CHARS) {
                rows.add(firstLine ? rest.substring(0, MAX_CHARS)
                        : "    " + rest.substring(0, MAX_CHARS));
                rest = rest.substring(MAX_CHARS);
                firstLine = false;
            }
            rows.add(firstLine ? rest : "  " + rest);
        }

        int linesPerPage = Math.max(1, pageHeight / LINE_GAP - 1);
        int pageCount = Math.max(1, (rows.size() + linesPerPage - 1) / linesPerPage);
        BufferedImage panel = new BufferedImage(PANEL_WIDTH * pageCount, pageHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = panel.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, panel.getWidth(), panel.getHeight());
            g.setColor(new Color(0, 0, 0));
            g.setFont(font);
            for (int i = 0; i < rows.size(); i++) {
                int col = i / linesPerPage;
                int lineInCol = i % linesPerPage;
                g.drawString(rows.get(i), col * PANEL_WIDTH, LINE_GAP * (lineInCol + 1));
            }
        } finally {
            g.dispose();
        }
        return panel;
    }

    // 长边缩放到 maxSide（小图也会放大，对应 Python resize_img 的 600/max 无条件缩放）
    private static BufferedImage scaleToMaxSide(BufferedImage img, int maxSide) {
        int max = Math.max(img.getWidth(), img.getHeight());
        if (max <= 0) {
            return img;
        }
        double s = (double) maxSide / max;
        int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    // 深拷贝为 TYPE_INT_RGB
    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    // 加载字体：指定文件 → classpath:fonts/simfang.ttf → SansSerif
    private static Font loadFont(Path font) {
        if (font != null) {
            try (InputStream in = Files.newInputStream(font)) {
                return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) FONT_SIZE);
            } catch (Exception e) {
                log.warn("加载指定字体失败: {}", font, e);
            }
        }
        try (InputStream in = ResourceUtils.getResourceAsStream("classpath:fonts/simfang.ttf")) {
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) FONT_SIZE);
        } catch (Exception e) {
            log.warn("加载 simfang.ttf 失败，降级为 SansSerif", e);
        }
        return new Font("SansSerif", Font.PLAIN, FONT_SIZE);
    }
}
