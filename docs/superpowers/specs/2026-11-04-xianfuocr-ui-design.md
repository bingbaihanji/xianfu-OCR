# 显符OCR（xianfuOCR）UI 层设计规格

日期：2026-11-04
状态：已确认（设计三部分均经用户逐段审阅通过）
范围：JavaFX UI + 相关事件；OCR 推理层（`com.bingbaihanji.model.ocr.*`）已完成，不改动（唯一例外见「模型层衔接」）。

## 1. 背景与目标

基于 JavaFX 的本地 OCR 桌面软件。推理部分（ONNX Runtime + PaddleOCR 套件）已完成，本规格定义 UI 层与交互事件：

- 左侧粘贴/显示图片，右侧显示识别结果（文本 + 逐行明细联动）
- 全局系统热键截图识别，截图交互对齐 Snipaste（十字线/放大镜/选区调整/多显示器/贴图）
- 菜单栏：文件（打开/导出/保存）、设置（识别模型/识别参数/快捷键映射/窗口）
- 暗色界面、统一风格、优雅动效、友好交互

### 硬约束

1. **不污染系统目录**：配置、窗口状态、运行时产物一律放 jar 同目录（`<jarDir>/`），禁止写 user.home / AppData / temp。
2. **零新增运行时依赖**：热键用 JDK 25 FFM 调 Win32 `RegisterHotKey`；配置 JSON 手写轻量工具；不引 JNativeHook / JNA / Jackson / Gson。唯一新增依赖为 test 作用域的 `junit-jupiter`。
3. **模型层冻结**：`com.bingbaihanji.model.ocr.*` 不改 API；仅允许 `OcrModel` 加枚举值 + `OcrProperties.withBundledModels` 补管线接线（暴露 v6 tiny/medium 两档）。

## 2. 总体架构

```
com.bingbaihanji
 ├ model.ocr.*              （已完成，冻结——见约束 3）
 ├ ocr.ui                   界面装配层
 │   ├ XianFuOCRApplication 启动/生命周期/服务装配
 │   ├ homepage.Homepage    主窗口根布局（扩展已有）
 │   ├ homepage.ImagePane   图片显示/缩放平移/识别框叠加/行高亮联动
 │   ├ homepage.ResultPane  可编辑文本区 + 逐行明细列表
 │   ├ homepage.StatusBar   状态提示/模型/行数/耗时
 │   ├ menu.AppMenuBar      菜单栏（文件/设置）
 │   └ widget.Toast         轻量提示（2s 自动消失）
 ├ ocr.service
 │   ├ RecognitionService   包装 OnnxOcrService：异步识别、取消旧任务、模型热切换
 │   └ ClipboardService     剪贴板图片读取
 ├ ocr.capture              截图子系统（§4）
 ├ ocr.hotkey               全局热键（§5）
 ├ ocr.settings             配置与持久化（§6）
 └ ocr.theme                dark.css + FxAnim 动效工具（§7）
```

分层原则：`ui` 只做展示与交互；识别调用经 `RecognitionService` 在 FX 线程外执行；capture / hotkey / settings 接口窄、可单测。

## 3. 主界面

### 3.1 布局

```
┌─ HeaderBar: logo │ 显符OCR │ — □ ✕ ─────────────────┐
│ 菜单: 文件  设置          [截图识别] [打开图片]      │ ← 工具行
├────────────────────────┬───────────────────────────┤
│      ImagePane         │  识别文本          [复制]  │
│  ┌──────────────────┐  │  ┌───────────────────────┐ │
│  │ 图片（fit 显示） │  │  │ 可编辑 TextArea       │ │
│  │ +识别框叠加层    │  │  │（导出以此处编辑后为准）│ │
│  │ 滚轮缩放/拖拽平移│  │  └───────────────────────┘ │
│  └──────────────────┘  │  逐行明细（列表）          │
│  [拖入图片或 Ctrl+V]   │  ① 文本预览…          0.98│
├────────────────────────┴───────────────────────────┤
│ StatusBar: 就绪 │ 模型: PPOCR_V5 │ 2 行 │ 320 ms    │
└────────────────────────────────────────────────────┘
```

- 窗口沿用已有 `HeaderBar` + `StageStyle.EXTENDED` 自定义标题栏（logo/标题/最小化/最大化/关闭）。
- **ImagePane**：`StackPane` = `ImageView`（等比 fit）+ 识别框叠加 `Pane`。滚轮以光标为锚点缩放（0.2×–8×），拖拽平移；框随缩放实时换算。悬停行列表行 → 对应框高亮（accent 描边 + 淡入）；点框 → 列表滚动到对应行。
- **ResultPane**：`TextArea`（可编辑，导出以编辑后内容为准）+ `ListView` 逐行（文本 + 置信度徽标），行点击复制该行（Toast 提示）。
- **工具行**：`截图识别` `打开图片` 两枚主按钮；导出/保存在菜单，不重复铺按钮。
- **空态**：图片区居中占位提示（拖入/粘贴），文本区灰字提示。

