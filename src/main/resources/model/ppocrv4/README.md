# PP-OCRv4 模型套件

项目 `OcrModel.PPOCR_V4` 指向这里。

> **这是 Mobile 版，不是 Server 版。** 同 `../ppocrv5`，来自 OnnxOCR 的
> `onnxocr/models/`，该套件默认只带 Mobile 版。体积佐证：det 4.5 MB / rec 10.3 MB，
> 而 Server 档的 `../ch_ppocr_server_v2.0/det/det.onnx` 有 46.6 MB。
> v4/v5 只有 Mobile / Server 两档，与 v6 的 tiny/small/medium 三档不是一回事。

```
ppocrv4/
├── det/det.onnx          4.7 MB
├── rec/rec.onnx         10.8 MB
└── cls/cls.onnx          0.6 MB   方向分类，enableRotate(true) 时使用
```

**本目录没有字典文件** —— 复用 `../ch_ppocr_server_v2.0/ppocr_keys_v1.txt`。

## 字典与 rec 输出维度

| | |
|---|---|
| `ppocr_keys_v1.txt` 行数 | 6623 |
| `CharDict.size()` = 行数 + blank + 空格 | **6625** |
| `rec.onnx` 输出 C | **6625** ✅ |

⚠ **不能挂 `../ppocrv5/ppocrv5_dict.txt`**（18383 行 → 18385 类），与 v4 的
`rec.onnx`（6625 类）对不上，`CtcLabelDecode` 会直接抛异常。

> 背景：Python 端 `utils.py` 的 `rec_char_dict_path` 默认值是
> `ppocrv5/ppocrv5_dict.txt`，但那个默认**只对 v5 套件成立**。照抄过来会踩坑。

## det 后处理参数

| | v4 |
|---|---|
| `det_db_thresh` | 0.3 |
| `det_db_box_thresh` | 0.6 |
| `det_db_unclip_ratio` | 1.5 |
| `max_candidates` | 1000 |

也即 `PipelinePaths` 的默认值，`OcrConfig.options` 可覆盖。
