package com.bingbaihanji.ocr.service;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.model.ocr.result.OcrResult;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

// 全分辨率标注图：识别框 + 各行文字标签（「文件→保存」用；不动模型层 OcrVisualizer）
public final class AnnotatedImageRenderer {

    private static final Color BOX_COLOR = new Color(0x5A, 0xB0, 0xFF);
    private static final Color LABEL_BG = new Color(0x1B, 0x1B, 0x22, 200);
    private static final Color LABEL_FG = new Color(0xE8, 0xE8, 0xF0);
    private static final int BOX_THICKNESS = 2;
    private static final int FONT_SIZE = 16;

    private AnnotatedImageRenderer() {
    }

    // 在原图副本上绘制识别框与文字标签，输出尺寸与原图一致
    public static BufferedImage render(BufferedImage src, OcrResult result) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(loadFont());
            g.setStroke(new BasicStroke(BOX_THICKNESS));
            for (OcrLine line : result.getLines()) {
                float[] p = line.getPoints();
                int n = p.length / 2;
                int[] xs = new int[n];
                int[] ys = new int[n];
                int minX = Integer.MAX_VALUE;
                int minY = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    xs[i] = Math.round(p[i * 2]);
                    ys[i] = Math.round(p[i * 2 + 1]);
                    minX = Math.min(minX, xs[i]);
                    minY = Math.min(minY, ys[i]);
                }
                g.setColor(BOX_COLOR);
                g.drawPolygon(xs, ys, n);
                drawLabel(g, line.getText(), minX, minY);
            }
            return out;
        } finally {
            g.dispose();
        }
    }

    // 在框左上角绘制深底文字标签（越出图像时贴边）
    private static void drawLabel(Graphics2D g, String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 8;
        int h = fm.getHeight() + 4;
        int ly = Math.max(0, y - h);
        g.setColor(LABEL_BG);
        g.fill(new RoundRectangle2D.Float(x, ly, w, h, 6, 6));
        g.setColor(LABEL_FG);
        g.drawString(text, x + 4, ly + fm.getAscent() + 2);
    }

    // 优先加载内置仿宋，失败退回系统无衬线字体
    private static Font loadFont() {
        try (InputStream in = AnnotatedImageRenderer.class.getResourceAsStream("/fonts/simfang.ttf")) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) FONT_SIZE);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                return f;
            }
        } catch (Exception e) {
            // 字体加载失败不阻塞渲染
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE);
    }
}