### 3.2 事件与数据流

图片统一入口 `showImage(BufferedImage, 来源)`，四个来源：

1. **Ctrl+V 粘贴**：剪贴板含图片时全局拦截（焦点在 TextArea 且剪贴板为文本时不拦截）→ 显示 → 自动识别。
2. **拖放**：根布局 `DragEvent`，接受图片文件（png/jpg/bmp/gif/webp）或图片数据。
3. **文件打开**：`FileChooser`，记住上次目录（存配置）。
4. **截图确认**：见 §4。

识别：`RecognitionService` 单线程 ExecutorService 串行执行（ORT 会话内存大，并发收益低）；`Task<OcrResult>` 取消令牌——新请求取消旧任务。识别中状态栏旋转加载动效；结果回 FX 线程填充三处（图框/文本/列表）。识别参数变更下次识别生效。退出时保存配置、关闭线程池与 `OcrService`。

### 3.3 模型层衔接

`RecognitionService` 持有 `OnnxOcrService`，经 `NativeBootstrap.bootstrap()` + `OcrProperties.withBundledModels(NativeBootstrap.jarDir())` 创建（与 CLI 入口同装配方式）。设置暴露 v6 tiny/small/medium 三档需要：`OcrModel` 加 `PPOCR_V6_TINY` / `PPOCR_V6_MEDIUM` 两个枚举值，`OcrProperties.withBundledModels` 补两行管线接线（字典按各档配对，参照 v6 small 现有写法）。不改其他任何模型层代码。

## 4. 截图子系统

### 4.1 流程

```
热键/按钮触发
  → ① Robot 先把所有显示器内容捕获成静态图（先捕获、后显示遮罩，
     保证截到真实桌面而非遮罩层）
  → ② 每显示器一个透明覆盖 Stage（置顶、无边框、铺满该屏）：
     静态截图打底 + 半透明暗罩 + 十字线 + 放大镜 + 选区
  → ③ 框选交互
  → ④ 确认后从静态图裁剪 → 弹工具条 [识别] [贴图] [复制图片] [取消]
  → ⑤ 识别 = 裁剪图送 OCR 并前置主窗口展示
```

### 4.2 框选交互（对齐 Snipaste）

| 操作 | 行为 |
|---|---|
| 移动鼠标 | 全屏十字线跟随；目标显示器显示放大镜（光标处像素放大 + 坐标 + RGB） |
| 按下拖动 | 矩形选区：选区内全亮，外暗罩；边缘旁实时尺寸 `宽×高` |
| 松开 | 选区成型，8 个调整手柄；可拖内部移动、拖手柄缩放 |
| 双击选区内 | 确认（= 主动作「识别」） |
| 双击选区外 | 捕获整个当前显示器 |
| Enter | 确认选区（同主动作） |
| Esc / 右键 | 取消，关闭全部覆盖层 |

### 4.3 工具条与贴图

- 工具条贴选区右下方（越界翻转到上方），半透明深色圆角条。动作：**识别**（默认主动作，高亮）/ **贴图** / **复制图片** / **取消**。
- 确认后不自动识别：热键 → 拖选 → Enter 两步到结果；工具条承担贴图/复制分支，不打断贴图流。
- **PinWindow**（贴图）：无边框透明置顶 Stage，初始贴在捕获位置附近；拖动移动、滚轮缩放（20%–400%）、双击关闭、悬停提亮边框；多张贴图各是独立 Stage。
- **截图贴图模式**（独立热键）：框选确认后跳过工具条直接贴图；该模式下 Enter / 双击的主动作同样变为「贴图」。

### 4.4 多显示器

- 覆盖层按 `Screen.getScreens()` 每屏一个 Stage，坐标与 `GraphicsDevice` 互映（处理不同缩放比）。
- 放大镜、尺寸标签只在光标所在屏显示。
- 选区限制在单显示器内（跨屏拖选留待后续版本）；双击空白可整屏捕获任意屏。

## 5. 全局热键（FFM）

- 机制：JDK 25 FFM 调 `user32.RegisterHotKey(null, id, modifiers, vk)`，专用守护线程 `GetMessageW` 消息循环，`WM_HOTKEY` → `Platform.runLater` 派发。零依赖、零原生库解压。
- 动作与默认值：

