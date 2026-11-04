package com.bingbaihanji.model.ocr.onnx;

// 角度分类后处理：argmax → (label, score)。label_list 固定 ["0", "180"]
public final class ClsPostProcess {

    // 对应 Python predict_cls 的 label_list
    private static final String[] LABELS = {"0", "180"};

    // 私有构造，禁止实例化
    private ClsPostProcess() {
    }

    /**
     * 单条预测解码。
     * <p>
     * 约定：{@code numClasses} 必须等于 {@code LABELS.length}（角度分类模型固定两类），
     * {@code probs[offset .. offset+numClasses)} 必须在数组范围内。
     * score 是 argmax 位置的原始取值，仅当模型输出已过 softmax 时才落在 0.0~1.0。
     *
     * @param probs      摊平的 logits 或概率
     * @param offset     本条预测在 probs 中的起始下标
     * @param numClasses 类别数，必须等于 2
     */
    public static Result decode(float[] probs, int offset, int numClasses) {
        if (numClasses != LABELS.length) {
            throw new IllegalArgumentException(
                    "numClasses 必须为 " + LABELS.length + ", 实际 " + numClasses);
        }
        if (offset < 0 || offset + numClasses > probs.length) {
            throw new IllegalArgumentException(
                    "offset=" + offset + " 越界, probs.length=" + probs.length
                            + ", numClasses=" + numClasses);
        }
        int best = 0;
        float bestProb = probs[offset];
        for (int k = 1; k < numClasses; k++) {
            float p = probs[offset + k];
            if (p > bestProb) {
                bestProb = p;
                best = k;
            }
        }
        return new Result(LABELS[best], bestProb);
    }

    // 分类结果
    public record Result(String label, double score) {
    }
}
