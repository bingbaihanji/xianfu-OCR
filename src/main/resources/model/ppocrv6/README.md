# PP-OCRv6 模型套件

来源：<https://huggingface.co/collections/PaddlePaddle/pp-ocrv6>
原始仓库文件（`README.md` / `inference.json` / `inference.yml`）保留在 `_source/<原仓库名>/`。

## 目录布局

```
ppocrv6/
├── tiny/
│   ├── det/det.onnx
│   ├── rec/rec.onnx
│   └── ppocrv6_dict.txt
├── small/          ← 项目默认用这套（OcrModel.PPOCR_V6）
│   ├── det/det.onnx
│   ├── rec/rec.onnx
│   └── ppocrv6_dict.txt
├── medium/
│   ├── det/det.onnx
│   ├── rec/rec.onnx
│   └── ppocrv6_dict.txt
└── _source/        ← HuggingFace 原始元数据，可安全删除
```

## 三档选型

| 档位 | det | rec | 适用 |
|---|---|---|---|
| `tiny` | 1.7 MB | 4.3 MB | 速度优先，精度最低 |
| `small` | 9.4 MB | 20.2 MB | **默认**，速度与精度均衡 |
| `medium` | 59.2 MB | 73.0 MB | 精度优先，CPU 上明显更慢 |

换档只需改 `OcrProperties.withBundledModels()` 里 v6 的三个路径前缀
（`ppocrv6/small/...` → `ppocrv6/medium/...` 或 `ppocrv6/tiny/...`）。

### 三档差在哪（实测）

同一张测试图、同一台机器，各档用自带的 det 参数：

| | 字典 | det | rec | 行数 | conf | 总耗时 | rec 耗时 |
|---|---|---|---|---|---|---|---|
| **tiny** | 6904 | 1.7 MB | 4.3 MB | 22 | 0.935 | **515 ms** | 212 ms |
| **small** | 18708 | 9.4 MB | 20.2 MB | 21 | 0.947 | 1209 ms | 827 ms |
| **medium** | 18708 | 59.2 MB | 73.0 MB | 22 | **0.972** | 3777 ms | 3073 ms |

**差在两件事上，别混为一谈：**

1. **tiny 的字符覆盖更小。** `tiny ⊂ small == medium` —— tiny 的字典是
   small/medium 的真子集，small 与 medium 字典**完全相同**。small/medium 比 tiny
   多 11804 字：CJK 基本区 9391（丌 丏 丟 並 亂 亀…）、拉丁/希腊/西里尔扩展 1342、
   **emoji 832**、CJK 扩展A 137、全角半角片假名 58、CJK 扩展B 44。反过来 tiny
   独有的一字没有。所以 tiny 不只是「精度低」，是**根本认不出**这些字。

2. **small 与 medium 只差模型容量**，覆盖一样，medium 更准但 rec 慢约 3.7 倍。

tiny 检出 22 行比 small 还多一行，但那是因为 tiny 的 `det_db_box_thresh` 是 0.4
（small/medium 是 0.45），阈值更松、框更多也更噪，不等于检测更好。

**选型**：常用汉字+英文且要快 → `tiny`；内容不确定的通用场景 → `small`；
生僻字/繁体/emoji 多或要最高精度 → `medium`（CPU 上 rec 3 秒偏慢，有 GPU 更合适）。

### 与 v4/v5 的档位关系

⚠ **两代分档命名不同，不要混为一谈**：

| | 分档 |
|---|---|
| PP-OCRv4 / v5 | **Mobile** / Server 两档 |
| PP-OCRv6 | tiny / small / medium 三档（无 server） |

本仓库的 `../ppocrv4`、`../ppocrv5` 是 **Mobile 版**（从 OnnxOCR 的
`onnxocr/models/` 拷来，那个项目的 README 明确写「默认使用 Mobile 版本模型」）。
Server 版体积较大，未包含在内。

实测体积与 CPU 耗时（同一张测试图、同一台机器）：

| | det | rec | rec 耗时 | 总耗时 |
|---|---|---|---|---|
| v4 Mobile | 4.5 MB | 10.3 MB | 1777 ms | 2388 ms |
| v5 Mobile | 4.5 MB | 15.8 MB | 1616 ms | 2258 ms |
| v6 tiny | 1.7 MB | 4.3 MB | — | — |
| **v6 small** | 9.4 MB | 20.2 MB | **960 ms** | **1382 ms** |
| v6 medium | 59.2 MB | 73.0 MB | — | — |

注意 v6 small 的 rec 比 v5 Mobile **更大却快得多** —— 是架构改进，不是靠缩小换速度。

## ⚠ 字典必须与 rec 同档配对

**每档的 rec 输出类目数不同，字典不能混用。** 从各 `inference.yml` 的
`PostProcess.character_dict` 提取，与模型输出精确对应：

| 档位 | 字典行数 | `CharDict.size()`<br/>(+blank +空格) | rec 输出 C | |
|---|---|---|---|---|
| tiny | 6904 | 6906 | 6906 | ✅ |
| small | 18708 | 18710 | 18710 | ✅ |
| medium | 18708 | 18710 | 18710 | ✅ |

`CtcLabelDecode` 有 `c == dict.size()` 的硬校验，挂错会直接抛
`IllegalArgumentException`，不会静默吐乱码。

字典第 N 项是 **U+3000 全角空格**（tiny 第 617 行、small/medium 第 1749 行）。
解析时**不能**对它做 `strip()` / `stripTrailing()` / `\s` 类的空白处理 ——
Java 的 `String.stripTrailing()` 与 Python 的 `str.strip()` 都会把它当空白吃掉，
导致解码索引整体错位。本项目已在三处踩过这个坑。

## 与 v4/v5 的差异

| | v6 | v4/v5 |
|---|---|---|
| 方向分类（cls）模型 | **无** | 有 |
| rec 输入高宽 | 3 × 48 × W（动态 W） | 同 |
| det 输出 | `[N, 1, H', W']` | 同 |
| det 后处理参数 | 见下表 | 见各目录 README |

`clsPath` 置 `null`，`--enableRotate` / `enableRotate(true)` 在 v6 下无效果。

## det 后处理参数（取自各 `inference.yml` 的 PostProcess）

| | v6 tiny | v6 small | v6 medium | v4/v5 默认 |
|---|---|---|---|---|
| `det_db_thresh` | 0.2 | 0.2 | 0.2 | 0.3 |
| `det_db_box_thresh` | 0.4 | 0.45 | 0.45 | 0.6 |
| `det_db_unclip_ratio` | 1.4 | 1.4 | 1.4 | 1.5 |
| `max_candidates` | 3000 | 3000 | 3000 | 1000 |

这些值随模型套件走（`PipelinePaths`），运行时也可用 `OcrConfig.options` 覆盖：

```java
OcrConfig.builder()
    .model(OcrModel.PPOCR_V6)
    .option("detDbBoxThresh", 0.5)   // 覆盖套件自带的 0.45
    .build();
```

预处理的 `NormalizeImage`（mean `[0.485, 0.456, 0.406]`、std `[0.229, 0.224, 0.225]`、
`order: hwc`）与 v4/v5 完全一致，`DetPreprocess` 无需改动。
`DetResizeForTest` 取默认（`limit_side_len=960`, `limit_type=max`），同样一致。
