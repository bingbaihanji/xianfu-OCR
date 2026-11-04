package com.bingbaihanji.model.ocr.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.bingbaihanji.model.ocr.config.OcrProperties;
import com.bingbaihanji.model.ocr.config.PipelinePaths;
import com.bingbaihanji.model.ocr.exception.OcrModelException;
import com.bingbaihanji.model.ocr.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

// 一个模型套件的三个 ONNX 会话与字典，一次装载，close 一并释放
public final class ModelBundle implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ModelBundle.class);

    private final OrtSession detSession;

    private final OrtSession recSession;

    private final OrtSession clsSession;

    private final CharDict dict;

    private final String detInputName;

    private final String detOutputName;

    private final String recInputName;

    private final String recOutputName;

    private final String clsInputName;

    private final String clsOutputName;

    // 从路径装配模型套件
    public ModelBundle(OrtEnvironment env, PipelinePaths paths, OcrProperties props) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(props, "props");
        OrtSession det = null;
        OrtSession rec = null;
        OrtSession cls = null;
        try {
            det = createSession(env, paths.getDetPath(), props);
            rec = createSession(env, paths.getRecPath(), props);
            cls = paths.getClsPath() == null
                    ? null
                    : createSession(env, paths.getClsPath(), props);
            this.dict = loadDict(paths.getDictPath());
            this.detSession = det;
            this.recSession = rec;
            this.clsSession = cls;
            // 节点名提取也放在这里：first() 抛异常时上面的会话必须被释放
            this.detInputName = first(det.getInputNames(), "det input");
            this.detOutputName = first(det.getOutputNames(), "det output");
            this.recInputName = first(rec.getInputNames(), "rec input");
            this.recOutputName = first(rec.getOutputNames(), "rec output");
            if (cls != null) {
                this.clsInputName = first(cls.getInputNames(), "cls input");
                this.clsOutputName = first(cls.getOutputNames(), "cls output");
            } else {
                this.clsInputName = null;
                this.clsOutputName = null;
            }
        } catch (OrtException | IOException | RuntimeException e) {
            // 半装载状态也要释放已建好的会话，避免泄漏
            closeQuietly(cls, "cls");
            closeQuietly(rec, "rec");
            closeQuietly(det, "det");
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new OcrModelException("加载模型套件失败: " + paths, e);
        }
    }

    // 关闭单个会话，失败只记日志
    private static void closeQuietly(OrtSession s, String name) {
        if (s == null) {
            return;
        }
        try {
            s.close();
            log.info("close onnx session {}", name);
        } catch (OrtException e) {
            log.warn("close onnx session {} failed", name, e);
        }
    }

    // 取集合中唯一一个节点名
    private static String first(Set<String> names, String what) {
        if (names == null || names.isEmpty()) {
            throw new OcrModelException("ONNX 模型没有" + what + "节点");
        }
        return names.iterator().next();
    }

    // 装载字典，use_space_char 固定为 true（对应 Python 默认）
    private static CharDict loadDict(String resourcePath) throws IOException {
        try (InputStream in = ResourceUtils.getResourceAsStream(resourcePath)) {
            return CharDict.load(in, true, resourcePath);
        }
    }

    /**
     * 解析资源路径并创建 ONNX 会话。
     * classpath: 先试 getFile()（IDE / 解压目录可用），失败则读字节走 ByteBuffer。
     */
    private static OrtSession createSession(OrtEnvironment env, String resourcePath, OcrProperties props)
            throws OrtException, IOException {
        try {
            return openSession(env, resourcePath, props, props.isEnableGpu());
        } catch (OrtException e) {
            if (!props.isEnableGpu()) {
                throw e;
            }
            // 对齐 Python predict_base 的 providers=[CUDA, CPU]：CUDA 不可用时回退 CPU，
            // 而不是让整条识别失败。固定只用 CPU 请设置 enableGpu(false)。
            log.warn("CUDA 不可用，回退 CPU 重试 {}: {}", resourcePath, e.getMessage());
            return openSession(env, resourcePath, props, false);
        }
    }

    // 按指定执行提供方真正建会话
    private static OrtSession openSession(OrtEnvironment env, String resourcePath, OcrProperties props, boolean useGpu)
            throws OrtException, IOException {
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            if (useGpu) {
                opts.addCUDA(props.getGpuDeviceId());
            }
            if (props.getIntraOpThreads() > 0) {
                opts.setIntraOpNumThreads(props.getIntraOpThreads());
            }
            if (props.getInterOpThreads() > 0) {
                opts.setInterOpNumThreads(props.getInterOpThreads());
            }
            String lower = resourcePath.toLowerCase(Locale.ROOT);
            if (lower.startsWith("file:")) {
                File f;
                try {
                    f = ResourceUtils.getFile(resourcePath);
                } catch (URISyntaxException e) {
                    // getFile 声明了 URISyntaxException，file: 分支实际不会触发，转成 IOException 统一上抛
                    throw new IOException("解析文件路径失败: " + resourcePath, e);
                }
                log.info("load onnx model from file {}", f.getAbsolutePath());
                return env.createSession(f.getAbsolutePath(), opts);
            }
            try {
                File f = ResourceUtils.getFile(resourcePath);
                log.info("load onnx model from classpath file {}", f.getAbsolutePath());
                return env.createSession(f.getAbsolutePath(), opts);
            } catch (Exception e) {
                log.info("classpath 不可直接取文件，改读字节装载: {}", resourcePath);
                byte[] bytes;
                try (InputStream in = ResourceUtils.getResourceAsStream(resourcePath)) {
                    bytes = in.readAllBytes();
                }
                // ORT 的 createSession(ByteBuffer) 只收直接内存缓冲区，
                // ByteBuffer.wrap 出来的是堆缓冲，会抛 "ByteBuffer is not direct"
                ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
                buf.put(bytes);
                buf.rewind();
                return env.createSession(buf, opts);
            }
        }
    }

    // 检测会话
    public OrtSession det() {
        return detSession;
    }

    // 识别会话
    public OrtSession rec() {
        return recSession;
    }

    // 方向分类会话，未配置时为 null
    public OrtSession cls() {
        return clsSession;
    }

    // 字典
    public CharDict dict() {
        return dict;
    }

    // 检测模型输入节点名
    public String detInputName() {
        return detInputName;
    }

    // 检测模型输出节点名
    public String detOutputName() {
        return detOutputName;
    }

    // 识别模型输入节点名
    public String recInputName() {
        return recInputName;
    }

    // 识别模型输出节点名
    public String recOutputName() {
        return recOutputName;
    }

    // 分类模型输入节点名，未配置时为 null
    public String clsInputName() {
        return clsInputName;
    }

    // 分类模型输出节点名，未配置时为 null
    public String clsOutputName() {
        return clsOutputName;
    }

    // 释放三个会话
    @Override
    public void close() {
        closeQuietly(detSession, "det");
        closeQuietly(recSession, "rec");
        closeQuietly(clsSession, "cls");
    }
}
