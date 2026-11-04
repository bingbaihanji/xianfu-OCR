package com.bingbaihanji.model.ocr.onnx;

import com.bingbaihanji.model.ocr.result.RecText;

import java.util.ArrayList;
import java.util.List;

// CTC 解码：argmax → 折叠相邻重复 → 去 blank(0) → 查字典 → 均值置信度
public final class CtcLabelDecode {

    // 私有构造，禁止实例化
    private CtcLabelDecode() {
    }

    /**
     * 解码一批预测。
     * <p>
     * 约定（不满足直接抛异常，避免静默出错）：{@code preds.length == n * t * c}
     * 且 {@code c == dict.size()}。置信度是各保留时间步 argmax 值的均值，
     * 仅当模型输出已过 softmax 时才落在 0.0~1.0。
     *
     * @param preds 行优先 [N, T, C] 摊平的浮点数
     * @param n     batch size
     * @param t     时间步长度
     * @param c     类别数，必须等于 dict.size()
     * @param dict  字典
     */
    public static List<RecText> decode(float[] preds, int n, int t, int c, CharDict dict) {
        if (n <= 0 || t <= 0 || c <= 0) {
            throw new IllegalArgumentException(
                    "n/t/c 必须为正数, n=" + n + ", t=" + t + ", c=" + c);
        }
        long expected = (long) n * t * c;
        if (preds.length != expected) {
            throw new IllegalArgumentException(
                    "preds.length=" + preds.length + " 与 n*t*c=" + expected + " 不符");
        }
        if (c != dict.size()) {
            throw new IllegalArgumentException(
                    "c=" + c + " 与字典大小 " + dict.size() + " 不符");
        }
        List<RecText> out = new ArrayList<>(n);
        for (int b = 0; b < n; b++) {
            int base = b * t * c;
            StringBuilder text = new StringBuilder();
            double confSum = 0.0;
            int confCount = 0;
            int prevIdx = -1;
            for (int step = 0; step < t; step++) {
                int offset = base + step * c;
                int best = 0;
                float bestProb = preds[offset];
                for (int k = 1; k < c; k++) {
                    float p = preds[offset + k];
                    if (p > bestProb) {
                        bestProb = p;
                        best = k;
                    }
                }
                // 折叠相邻重复
                if (best == prevIdx) {
                    continue;
                }
                prevIdx = best;
                // 去 CTC blank（index 0）
                if (best == 0) {
                    continue;
                }
                String ch = dict.get(best);
                if (ch == null) {
                    continue;
                }
                text.append(ch);
                confSum += bestProb;
                confCount++;
            }
            String s = text.toString();
            if (dict.isReverse()) {
                s = predReverse(s);
            }
            double score = confCount == 0 ? 0.0 : confSum / confCount;
            out.add(new RecText(s, score));
        }
        return out;
    }

    /**
     * 阿拉伯语后处理：整体反转，但连续的 [a-zA-Z0-9 :*./%+-] 片段内部保持原序。
     * 对应 Python BaseRecLabelDecode.pred_reverse。
     */
    private static String predReverse(String pred) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < pred.length(); i++) {
            char ch = pred.charAt(i);
            boolean isAscii = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == ' ' || ch == ':' || ch == '*'
                    || ch == '.' || ch == '/' || ch == '%' || ch == '+' || ch == '-';
            if (!isAscii) {
                if (!cur.isEmpty()) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                }
                parts.add(String.valueOf(ch));
            } else {
                cur.append(ch);
            }
        }
        if (!cur.isEmpty()) {
            parts.add(cur.toString());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = parts.size() - 1; i >= 0; i--) {
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
