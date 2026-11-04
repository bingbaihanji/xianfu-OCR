package com.bingbaihanji.model.cli;

import com.bingbaihanji.model.ocr.enums.OcrModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 命令行参数，不可变值对象
public final class CliArgs {

    // 正常结束
    public static final int EXIT_OK = 0;
    // 用法错误：缺 --image、参数无法解析、图片不存在
    public static final int EXIT_USAGE = 1;
    // 输出写文件失败
    public static final int EXIT_IO = 2;
    // 识别过程失败（模型加载 / 推理异常）
    public static final int EXIT_RECOGNITION = 3;

    private final Path image;
    private final Path outString;
    private final Path outImage;
    private final OcrModel model;

    // 私有构造，统一经由 parse 创建
    private CliArgs(Path image, Path outString, Path outImage, OcrModel model) {
        this.image = image;
        this.outString = outString;
        this.outImage = outImage;
        this.model = model;
    }

    /**
     * 解析命令行参数。
     * <p>
     * {@code --help} / {@code -h} 会抛出 message 为 null 的 IllegalArgumentException，
     * 供调用方区分「打印用法」与「打印错误 + 用法」。
     *
     * @param args main 收到的原始参数
     * @return 解析结果
     * @throws IllegalArgumentException 用法错误，message 可直接打印（--help 时为 null）
     */
    public static CliArgs parse(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("缺少 --image 参数");
        }
        Path image = null;
        Path outString = null;
        Path outImage = null;
        OcrModel model = OcrModel.DEFAULT;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--help", "-h" -> throw new IllegalArgumentException((String) null);
                case "--image" -> image = Path.of(requireValue(args, ++i, "--image"));
                case "--outString" -> outString = Path.of(requireValue(args, ++i, "--outString"));
                case "--outImage" -> outImage = Path.of(requireValue(args, ++i, "--outImage"));
                case "--model" -> model = parseModel(requireValue(args, ++i, "--model"));
                default -> rest.add(a);
            }
        }
        if (!rest.isEmpty()) {
            throw new IllegalArgumentException("无法识别的参数: " + rest.get(0));
        }
        if (image == null) {
            throw new IllegalArgumentException("缺少 --image 参数");
        }
        // 输出不能与输入或彼此重名，否则后写的会覆盖先写的，甚至毁掉原图
        Path in = image.toAbsolutePath().normalize();
        if (outString != null && outString.toAbsolutePath().normalize().equals(in)) {
            throw new IllegalArgumentException("--outString 不能与 --image 指向同一文件: " + image);
        }
        if (outImage != null && outImage.toAbsolutePath().normalize().equals(in)) {
            throw new IllegalArgumentException("--outImage 不能与 --image 指向同一文件: " + image);
        }
        if (outString != null && outImage != null
                && outString.toAbsolutePath().normalize()
                        .equals(outImage.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("--outString 与 --outImage 不能指向同一文件: " + outString);
        }
        return new CliArgs(image, outString, outImage, model);
    }

    // 取参数值，越界即用法错误
    private static String requireValue(String[] args, int i, String flag) {
        if (i >= args.length) {
            throw new IllegalArgumentException(flag + " 缺少取值");
        }
        return args[i];
    }

    // 解析模型编码，未命中即用法错误
    private static OcrModel parseModel(String value) {
        return OcrModel.fromCode(value).orElseThrow(
                () -> new IllegalArgumentException("未知模型: " + value + "（可选 default / ppocr_v4 / ppocr_v5 / ppocr_v6）"));
    }

    // 输入图片路径
    public Path image() {
        return image;
    }

    // 识别文本输出路径，null 表示打控制台
    public Path outString() {
        return outString;
    }

    // 可视化图输出路径，null 表示不产图
    public Path outImage() {
        return outImage;
    }

    // 识别模型套件，未指定时为 OcrModel.DEFAULT
    public OcrModel model() {
        return model;
    }

    // 用法说明
    public static String usage() {
        return """
                用法: java -jar ocr-1.0-SNAPSHOT.jar --image <图片路径> [选项]

                参数:
                  --image <path>       必填。待识别的图片，可指向任意可读路径
                  --outString <path>   可选。识别文本写入该文件（UTF-8）；不给则打控制台
                  --outImage <path>    可选。可视化图写入该文件（按扩展名选 png/jpg）；不给则不产图
                  --model <code>       可选。识别模型套件，默认 default：
                                         default / ppocr_v5  →  PP-OCRv5
                                         ppocr_v4            →  PP-OCRv4
                                         ppocr_v6            →  PP-OCRv6 small
                  --help, -h           显示本说明

                示例:
                  java -jar ocr-1.0-SNAPSHOT.jar --image a.png
                  java -jar ocr-1.0-SNAPSHOT.jar --image a.png --outString out.txt --outImage out.png
                  java -jar ocr-1.0-SNAPSHOT.jar --image a.png --model ppocr_v6

                退出码:
                  0 成功 / 1 用法错误 / 2 写文件失败 / 3 识别失败
                """;
    }
}
