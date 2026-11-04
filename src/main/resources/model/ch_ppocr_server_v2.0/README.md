# ch_ppocr_server_v2.0（不完整，未接入枚举）

```
ch_ppocr_server_v2.0/
├── det/det.onnx         48.9 MB   检测模型
├── cls/cls.onnx          0.6 MB   方向分类模型
└── ppocr_keys_v1.txt     6623 行  ← 本目录唯一的字典
```

⚠ **没有 `rec/rec.onnx`**，所以不构成完整套件，未注册到 `OcrModel` 枚举。
要启用需先补齐 v2.0 的识别模型。

## 现在的实际用途

字典 `ppocr_keys_v1.txt` **被 `../ppocrv4` 复用** —— v4 的 `rec.onnx` 输出
C=6625，正好是 6623 行 + blank + 空格。所以这个目录目前是 v4 的字典来源，
不能删。

## 参数

| | |
|---|---|
| `ppocr_keys_v1.txt` 行数 | 6623 |
| `CharDict.size()` | **6625** |
