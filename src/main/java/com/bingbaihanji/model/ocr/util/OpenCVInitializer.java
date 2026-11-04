package com.bingbaihanji.model.ocr.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * OpenCV初始化工具类 - 确保全局只初始化一次
 */
@Slf4j
public class OpenCVInitializer {

    private static final Object lock = new Object();

    private static final String DLL_NAME = "opencv_java4110.dll";

    private static volatile boolean initialized = false;

    private OpenCVInitializer() {
        // 工具类,禁止实例化
    }

    /**
     * 初始化OpenCV本地库(线程安全)
     */
    public static void initialize() {
        if (!initialized) {
            synchronized (lock) {
                if (!initialized) {
                    try {
                        String runtime = "runtime is not jar";
                        // 1. 提取dll文件到jar同目录
                        if (isRunningInJar()) {
                            runtime = "runtime is jar";
                            String dllPath = extractDllFromJar();
                            // 2. 加载提取的dll
                            if (dllPath != null && new File(dllPath).exists()) {
                                System.load(dllPath);
                                log.info("成功加载OpenCV DLL: {}", dllPath);
                            }
                        }
                        log.debug("{}", runtime);
                        // 3. 加载真正用到的模块。OCR 只用 core + imgproc；
                        //    原先的 opencv_face 会连带拉入人脸识别及其依赖的一串模块
                        Loader.load(opencv_core.class);
                        Loader.load(opencv_imgproc.class);
                        log.info("OpenCV初始化成功 - 版本: {}", opencv_core.getVersionString().getString());
                        initialized = true;
                    } catch (Exception e) {
                        log.error("OpenCV初始化失败", e);
                        throw new RuntimeException("OpenCV初始化失败", e);
                    }
                }
            }
        }
    }

    /**
     * 判断当前程序是否在JAR包中运行
     *
     * @return true: JAR包运行  false: 直接执行（类文件/IDE运行）
     */
    public static boolean isRunningInJar() {
        // 获取当前类的保护域和代码源位置
        String path = OpenCVInitializer.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();
        return path.endsWith(".jar");
    }

    /**
     * 从jar包中提取dll文件到jar同目录
     *
     * @return dll文件的绝对路径，如果提取失败则返回null
     */
    private static String extractDllFromJar() {
        try {
            // 检测系统架构
            String arch = getSystemArchitecture();
            String dllResourcePath = "/" + arch + "/" + DLL_NAME;

            log.info("系统架构: {}, DLL资源路径: {}", arch, dllResourcePath);

            // 获取jar包所在目录
            String jarDir = getJarDirectory();
            File targetDllFile = new File(jarDir, DLL_NAME);

            // 如果目标dll已存在，检查是否需要更新
            if (targetDllFile.exists()) {
                log.info("DLL文件已存在: {}", targetDllFile.getAbsolutePath());
                return targetDllFile.getAbsolutePath();
            }

            // 从jar包中读取dll资源
            InputStream inputStream = OpenCVInitializer.class.getResourceAsStream(dllResourcePath);
            if (inputStream == null) {
                // 这条路径是早期 OpenCV Java 绑定的残留：资源已不打进 jar，
                // 且查找路径（/x64/...）与实际布局（native/x64/...）本就对不上，
                // 必然找不到。真正的加载走下面的 Loader.load(opencv_core/imgproc)。
                log.debug("未内嵌 OpenCV DLL（预期行为，走 JavaCPP Loader）: {}", dllResourcePath);
                return null;
            }

            // 写入到目标文件
            try (InputStream is = inputStream;
                 FileOutputStream fos = new FileOutputStream(targetDllFile)
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                log.info("成功提取DLL文件到: {}", targetDllFile.getAbsolutePath());
            }

            return targetDllFile.getAbsolutePath();
        } catch (Exception e) {
            log.error("提取DLL文件失败", e);
            return null;
        }
    }

    /**
     * 获取系统架构
     *
     * @return "x64" 或 "x86"
     */
    private static String getSystemArchitecture() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            return "x64";
        } else {
            return "x86";
        }
    }

    /**
     * 获取jar包所在目录
     *
     * @return jar包所在目录的绝对路径
     */
    private static String getJarDirectory() {
        try {
            String path = OpenCVInitializer.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            // 如果是jar文件，获取其父目录
            File file = new File(path);
            if (file.isFile()) {
                return file.getParent();
            } else {
                // 如果是目录（开发环境），返回当前工作目录
                return System.getProperty("user.dir");
            }
        } catch (Exception e) {
            log.error("获取jar目录失败", e);
            return System.getProperty("user.dir");
        }
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
