package com.bingbaihanji.model.ocr.config;

import java.util.Objects;

// 一个模型套件的四个资源路径与该套件自带的检测后处理参数，
// 路径统一走 classpath: / file: 前缀，由 ResourceUtils 解析
public final class PipelinePaths {

    // 各模型自带的 DB 后处理默认值（v4/v5 与 v6 不同，故随套件携带）
    public static final double DEFAULT_DET_THRESH = 0.3;
    public static final double DEFAULT_DET_BOX_THRESH = 0.6;
    public static final double DEFAULT_DET_UNCLIP_RATIO = 1.5;
    public static final int DEFAULT_MAX_CANDIDATES = 1000;

    private final String detPath;

    private final String recPath;

    private final String clsPath;

    private final String dictPath;

    private final double detThresh;

    private final double detBoxThresh;

    private final double detUnclipRatio;

    private final int maxCandidates;

    // 私有构造，统一经由 Builder 创建，保证不可变
    private PipelinePaths(Builder b) {
        this.detPath = Objects.requireNonNull(b.detPath, "detPath");
        this.recPath = Objects.requireNonNull(b.recPath, "recPath");
        this.clsPath = b.clsPath;
        this.dictPath = Objects.requireNonNull(b.dictPath, "dictPath");
        this.detThresh = b.detThresh;
        this.detBoxThresh = b.detBoxThresh;
        this.detUnclipRatio = b.detUnclipRatio;
        this.maxCandidates = b.maxCandidates;
    }

    // 创建 Builder
    public static Builder builder() {
        return new Builder();
    }

    // 获取检测模型路径
    public String getDetPath() {
        return detPath;
    }

    // 获取识别模型路径
    public String getRecPath() {
        return recPath;
    }

    // 获取方向分类模型路径，null 表示不启用
    public String getClsPath() {
        return clsPath;
    }

    // 获取字典路径
    public String getDictPath() {
        return dictPath;
    }

    // 概率图二值化阈值（对应 Python det_db_thresh）
    public double getDetThresh() {
        return detThresh;
    }

    // 检出框置信度阈值（对应 Python det_db_box_thresh）
    public double getDetBoxThresh() {
        return detBoxThresh;
    }

    // 检出框外扩比例（对应 Python det_db_unclip_ratio）
    public double getDetUnclipRatio() {
        return detUnclipRatio;
    }

    // 最多候选框数（对应 Python max_candidates）
    public int getMaxCandidates() {
        return maxCandidates;
    }

    // 返回路径与后处理参数摘要
    @Override
    public String toString() {
        return "PipelinePaths{det=" + detPath + ", rec=" + recPath
                + ", cls=" + clsPath + ", dict=" + dictPath
                + ", detThresh=" + detThresh + ", detBoxThresh=" + detBoxThresh
                + ", detUnclipRatio=" + detUnclipRatio
                + ", maxCandidates=" + maxCandidates + "}";
    }

    // 路径与后处理参数 Builder
    public static final class Builder {

        private String detPath;

        private String recPath;

        private String clsPath;

        private String dictPath;

        private double detThresh = DEFAULT_DET_THRESH;

        private double detBoxThresh = DEFAULT_DET_BOX_THRESH;

        private double detUnclipRatio = DEFAULT_DET_UNCLIP_RATIO;

        private int maxCandidates = DEFAULT_MAX_CANDIDATES;

        // 设置检测模型路径
        public Builder detPath(String detPath) {
            this.detPath = detPath;
            return this;
        }

        // 设置识别模型路径
        public Builder recPath(String recPath) {
            this.recPath = recPath;
            return this;
        }

        // 设置方向分类模型路径（传 null 表示不启用方向分类）
        public Builder clsPath(String clsPath) {
            this.clsPath = clsPath;
            return this;
        }

        // 设置字典路径
        public Builder dictPath(String dictPath) {
            this.dictPath = dictPath;
            return this;
        }

        // 设置概率图二值化阈值
        public Builder detThresh(double detThresh) {
            this.detThresh = detThresh;
            return this;
        }

        // 设置检出框置信度阈值
        public Builder detBoxThresh(double detBoxThresh) {
            this.detBoxThresh = detBoxThresh;
            return this;
        }

        // 设置检出框外扩比例
        public Builder detUnclipRatio(double detUnclipRatio) {
            this.detUnclipRatio = detUnclipRatio;
            return this;
        }

        // 设置最多候选框数
        public Builder maxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
            return this;
        }

        // 构建不可变 PipelinePaths 实例
        public PipelinePaths build() {
            return new PipelinePaths(this);
        }
    }
}
