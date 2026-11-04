package com.bingbaihanji.model.ocr.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 资源工具类，提供加载 classpath、文件系统和 URL 资源的方法
 */
public class ResourceUtils {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String FILE_PREFIX = "file:";

    // 正则表达式，用于提取 "classpath:" 后的路径
    private static final Pattern CLASSPATH_PATTERN = Pattern.compile("^classpath:(.*)", Pattern.CASE_INSENSITIVE);    // 正则表达式，用于提取 "file:" 后的路径

    private static final Pattern FILE_PATTERN = Pattern.compile("^file:(/*)(.*)", Pattern.CASE_INSENSITIVE);

    /**
     * 根据路径获取资源的 InputStream
     *
     * @param resourcePath 资源路径 (支持 classpath:, file:, 以及 URL)
     * @return InputStream 对象
     * @throws IOException 资源不存在或无法读取时抛出异常
     */
    public static InputStream getResourceAsStream(String resourcePath) throws IOException {
        Objects.requireNonNull(resourcePath, "Resource path 不能为 null");

        if (resourcePath.toLowerCase().startsWith(CLASSPATH_PREFIX)) {
            return getClasspathResourceAsStream(getClasspathResourcePath(resourcePath));
        } else if (resourcePath.toLowerCase().startsWith(FILE_PREFIX)) {
            return getFileResourceAsStream(getFileResourcePath(resourcePath));
        } else {
            throw new IllegalArgumentException("资源路径必须以 'classpath：' 或 'file '开头:'");
        }
    }

    /**
     * 使用正则表达式提取 classpath 资源路径
     *
     * @param resourcePath 资源路径
     * @return 资源路径
     */
    private static String getClasspathResourcePath(String resourcePath) {
        // 必须在原串上匹配：CLASSPATH_PATTERN 已带 CASE_INSENSITIVE 处理前缀大小写，
        // 若对整串 toLowerCase() 再取 group(1)，路径里的大写字母会被一起改掉，
        // 在 jar / Linux 上找不到资源（Windows 文件系统大小写不敏感，问题会被掩盖）
        Matcher matcher = CLASSPATH_PATTERN.matcher(resourcePath);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("无效的 Classpath 资源路径: " + resourcePath);
    }

    /**
     * 使用正则表达式提取 file 资源路径
     *
     * @param resourcePath 资源路径
     * @return 资源路径
     */
    private static String getFileResourcePath(String resourcePath) {
        Matcher matcher = FILE_PATTERN.matcher(resourcePath);
        if (matcher.matches()) {
            String path = matcher.group(2);
            // 处理Windows路径
            if (path.startsWith("/") && path.length() > 3 && path.charAt(2) == ':') {
                return path.substring(1); // 移除开头的斜杠
            }
            return path;
        }
        throw new IllegalArgumentException("文件资源路径无效: " + resourcePath);
    }

    /**
     * 判断资源是否存在
     *
     * @param resourcePath 资源路径
     * @return true 表示资源存在，否则返回 false
     */
    public static boolean resourceExists(String resourcePath) {
        try (InputStream ignored = getResourceAsStream(resourcePath)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取资源的 File 对象
     *
     * @param resourcePath 资源路径 (支持 classpath:, file:)
     * @return File 对象
     * @throws IOException 如果文件不存在或无法访问
     */
    public static File getFile(String resourcePath) throws IOException, URISyntaxException {
        Objects.requireNonNull(resourcePath, "Resource path 不能为 null");
        if (resourcePath.toLowerCase().startsWith(CLASSPATH_PREFIX)) {
            URL url = getClasspathResourceURL(getClasspathResourcePath(resourcePath));
            return new File(Objects.requireNonNull(url).toURI());
        } else if (resourcePath.toLowerCase().startsWith(FILE_PREFIX)) {
            return new File(getFileResourcePath(resourcePath));
        } else {
            throw new IllegalArgumentException("不支持的资源类型: " + resourcePath);
        }
    }

    /**
     * 获取资源的 URL 对象
     *
     * @param resourceLocation 资源路径 (支持 classpath:, file:, 以及 URL)
     * @return URL 对象
     * @throws IOException 如果资源不存在或格式错误
     */
    public static URL getURL(String resourceLocation) throws IOException {
        Objects.requireNonNull(resourceLocation, "资源位置不能为 null");
        if (resourceLocation.toLowerCase().startsWith(CLASSPATH_PREFIX)) {
            return getClasspathResourceURL(getClasspathResourcePath(resourceLocation));
        } else if (resourceLocation.toLowerCase().startsWith(FILE_PREFIX)) {
            return new File(getFileResourcePath(resourceLocation)).toURI().toURL();
        } else {
            return new URL(resourceLocation);
        }
    }

    /**
     * 从 classpath 加载资源
     *
     * @param classpath 资源在 classpath 中的路径
     * @return InputStream
     * @throws IOException 如果资源不存在
     */
    private static InputStream getClasspathResourceAsStream(String classpath) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(classpath);
        if (inputStream == null) {
            throw new FileNotFoundException("未找到 Classpath 资源: " + classpath);
        }
        return inputStream;
    }

    /**
     * 获取 classpath 资源的 URL
     *
     * @param classpath 资源在 classpath 中的路径
     * @return URL 对象
     * @throws IOException 如果资源不存在
     */
    private static URL getClasspathResourceURL(String classpath) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource(classpath);
        if (url == null) {
            throw new FileNotFoundException("未找到 Classpath 资源: " + classpath);
        }
        return url;
    }

    /**
     * 从文件系统加载资源
     *
     * @param filePath 文件路径
     * @return InputStream
     * @throws IOException 如果文件不存在或无法读取
     */
    private static InputStream getFileResourceAsStream(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new FileNotFoundException("文件未找到或不可读: " + filePath);
        }
        return Files.newInputStream(path);
    }

    /**
     * 从 URL 加载资源
     *
     * @param urlString 资源 URL
     * @return InputStream
     * @throws IOException 如果 URL 不可访问
     */
    public static InputStream getUrlResourceAsStream(String urlString) throws IOException {
        try {
            URL url = new URL(urlString);
            return url.openStream();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL 无效: " + urlString, e);
        }
    }
}
