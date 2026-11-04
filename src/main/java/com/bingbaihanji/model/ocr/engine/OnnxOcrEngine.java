package com.bingbaihanji.model.ocr.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.config.OcrProperties;
import com.bingbaihanji.model.ocr.config.PipelinePaths;
import com.bingbaihanji.model.ocr.enums.OcrModel;
import com.bingbaihanji.model.ocr.exception.OcrModelException;
import com.bingbaihanji.model.ocr.onnx.ClsPostProcess;
import com.bingbaihanji.model.ocr.onnx.ClsPreprocess;
import com.bingbaihanji.model.ocr.onnx.CtcLabelDecode;
import com.bingbaihanji.model.ocr.onnx.DbPostProcess;
import com.bingbaihanji.model.ocr.onnx.DetPreprocess;
import com.bingbaihanji.model.ocr.onnx.ModelBundle;
import com.bingbaihanji.model.ocr.onnx.RecPreprocess;
import com.bingbaihanji.model.ocr.onnx.TensorUtils;
import com.bingbaihanji.model.ocr.result.OcrBox;
import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.result.RecText;
import com.bingbaihanji.model.ocr.util.CVPlatformImageConvertUtils;
import com.bingbaihanji.model.ocr.util.GeometryUtils;
import com.bingbaihanji.model.ocr.util.OpenCVInitializer;
import com.bingbaihanji.model.ocr.visual.OcrVisualizer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ONNX Runtime OCR 引擎实现，负责会话管理、模型懒加载与推理调度
public class OnnxOcrEngine implements OcrEngine {

    // 日志器
    private static final Logger log = LoggerFactory.getLogger(OnnxOcrEngine.class);

    // DB 后处理阈值随模型套件携带（见 PipelinePaths），不再在此重复定义

    // 方向分类默认置信度阈值，可用 options 的 clsThresh 覆盖
    private static final double DEFAULT_CLS_THRESH = 0.9;

    // 识别批内宽高比下限，对应 Python rec_image_shape[1]/rec_image_shape[2]
    private static final double BASE_MAX_WH_RATIO = RecPreprocess.IMG_W / (double) RecPreprocess.IMG_H;

    // ONNX Runtime 全局环境，进程级单例
    private final OrtEnvironment env;

    // 运行属性（模型套件路径、GPU、线程数等）
    private final OcrProperties props;

    // 按模型缓存的模型套件，避免重复加载
    private final Map<OcrModel, ModelBundle> bundles = new ConcurrentHashMap<>();

    // 关闭标记，防止关闭后继续推理
    private volatile boolean closed = false;

    // 使用给定属性构造引擎，并初始化 ONNX 环境与 OpenCV
    public OnnxOcrEngine(OcrProperties props) {
        this.props = props;
        this.env = OrtEnvironment.getEnvironment();
        OpenCVInitializer.initialize();
    }

    // 从 options 取 Number 型参数，缺失或类型不符返回默认值
    /**
     * 构造检测后处理。取值优先级：
     * OcrConfig.options（调用方显式指定） &gt; PipelinePaths（模型套件自带，见各套件 inference.yml）
     * &gt; PipelinePaths 的默认值。v4/v5 与 v6 的 DB 参数不同，故不能写死。
     */
    private DbPostProcess newDbPostProcess(OcrConfig config) {
        PipelinePaths paths = props.getPipelines().get(config.getModel());
        double defThresh = paths != null ? paths.getDetThresh() : PipelinePaths.DEFAULT_DET_THRESH;
        double defBox = paths != null ? paths.getDetBoxThresh() : PipelinePaths.DEFAULT_DET_BOX_THRESH;
        double defUnclip = paths != null ? paths.getDetUnclipRatio() : PipelinePaths.DEFAULT_DET_UNCLIP_RATIO;
        int defMax = paths != null ? paths.getMaxCandidates() : PipelinePaths.DEFAULT_MAX_CANDIDATES;
        return new DbPostProcess(
                optionDouble(config, "detDbThresh", defThresh),
                optionDouble(config, "detDbBoxThresh", defBox),
                optionDouble(config, "detDbUnclipRatio", defUnclip),
                optionBool(config, "useDilation", false),
                defMax);
    }

