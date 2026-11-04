package com.bingbaihanji.model.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// 原生库引导：定位 jar 目录、解压 ONNX Runtime 原生库、把两个库的查找位置钉死到 jar 同级
//
// 必须在任何原生库加载之前调用，即早于 OpenCVInitializer.initialize()
// 与 OrtEnvironment.getEnvironment()。否则 JavaCPP 会解压到 java.io.tmpdir、
// ORT 会解压到 %TEMP%/onnxruntime-java*，污染用户目录。
public final class NativeBootstrap {

    private static final Logger log = LoggerFactory.getLogger(NativeBootstrap.class);

    // ONNX Runtime 原生库在 classpath 下的目录
    private static final String ORT_RESOURCE_DIR = "ai/onnxruntime/native/win-x64/";

    // ONNX Runtime CPU 版 win-x64 的三个原生库（jar 内固定清单）
    private static final String[] ORT_LIBS = {
            "onnxruntime.dll",
            "onnxruntime4j_jni.dll",
            "onnxruntime_providers_shared.dll",
    };

    private static Path jarDir;

    // 私有构造，禁止实例化
    private NativeBootstrap() {
    }

    // 执行全部引导步骤
    public static void bootstrap() throws IOException {
        jarDir = locateJarDir();
        Path nativeDir = jarDir.resolve("native");
        Files.createDirectories(nativeDir);
        extractOrtNatives(nativeDir);
        applySystemProperties(nativeDir);
        log.info("native 库目录: {}", nativeDir.toAbsolutePath());
    }

    // jar 同级目录；IDE 里跑（classpath 是目录）时退化为工作目录。
    // bootstrap() 尚未调用时抛异常，避免调用方拿到 null 走到别处才炸
    public static Path jarDir() {
        if (jarDir == null) {
            throw new IllegalStateException("bootstrap() 尚未调用");
        }
        return jarDir;
    }

    // 从 ProtectionDomain 取 jar 所在目录
    private static Path locateJarDir() {
        try {
            URL url = NativeBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                Path p = Path.of(url.toURI());
                if (Files.isRegularFile(p)) {
                    return p.toAbsolutePath().getParent();
                }
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // 落到工作目录兜底
        }
        log.debug("未取到 jar 路径，使用工作目录: {}", Path.of("").toAbsolutePath());
        return Path.of("").toAbsolutePath();
    }

    // 从 classpath 解压 ONNX Runtime 原生库到 native/，已存在且字节数一致则跳过
    private static void extractOrtNatives(Path nativeDir) throws IOException {
        for (String name : ORT_LIBS) {
            Path target = nativeDir.resolve(name);
            // 必须用 ClassLoader 的根相对查找：Class.getResource 对不带 / 的名字
            // 按包名解析，会去找 com/bingbaihanji/model/cli/ai/... 导致永远找不到
            String resource = ORT_RESOURCE_DIR + name;
            URL res = NativeBootstrap.class.getClassLoader().getResource(resource);
            if (res == null) {
                throw new IOException("classpath 中缺少原生库: " + resource);
            }
            // 先比对字节数再决定是否拷贝，避免每次启动白写 50 MB
            long expected = res.openConnection().getContentLengthLong();
            if (expected > 0 && Files.isRegularFile(target) && Files.size(target) == expected) {
                log.debug("原生库已存在，跳过: {}", target.toAbsolutePath());
                continue;
            }
            // 随机名 .tmp：两个进程从同一安装目录启动时不会互踩
            Path tmp = Files.createTempFile(nativeDir, name, ".tmp");
            try {
                try (InputStream in = res.openStream();
                     OutputStream out = Files.newOutputStream(tmp)) {
                    in.transferTo(out);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                log.debug("原生库就绪: {}", target.toAbsolutePath());
            } finally {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    // 目标已就位，残留 .tmp 不必让启动失败
                    log.debug("清理临时文件失败: {}", tmp, e);
                }
            }
        }
    }

    // 把 JavaCPP 与 ORT 的查找位置都钉到 native/，禁掉各自的临时目录回退
    private static void applySystemProperties(Path nativeDir) {
        String dir = nativeDir.toAbsolutePath().toString();
        // JavaCPP：缓存目录 + 不建哈希子目录，文件平铺在 native/ 下
        System.setProperty("org.bytedeco.javacpp.cachedir", dir);
        System.setProperty("org.bytedeco.javacpp.cachedir.nosubdir", "true");
        // ONNX Runtime：直接从该目录加载，不再往 %TEMP% 解压
        System.setProperty("onnxruntime.native.path", dir);
    }
}
