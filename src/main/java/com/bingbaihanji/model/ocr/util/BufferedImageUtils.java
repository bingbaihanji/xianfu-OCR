package com.bingbaihanji.model.ocr.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * 图像处理工具接口
 * @author bingbiahanji
 * @date 2025-7-14
 */

public interface BufferedImageUtils {

    /**
     * 水平翻转 BufferedImage
     *
     * @param originalImage 原始图像
     * @return 水平翻转后的新图像
     */
    static BufferedImage flipImageHorizontally(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        // 创建一个新的 BufferedImage 用于存储翻转后的图像
        BufferedImage flippedImage = new BufferedImage(width, height, originalImage.getType());

        // 遍历原图像的每一行
        for (int y = 0; y < height; y++) {
            // 从左到右遍历原图像的每一列，同时从右到左设置新图像的像素
            for (int x = 0; x < width; x++) {
                int rgb = originalImage.getRGB(x, y);
                // 计算翻转后的 x 坐标 (width - 1 - x)
                flippedImage.setRGB(width - 1 - x, y, rgb);
            }
        }

        return flippedImage;
    }


    /**
     * 垂直翻转 BufferedImage
     *
     * @param originalImage 原始图像
     * @return 垂直翻转后的新图像
     */
    static BufferedImage flipImageVertically(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        // 创建与原始图像相同类型的新图像
        BufferedImage flippedImage = new BufferedImage(width, height, originalImage.getType());

        // 遍历原图像的每个像素
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 获取当前像素的RGB值
                int rgb = originalImage.getRGB(x, y);
                // 计算翻转后的y坐标：height - 1 - y
                flippedImage.setRGB(x, height - 1 - y, rgb);
            }
        }

        return flippedImage;
    }


    /**
     * 反色处理图像
     *
     * @param img 要进行反色处理的图像
     * @return 反色处理后的图像
     */
    static BufferedImage antiColor(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = img.getRGB(x, y);

                int red = (rgb >> 16) & 0x0ff;
                int green = (rgb >> 8) & 0x0ff;
                int blue = rgb & 0x0ff;

                red = 255 - red;
                green = 255 - green;
                blue = 255 - blue;

                rgb = (red << 16) | (green << 8) | blue;
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }


    /**
     * 将传入的BufferedImage对象转换成灰度图像
     *
     * @param bufferedImage 需要转换的BufferedImage对象
     * @return 转换后的灰度图像BufferedImage对象
     */
    static BufferedImage gray(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        BufferedImage grayImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = bufferedImage.getRGB(x, y);
                grayImage.setRGB(x, y, rgb);
            }
        }
        return grayImage;
    }

    /**
     * 缩放图像
     *
     * @param bufferedImage 需要缩放的图像
     * @param scaleFactor   缩放比例（如 0.5 表示缩小一半）
     * @return 缩放后的图像
     */
    static BufferedImage scaleImage(BufferedImage bufferedImage, float scaleFactor) {
        if (bufferedImage == null || scaleFactor <= 0) {
            throw new IllegalArgumentException("图像不能为空，缩放比例必须大于0");
        }

        int newWidth = Math.round(bufferedImage.getWidth() * scaleFactor);
        int newHeight = Math.round(bufferedImage.getHeight() * scaleFactor);

        return scaleImage(bufferedImage, newWidth, newHeight);
    }

    /**
     * 缩放图像
     *
     * @param bufferedImage 需要缩放的图像
     * @param width         缩放后的宽度
     * @param height        缩放后的高度
     * @return 缩放后的图像
     */
    static BufferedImage scaleImage(BufferedImage bufferedImage, int width, int height) {
        if (bufferedImage == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("图像不能为空，宽高必须大于0");
        }

        Image scaledInstance = bufferedImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(width, height, bufferedImage.getType());

        Graphics2D g2d = outputImage.createGraphics();
        g2d.drawImage(scaledInstance, 0, 0, null);
        g2d.dispose();

        return outputImage;
    }


    /**
     * 将 BufferedImage 图像编码为 Base64 字符串（PNG 格式）
     *
     * @param image BufferedImage 图像
     * @return Base64 编码的图像字符串
     */
    static String getBase64ImageString(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image must not be null(Image 不能为 null)");
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", baos)) {
                throw new IOException("Failed to encode image as PNG(无法将图像编码为 PNG)");
            }
            byte[] bytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to convert image to Base64(无法将图像转换为 Base64)", e);
        }
    }

    /**
     * 将 BufferedImage 图像编码为 Base64 字符串（PNG 格式）
     *
     * @param image    BufferedImage 图像
     * @param isPrefix 是否添加前缀
     * @return Base64 编码的图像字符串
     */
    static String getBase64ImageString(BufferedImage image, boolean isPrefix) {
        return isPrefix ? "data:image/png;base64," + getBase64ImageString(image) : getBase64ImageString(image);
    }


    /**
     * 顺时针旋转指定角度（单位：度）
     *
     * @param image        要旋转的图像
     * @param angleDegrees 旋转的角度（单位：度）
     * @return 旋转后的图像
     * @throws IllegalArgumentException 如果传入的图像为空，则抛出此异常
     */
    static BufferedImage rotate(BufferedImage image, double angleDegrees) {
        if (image == null) {
            throw new IllegalArgumentException("图像不能为空");
        }

        double angleRadians = Math.toRadians(angleDegrees); // 角度转弧度

        int width = image.getWidth();
        int height = image.getHeight();

        // 计算旋转后的图像尺寸
        double sin = Math.abs(Math.sin(angleRadians));
        double cos = Math.abs(Math.cos(angleRadians));
        int newWidth = (int) Math.floor(width * cos + height * sin);
        int newHeight = (int) Math.floor(height * cos + width * sin);

        // 创建一个空白图像用于容纳旋转后的结果
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();

        // 启用抗锯齿和高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 平移坐标系，使图像中心对准新画布中心
        AffineTransform transform = new AffineTransform();
        transform.translate(newWidth / 2.0, newHeight / 2.0);
        transform.rotate(angleRadians);
        transform.translate(-width / 2.0, -height / 2.0);

        // 绘制旋转图像
        g2d.drawImage(image, transform, null);
        g2d.dispose();

        return rotated;
    }


}