    private static double optionDouble(OcrConfig config, String key, double def) {
        Object v = config.getOptions().get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    // 从 options 取 Boolean 型参数，缺失或类型不符返回默认值
    private static boolean optionBool(OcrConfig config, String key, boolean def) {
        Object v = config.getOptions().get(key);
        return v instanceof Boolean b ? b : def;
    }

    // 执行完整识别：检测 → 裁剪 → 方向分类 → 识别 → 按 minConfidence 过滤组装
    @Override
    public OcrResult recognize(byte[] image, OcrConfig config) {
        ensureOpen();
        long t0 = System.nanoTime();
        Map<String, Long> stages = new HashMap<>();
        Mat src = CVPlatformImageConvertUtils.decodeToBgrMat(image);
        // 裁剪出的文字块 Mat 声明在 try 之外，保证 finally 在成功与异常路径都能逐个释放
        List<Mat> crops = new ArrayList<>();
        try {
            ModelBundle bundle = bundleOf(config.getModel());
            DbPostProcess db = newDbPostProcess(config);

            long t = System.nanoTime();
            List<OcrBox> boxes = detectInternal(src, bundle, db, config);
            stages.put("detect", (System.nanoTime() - t) / 1_000_000);

            if (boxes.isEmpty()) {
                return buildResult(bundle, config, List.of(), 0.0,
                        (System.nanoTime() - t0) / 1_000_000, stages);
            }

            t = System.nanoTime();
            for (OcrBox box : boxes) {
                crops.add(GeometryUtils.getRotateCropImage(src, box.getPoints()));
            }
            stages.put("crop", (System.nanoTime() - t) / 1_000_000);

            t = System.nanoTime();
            if (config.isEnableRotate() && bundle.cls() != null) {
                rotateCrops(crops, bundle, config);
            }
            stages.put("cls", (System.nanoTime() - t) / 1_000_000);

            t = System.nanoTime();
            List<RecText> recs = recognizeInternal(crops, bundle);
            stages.put("rec", (System.nanoTime() - t) / 1_000_000);

            // minConfidence 过滤并组装识别行
            List<OcrLine> lines = new ArrayList<>();
            double confSum = 0.0;
            for (int i = 0; i < recs.size(); i++) {
                RecText r = recs.get(i);
                if (r.getConfidence() >= config.getMinConfidence()) {
                    lines.add(new OcrLine(r.getText(), r.getConfidence(), boxes.get(i).getPoints()));
                    confSum += r.getConfidence();
                }
            }
            double conf = lines.isEmpty() ? 0.0 : confSum / lines.size();
            return buildResult(bundle, config, lines, conf,
                    (System.nanoTime() - t0) / 1_000_000, stages);
        } finally {
            // 成功与异常路径都要释放裁剪块与源图
            for (Mat m : crops) {
                m.release();
            }
            src.release();
        }
    }

    // 只做文本检测，结果已按 sortedBoxes 规则排序（上→下、左→右）
    @Override
    public List<OcrBox> detect(byte[] image, OcrConfig config) {
        ensureOpen();
        Mat src = CVPlatformImageConvertUtils.decodeToBgrMat(image);
        try {
            ModelBundle bundle = bundleOf(config.getModel());
            DbPostProcess db = newDbPostProcess(config);
            return detectInternal(src, bundle, db, config);
        } finally {
            src.release();
        }
    }

    // 对已裁剪文字块做方向分类（若启用）与识别，不检测、不做 minConfidence 过滤
    @Override
    public List<RecText> recognizeCrops(List<byte[]> crops, OcrConfig config) {
        ensureOpen();
        ModelBundle bundle = bundleOf(config.getModel());
        // rotateCrops 可能替换表项，finally 释放列表当前持有的 Mat，被替换的旧 Mat 在 rotateCrops 内释放
        List<Mat> mats = new ArrayList<>(crops.size());
        try {
            for (byte[] crop : crops) {
                mats.add(CVPlatformImageConvertUtils.decodeToBgrMat(crop));
            }
            if (config.isEnableRotate() && bundle.cls() != null) {
                rotateCrops(mats, bundle, config);
            }
            return recognizeInternal(mats, bundle);
        } finally {
            for (Mat m : mats) {
                m.release();
            }
        }
    }

    // 把识别结果画回原图，并在右侧拼接文字面板
    @Override
    public BufferedImage visualize(BufferedImage image, OcrResult result) {
        return OcrVisualizer.draw(image, result);
    }

    // 检测内部流程：预处理 → 推理 → DB 后处理 → 按 sortedBoxes 排序重建检测框
    private List<OcrBox> detectInternal(Mat src, ModelBundle bundle, DbPostProcess db, OcrConfig config) {
        int limitSideLen = config.getMaxSideLen() > 0 ? config.getMaxSideLen() : 960;
        DetPreprocess.Out pre = DetPreprocess.run(src, limitSideLen, true);
        float[] maps = runDet(bundle, pre);
        int predH = pre.dstH();
        int predW = pre.dstW();
        if (maps.length != predH * predW) {
            throw new OcrModelException(
                    "检测输出长度 " + maps.length + " 与预期 " + predH * predW + " 不符");
        }
        List<OcrBox> boxes = db.call(maps, predH, predW, pre.srcH(), pre.srcW());
        // 排序：保证 detect() 与 recognize() 的框顺序一致
        List<float[]> sorted = GeometryUtils.sortedBoxes(
                boxes.stream().map(OcrBox::getPoints).toList());
        List<OcrBox> out = new ArrayList<>(sorted.size());
        for (float[] pts : sorted) {
            out.add(new OcrBox(pts));
        }
        return out;
    }

    // 批量方向分类：label 含 "180" 且 score > clsThresh 时就地旋转 180 度
    private void rotateCrops(List<Mat> crops, ModelBundle bundle, OcrConfig config) {
        int n = crops.size();
        if (n == 0) {
            return;
        }
        double clsThresh = optionDouble(config, "clsThresh", DEFAULT_CLS_THRESH);
        int batch = 6;
        for (int beg = 0; beg < n; beg += batch) {
            int end = Math.min(n, beg + batch);
            int cur = end - beg;
            int plane = ClsPreprocess.IMG_C * ClsPreprocess.IMG_H * ClsPreprocess.IMG_W;
            float[] data = new float[cur * plane];
            for (int i = beg; i < end; i++) {
                float[] one = ClsPreprocess.run(crops.get(i));
                System.arraycopy(one, 0, data, (i - beg) * plane, plane);
            }
            OnnxTensor tensor = TensorUtils.toTensor(env, data,
                    new long[]{cur, ClsPreprocess.IMG_C, ClsPreprocess.IMG_H, ClsPreprocess.IMG_W});
            float[] probs;
            try (OrtSession.Result r = bundle.cls().run(Map.of(bundle.clsInputName(), tensor))) {
                probs = TensorUtils.readFloats((OnnxTensor) r.get(0));
            } catch (OrtException e) {
                throw new OcrModelException("方向分类推理失败", e);
            } finally {
                tensor.close();
            }
            for (int i = 0; i < cur; i++) {
                ClsPostProcess.Result res = ClsPostProcess.decode(probs, i * 2, 2);
                if (res.label().contains("180") && res.score() > clsThresh) {
                    Mat rotated = new Mat();
                    org.bytedeco.opencv.global.opencv_core.rotate(
                            crops.get(beg + i), rotated,
                            org.bytedeco.opencv.global.opencv_core.ROTATE_180);
                    // 释放被替换的旧 Mat，旋转结果由列表持有
                    crops.get(beg + i).release();
                    crops.set(beg + i, rotated);
                }
            }
        }
    }

    // 识别内部流程：按宽高比排序 → 分批推理 → CTC 解码 → 还原输入顺序
    private List<RecText> recognizeInternal(List<Mat> mats, ModelBundle bundle) {
        int n = mats.size();
        if (n == 0) {
            return List.of();
        }
        // Python 先按宽高比升序排序，相近形状同批提速
        Integer[] indices = new Integer[n];
        double[] ratios = new double[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
            Mat m = mats.get(i);
            ratios[i] = m.cols() / (double) m.rows();
        }
        Arrays.sort(indices, Comparator.comparingDouble(i -> ratios[i]));

        RecText[] result = new RecText[n];
        int batch = 6;
        for (int beg = 0; beg < n; beg += batch) {
            int end = Math.min(n, beg + batch);
            int cur = end - beg;
            double maxWhRatio = BASE_MAX_WH_RATIO;
            for (int i = beg; i < end; i++) {
                maxWhRatio = Math.max(maxWhRatio, ratios[indices[i]]);
            }
            int imgW = (int) (RecPreprocess.IMG_H * maxWhRatio);
            int plane = RecPreprocess.IMG_C * RecPreprocess.IMG_H * imgW;
            float[] data = new float[cur * plane];
            for (int i = beg; i < end; i++) {
                float[] one = RecPreprocess.run(mats.get(indices[i]), maxWhRatio);
                System.arraycopy(one, 0, data, (i - beg) * plane, plane);
            }
            OnnxTensor tensor = TensorUtils.toTensor(env, data,
                    new long[]{cur, RecPreprocess.IMG_C, RecPreprocess.IMG_H, imgW});
            float[] preds;
            try (OrtSession.Result r = bundle.rec().run(Map.of(bundle.recInputName(), tensor))) {
                preds = TensorUtils.readFloats((OnnxTensor) r.get(0));
            } catch (OrtException e) {
                throw new OcrModelException("识别推理失败", e);
            } finally {
                tensor.close();
            }
            // 输出形状 [cur, T, C]
            int c = bundle.dict().size();
            int t = preds.length / (cur * c);
            List<RecText> decoded = CtcLabelDecode.decode(preds, cur, t, c, bundle.dict());
            for (int i = 0; i < cur; i++) {
                result[indices[beg + i]] = decoded.get(i);
            }
        }
        // 按输入顺序输出，未覆盖的空槽兜底为空文本
        List<RecText> out = new ArrayList<>(n);
        for (RecText r : result) {
            out.add(r == null ? new RecText("", 0.0) : r);
        }
        return out;
    }

    // 检测模型推理，返回摊平的概率图
    private float[] runDet(ModelBundle bundle, DetPreprocess.Out pre) {
        OnnxTensor tensor = TensorUtils.toTensor(env, pre.chw(),
                new long[]{1, 3, pre.dstH(), pre.dstW()});
        try (OrtSession.Result r = bundle.det().run(Map.of(bundle.detInputName(), tensor))) {
            return TensorUtils.readFloats((OnnxTensor) r.get(0));
        } catch (OrtException e) {
            throw new OcrModelException("检测推理失败", e);
        } finally {
            tensor.close();
        }
    }

    // 组装 OcrResult，附带模型信息与阶段耗时
    private OcrResult buildResult(ModelBundle bundle, OcrConfig config, List<OcrLine> lines,
                                  double conf, long elapsed, Map<String, Long> stages) {
        return OcrResult.builder()
                .lines(lines)
                .confidence(conf)
                .model(config.getModel())
                .modelVersion(config.getModel().getCode())
                .elapsedMillis(elapsed)
                .stageTimings(stages)
                .build();
    }

    // 获取或懒加载指定模型套件，未配置时抛异常
    private ModelBundle bundleOf(OcrModel model) {
        return bundles.computeIfAbsent(model, m -> {
            PipelinePaths paths = props.getPipelines().get(m);
            if (paths == null) {
                throw new OcrModelException("未配置模型套件: " + m);
            }
            return new ModelBundle(env, paths, props);
        });
    }

    // 引擎已关闭时抛出异常
    private void ensureOpen() {
        if (closed) {
            throw new OcrModelException("引擎已关闭");
        }
    }

    // 关闭引擎，释放所有已缓存模型套件
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        bundles.forEach((m, b) -> {
            b.close();
            log.info("close model bundle {}", m);
        });
        bundles.clear();
    }
}