| 动作 | 默认热键 |
|---|---|
| 截图识别 | `Ctrl+Alt+A` |
| 截图贴图 | `Ctrl+Alt+X` |
| 显示/隐藏主窗口 | `Ctrl+Alt+Z` |

- 健壮性：注册失败（被占用）→ 设置标红 + 启动 Toast 警告，跳过该热键；修改映射先注销旧的再注册新的；退出全部注销。

## 6. 设置与持久化

### 6.1 设置对话框（自定义暗色窗口，应用模态）

| 分类 | 内容 |
|---|---|
| 识别 | 模型下拉（PPOCR_V4 / V5 / V6-tiny / V6-small / V6-medium / DEFAULT）；置信度滑杆 0.0–1.0（默认 0.5）；最大边长数字框（默认 960）；方向分类开关 |
| 快捷键 | 动作/热键表格 +「录制」（按下组合键捕获、冲突标红、Esc 取消）+「恢复默认」 |
| 窗口 | 窗口状态记忆开关（位置/尺寸/最大化，默认开）；退出清空剪贴板开关（默认关） |
| 关于 | 版本号、模型来源说明（只读） |

按钮：确定（应用+关闭）/ 应用 / 取消。识别参数下次识别生效；快捷键立即重注册。

### 6.2 持久化

```
<jarDir>/config/settings.json    全部配置 + 窗口状态
<jarDir>/exports/                导出/保存默认目录（未选过目录时）
<jarDir>/native/                 （已有）原生库解压位置
```

- JSON 全扁平键（`model`、`minConfidence`、`hotkey.captureRecognize`、`window.x/y/w/h/maximized`、`lastOpenDir`…）。
- 手写轻量 `Json` 工具（扁平对象即可，配单测）。
- 写入：设置确定/应用时、退出时。读取失败 → 备份 `settings.bad.json` 并回退默认值，不阻塞启动。
- 日志维持 stderr，不落文件。

## 7. 主题与动效（`ocr.theme`）

- 色板（looked-up colors 统一引用）：背景 `#1B1B22`、面板 `#23232E`、悬浮 `#2C2C3A`、边框 `#3A3A4A`、文本 `#E8E8F0`、次要文本 `#9A9AAE`、accent `#5AB0FF`、危险 `#E06C75`。
- 规范：圆角 8px，间距 4/8/12/16，控件高 32px，字体 Segoe UI / 微软雅黑。
- 动效（150–250ms 缓出）：窗口首显 fade+缩放；Toast 滑入淡出；识别框高亮淡入；按钮悬停微缩放+变色；截图工具条滑入；贴图打开缩放；识别中旋转图标。
- 全组件同一份 `dark.css`，无散落硬编码颜色。

## 8. 错误处理

| 场景 | 行为 |
|---|---|
| 识别失败 | Toast 报错 + 状态栏错误态，栈迹进 stderr，UI 不崩 |
| 模型加载失败 | 启动后台预热，失败 Toast 指明模型名，可切其他模型 |
| 粘贴/拖入非图片 | Toast 轻提示，无副作用 |
| 热键被占用 | 设置标红 + 启动警告，跳过 |
| 配置损坏 | 备份 + 默认值回退 |
| 截图取消 | 静默关闭覆盖层 |

## 9. 测试与验收

- 新增 `junit-jupiter`（test 作用域，唯一新增依赖）。
- 单测：`HotkeyBinding` 解析/序列化/冲突；`Json` 读写；`SettingsStore` 损坏恢复；选区几何（手柄命中/移动缩放）；多屏坐标映射；标注图渲染。
- 手工验收：粘贴识别、拖放、打开/导出/保存、截图全流程（多显示器各一遍）、贴图拖动/缩放/关闭、全局热键三枚、设置持久化重启生效、坏配置恢复。
- 命令：`mvn test` 全绿；`mvn -DskipTests package` 出包实跑主流程。

## 10. 语义决定记录

- **导出** = 识别文本（TXT，UTF-8；以 TextArea 编辑后内容为准）。**保存** = 标注图 PNG（全分辨率，绘制识别框 + 各行文字标签；新写 `AnnotatedImageRenderer` 于 service 层，不动模型层 `OcrVisualizer`）。**打开** = 图片文件送识别。
- 粘贴语义：剪贴板有图 → 图片优先（全窗口）；无图且焦点在 TextArea → 文本粘贴自然工作。
- 截图确认后不自动识别，由 Enter/双击/工具条「识别」触发。
- 贴图不含贴图右键菜单（v1 仅拖动/缩放/双击关闭）。
