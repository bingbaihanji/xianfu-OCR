# PP-OCRv5 模型套件

项目 `OcrModel.DEFAULT` / `OcrModel.PPOCR_V5` 指向这里。

> **这是 Mobile 版，不是 Server 版。** 来自 OnnxOCR 项目的 `onnxocr/models/ppocrv5`，
> 该 README 明确写「默认使用 Mobile 版本模型，使用 PP-OCRv5_Server-ONNX 模型效果更佳」，
> Server 版需另行下载（体积较大）后替换本目录下的 `det/det.onnx` 与 `rec/rec.onnx`。
> 分档命名上 v4/v5 只有 Mobile / Server 两档，与 v6 的 tiny/small/medium 三档不是一回事。

```
ppocrv5/
├── det/det.onnx          4.7 MB
├── rec/rec.onnx         16.5 MB
├── cls/cls.onnx          0.6 MB   方向分类，enableRotate(true) 时使用
└── ppocrv5_dict.txt     18383 行
```

## 字典与 rec 输出维度

| | |
|---|---|
| `ppocrv5_dict.txt` 行数 | 18383 |
| `CharDict.size()` = 行数 + blank + 空格 | **18385** |
| `rec.onnx` 输出 C | **18385** ✅ |

`CtcLabelDecode` 硬校验 `c == dict.size()`，不匹配会抛 `IllegalArgumentException`。

字典第 1 项是 **U+3000 全角空格**。任何解析都不要对它做空白 strip
（Java `String.stripTrailing()` / Python `str.strip()` 都会把它吃掉）。

## det 后处理参数

| | v5 |
|---|---|
| `det_db_thresh` | 0.3 |
| `det_db_box_thresh` | 0.6 |
| `det_db_unclip_ratio` | 1.5 |
| `max_candidates` | 1000 |

也即 `PipelinePaths` 的默认值，`OcrConfig.options` 可覆盖。

## 与其它套件的关系

- `ch_ppocr_server_v2.0/ppocr_keys_v1.txt` **不是** v5 的字典（那是 v4 用的），别混。
- v6 见 `../ppocrv6/README.md` —— v6 无 cls 模型，且每个尺寸一套字典。
