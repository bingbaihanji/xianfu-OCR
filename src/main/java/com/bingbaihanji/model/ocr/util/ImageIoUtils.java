package com.bingbaihanji.model.ocr.util;

import com.bingbaihanji.model.ocr.exception.OcrImageException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

// 图片输入工具类，负责图片字节数组、BufferedImage 与 InputStream 之间的归一化转换
public final class ImageIoUtils {

    // 单张图片最大字节数，防止超大输入导致 OOM
    public static final int MAX_IMAGE_BYTES = 64 * 1024 * 1024;

    // 私有构造，禁止实例化工具类
    private ImageIoUtils() {
    }

    // 将 BufferedImage 按 PNG 编码为 byte[]
    public static byte[] toPngBytes(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", baos)) {
                throw new OcrImageException("当前 JVM 无 PNG 编码器");
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new OcrImageException("BufferedImage 编码为 PNG 失败", e);
        }
    }

    // 全量读取 InputStream 为 byte[]（不关闭流，由调用方负责关闭），超过上限时抛异常
    public static byte[] readAll(InputStream in) {
        Objects.requireNonNull(in, "image");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_IMAGE_BYTES) {
                    throw new OcrImageException("图片字节数超过上限: " + MAX_IMAGE_BYTES);
                }
                baos.write(buffer, 0, n);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new OcrImageException("InputStream 读取为字节数组失败", e);
        }
    }

    // 解码图片字节数组为 BufferedImage，解码失败抛 OcrImageException
    public static BufferedImage decode(byte[] image) {
        Objects.requireNonNull(image, "image");
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(image));
            if (img == null) {
                throw new OcrImageException("无法解码图片，格式不支持或数据损坏");
            }
            return img;
        } catch (IOException e) {
            throw new OcrImageException("图片解码失败", e);
        }
    }
}