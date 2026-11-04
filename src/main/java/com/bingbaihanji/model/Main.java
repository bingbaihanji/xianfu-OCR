package com.bingbaihanji.model;

import com.bingbaihanji.model.cli.CliArgs;
import com.bingbaihanji.model.cli.NativeBootstrap;
import com.bingbaihanji.model.ocr.api.OnnxOcrService;
import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.config.OcrProperties;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.visual.OcrVisualizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// 命令行入口：
//   java -jar ocr-1.0-SNAPSHOT.jar --image <图片> [--outString <txt>] [--outImage <img>]
public class Main {

    public static void main(String[] args) {
        // 1. 参数
        CliArgs cli;
        try {
            cli = CliArgs.parse(args);
        } catch (IllegalArgumentException e) {
            // message 为 null 表示 --help，只打用法不打错误行
            if (e.getMessage() != null) {
                System.err.println("错误: " + e.getMessage());
                System.err.println();
            }
            System.err.println(CliArgs.usage());
            System.exit(CliArgs.EXIT_USAGE);
            return;
        }

        // 2. 校验并读图（纯 Java，不碰原生库；放在 bootstrap 之前，
        //    免得注定失败的调用还白解压几十 MB 原生库）
        Path image = cli.image();
        if (!Files.isRegularFile(image)) {
            System.err.println("错误: 图片不存在: " + image);
            System.err.println();
            System.err.println(CliArgs.usage());
            System.exit(CliArgs.EXIT_USAGE);
            return;
        }
        byte[] imageBytes;
        try {
            imageBytes = Files.readAllBytes(image);
        } catch (IOException e) {
            System.err.println("错误: 读取图片失败: " + image + " (" + e.getMessage() + ")");
            System.exit(CliArgs.EXIT_USAGE);
            return;
        }

        // 3. 原生库引导（必须早于任何 ONNX / OpenCV 调用）
        try {
            NativeBootstrap.bootstrap();
        } catch (IOException e) {
            System.err.println("原生库初始化失败: " + e.getMessage());
            System.exit(CliArgs.EXIT_RECOGNITION);
            return;
        }

        // 4. 识别 + 输出
        try (OnnxOcrService ocr = new OnnxOcrService(
                OcrProperties.withBundledModels(NativeBootstrap.jarDir()))) {
            OcrResult result = ocr.recognize(imageBytes,
                    OcrConfig.builder().model(cli.model()).build());

            if (cli.outString() != null) {
                try {
                    Files.writeString(cli.outString(), result.getText(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    System.err.println("错误: 写入文本失败: " + cli.outString() + " (" + e.getMessage() + ")");
                    System.exit(CliArgs.EXIT_IO);
                    return;
                }
            } else {
                System.out.println(result.getText());
            }

            if (cli.outImage() != null) {
                try {
                    BufferedImage src = ImageIO.read(image.toFile());
                    if (src == null) {
                        throw new IOException("无法解码图片");
                    }
                    OcrVisualizer.save(ocr.visualize(src, result), cli.outImage());
                } catch (IOException | RuntimeException e) {
                    System.err.println("错误: 写入图片失败: " + cli.outImage() + " (" + e.getMessage() + ")");
                    System.exit(CliArgs.EXIT_IO);
                    return;
                }
            }
        } catch (RuntimeException e) {
            // 只打 getMessage() 会吞掉根因（例如 ORT 报 "ByteBuffer is not direct"），
            // 排错时看不到为什么失败，故把整条异常链打到 stderr
            System.err.println("错误: 识别失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(CliArgs.EXIT_RECOGNITION);
        }
    }
}
