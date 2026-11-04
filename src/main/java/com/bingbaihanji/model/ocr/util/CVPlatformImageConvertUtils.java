package com.bingbaihanji.model.ocr.util;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC4;

/**
 * OpenCV Mat 与 Java BufferedImage / 图片字节数组互转工具类
 * <p>
 * 说明：
 * - OpenCV Mat 默认通道顺序：B G R (CV_8UC3) / B G R A (CV_8UC4)
 * - BufferedImage.TYPE_3BYTE_BGR 内部顺序也是 B G R → 可直接拷贝
 * - BufferedImage.TYPE_4BYTE_ABGR 内部顺序是 A B G R → 需从 BGRA 重排为 ABGR
 * - 灰度图 (CV_8UC1) 对应 TYPE_BYTE_GRAY
 */
public class CVPlatformImageConvertUtils {

    private CVPlatformImageConvertUtils() {
    }

    public static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Input Mat cannot be null or empty");
        }

        int width = mat.cols();
        int height = mat.rows();
        int channels = mat.channels();

        if (channels == 1) {
            // 灰度图：直接拷贝
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            // 从 Mat 读取 bytes
            BytePointer bp = mat.data();
            bp.get(targetPixels);
            return image;
        } else if (channels == 3) {
            // Mat 是 BGR，BufferedImage.TYPE_3BYTE_BGR 也期望 BGR → 直接拷贝
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            BytePointer bp = mat.data();
            bp.get(targetPixels);
            return image;
        } else if (channels == 4) {
            // Mat 是 BGRA (B,G,R,A) -> BufferedImage TYPE_4BYTE_ABGR (A,B,G,R)
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

            // 读取源字节
            int srcLen = width * height * 4;
            byte[] src = new byte[srcLen];
            BytePointer bp = mat.data();
            bp.get(src);

            // 重排 BGRA -> ABGR
            // 源索引: iSrc = p*4 + (0:B,1:G,2:R,3:A)
            // 目标索引: iTgt = p*4 + (0:A,1:B,2:G,3:R)
            for (int p = 0, s = 0, t = 0; p < width * height; p++, s += 4, t += 4) {
                byte b = src[s];
                byte g = src[s + 1];
                byte r = src[s + 2];
                byte a = src[s + 3];

                targetPixels[t] = a;      // A
                targetPixels[t + 1] = b;  // B
                targetPixels[t + 2] = g;  // G
                targetPixels[t + 3] = r;  // R
            }
            return image;
        } else {
            throw new UnsupportedOperationException("Unsupported number of channels: " + channels);
        }
    }

    public static Mat bufferedImageToMat(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Input BufferedImage cannot be null");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int type = image.getType();

        if (type == BufferedImage.TYPE_BYTE_GRAY) {
            Mat mat = new Mat(height, width, CV_8UC1);
            byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            mat.data().put(data);
            return mat;
        } else if (type == BufferedImage.TYPE_3BYTE_BGR) {
            // BufferedImage 存的是 B G R，直接创建 CV_8UC3 并拷贝
            Mat mat = new Mat(height, width, CV_8UC3);
            byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            mat.data().put(data);
            return mat;
        } else if (type == BufferedImage.TYPE_4BYTE_ABGR) {
            // BufferedImage: A B G R -> 我们需要 Mat 的 BGRA (B G R A)
            byte[] src = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            byte[] dst = new byte[width * height * 4];

            // src order: [A, B, G, R], dst order needed: [B, G, R, A]
            for (int p = 0, s = 0, d = 0; p < width * height; p++, s += 4, d += 4) {
                byte a = src[s];
                byte b = src[s + 1];
                byte g = src[s + 2];
                byte r = src[s + 3];

                dst[d] = b;     // B
                dst[d + 1] = g; // G
                dst[d + 2] = r; // R
                dst[d + 3] = a; // A
            }

            Mat mat = new Mat(height, width, CV_8UC4);
            mat.data().put(dst);
            return mat;
        } else {
            // 处理其他类型（例如 TYPE_INT_ARGB 等）——将其先转换成 3BYTE/4BYTE/GRAY，再复用上面逻辑
            // 这里默认将其他类型转成 TYPE_3BYTE_BGR 然后递归调用
            BufferedImage converted;
            if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                converted = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                converted.getGraphics().drawImage(image, 0, 0, null);
            } else {
                converted = image;
            }
            return bufferedImageToMat(converted);
        }
    }

    /**
     * 将图片字节数组解码为 OpenCV BGR Mat，alpha 处理对齐 cv2.imread(path, IMREAD_COLOR)。
     * <p>
     * 说明：BufferedImage 可能带 alpha（TYPE_INT_ARGB / TYPE_4BYTE_ABGR），
     * cv2.imread 会直接丢弃 alpha 保留 RGB，而 {@link #bufferedImageToMat} 的
     * Graphics2D.drawImage 分支会按透明合成到黑色背景。本方法用 getRGB 逐像素
     * 取 R/G/B 重排为 BGR，与 cv2 的 alpha 行为一致。
     * <p>
     * 差异：cv2.imread 默认还会按 EXIF 方向旋转，ImageIO 不会；带 EXIF 旋转标记的
     * JPEG 与 cv2 结果可能不同，需要时由调用方自行处理。
     * <p>
     * 返回的 Mat 包装原生内存，由调用方负责 {@code release()}。
     */
    public static Mat decodeToBgrMat(byte[] image) {
        BufferedImage img = ImageIoUtils.decode(image);
        int width = img.getWidth();
        int height = img.getHeight();

        // 已是 BGR 三通道可直接拷贝
        if (img.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            Mat mat = new Mat(height, width, CV_8UC3);
            byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            mat.data().put(data);
            return mat;
        }

        byte[] bgr = new byte[width * height * 3];
        for (int y = 0, o = 0; y < height; y++) {
            for (int x = 0; x < width; x++, o += 3) {
                int rgb = img.getRGB(x, y);
                bgr[o] = (byte) ((rgb) & 0xFF);           // B
                bgr[o + 1] = (byte) ((rgb >> 8) & 0xFF);  // G
                bgr[o + 2] = (byte) ((rgb >> 16) & 0xFF); // R
            }
        }
        Mat mat = new Mat(height, width, CV_8UC3);
        mat.data().put(bgr);
        return mat;
    }
}
