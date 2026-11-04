package com.bingbaihanji.model.ocr.config;

import com.bingbaihanji.model.ocr.enums.OcrModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

// ONNX 引擎运行属性，承载模型套件路径、GPU 设备、线程数等部署相关配置
public final class OcrProperties {

    private final Map<OcrModel, PipelinePaths> pipelines;

    private final int gpuDeviceId;

    private final int intraOpThreads;

    private final int interOpThreads;

    private final boolean enableGpu;

    // 私有构造，统一经由 Builder 创建，保证不可变
    private OcrProperties(Builder b) {
        this.pipelines = Collections.unmodifiableMap(new EnumMap<>(b.pipelines));
        this.gpuDeviceId = b.gpuDeviceId;
        this.intraOpThreads = b.intraOpThreads;
        this.interOpThreads = b.interOpThreads;
        this.enableGpu = b.enableGpu;
    }

    // 创建属性 Builder
    public static Builder builder() {
        return new Builder();
    }

    // 使用打包在 classpath 的 ppocrv4 / ppocrv5 模型创建默认属性
    public static OcrProperties withBundledModels() {
        return withBundledModels(null);
    }

    /**
     * 创建默认属性。jar 同级的 model/ 目录里若有同名文件则优先使用，
     * 否则退回 classpath 内置模型——发布物自包含，又能放外部模型覆盖而无需重新打包。
     *
     * @param jarDir jar 所在目录；null 表示只用 classpath
     */
    public static OcrProperties withBundledModels(Path jarDir) {
        PipelinePaths v5 = PipelinePaths.builder()
                .detPath(resolve(jarDir, "ppocrv5/det/det.onnx"))
                .recPath(resolve(jarDir, "ppocrv5/rec/rec.onnx"))
                .clsPath(resolve(jarDir, "ppocrv5/cls/cls.onnx"))
                .dictPath(resolve(jarDir, "ppocrv5/ppocrv5_dict.txt"))
                .build();
        PipelinePaths v4 = PipelinePaths.builder()
                .detPath(resolve(jarDir, "ppocrv4/det/det.onnx"))
                .recPath(resolve(jarDir, "ppocrv4/rec/rec.onnx"))
                .clsPath(resolve(jarDir, "ppocrv4/cls/cls.onnx"))
                // v4 的 rec.onnx 输出 C=6625，对应 ppocr_keys_v1.txt（6623 行 + blank + 空格）。
                // 不能挂 ppocrv5_dict.txt（18383 行 → 18385），否则 CTC 解码维度对不上。
                // Python 端 utils.py 的 rec_char_dict_path 默认值只对 v5 套件成立。
                .dictPath(resolve(jarDir, "ch_ppocr_server_v2.0/ppocr_keys_v1.txt"))
                .build();
        // v6 无方向分类模型，clsPath 置 null；字典每个尺寸一套，必须与该尺寸的 rec 配对。
        // det 后处理参数取自该套件自带的 inference.yml，与 v4/v5 的默认值不同。
        PipelinePaths v6 = PipelinePaths.builder()
                .detPath(resolve(jarDir, "ppocrv6/small/det/det.onnx"))
                .recPath(resolve(jarDir, "ppocrv6/small/rec/rec.onnx"))
                .clsPath(null)
                .dictPath(resolve(jarDir, "ppocrv6/small/ppocrv6_dict.txt"))
                .detThresh(0.2)
                .detBoxThresh(0.45)
                .detUnclipRatio(1.4)
                .maxCandidates(3000)
                .build();
        // v6 tiny/medium 档：与 small 同族；det 后处理参数取自各档自带 inference.yml，
        // tiny 的 box_thresh 是 0.4（small/medium 是 0.45），字典必须与 rec 同档配对。
        PipelinePaths v6Tiny = PipelinePaths.builder()
                .detPath(resolve(jarDir, "ppocrv6/tiny/det/det.onnx"))
                .recPath(resolve(jarDir, "ppocrv6/tiny/rec/rec.onnx"))
                .clsPath(null)
                .dictPath(resolve(jarDir, "ppocrv6/tiny/ppocrv6_dict.txt"))
                .detThresh(0.2)
                .detBoxThresh(0.4)
                .detUnclipRatio(1.4)
                .maxCandidates(3000)
                .build();
        PipelinePaths v6Medium = PipelinePaths.builder()
                .detPath(resolve(jarDir, "ppocrv6/medium/det/det.onnx"))
                .recPath(resolve(jarDir, "ppocrv6/medium/rec/rec.onnx"))
                .clsPath(null)
                .dictPath(resolve(jarDir, "ppocrv6/medium/ppocrv6_dict.txt"))
                .detThresh(0.2)
                .detBoxThresh(0.45)
                .detUnclipRatio(1.4)
                .maxCandidates(3000)
                .build();
        return builder()
                // 打包的是 onnxruntime CPU 版，CUDA 必然不可用；
                // 置 true 只会让每次装载都先失败一次再回退，平白三条 WARN。
                // 将来换成 onnxruntime_gpu 时改回 true 即可。
                .enableGpu(false)
                .pipeline(OcrModel.DEFAULT, v5)
                .pipeline(OcrModel.PPOCR_V5, v5)
                .pipeline(OcrModel.PPOCR_V4, v4)
                .pipeline(OcrModel.PPOCR_V6, v6)
                .pipeline(OcrModel.PPOCR_V6_TINY, v6Tiny)
                .pipeline(OcrModel.PPOCR_V6_MEDIUM, v6Medium)
                .build();
    }

