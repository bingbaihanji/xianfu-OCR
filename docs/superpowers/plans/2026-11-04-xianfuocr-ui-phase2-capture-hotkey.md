# 显符OCR UI 层 · 计划 B：截图贴图 + 全局热键（任务清单）

> 执行方式：本会话内实施，不写测试类（用户运行时手动验证），每任务编译通过后提交。
> 设计依据：`docs/superpowers/specs/2026-11-04-xianfuocr-ui-design.md` §4 截图子系统 / §5 全局热键 / §6 设置。
> 运行环境：Liberica Full JDK 25 + `-Djavafx.enablePreview=true --enable-native-access=ALL-UNNAMED`，入口 `com.bingbaihanji.StarApplication`。

**目标：** Snipaste 式区域截图（多显示器/十字线/放大镜/选区调整）→ 工具条[识别/贴图/复制图片/取消] + 贴图窗口 + FFM 全局热键三枚 + 设置「快捷键」页（录制/冲突检测/恢复默认）。

## 任务

- [ ] T1 `ocr.hotkey.HotkeyBinding`：组合键解析/规范化/显示/冲突检测/Win32 MOD_+VK 映射；`AppSettings`+`SettingsStore` 增热键三字段（`hotkey.captureRecognize` CTRL+ALT+A / `hotkey.capturePin` CTRL+ALT+X / `hotkey.toggleWindow` CTRL+ALT+Z）
- [ ] T2 `ocr.hotkey.GlobalHotkey`：FFM 调 user32（`RegisterHotKey`/`UnregisterHotKey`/`GetMessageW`/`PostThreadMessageW`，MSG 结构 48B：message@8、wParam@16）；专用线程消息循环，register/unregister 经任务队列+唤醒消息在循环线程执行；`register(id, binding)` 有冲突返回 false；MOD_NOREPEAT
- [ ] T3 `ocr.capture.CaptureGeometry`（选区归一化/8 手柄命中/拖拽移动缩放，纯逻辑）+ `ScreenCaptureService`（AWT Robot 按 GraphicsDevice 先行捕获各屏静态图；MonitorCapture(image, Screen, primary)；相对坐标换算 `imgX = selX × imgW / overlayW`，天然兼容多屏不同 DPI）
- [ ] T4 `CaptureOverlay`（每显示器一个 Stage：暗化底图 + 选区裁剪亮图层 + 十字线 + 放大镜(像素格+坐标+RGB) + 尺寸标签 + 8 手柄；拖拽选区/双击选区内确认/双击选区外整屏/Enter 确认/Esc·右键取消）+ `CaptureToolbar`（识别/贴图/复制图片/取消，贴选区右下越界翻上）+ `PinWindow`（置顶无边框贴图：拖动/滚轮缩放 20%~400%/双击关闭/悬停边框）
- [ ] T5 `CaptureController`：编排（先捕获→全屏遮罩→选区→裁剪）；模式 `CaptureMode.RECOGNIZE`（出工具条）/ `PIN`（确认后直接贴图）；识别回调交主窗口
- [ ] T6 装配：设置对话框「快捷键」页（录制/冲突标红/恢复默认/应用即重注册）；主页工具行加「截图识别」按钮；`AppController` 热键注册生命周期 + `toggleWindow`；dark.css 补截图/工具条/贴图样式
- [ ] T7 `mvn -DskipTests package` + 启动验证 + 手工清单（热键三枚/多显示器框选/放大镜/手柄调整/贴图交互/快捷键录制与冲突/重启热键生效）

## 关键契约（防漂移）

- `HotkeyBinding.parse(String)->HotkeyBinding`，`combo()` 规范形如 `CTRL+ALT+A`，`display()`→`Ctrl+Alt+A`，`modifiers()`→int（CTRL=2/SHIFT=4/ALT=1/WIN=8），`virtualKey()`→int VK（A-Z 0x41.. / 0-9 0x30.. / F1-F24 0x70..），`fromFx(KeyCode,ctrl,shift,alt,win)`，`conflicts(a,b)`
- `GlobalHotkey implements AutoCloseable`：`boolean register(int id, HotkeyBinding)`、`void unregister(int id)`、`void close()`；回调 `Consumer<Integer>`（id），经 `Platform.runLater`
- `CaptureController.start(CaptureMode mode, Consumer<BufferedImage> onRecognizeImage)`；贴图/复制图片内部完成
- `AppSettings` 新增 `getHotkeyCaptureRecognize()` 等三个 getter/setter，缺省值见 T1