    // jar 同级 model/ 下存在该文件则用绝对 file: 路径，否则用 classpath:
    private static String resolve(Path jarDir, String relative) {
        if (jarDir != null) {
            Path p = jarDir.resolve("model").resolve(relative);
            if (Files.isRegularFile(p)) {
                return "file:" + p.toAbsolutePath();
            }
        }
        return "classpath:model/" + relative;
    }

    // 获取各模型套件对应的资源路径
    public Map<OcrModel, PipelinePaths> getPipelines() {
        return pipelines;
    }

    // 获取 GPU 设备号
    public int getGpuDeviceId() {
        return gpuDeviceId;
    }

    // 获取单图内并行线程数（0 表示由 ORT 默认决定）
    public int getIntraOpThreads() {
        return intraOpThreads;
    }

    // 获取图间并行线程数（0 表示由 ORT 默认决定）
    public int getInterOpThreads() {
        return interOpThreads;
    }

    // 是否启用 GPU 推理
    public boolean isEnableGpu() {
        return enableGpu;
    }

    // 属性 Builder
    public static final class Builder {

        private final Map<OcrModel, PipelinePaths> pipelines = new EnumMap<>(OcrModel.class);

        private int gpuDeviceId = 0;

        private int intraOpThreads = 0;

        private int interOpThreads = 0;

        private boolean enableGpu = true;

        // 注册指定模型套件对应的资源路径
        public Builder pipeline(OcrModel model, PipelinePaths paths) {
            this.pipelines.put(model, java.util.Objects.requireNonNull(paths, "paths"));
            return this;
        }

        // 设置 GPU 设备号
        public Builder gpuDeviceId(int gpuDeviceId) {
            this.gpuDeviceId = gpuDeviceId;
            return this;
        }

        // 设置单图内并行线程数
        public Builder intraOpThreads(int intraOpThreads) {
            this.intraOpThreads = intraOpThreads;
            return this;
        }

        // 设置图间并行线程数
        public Builder interOpThreads(int interOpThreads) {
            this.interOpThreads = interOpThreads;
            return this;
        }

        // 设置是否启用 GPU
        public Builder enableGpu(boolean enableGpu) {
            this.enableGpu = enableGpu;
            return this;
        }

        // 构建不可变 OcrProperties 实例
        public OcrProperties build() {
            return new OcrProperties(this);
        }
    }
}
