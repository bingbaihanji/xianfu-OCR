# 显符OCR UI 层 · 计划 A：基础层 + 主界面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可用的 OCR 桌面应用——左图右文主界面、粘贴/拖放/打开图片、异步识别、文本导出、标注图保存、设置对话框与 jar 目录配置持久化、暗色主题动效。

**Architecture:** 在已完成的 `com.bingbaihanji.model.ocr.*` 推理层之上新增 `com.bingbaihanji.ocr.*` 界面层：`settings`（JSON 持久化）/ `theme`（暗色 CSS + 动效）/ `service`（异步识别、标注渲染、几何映射）/ `ui`（主窗口组件 + 控制器）。识别走单线程 Executor 串行推理，回调经可注入 UI 投递器回 FX 线程。唯一允许触碰的模型层改动：`OcrModel` 加 `PPOCR_V6_TINY`/`PPOCR_V6_MEDIUM` 两个枚举值并在 `OcrProperties.withBundledModels` 补两行接线。

**Tech Stack:** Java 25、JavaFX 25（`HeaderBar` 自定义标题栏）、ONNX Runtime（经既有 `OnnxOcrService`）、Lombok、slf4j/logback、JUnit 5（test，唯一新增依赖）。

**范围约定：** 本计划是两份计划中的第一份。计划 B（另文）交付截图子系统、贴图 PinWindow、FFM 全局热键与快捷键设置页——本计划中的设置对话框只含「识别/窗口/关于」三页，工具行只含「打开图片」，均由计划 B 扩展。

**硬约束（每个任务都必须遵守）：**
1. 持久化文件只写 `<jarDir>/`（`config/settings.json`、`exports/`），禁止 user.home / AppData / temp。
2. 零新增运行时依赖（仅 test 作用域 junit-jupiter + surefire 插件）。
3. 格式：4 空格缩进、UTF-8、稀疏中文注释；提交信息用 `feat:`/`fix:`/`chore:`/`test:` 前缀，结尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。

---

## File Structure

```
修改  pom.xml                                                    +junit-jupiter(test) +maven-surefire-plugin
修改  .gitignore                                                 +根锚定忽略 bin/ config/ native/ exports/
修改  src/main/java/com/bingbaihanji/model/ocr/enums/OcrModel.java        +PPOCR_V6_TINY/PPOCR_V6_MEDIUM
修改  src/main/java/com/bingbaihanji/model/ocr/config/OcrProperties.java  +tiny/medium 管线接线
新建  src/main/java/com/bingbaihanji/ocr/settings/Json.java               扁平 JSON 读写
新建  src/main/java/com/bingbaihanji/ocr/settings/AppSettings.java        可变设置对象
新建  src/main/java/com/bingbaihanji/ocr/settings/SettingsStore.java      jar 目录持久化+损坏恢复
新建  src/main/java/com/bingbaihanji/ocr/theme/Theme.java                 色板常量+样式装载
新建  src/main/java/com/bingbaihanji/ocr/theme/FxAnim.java                统一动效
新建  src/main/resources/css/dark.css                                     暗色主题全量样式
新建  src/main/java/com/bingbaihanji/ocr/service/AnnotatedImageRenderer.java  全分辨率标注图
新建  src/main/java/com/bingbaihanji/ocr/service/RecognitionService.java  异步识别（串行+替代）
新建  src/main/java/com/bingbaihanji/ocr/geom/BoxLayout.java              识别框坐标映射（纯函数）
新建  src/main/java/com/bingbaihanji/ocr/ui/widget/Toast.java             轻提示
新建  src/main/java/com/bingbaihanji/ocr/ui/homepage/StatusBar.java       状态栏
新建  src/main/java/com/bingbaihanji/ocr/ui/homepage/ImagePane.java       图片区（缩放/平移/框联动）
新建  src/main/java/com/bingbaihanji/ocr/ui/homepage/ResultPane.java      结果面板（文本+逐行）
新建  src/main/java/com/bingbaihanji/ocr/ui/menu/AppMenuBar.java          文件/设置菜单
新建  src/main/java/com/bingbaihanji/ocr/ui/settings/SettingsDialog.java  设置对话框
新建  src/main/java/com/bingbaihanji/ocr/ui/AppController.java            事件装配与识别流
重写  src/main/java/com/bingbaihanji/ocr/ui/homepage/Homepage.java        主窗口装配
重写  src/main/java/com/bingbaihanji/ocr/ui/XianFuOCRApplication.java     生命周期
测试  src/test/java/com/bingbaihanji/model/ocr/config/OcrPropertiesTest.java
测试  src/test/java/com/bingbaihanji/ocr/settings/JsonTest.java
测试  src/test/java/com/bingbaihanji/ocr/settings/SettingsStoreTest.java
测试  src/test/java/com/bingbaihanji/ocr/service/AnnotatedImageRendererTest.java
测试  src/test/java/com/bingbaihanji/ocr/service/RecognitionServiceTest.java
测试  src/test/java/com/bingbaihanji/ocr/geom/BoxLayoutTest.java
```

---

### Task 1: 构建基线（JUnit5 + Surefire + .gitignore）

**Files:**
- Modify: `pom.xml`
- Modify: `.gitignore`

- [ ] **Step 1: pom.xml 增加测试依赖与 Surefire 插件**

在 `<dependencies>` 末尾（`javafx-swing` 之后）加入：

```xml
        <!-- JUnit 5：仅测试用 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
```

在 `<build><plugins>` 中 `maven-compiler-plugin` **之前**加入（保证任何任务的 `mvn test` 都能跑 JUnit5）：

```xml
            <!-- Surefire 3.x 才支持 JUnit5（Maven 默认的 2.12.4 不行） -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
```

- [ ] **Step 2: .gitignore 末尾追加运行时目录忽略**

```
# 构建产物与运行时目录（配置/导出/原生库解压均在 jar 同目录产生，不入库）
/bin/
/config/
/native/
/exports/
```

- [ ] **Step 3: 验证构建基线**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`（暂无测试类，0 tests 也算通过）

- [ ] **Step 4: 提交**

```bash
git add pom.xml .gitignore
git commit -m "chore: 构建基线——引入 JUnit5/Surefire 并忽略运行时目录

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: OcrModel 枚举扩展 + OcrProperties 管线接线

**Files:**
- Modify: `src/main/java/com/bingbaihanji/model/ocr/enums/OcrModel.java`
- Modify: `src/main/java/com/bingbaihanji/model/ocr/config/OcrProperties.java`
- Test: `src/test/java/com/bingbaihanji/model/ocr/config/OcrPropertiesTest.java`

事实依据（已核实）：v6 tiny 的 `inference.yml` det 后处理为 `thresh=0.2, box_thresh=0.4, unclip_ratio=1.4, max_candidates=3000`；small/medium 的 `box_thresh=0.45`，其余同 tiny。各档字典独立（`ppocrv6/<size>/ppocrv6_dict.txt`），不得混用。

- [ ] **Step 1: 写失败的测试**

新建 `src/test/java/com/bingbaihanji/model/ocr/config/OcrPropertiesTest.java`：

```java
package com.bingbaihanji.model.ocr.config;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// OcrProperties 内置模型接线测试
class OcrPropertiesTest {

    @Test
    void 内置模型管线覆盖全部枚举值() {
        Map<OcrModel, PipelinePaths> pipelines = OcrProperties.withBundledModels().getPipelines();
        for (OcrModel m : OcrModel.values()) {
            assertNotNull(pipelines.get(m), "缺少模型管线: " + m);
        }
    }

    @Test
    void v6三档按各自套件接线且det后处理参数区分() {
        Map<OcrModel, PipelinePaths> p = OcrProperties.withBundledModels().getPipelines();

        PipelinePaths tiny = p.get(OcrModel.PPOCR_V6_TINY);
        assertTrue(tiny.getDetPath().contains("ppocrv6/tiny"), tiny.getDetPath());
        assertTrue(tiny.getDictPath().contains("ppocrv6/tiny"), tiny.getDictPath());
        assertEquals(0.4, tiny.getDetBoxThresh(), 1e-9);

        PipelinePaths small = p.get(OcrModel.PPOCR_V6);
        assertTrue(small.getDetPath().contains("ppocrv6/small"), small.getDetPath());
        assertEquals(0.45, small.getDetBoxThresh(), 1e-9);

        PipelinePaths medium = p.get(OcrModel.PPOCR_V6_MEDIUM);
        assertTrue(medium.getRecPath().contains("ppocrv6/medium"), medium.getRecPath());
        assertEquals(0.45, medium.getDetBoxThresh(), 1e-9);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=OcrPropertiesTest`
Expected: 编译失败（`PPOCR_V6_TINY` 不存在）

- [ ] **Step 3: 扩展 OcrModel 枚举**

`OcrModel.java` 中把 `PPOCR_V6` 的常量结尾 `;` 改为 `,`，追加两个常量（保持 `@Getter`/`fromCode` 不动）：

```java
    // PaddleOCR V6 系列 ONNX 模型（默认指向 small 档，见 model/ppocrv6/README.md）
    PPOCR_V6("ppocr_v6", "PaddleOCR V6 ONNX 模型"),

    // PaddleOCR V6 tiny 档（速度优先，字典覆盖较小）
    PPOCR_V6_TINY("ppocr_v6_tiny", "PaddleOCR V6 tiny 档"),

    // PaddleOCR V6 medium 档（精度优先，CPU 上 rec 明显更慢）
    PPOCR_V6_MEDIUM("ppocr_v6_medium", "PaddleOCR V6 medium 档");
```

- [ ] **Step 4: OcrProperties 补 tiny/medium 管线**

`OcrProperties.withBundledModels` 中，在 `PipelinePaths v6 = ...` 定义之后、`return builder()` 之前加入：

```java
        // v6 tiny/medium 档：与 small 同族；det 后处理参数取自各档自带 inference.yml，
        // tiny 的 box_thresh 是 0.4（small/medium 是 0.45），字典必须与 rec 同档配对。
        PipelinePaths v6Tiny = PipelinePaths.builder()
                .detPath(resolve(jarDir, "ppocrv6/tiny/det/det.onnx"))
                .recPath(resolve(jarDir, "ppocrv6/tiny/rec/rec.onnx"))
                .clsPath(null)
                .dictPath(resolve(jarDir, "ppocrv6/tiny/ppocrv6_dict.txt"))
                .detThresh(0.2)
                .detBoxThresh(0.4)
                .detUnclipRatio(1.4)
                .maxCandidates(3000)
                .build();
        PipelinePaths v6Medium = PipelinePaths.builder()
                .detPath(resolve(jarDir, "ppocrv6/medium/det/det.onnx"))
                .recPath(resolve(jarDir, "ppocrv6/medium/rec/rec.onnx"))
                .clsPath(null)
                .dictPath(resolve(jarDir, "ppocrv6/medium/ppocrv6_dict.txt"))
                .detThresh(0.2)
                .detBoxThresh(0.45)
                .detUnclipRatio(1.4)
                .maxCandidates(3000)
                .build();
```

并在 `return builder()...` 的注册链中 `.pipeline(OcrModel.PPOCR_V6, v6)` 之后追加两行：

```java
                .pipeline(OcrModel.PPOCR_V6_TINY, v6Tiny)
                .pipeline(OcrModel.PPOCR_V6_MEDIUM, v6Medium)
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dtest=OcrPropertiesTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/bingbaihanji/model/ocr/enums/OcrModel.java src/main/java/com/bingbaihanji/model/ocr/config/OcrProperties.java src/test/java/com/bingbaihanji/model/ocr/config/OcrPropertiesTest.java
git commit -m "feat: OcrModel 增加 v6 tiny/medium 档并接线 OcrProperties

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Json 工具（扁平对象读写）

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/settings/Json.java`
- Test: `src/test/java/com/bingbaihanji/ocr/settings/JsonTest.java`

- [ ] **Step 1: 写失败的测试**

新建 `src/test/java/com/bingbaihanji/ocr/settings/JsonTest.java`：

```java
package com.bingbaihanji.ocr.settings;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 扁平 JSON 读写测试
class JsonTest {

    @Test
    void 序列化解析往返一致() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("model", "ppocr_v5");
        in.put("minConfidence", 0.5);
        in.put("maxSideLen", 960L);
        in.put("enableRotate", true);
        in.put("lastOpenDir", "D:\\图片\\测试\"引号\"");
        Map<String, Object> out = Json.parse(Json.write(in));
        assertEquals("ppocr_v5", out.get("model"));
        assertEquals(0.5, (Double) out.get("minConfidence"), 1e-9);
        assertEquals(960L, out.get("maxSideLen"));
        assertEquals(true, out.get("enableRotate"));
        assertEquals("D:\\图片\\测试\"引号\"", out.get("lastOpenDir"));
    }

    @Test
    void 解析转义字符() {
        Map<String, Object> out = Json.parse("{\"a\":\"x\\ny\\t\\\"q\\\"\\\\z\"}");
        assertEquals("x\ny\t\"q\"\\z", out.get("a"));
    }

    @Test
    void 非法输入抛异常() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{not json"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
    }

    @Test
    void 空对象往返() {
        assertEquals("{}", Json.write(Map.of()));
        assertTrue(Json.parse("{}").isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=JsonTest`
Expected: 编译失败（`Json` 未定义）

- [ ] **Step 3: 实现 Json**

新建 `src/main/java/com/bingbaihanji/ocr/settings/Json.java`：

```java
package com.bingbaihanji.ocr.settings;

import java.util.LinkedHashMap;
import java.util.Map;

// 轻量 JSON 读写：仅支持「扁平对象」——键-值对，值限字符串/数值/布尔。
// 满足 settings.json 持久化需求，避免为配置引入第三方 JSON 库。
public final class Json {

    private Json() {
    }

    // 序列化为 JSON 字符串；null 值键被跳过；不支持的类型抛 IllegalArgumentException
    public static String write(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            appendValue(sb, v);
        }
        return sb.append('}').toString();
    }

    // 解析扁平 JSON 对象；语法错误抛 IllegalArgumentException
    public static Map<String, Object> parse(String text) {
        return new Parser(text).parseObject();
    }

    // 转义 JSON 字符串内容
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, Object v) {
        if (v instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Double) {
            sb.append(v);
        } else {
            throw new IllegalArgumentException("不支持的值类型: " + v.getClass().getName());
        }
    }

    // 递归下降解析器（本项目只用到对象 + 原始值）
    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        Map<String, Object> parseObject() {
            Map<String, Object> out = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                next();
                return out;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                skipWs();
                out.put(key, readValue());
                skipWs();
                char c = next();
                if (c == '}') {
                    return out;
                }
                if (c != ',') {
                    throw err("期望 ',' 或 '}'，实际为 '" + c + "'");
                }
            }
        }

        private Object readValue() {
            char c = peek();
            return switch (c) {
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                default -> readNumber();
            };
        }

        private Object readNumber() {
            int start = pos;
            if (peek() == '-') {
                next();
            }
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    next();
                } else {
                    break;
                }
            }
            String s = text.substring(start, pos);
            try {
                return s.contains(".") || s.contains("e") || s.contains("E")
                        ? (Object) Double.parseDouble(s)
                        : (Object) Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw err("非法数字: " + s);
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, pos)) {
                throw err("非法字面量");
            }
            pos += literal.length();
            return value;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw err("\\u 转义不完整");
                        }
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw err("非法转义: \\" + e);
                }
            }
        }

        private void skipWs() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw err("意外结束");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            char actual = next();
            if (actual != c) {
                throw err("期望 '" + c + "'，实际为 '" + actual + "'");
            }
        }

        private IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON 解析失败 (pos=" + pos + "): " + msg);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=JsonTest`
Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/settings/Json.java src/test/java/com/bingbaihanji/ocr/settings/JsonTest.java
git commit -m "feat: 扁平 JSON 读写工具

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: AppSettings + SettingsStore

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/settings/AppSettings.java`
- Create: `src/main/java/com/bingbaihanji/ocr/settings/SettingsStore.java`
- Test: `src/test/java/com/bingbaihanji/ocr/settings/SettingsStoreTest.java`

- [ ] **Step 1: 写失败的测试**

新建 `src/test/java/com/bingbaihanji/ocr/settings/SettingsStoreTest.java`：

```java
package com.bingbaihanji.ocr.settings;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 设置持久化测试：往返一致、缺失回退默认、损坏备份回退
class SettingsStoreTest {

    @Test
    void 保存后可完整读回(@TempDir Path dir) throws Exception {
        AppSettings s = new AppSettings();
        s.setModel(OcrModel.PPOCR_V6_TINY);
        s.setMinConfidence(0.8);
        s.setMaxSideLen(1280);
        s.setEnableRotate(true);
        s.setWindowX(100);
        s.setWindowY(80);
        s.setWindowWidth(900);
        s.setWindowHeight(700);
        s.setWindowMaximized(true);
        s.setLastOpenDir("D:\\in");
        s.setLastExportDir("D:\\out");
        SettingsStore.save(dir, s);

        AppSettings r = SettingsStore.load(dir);
        assertEquals(OcrModel.PPOCR_V6_TINY, r.getModel());
        assertEquals(0.8, r.getMinConfidence(), 1e-9);
        assertEquals(1280, r.getMaxSideLen());
        assertTrue(r.isEnableRotate());
        assertEquals(100, r.getWindowX(), 1e-9);
        assertEquals(80, r.getWindowY(), 1e-9);
        assertTrue(r.isWindowMaximized());
        assertTrue(r.isRememberWindow());
        assertEquals("D:\\in", r.getLastOpenDir());
        assertEquals("D:\\out", r.getLastExportDir());
    }

    @Test
    void 缺失文件返回默认值(@TempDir Path dir) {
        AppSettings s = SettingsStore.load(dir);
        assertEquals(OcrModel.PPOCR_V5, s.getModel());
        assertEquals(0.5, s.getMinConfidence(), 1e-9);
        assertEquals(960, s.getMaxSideLen());
        assertFalse(s.isEnableRotate());
        assertFalse(s.isClearClipboardOnExit());
    }

    @Test
    void 损坏配置回退默认并备份坏文件(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("config"));
        Files.writeString(SettingsStore.file(dir), "{ 这不是合法 JSON");
        AppSettings s = SettingsStore.load(dir);
        assertEquals(0.5, s.getMinConfidence(), 1e-9);
        assertTrue(Files.exists(dir.resolve("config").resolve("settings.bad.json")));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=SettingsStoreTest`
Expected: 编译失败（`AppSettings` 未定义）

- [ ] **Step 3: 实现 AppSettings**

新建 `src/main/java/com/bingbaihanji/ocr/settings/AppSettings.java`：

```java
package com.bingbaihanji.ocr.settings;

import com.bingbaihanji.model.ocr.enums.OcrModel;

// 应用设置（可变）：识别参数、窗口状态与最近目录；序列化经 SettingsStore
public class AppSettings {

    private OcrModel model = OcrModel.PPOCR_V5;
    private double minConfidence = 0.5;
    private int maxSideLen = 960;
    private boolean enableRotate = false;

    private boolean rememberWindow = true;
    private double windowX = Double.NaN;
    private double windowY = Double.NaN;
    private double windowWidth = 1100;
    private double windowHeight = 720;
    private boolean windowMaximized = false;

    private boolean clearClipboardOnExit = false;

    private String lastOpenDir = "";
    private String lastExportDir = "";

    public OcrModel getModel() {
        return model;
    }

    public void setModel(OcrModel model) {
        this.model = model;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public int getMaxSideLen() {
        return maxSideLen;
    }

    public void setMaxSideLen(int maxSideLen) {
        this.maxSideLen = maxSideLen;
    }

    public boolean isEnableRotate() {
        return enableRotate;
    }

    public void setEnableRotate(boolean enableRotate) {
        this.enableRotate = enableRotate;
    }

    public boolean isRememberWindow() {
        return rememberWindow;
    }

    public void setRememberWindow(boolean rememberWindow) {
        this.rememberWindow = rememberWindow;
    }

    // 未记录位置时为 NaN
    public double getWindowX() {
        return windowX;
    }

    public void setWindowX(double windowX) {
        this.windowX = windowX;
    }

    public double getWindowY() {
        return windowY;
    }

    public void setWindowY(double windowY) {
        this.windowY = windowY;
    }

    public double getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(double windowWidth) {
        this.windowWidth = windowWidth;
    }

    public double getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(double windowHeight) {
        this.windowHeight = windowHeight;
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }

    public boolean isClearClipboardOnExit() {
        return clearClipboardOnExit;
    }

    public void setClearClipboardOnExit(boolean clearClipboardOnExit) {
        this.clearClipboardOnExit = clearClipboardOnExit;
    }

    public String getLastOpenDir() {
        return lastOpenDir;
    }

    public void setLastOpenDir(String lastOpenDir) {
        this.lastOpenDir = lastOpenDir == null ? "" : lastOpenDir;
    }

    public String getLastExportDir() {
        return lastExportDir;
    }

    public void setLastExportDir(String lastExportDir) {
        this.lastExportDir = lastExportDir == null ? "" : lastExportDir;
    }

    // 复制一份（设置对话框在副本上编辑）
    public AppSettings copy() {
        AppSettings c = new AppSettings();
        c.copyFrom(this);
        return c;
    }

    // 从另一份设置覆盖全部字段
    public void copyFrom(AppSettings other) {
        this.model = other.model;
        this.minConfidence = other.minConfidence;
        this.maxSideLen = other.maxSideLen;
        this.enableRotate = other.enableRotate;
        this.rememberWindow = other.rememberWindow;
        this.windowX = other.windowX;
        this.windowY = other.windowY;
        this.windowWidth = other.windowWidth;
        this.windowHeight = other.windowHeight;
        this.windowMaximized = other.windowMaximized;
        this.clearClipboardOnExit = other.clearClipboardOnExit;
        this.lastOpenDir = other.lastOpenDir;
        this.lastExportDir = other.lastExportDir;
    }
}
```

- [ ] **Step 4: 实现 SettingsStore**

新建 `src/main/java/com/bingbaihanji/ocr/settings/SettingsStore.java`：

```java
package com.bingbaihanji.ocr.settings;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

// 设置持久化：<jarDir>/config/settings.json；损坏时备份 settings.bad.json 并回退默认值
public final class SettingsStore {

    private static final Logger log = LoggerFactory.getLogger(SettingsStore.class);

    private SettingsStore() {
    }

    // 配置文件位置（jar 同目录 config/ 下）
    public static Path file(Path jarDir) {
        return jarDir.resolve("config").resolve("settings.json");
    }

    // 读取设置；文件缺失返回默认值，损坏则备份并返回默认值
    public static AppSettings load(Path jarDir) {
        Path f = file(jarDir);
        if (!Files.isRegularFile(f)) {
            return new AppSettings();
        }
        try {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            return fromMap(Json.parse(text));
        } catch (IOException | RuntimeException e) {
            log.warn("配置读取失败，回退默认值: {}", e.toString());
            backupBad(f);
            return new AppSettings();
        }
    }

    // 保存设置（自动建 config/ 目录）
    public static void save(Path jarDir, AppSettings s) throws IOException {
        Path f = file(jarDir);
        Files.createDirectories(f.getParent());
        Files.writeString(f, Json.write(toMap(s)), StandardCharsets.UTF_8);
    }

    private static void backupBad(Path f) {
        try {
            Path bad = f.resolveSibling("settings.bad.json");
            Files.move(f, bad, StandardCopyOption.REPLACE_EXISTING);
            log.warn("损坏配置已备份到: {}", bad);
        } catch (IOException e) {
            log.warn("备份损坏配置失败: {}", e.toString());
        }
    }

    static Map<String, Object> toMap(AppSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", s.getModel().getCode());
        m.put("minConfidence", s.getMinConfidence());
        m.put("maxSideLen", s.getMaxSideLen());
        m.put("enableRotate", s.isEnableRotate());
        m.put("rememberWindow", s.isRememberWindow());
        if (!Double.isNaN(s.getWindowX())) {
            m.put("windowX", s.getWindowX());
        }
        if (!Double.isNaN(s.getWindowY())) {
            m.put("windowY", s.getWindowY());
        }
        m.put("windowWidth", s.getWindowWidth());
        m.put("windowHeight", s.getWindowHeight());
        m.put("windowMaximized", s.isWindowMaximized());
        m.put("clearClipboardOnExit", s.isClearClipboardOnExit());
        m.put("lastOpenDir", s.getLastOpenDir());
        m.put("lastExportDir", s.getLastExportDir());
        return m;
    }

    static AppSettings fromMap(Map<String, Object> m) {
        AppSettings s = new AppSettings();
        s.setModel(OcrModel.fromCode(str(m, "model", "")).orElse(OcrModel.PPOCR_V5));
        s.setMinConfidence(num(m, "minConfidence", 0.5));
        s.setMaxSideLen((int) num(m, "maxSideLen", 960));
        s.setEnableRotate(bool(m, "enableRotate", false));
        s.setRememberWindow(bool(m, "rememberWindow", true));
        s.setWindowX(num(m, "windowX", Double.NaN));
        s.setWindowY(num(m, "windowY", Double.NaN));
        s.setWindowWidth(num(m, "windowWidth", 1100));
        s.setWindowHeight(num(m, "windowHeight", 720));
        s.setWindowMaximized(bool(m, "windowMaximized", false));
        s.setClearClipboardOnExit(bool(m, "clearClipboardOnExit", false));
        s.setLastOpenDir(str(m, "lastOpenDir", ""));
        s.setLastExportDir(str(m, "lastExportDir", ""));
        return s;
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private static double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dtest=SettingsStoreTest`
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/settings/AppSettings.java src/main/java/com/bingbaihanji/ocr/settings/SettingsStore.java src/test/java/com/bingbaihanji/ocr/settings/SettingsStoreTest.java
git commit -m "feat: 应用设置模型与 jar 目录持久化

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Theme + FxAnim + dark.css

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/theme/Theme.java`
- Create: `src/main/java/com/bingbaihanji/ocr/theme/FxAnim.java`
- Create: `src/main/resources/css/dark.css`

纯视觉层，无自动化测试（JavaFX 控件需工具链），验收在 Task 16 手工清单。

- [ ] **Step 1: 实现 Theme**

新建 `src/main/java/com/bingbaihanji/ocr/theme/Theme.java`：

```java
package com.bingbaihanji.ocr.theme;

import javafx.scene.Scene;

// 主题常量与样式装载（全部颜色/圆角在 dark.css 中以 looked-up colors 统一定义）
public final class Theme {

    public static final String ACCENT = "#5AB0FF";
    public static final String DANGER = "#E06C75";

    private Theme() {
    }

    // 把暗色样式表挂到场景（幂等，重复调用只会挂一次）
    public static void apply(Scene scene) {
        String css = Theme.class.getResource("/css/dark.css").toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }
}
```

- [ ] **Step 2: 实现 FxAnim**

新建 `src/main/java/com/bingbaihanji/ocr/theme/FxAnim.java`：

```java
package com.bingbaihanji.ocr.theme;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

// 统一动效：淡入淡出/滑入/弹出，150-250ms 缓出
public final class FxAnim {

    private FxAnim() {
    }

    public static void fadeIn(Node node) {
        fadeIn(node, 180);
    }

    public static void fadeIn(Node node, double millis) {
        FadeTransition t = new FadeTransition(Duration.millis(millis), node);
        t.setFromValue(0);
        t.setToValue(1);
        t.play();
    }

    // 淡出后执行回调（如隐藏节点）
    public static void fadeOut(Node node, double millis, Runnable after) {
        FadeTransition t = new FadeTransition(Duration.millis(millis), node);
        t.setFromValue(node.getOpacity());
        t.setToValue(0);
        if (after != null) {
            t.setOnFinished(e -> after.run());
        }
        t.play();
    }

    // 自上而下滑入 + 淡入（Toast 用）
    public static void slideIn(Node node, double millis) {
        TranslateTransition slide = new TranslateTransition(Duration.millis(millis), node);
        slide.setFromY(-12);
        slide.setToY(0);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        new ParallelTransition(slide, fade).play();
    }

    // 轻微放大弹入 + 淡入（对话框/工具条用）
    public static void popIn(Node node, double millis) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(millis), node);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1);
        scale.setToY(1);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        new ParallelTransition(scale, fade).play();
    }
}
```

- [ ] **Step 3: 实现 dark.css**

新建 `src/main/resources/css/dark.css`：

```css
/* 显符OCR 暗色主题：全组件统一色板（looked-up colors），无散落硬编码颜色 */

.root {
    -fx-bg: #1B1B22;
    -fx-panel: #23232E;
    -fx-hover: #2C2C3A;
    -fx-border: #3A3A4A;
    -fx-text: #E8E8F0;
    -fx-text-dim: #9A9AAE;
    -fx-accent: #5AB0FF;
    -fx-danger: #E06C75;
    -fx-background: -fx-bg;
    -fx-font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
    -fx-font-size: 13px;
    -fx-text-fill: -fx-text;
}

/* ---------- 文本 ---------- */
.label {
    -fx-text-fill: -fx-text;
}
.label-dim {
    -fx-text-fill: -fx-text-dim;
}

/* ---------- 按钮 ---------- */
.button {
    -fx-background-color: -fx-panel;
    -fx-text-fill: -fx-text;
    -fx-background-radius: 8;
    -fx-border-color: -fx-border;
    -fx-border-radius: 8;
    -fx-padding: 6 14 6 14;
    -fx-cursor: hand;
}
.button:hover {
    -fx-background-color: -fx-hover;
}
.button:pressed {
    -fx-background-color: #1B1B22;
}
.button.primary {
    -fx-background-color: -fx-accent;
    -fx-text-fill: #10141C;
    -fx-border-color: transparent;
    -fx-font-weight: bold;
}
.button.primary:hover {
    -fx-background-color: #7BC2FF;
}

/* ---------- 输入控件 ---------- */
.text-area,
.text-field,
.password-field {
    -fx-background-color: -fx-panel;
    -fx-text-fill: -fx-text;
    -fx-background-radius: 8;
    -fx-border-color: -fx-border;
    -fx-border-radius: 8;
    -fx-prompt-text-fill: -fx-text-dim;
}
.text-area {
    -fx-font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
}
.text-area .content {
    -fx-background-color: -fx-panel;
    -fx-background-radius: 8;
}
.text-area:focused,
.text-field:focused {
    -fx-border-color: -fx-accent;
}

/* ---------- 列表 ---------- */
.list-view {
    -fx-background-color: -fx-panel;
    -fx-background-radius: 8;
    -fx-border-color: -fx-border;
    -fx-border-radius: 8;
}
.list-cell {
    -fx-background-color: transparent;
    -fx-text-fill: -fx-text;
    -fx-padding: 6 10 6 10;
}
.list-cell:filled:hover {
    -fx-background-color: -fx-hover;
}
.list-cell:filled:selected {
    -fx-background-color: rgba(90, 176, 255, 0.25);
}

/* ---------- 菜单 ---------- */
.menu-bar {
    -fx-background-color: transparent;
    -fx-padding: 0;
}
.menu-bar .label,
.menu .label {
    -fx-text-fill: -fx-text;
}
.menu {
    -fx-background-color: transparent;
}
.context-menu {
    -fx-background-color: -fx-panel;
    -fx-border-color: -fx-border;
    -fx-border-radius: 8;
    -fx-background-radius: 8;
}
.menu-item {
    -fx-background-color: transparent;
}
.menu-item .label {
    -fx-text-fill: -fx-text;
}
.menu-item:focused {
    -fx-background-color: -fx-hover;
}

/* ---------- 分栏 ---------- */
.split-pane {
    -fx-background-color: transparent;
    -fx-padding: 0;
}
.split-pane-divider {
    -fx-background-color: -fx-border;
    -fx-padding: 0 1 0 1;
}

/* ---------- 表单控件 ---------- */
.slider .track {
    -fx-background-color: -fx-border;
    -fx-background-radius: 4;
}
.slider .thumb {
    -fx-background-color: -fx-accent;
    -fx-background-radius: 8;
}
.combo-box {
    -fx-background-color: -fx-panel;
    -fx-border-color: -fx-border;
    -fx-border-radius: 8;
    -fx-background-radius: 8;
}
.combo-box .list-cell {
    -fx-text-fill: -fx-text;
    -fx-background-color: transparent;
}
.combo-box-popup .list-view {
    -fx-border-color: -fx-border;
}
.spinner {
    -fx-background-color: -fx-panel;
    -fx-border-color: -fx-border;
    -fx-border-radius: 8;
    -fx-background-radius: 8;
}
.check-box {
    -fx-text-fill: -fx-text;
}
.check-box .box {
    -fx-background-color: -fx-panel;
    -fx-border-color: -fx-border;
    -fx-border-radius: 4;
    -fx-background-radius: 4;
}
.check-box:selected .mark {
    -fx-background-color: -fx-accent;
}
.toggle-button {
    -fx-background-color: transparent;
    -fx-text-fill: -fx-text-dim;
    -fx-background-radius: 8;
}
.toggle-button:selected {
    -fx-background-color: -fx-hover;
    -fx-text-fill: -fx-text;
}

/* ---------- 滚动条 ---------- */
.scroll-bar {
    -fx-background-color: transparent;
}
.scroll-bar .thumb {
    -fx-background-color: #3A3A4A;
    -fx-background-radius: 4;
}
.scroll-bar .increment-button,
.scroll-bar .decrement-button {
    -fx-pref-height: 0;
    -fx-pref-width: 0;
}

/* ---------- 提示与状态 ---------- */
.tooltip {
    -fx-background-color: -fx-hover;
    -fx-text-fill: -fx-text;
    -fx-border-color: -fx-border;
    -fx-background-radius: 8;
    -fx-border-radius: 8;
}
.progress-indicator {
    -fx-progress-color: -fx-accent;
}

/* ---------- 自定义组件 ---------- */
#status-bar {
    -fx-background-color: -fx-panel;
    -fx-border-color: -fx-border transparent transparent transparent;
}
#image-pane {
    -fx-background-color: #16161C;
    -fx-background-radius: 8;
}
.toast {
    -fx-background-color: #2C2C3A;
    -fx-background-radius: 8;
    -fx-border-color: -fx-border;
    -fx-border-radius: 8;
}
.toast-error {
    -fx-background-color: #3A2C30;
    -fx-border-color: -fx-danger;
    -fx-background-radius: 8;
    -fx-border-radius: 8;
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`（CSS 不参与编译，验证无路径/语法遗漏）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/theme src/main/resources/css/dark.css
git commit -m "feat: 暗色主题与统一动效工具

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: AnnotatedImageRenderer（全分辨率标注图）

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/service/AnnotatedImageRenderer.java`
- Test: `src/test/java/com/bingbaihanji/ocr/service/AnnotatedImageRendererTest.java`

- [ ] **Step 1: 写失败的测试**

新建 `src/test/java/com/bingbaihanji/ocr/service/AnnotatedImageRendererTest.java`：

```java
package com.bingbaihanji.ocr.service;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.model.ocr.result.OcrResult;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// 标注图渲染测试
class AnnotatedImageRendererTest {

    @Test
    void 绘制识别框与标签且不改变图像尺寸() {
        BufferedImage src = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.dispose();

        OcrResult result = OcrResult.builder()
                .lines(List.of(new OcrLine("你好", 0.9, 20, 20, 100, 30)))
                .build();

        BufferedImage out = AnnotatedImageRenderer.render(src, result);

        assertEquals(200, out.getWidth());
        assertEquals(100, out.getHeight());
        // 框线经过处的像素不再是纯白
        assertNotEquals(Color.WHITE.getRGB(), out.getRGB(20, 20));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=AnnotatedImageRendererTest`
Expected: 编译失败（`AnnotatedImageRenderer` 未定义）

- [ ] **Step 3: 实现 AnnotatedImageRenderer**

新建 `src/main/java/com/bingbaihanji/ocr/service/AnnotatedImageRenderer.java`：

```java
package com.bingbaihanji.ocr.service;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.model.ocr.result.OcrResult;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

// 全分辨率标注图：识别框 + 各行文字标签（「文件→保存」用；不动模型层 OcrVisualizer）
public final class AnnotatedImageRenderer {

    private static final Color BOX_COLOR = new Color(0x5A, 0xB0, 0xFF);
    private static final Color LABEL_BG = new Color(0x1B, 0x1B, 0x22, 200);
    private static final Color LABEL_FG = new Color(0xE8, 0xE8, 0xF0);
    private static final int BOX_THICKNESS = 2;
    private static final int FONT_SIZE = 16;

    private AnnotatedImageRenderer() {
    }

    // 在原图副本上绘制识别框与文字标签，输出尺寸与原图一致
    public static BufferedImage render(BufferedImage src, OcrResult result) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(loadFont());
            g.setStroke(new BasicStroke(BOX_THICKNESS));
            for (OcrLine line : result.getLines()) {
                float[] p = line.getPoints();
                int n = p.length / 2;
                int[] xs = new int[n];
                int[] ys = new int[n];
                int minX = Integer.MAX_VALUE;
                int minY = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    xs[i] = Math.round(p[i * 2]);
                    ys[i] = Math.round(p[i * 2 + 1]);
                    minX = Math.min(minX, xs[i]);
                    minY = Math.min(minY, ys[i]);
                }
                g.setColor(BOX_COLOR);
                g.drawPolygon(xs, ys, n);
                drawLabel(g, line.getText(), minX, minY);
            }
            return out;
        } finally {
            g.dispose();
        }
    }

    // 在框左上角绘制深底文字标签（越出图像时贴边）
    private static void drawLabel(Graphics2D g, String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 8;
        int h = fm.getHeight() + 4;
        int ly = Math.max(0, y - h);
        g.setColor(LABEL_BG);
        g.fill(new RoundRectangle2D.Float(x, ly, w, h, 6, 6));
        g.setColor(LABEL_FG);
        g.drawString(text, x + 4, ly + fm.getAscent() + 2);
    }

    // 优先加载内置仿宋，失败退回系统无衬线字体
    private static Font loadFont() {
        try (InputStream in = AnnotatedImageRenderer.class.getResourceAsStream("/fonts/simfang.ttf")) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) FONT_SIZE);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                return f;
            }
        } catch (Exception e) {
            // 字体加载失败不阻塞渲染
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=AnnotatedImageRendererTest`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/service/AnnotatedImageRenderer.java src/test/java/com/bingbaihanji/ocr/service/AnnotatedImageRendererTest.java
git commit -m "feat: 全分辨率标注图渲染（识别框+文字标签）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: RecognitionService（异步识别）

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/service/RecognitionService.java`
- Test: `src/test/java/com/bingbaihanji/ocr/service/RecognitionServiceTest.java`

关键语义：单线程串行推理；新任务提交后旧任务的回调**不再触发**（以最后一次提交为准）；UI 投递器可注入（测试直接 `Runnable::run`，生产 `Platform::runLater`）。

- [ ] **Step 1: 写失败的测试**

新建 `src/test/java/com/bingbaihanji/ocr/service/RecognitionServiceTest.java`：

```java
package com.bingbaihanji.ocr.service;

import com.bingbaihanji.model.ocr.api.OcrService;
import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.result.OcrBox;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.model.ocr.result.RecText;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 异步识别服务测试：回调投递与旧任务丢弃
class RecognitionServiceTest {

    private static BufferedImage img() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    }

    private static OcrResult result(String text) {
        return OcrResult.builder().text(text).build();
    }

    // 可控假识别服务：每次调用从队列取一个处理器
    static final class FakeOcrService implements OcrService {

        final BlockingQueue<Function<OcrConfig, OcrResult>> handlers = new LinkedBlockingQueue<>();
        volatile int calls = 0;

        @Override
        public OcrResult recognize(byte[] image, OcrConfig config) {
            calls++;
            try {
                return handlers.take().apply(config);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<OcrBox> detect(byte[] image, OcrConfig config) {
            return List.of();
        }

        @Override
        public List<RecText> recognizeCrops(List<byte[]> crops, OcrConfig config) {
            return List.of();
        }

        @Override
        public BufferedImage visualize(BufferedImage image, OcrResult result) {
            return image;
        }
    }

    @Test
    void 识别成功后回调携带结果() throws Exception {
        FakeOcrService fake = new FakeOcrService();
        fake.handlers.add(cfg -> result("hello"));
        try (RecognitionService svc = new RecognitionService(fake, Runnable::run)) {
            CompletableFuture<String> got = new CompletableFuture<>();
            svc.submit(img(), OcrConfig.defaults(),
                    r -> got.complete(r.getText()),
                    t -> got.completeExceptionally(t));
            assertEquals("hello", got.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void 新任务提交后旧任务回调被丢弃() throws Exception {
        FakeOcrService fake = new FakeOcrService();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        fake.handlers.add(cfg -> {
            firstStarted.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result("旧");
        });
        fake.handlers.add(cfg -> result("新"));

        try (RecognitionService svc = new RecognitionService(fake, Runnable::run)) {
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            svc.submit(img(), OcrConfig.defaults(), r -> received.add(r.getText()), t -> {
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            CompletableFuture<String> second = new CompletableFuture<>();
            svc.submit(img(), OcrConfig.defaults(),
                    r -> {
                        received.add(r.getText());
                        second.complete(r.getText());
                    },
                    t -> second.completeExceptionally(t));

            // 释放第一个任务后它会先完成，但其回调应被丢弃，只有「新」被投递
            releaseFirst.countDown();
            assertEquals("新", second.get(2, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertEquals(List.of("新"), received);
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=RecognitionServiceTest`
Expected: 编译失败（`RecognitionService` 未定义）

- [ ] **Step 3: 实现 RecognitionService**

新建 `src/main/java/com/bingbaihanji/ocr/service/RecognitionService.java`：

```java
package com.bingbaihanji.ocr.service;

import com.bingbaihanji.model.ocr.api.OnnxOcrService;
import com.bingbaihanji.model.ocr.api.OcrService;
import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.config.OcrProperties;
import com.bingbaihanji.model.ocr.result.OcrResult;
import javafx.application.Platform;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

// 异步识别服务：单线程串行推理 + 新任务替代旧任务 + 可注入 UI 线程投递
public final class RecognitionService implements AutoCloseable {

    private final OcrService ocr;
    private final Consumer<Runnable> uiRunner;
    private final ExecutorService exec;
    private final AtomicLong seq = new AtomicLong();

    // 生产构造：回调投递到 JavaFX 应用线程
    public RecognitionService(OcrService ocr) {
        this(ocr, Platform::runLater);
    }

    // 测试构造：uiRunner 可注入直接执行的实现
    public RecognitionService(OcrService ocr, Consumer<Runnable> uiRunner) {
        this.ocr = Objects.requireNonNull(ocr, "ocr");
        this.uiRunner = Objects.requireNonNull(uiRunner, "uiRunner");
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ocr-infer");
            t.setDaemon(true);
            return t;
        });
    }

    // 默认装配：内置模型套件（外部 model/ 目录可覆盖，见 OcrProperties.withBundledModels）
    public static RecognitionService createDefault(Path jarDir) {
        return new RecognitionService(new OnnxOcrService(OcrProperties.withBundledModels(jarDir)));
    }

    // 提交识别；最后一次提交为准，旧任务即使完成其回调也不再触发
    public void submit(BufferedImage image, OcrConfig config,
                       Consumer<OcrResult> onSuccess, Consumer<Throwable> onError) {
        long id = seq.incrementAndGet();
        exec.execute(() -> {
            try {
                OcrResult result = ocr.recognize(image, config);
                if (id == seq.get()) {
                    uiRunner.accept(() -> onSuccess.accept(result));
                }
            } catch (Throwable t) {
                if (id == seq.get()) {
                    uiRunner.accept(() -> onError.accept(t));
                }
            }
        });
    }

    @Override
    public void close() {
        exec.shutdownNow();
        ocr.close();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=RecognitionServiceTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/service/RecognitionService.java src/test/java/com/bingbaihanji/ocr/service/RecognitionServiceTest.java
git commit -m "feat: 异步识别服务（串行推理+新任务替代旧任务）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: BoxLayout（识别框坐标映射）

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/geom/BoxLayout.java`
- Test: `src/test/java/com/bingbaihanji/ocr/geom/BoxLayoutTest.java`

- [ ] **Step 1: 写失败的测试**

新建 `src/test/java/com/bingbaihanji/ocr/geom/BoxLayoutTest.java`：

```java
package com.bingbaihanji.ocr.geom;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

// 识别框坐标映射测试
class BoxLayoutTest {

    @Test
    void 外接矩形取极值() {
        float[] pts = {10, 20, 60, 20, 60, 50, 10, 50};
        Rectangle2D b = BoxLayout.bounds(pts);
        assertEquals(10, b.getMinX(), 1e-9);
        assertEquals(20, b.getMinY(), 1e-9);
        assertEquals(50, b.getWidth(), 1e-9);
        assertEquals(30, b.getHeight(), 1e-9);
    }

    @Test
    void 映射点集与外接矩形() {
        float[] pts = {10, 20, 60, 50};
        double[] m = BoxLayout.map(pts, 2, 5, 5);
        assertArrayEquals(new double[]{25, 45, 125, 105}, m, 1e-9);
        Rectangle2D b = BoxLayout.mapBounds(pts, 2, 5, 5);
        assertEquals(25, b.getMinX(), 1e-9);
        assertEquals(45, b.getMinY(), 1e-9);
        assertEquals(115, b.getWidth(), 1e-9);
        assertEquals(65, b.getHeight(), 1e-9);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=BoxLayoutTest`
Expected: 编译失败（`BoxLayout` 未定义）

- [ ] **Step 3: 实现 BoxLayout**

新建 `src/main/java/com/bingbaihanji/ocr/geom/BoxLayout.java`：

```java
package com.bingbaihanji.ocr.geom;

import javafx.geometry.Rectangle2D;

// 识别框坐标映射：图片坐标系 → 显示坐标系（纯函数）
public final class BoxLayout {

    private BoxLayout() {
    }

    // 点集外接矩形（图片坐标系）
    public static Rectangle2D bounds(float[] points) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    // 映射点集：显示坐标 = 图片坐标 × scale + offset
    public static double[] map(float[] points, double scale, double offX, double offY) {
        double[] out = new double[points.length];
        for (int i = 0; i < points.length; i += 2) {
            out[i] = points[i] * scale + offX;
            out[i + 1] = points[i + 1] * scale + offY;
        }
        return out;
    }

    // 外接矩形映射到显示坐标系
    public static Rectangle2D mapBounds(float[] points, double scale, double offX, double offY) {
        Rectangle2D b = bounds(points);
        return new Rectangle2D(
                b.getMinX() * scale + offX,
                b.getMinY() * scale + offY,
                b.getWidth() * scale,
                b.getHeight() * scale);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=BoxLayoutTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/geom/BoxLayout.java src/test/java/com/bingbaihanji/ocr/geom/BoxLayoutTest.java
git commit -m "feat: 识别框坐标映射

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: StatusBar + Toast

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/ui/homepage/StatusBar.java`
- Create: `src/main/java/com/bingbaihanji/ocr/ui/widget/Toast.java`

JavaFX 视图，无自动化测试（验收见 Task 16）。

- [ ] **Step 1: 实现 StatusBar**

新建 `src/main/java/com/bingbaihanji/ocr/ui/homepage/StatusBar.java`：

```java
package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

// 状态栏：状态文案 + 模型/行数/耗时，识别中显示加载动效
public class StatusBar extends HBox {

    private final Label status = new Label("就绪");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label info = new Label("");

    public StatusBar() {
        setId("status-bar");
        setSpacing(12);
        setPadding(new Insets(6, 12, 6, 12));
        spinner.setPrefSize(16, 16);
        spinner.setMinSize(16, 16);
        spinner.setVisible(false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(status, spinner, spacer, info);
    }

    public void setRunning() {
        status.setText("识别中…");
        spinner.setVisible(true);
    }

    public void setIdle() {
        spinner.setVisible(false);
        status.setText("就绪");
    }

    public void setError(String message) {
        spinner.setVisible(false);
        status.setText("出错：" + message);
    }

    public void setResultInfo(OcrModel model, int lines, long elapsedMillis) {
        info.setText("模型: " + model + " │ " + lines + " 行 │ " + elapsedMillis + " ms");
    }
}
```

- [ ] **Step 2: 实现 Toast**

新建 `src/main/java/com/bingbaihanji/ocr/ui/widget/Toast.java`：

```java
package com.bingbaihanji.ocr.ui.widget;

import com.bingbaihanji.ocr.theme.FxAnim;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

// 轻提示：顶部居中滑入，2 秒后淡出
public class Toast extends StackPane {

    private final Label label = new Label();
    private Timeline hideTimer;

    public Toast() {
        getStyleClass().add("toast");
        setVisible(false);
        setMouseTransparent(true);
        setPadding(new Insets(8, 16, 8, 16));
        label.setWrapText(true);
        getChildren().add(label);
    }

    public void show(String message) {
        show(message, false);
    }

    public void showError(String message) {
        show(message, true);
    }

    private void show(String message, boolean error) {
        label.setText(message);
        getStyleClass().setAll(error ? "toast-error" : "toast");
        setVisible(true);
        setOpacity(1);
        FxAnim.slideIn(this, 180);
        if (hideTimer != null) {
            hideTimer.stop();
        }
        hideTimer = new Timeline(new KeyFrame(Duration.millis(2000),
                e -> FxAnim.fadeOut(this, 200, () -> setVisible(false))));
        hideTimer.play();
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/ui/homepage/StatusBar.java src/main/java/com/bingbaihanji/ocr/ui/widget/Toast.java
git commit -m "feat: 状态栏与 Toast

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 10: ImagePane（缩放平移 + 识别框联动）

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/ui/homepage/ImagePane.java`

几何计算用 Task 8 的 `BoxLayout`；缩放以光标为锚点（`pan += (c - P) × (z - z')`，c 为变换前内容坐标，P 为内容中心）。

- [ ] **Step 1: 实现 ImagePane**

新建 `src/main/java/com/bingbaihanji/ocr/ui/homepage/ImagePane.java`：

```java
package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.ocr.geom.BoxLayout;
import com.bingbaihanji.ocr.theme.Theme;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

// 左侧图片区：等比显示 + 滚轮以光标为锚点缩放 + 拖拽平移 + 识别框叠加/行高亮联动
public class ImagePane extends StackPane {

    private static final double PAD = 16;
    private static final double MIN_SCALE = 0.2;
    private static final double MAX_SCALE = 8;

    private final ImageView imageView = new ImageView();
    private final Pane boxLayer = new Pane();
    private final StackPane content = new StackPane(imageView, boxLayer);
    private final Label placeholder = new Label("拖入图片或按 Ctrl+V 粘贴");
    private final Label sizeHint = new Label("");

    private final List<OcrLine> lines = new ArrayList<>();
    private final List<Polygon> boxes = new ArrayList<>();

    private BufferedImage source;
    private double baseScale = 1;
    private double zoom = 1;
    private double panX;
    private double panY;
    private int highlighted = -1;
    private IntConsumer onLineClick = i -> {
    };
    private double dragStartX;
    private double dragStartY;
    private double dragStartPanX;
    private double dragStartPanY;

    public ImagePane() {
        setId("image-pane");
        placeholder.getStyleClass().add("label-dim");
        sizeHint.getStyleClass().add("label-dim");
        getChildren().addAll(content, placeholder, sizeHint);
        StackPane.setAlignment(placeholder, javafx.geometry.Pos.CENTER);
        StackPane.setAlignment(sizeHint, javafx.geometry.Pos.BOTTOM_LEFT);
        StackPane.setMargin(sizeHint, new javafx.geometry.Insets(8));

        content.setManaged(false);
        content.setVisible(false);

        widthProperty().addListener((o, a, b) -> layoutImage());
        heightProperty().addListener((o, a, b) -> layoutImage());

        addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPress);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onDrag);
    }

    // 设置显示图片（null 等价于清空）
    public void setImage(BufferedImage img) {
        this.source = img;
        if (img == null) {
            clear();
            return;
        }
        Image fx = SwingFXUtils.toFXImage(img, null);
        imageView.setImage(fx);
        placeholder.setVisible(false);
        content.setVisible(true);
        sizeHint.setText(img.getWidth() + " × " + img.getHeight());
        zoom = 1;
        panX = 0;
        panY = 0;
        layoutImage();
    }

    // 清空图片与识别框
    public void clear() {
        source = null;
        imageView.setImage(null);
        boxLayer.getChildren().clear();
        boxes.clear();
        lines.clear();
        highlighted = -1;
        placeholder.setVisible(true);
        content.setVisible(false);
        sizeHint.setText("");
    }

    // 设置识别框（图片坐标系）
    public void setLines(List<OcrLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
        redrawBoxes();
    }

    // 高亮第 index 个识别框（-1 清除）
    public void highlightLine(int index) {
        highlighted = index;
        for (int i = 0; i < boxes.size(); i++) {
            boolean on = i == index;
            boxes.get(i).setStroke(on ? Color.web(Theme.ACCENT) : Color.web(Theme.ACCENT, 0.55));
            boxes.get(i).setStrokeWidth(on ? 3 : 1.5);
        }
    }

    public void clearHighlight() {
        highlightLine(-1);
    }

    // 点击识别框回调
    public void setOnLineClick(IntConsumer handler) {
        this.onLineClick = handler == null ? i -> {
        } : handler;
    }

    // ------------------------------------------------------------------ 布局

    private void layoutImage() {
        if (source == null) {
            return;
        }
        double availW = Math.max(1, getWidth() - 2 * PAD);
        double availH = Math.max(1, getHeight() - 2 * PAD);
        double iw = source.getWidth();
        double ih = source.getHeight();
        baseScale = Math.min(availW / iw, availH / ih);
        double dispW = iw * baseScale;
        double dispH = ih * baseScale;
        imageView.setFitWidth(dispW);
        imageView.setFitHeight(dispH);
        content.setPrefSize(dispW, dispH);
        content.setMinSize(dispW, dispH);
        content.setMaxSize(dispW, dispH);
        content.setPivotX(dispW / 2);
        content.setPivotY(dispH / 2);
        applyTransform();
        redrawBoxes();
    }

    private void applyTransform() {
        content.setScaleX(zoom);
        content.setScaleY(zoom);
        content.setTranslateX(panX);
        content.setTranslateY(panY);
    }

    private void redrawBoxes() {
        boxLayer.getChildren().clear();
        boxes.clear();
        for (OcrLine line : lines) {
            Rectangle2D b = BoxLayout.bounds(line.getPoints());
            double w = Math.max(2, b.getWidth() * baseScale);
            double h = Math.max(2, b.getHeight() * baseScale);
            Rectangle r = new Rectangle(b.getMinX() * baseScale, b.getMinY() * baseScale, w, h);
            r.setFill(Color.web(Theme.ACCENT, 0.08));
            r.setStroke(Color.web(Theme.ACCENT, 0.55));
            r.setStrokeWidth(1.5);
            final int idx = boxes.size();
            r.setOnMouseClicked(e -> {
                onLineClick.accept(idx);
                e.consume();
            });
            boxes.add(r);
            boxLayer.getChildren().add(r);
        }
        if (highlighted >= 0) {
            highlightLine(highlighted);
        }
    }

    // ------------------------------------------------------------------ 交互

    private void onScroll(ScrollEvent e) {
        if (source == null) {
            return;
        }
        double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
        double newZoom = Math.min(MAX_SCALE, Math.max(MIN_SCALE, zoom * factor));
        if (newZoom == zoom) {
            return;
        }
        // 以光标处内容点为锚点：pan += (c - P) × (z - z')
        Point2D c = content.sceneToLocal(e.getSceneX(), e.getSceneY());
        double px = content.getPivotX();
        double py = content.getPivotY();
        panX += (c.getX() - px) * (zoom - newZoom);
        panY += (c.getY() - py) * (zoom - newZoom);
        zoom = newZoom;
        applyTransform();
        e.consume();
    }

    private void onPress(MouseEvent e) {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
        dragStartPanX = panX;
        dragStartPanY = panY;
    }

    private void onDrag(MouseEvent e) {
        if (source == null || !e.isPrimaryButtonDown()) {
            return;
        }
        panX = dragStartPanX + (e.getSceneX() - dragStartX);
        panY = dragStartPanY + (e.getSceneY() - dragStartY);
        applyTransform();
        e.consume();
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/ui/homepage/ImagePane.java
git commit -m "feat: 图片区（缩放平移+识别框联动）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 11: ResultPane（文本 + 逐行联动）

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/ui/homepage/ResultPane.java`

- [ ] **Step 1: 实现 ResultPane**

新建 `src/main/java/com/bingbaihanji/ocr/ui/homepage/ResultPane.java`：

```java
package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.model.ocr.result.OcrLine;
import com.bingbaihanji.model.ocr.result.OcrResult;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

// 右侧结果面板：可编辑文本 + 逐行明细（悬停/点击与左图识别框联动）
public class ResultPane extends VBox {

    // 逐行明细项：序号、文本、置信度
    public record LineItem(int index, String text, double confidence) {
    }

    private final TextArea textArea = new TextArea();
    private final ListView<LineItem> list = new ListView<>();
    private final Button copyButton = new Button("复制");

    private IntConsumer onLineHover = i -> {
    };
    private Consumer<String> onLineClick = s -> {
    };

    public ResultPane() {
        setSpacing(8);
        setPadding(new Insets(4));

        Label textLabel = new Label("识别文本");
        textLabel.getStyleClass().add("label-dim");
        HBox header = new HBox(8, textLabel, copyButton);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        textArea.setPromptText("识别结果将显示在这里，可直接编辑；导出以编辑后内容为准");
        textArea.setWrapText(true);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        Label listLabel = new Label("逐行明细（悬停定位识别框，点击复制该行）");
        listLabel.getStyleClass().add("label-dim");

        list.setPrefHeight(180);
        list.setCellFactory(v -> new ListCell<>() {
            {
                hoverProperty().addListener((o, a, b) -> {
                    if (b && getItem() != null) {
                        onLineHover.accept(getItem().index());
                    } else if (!b) {
                        onLineHover.accept(-1);
                    }
                });
                setOnMouseClicked(e -> {
                    if (getItem() != null) {
                        onLineClick.accept(getItem().text());
                    }
                });
            }

            @Override
            protected void updateItem(LineItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label text = new Label((item.index() + 1) + ". " + item.text());
                    text.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(text, Priority.ALWAYS);
                    Label conf = new Label(String.format("%.2f", item.confidence()));
                    conf.getStyleClass().add("label-dim");
                    HBox row = new HBox(8, text, conf);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        getChildren().addAll(header, textArea, listLabel, list);
    }

    // 填充识别结果
    public void setResult(OcrResult result) {
        textArea.setText(result.getText());
        List<LineItem> items = new ArrayList<>();
        List<OcrLine> lines = result.getLines();
        for (int i = 0; i < lines.size(); i++) {
            OcrLine l = lines.get(i);
            items.add(new LineItem(i, l.getText(), l.getConfidence()));
        }
        list.setItems(FXCollections.observableArrayList(items));
    }

    // 清空
    public void clear() {
        textArea.clear();
        list.setItems(FXCollections.observableArrayList());
    }

    // 当前（可能被编辑过的）文本，导出以此为准
    public String currentText() {
        return textArea.getText();
    }

    // 选中并滚动到第 index 行（点识别框联动）
    public void selectLine(int index) {
        if (index >= 0 && index < list.getItems().size()) {
            list.getSelectionModel().select(index);
            list.scrollTo(index);
        }
    }

    public void setOnCopy(Runnable handler) {
        copyButton.setOnAction(e -> handler.run());
    }

    public void setOnLineHover(IntConsumer handler) {
        this.onLineHover = handler == null ? i -> {
        } : handler;
    }

    public void setOnLineClick(Consumer<String> handler) {
        this.onLineClick = handler == null ? s -> {
        } : handler;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/ui/homepage/ResultPane.java
git commit -m "feat: 结果面板（可编辑文本+逐行联动）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 12: AppMenuBar

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/ui/menu/AppMenuBar.java`

- [ ] **Step 1: 实现 AppMenuBar**

新建 `src/main/java/com/bingbaihanji/ocr/ui/menu/AppMenuBar.java`：

```java
package com.bingbaihanji.ocr.ui.menu;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

// 主菜单栏：文件（打开/导出/保存/退出）、设置（设置…）
public class AppMenuBar extends MenuBar {

    public AppMenuBar(Runnable onOpen, Runnable onExport, Runnable onSave,
                      Runnable onSettings, Runnable onExit) {
        Menu file = new Menu("文件");
        file.getItems().addAll(
                item("打开图片…", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), onOpen),
                item("导出文本…", new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), onExport),
                item("保存标注图…", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), onSave),
                new SeparatorMenuItem(),
                item("退出", null, onExit));

        Menu settings = new Menu("设置");
        settings.getItems().add(
                item("设置…", new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN), onSettings));

        getMenus().addAll(file, settings);
    }

    private static MenuItem item(String text, KeyCombination accel, Runnable action) {
        MenuItem mi = new MenuItem(text);
        if (accel != null) {
            mi.setAccelerator(accel);
        }
        mi.setOnAction(e -> action.run());
        return mi;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/ui/menu/AppMenuBar.java
git commit -m "feat: 主菜单栏（文件/设置）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 13: SettingsDialog

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/ui/settings/SettingsDialog.java`

自定义暗色窗口（`StageStyle.EXTENDED` + `HeaderBar`，与主窗一致），应用模态；在 `AppSettings` 副本上编辑，「确定/应用」回调 `onApply`，「取消/关闭」丢弃。快捷键页由计划 B 追加。

- [ ] **Step 1: 实现 SettingsDialog**

新建 `src/main/java/com/bingbaihanji/ocr/ui/settings/SettingsDialog.java`：

```java
package com.bingbaihanji.ocr.ui.settings;

import com.bingbaihanji.model.ocr.enums.OcrModel;
import com.bingbaihanji.ocr.settings.AppSettings;
import com.bingbaihanji.ocr.theme.FxAnim;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HeaderDragType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.function.Consumer;

// 设置对话框：识别 / 窗口 / 关于 三页（快捷键页由计划 B 追加）
public class SettingsDialog extends Stage {

    private final AppSettings edited;
    private final Consumer<AppSettings> onApply;

    public SettingsDialog(Window owner, AppSettings current, Consumer<AppSettings> onApply) {
        this.edited = current.copy();
        this.onApply = onApply;
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.EXTENDED);
        HeaderBar.setPrefButtonHeight(this, 0);
        setTitle("设置");
        setMinWidth(620);
        setMinHeight(420);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: -fx-bg;");

        HBox topBar = new HBox(createHeaderBar());
        topBar.setPadding(new Insets(4, 8, 8, 8));
        HBox.setHgrow(topBar.getChildren().get(0), Priority.ALWAYS);
        root.setTop(topBar);

        // 左侧导航
        ToggleGroup navGroup = new ToggleGroup();
        ToggleButton navRecognize = navButton("识别", navGroup, true);
        ToggleButton navWindow = navButton("窗口", navGroup, false);
        ToggleButton navAbout = navButton("关于", navGroup, false);
        VBox nav = new VBox(4, navRecognize, navWindow, navAbout);
        nav.setPadding(new Insets(8));
        nav.setPrefWidth(110);

        // 右侧面板
        StackPane cards = new StackPane();
        VBox recognizeCard = buildRecognizeCard();
        VBox windowCard = buildWindowCard();
        VBox aboutCard = buildAboutCard();
        cards.getChildren().addAll(recognizeCard, windowCard, aboutCard);
        windowCard.setVisible(false);
        aboutCard.setVisible(false);

        navGroup.selectedToggleProperty().addListener((o, a, b) -> {
            recognizeCard.setVisible(b == navRecognize);
            windowCard.setVisible(b == navWindow);
            aboutCard.setVisible(b == navAbout);
        });

        HBox center = new HBox(nav, cards);
        HBox.setHgrow(cards, Priority.ALWAYS);
        root.setCenter(center);

        // 底部按钮
        Button ok = new Button("确定");
        ok.getStyleClass().add("primary");
        ok.setOnAction(e -> {
            onApply.accept(edited);
            close();
        });
        Button apply = new Button("应用");
        apply.setOnAction(e -> onApply.accept(edited));
        Button cancel = new Button("取消");
        cancel.setOnAction(e -> close());
        HBox buttons = new HBox(8, new Region(), ok, apply, cancel);
        buttons.setPadding(new Insets(8));
        buttons.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(buttons.getChildren().get(0), Priority.ALWAYS);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 680, 460);
        scene.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        setScene(scene);
    }

    // ------------------------------------------------------------------ 面板

    private VBox buildRecognizeCard() {
        ComboBox<OcrModel> modelBox = new ComboBox<>(FXCollections.observableArrayList(OcrModel.values()));
        modelBox.setValue(edited.getModel());
        modelBox.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(OcrModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDescription());
            }
        });
        modelBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(OcrModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDescription());
            }
        });
        modelBox.valueProperty().addListener((o, a, b) -> edited.setModel(b));

        Slider confidence = new Slider(0, 1, edited.getMinConfidence());
        confidence.setMajorTickUnit(0.1);
        confidence.setMinorTickCount(0);
        Label confValue = new Label(String.format("%.2f", edited.getMinConfidence()));
        confValue.getStyleClass().add("label-dim");
        confidence.valueProperty().addListener((o, a, b) -> {
            edited.setMinConfidence(b.doubleValue());
            confValue.setText(String.format("%.2f", b.doubleValue()));
        });

        Spinner<Integer> maxSide = new Spinner<>();
        maxSide.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(32, 4096, edited.getMaxSideLen(), 64));
        maxSide.setEditable(true);
        maxSide.valueProperty().addListener((o, a, b) -> edited.setMaxSideLen(b));

        CheckBox rotate = new CheckBox("启用方向分类（cls，纠正倒置文字）");
        rotate.setSelected(edited.isEnableRotate());
        rotate.selectedProperty().addListener((o, a, b) -> edited.setEnableRotate(b));

        GridPane grid = formGrid();
        grid.addRow(0, new Label("识别模型"), modelBox);
        grid.addRow(1, new Label("最低置信度"), new HBox(8, confidence, confValue));
        grid.addRow(2, new Label("预处理最大边长"), maxSide);
        grid.addRow(3, new Label("方向分类"), rotate);

        Label hint = new Label("识别参数在下一次识别时生效，无需重启。");
        hint.getStyleClass().add("label-dim");

        VBox box = new VBox(12, grid, hint);
        box.setPadding(new Insets(16));
        VBox.setVgrow(grid, Priority.ALWAYS);
        return box;
    }

    private VBox buildWindowCard() {
        CheckBox remember = new CheckBox("记住主窗口位置与大小");
        remember.setSelected(edited.isRememberWindow());
        remember.selectedProperty().addListener((o, a, b) -> edited.setRememberWindow(b));

        CheckBox clearClipboard = new CheckBox("退出时清空系统剪贴板");
        clearClipboard.setSelected(edited.isClearClipboardOnExit());
        clearClipboard.selectedProperty().addListener((o, a, b) -> edited.setClearClipboardOnExit(b));

        VBox box = new VBox(12, remember, clearClipboard);
        box.setPadding(new Insets(16));
        return box;
    }

    private VBox buildAboutCard() {
        Label name = new Label("显符OCR 1.0");
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label engine = new Label("识别引擎：PaddleOCR ONNX 套件（ppocr v4 / v5 / v6 tiny·small·medium）");
        Label privacy = new Label("完全本地推理，不上传任何数据。");
        Label models = new Label("模型文件内置并支持 jar 同目录 model/ 外部覆盖。");
        engine.getStyleClass().add("label-dim");
        privacy.getStyleClass().add("label-dim");
        models.getStyleClass().add("label-dim");
        VBox box = new VBox(8, name, engine, privacy, models);
        box.setPadding(new Insets(16));
        return box;
    }

    // ------------------------------------------------------------------ 工具

    private HeaderBar createHeaderBar() {
        HeaderBar bar = new HeaderBar();
        Label title = new Label("设置");
        bar.setCenter(title);
        HeaderBar.setDragType(title, HeaderDragType.DRAGGABLE_SUBTREE);
        Button closeBtn = new Button("✕");
        HeaderBar.setButtonType(closeBtn, HeaderButtonType.CLOSE);
        bar.setTrailing(closeBtn);
        return bar;
    }

    private static ToggleButton navButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setSelected(selected);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        return grid;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/ui/settings/SettingsDialog.java
git commit -m "feat: 设置对话框（识别/窗口/关于）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 14: AppController（事件装配与识别流）

**Files:**
- Create: `src/main/java/com/bingbaihanji/ocr/ui/AppController.java`

职责：粘贴/拖放/打开/导出/保存/设置/识别流/窗口状态。主窗口组件经 Task 15 的 `Homepage` 访问器获取（两任务共享接口，已锁定）：`getView()`、`imagePane()`、`resultPane()`、`statusBar()`、`toast()`、`rootPane()`、`openButton()`。

- [ ] **Step 1: 实现 AppController**

新建 `src/main/java/com/bingbaihanji/ocr/ui/AppController.java`：

```java
package com.bingbaihanji.ocr.ui;

import com.bingbaihanji.model.ocr.config.OcrConfig;
import com.bingbaihanji.model.ocr.result.OcrResult;
import com.bingbaihanji.ocr.service.AnnotatedImageRenderer;
import com.bingbaihanji.ocr.service.RecognitionService;
import com.bingbaihanji.ocr.settings.AppSettings;
import com.bingbaihanji.ocr.settings.SettingsStore;
import com.bingbaihanji.ocr.ui.homepage.Homepage;
import com.bingbaihanji.ocr.ui.menu.AppMenuBar;
import com.bingbaihanji.ocr.ui.settings.SettingsDialog;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

// 应用控制器：装配主窗口事件与识别流
public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    private final Stage stage;
    private final RecognitionService recognition;
    private final AppSettings settings;
    private final Path jarDir;
    private final Homepage view;

    private BufferedImage currentImage;
    private OcrResult lastResult;

    public AppController(Stage stage, RecognitionService recognition, AppSettings settings, Path jarDir) {
        this.stage = stage;
        this.recognition = recognition;
        this.settings = settings;
        this.jarDir = jarDir;
        AppMenuBar menuBar = new AppMenuBar(
                this::openImage, this::exportText, this::saveAnnotated, this::openSettings, stage::close);
        this.view = new Homepage(menuBar);
        wire();
    }

    public Homepage getView() {
        return view;
    }

    // 启动后调用：后台预热当前模型
    public void start() {
        BufferedImage warm = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        recognition.submit(warm, buildConfig(), r -> {
        }, t -> view.toast().showError("模型预热失败: " + t.getMessage()));
    }

    // 退出前保存窗口状态到 settings
    public void persistWindowState() {
        if (!settings.isRememberWindow()) {
            return;
        }
        settings.setWindowX(stage.getX());
        settings.setWindowY(stage.getY());
        settings.setWindowWidth(stage.getWidth());
        settings.setWindowHeight(stage.getHeight());
        settings.setWindowMaximized(stage.isMaximized());
    }

    // 启动时恢复窗口状态（在 show 之前调用）
    public void restoreWindowState() {
        if (!settings.isRememberWindow()) {
            return;
        }
        double x = settings.getWindowX();
        double y = settings.getWindowY();
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            stage.setX(x);
            stage.setY(y);
        }
        stage.setWidth(settings.getWindowWidth());
        stage.setHeight(settings.getWindowHeight());
        if (settings.isWindowMaximized()) {
            stage.setMaximized(true);
        }
    }

    // ------------------------------------------------------------------ 装配

    private void wire() {
        view.openButton().setOnAction(e -> openImage());
        view.resultPane().setOnCopy(this::copyText);
        view.resultPane().setOnLineHover(i -> {
            if (i >= 0) {
                view.imagePane().highlightLine(i);
            } else {
                view.imagePane().clearHighlight();
            }
        });
        view.resultPane().setOnLineClick(this::copyLine);
        view.imagePane().setOnLineClick(view.resultPane()::selectLine);

        view.addEventFilter(KeyEvent.KEY_PRESSED, this::handlePasteHotkey);
        view.rootPane().setOnDragOver(this::handleDragOver);
        view.rootPane().setOnDragDropped(this::handleDragDropped);
    }

    // Ctrl+V 剪贴板有图则粘贴并识别；无图放行给文本控件
    private void handlePasteHotkey(KeyEvent e) {
        if (!e.isShortcutDown() || e.getCode() != KeyCode.V) {
            return;
        }
        Clipboard cb = Clipboard.getSystemClipboard();
        if (!cb.hasImage()) {
            return;
        }
        Image img = cb.getImage();
        if (img == null) {
            return;
        }
        e.consume();
        showImage(SwingFXUtils.fromFXImage(img, null), "剪贴板图片");
    }

    private void handleDragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (db.hasFiles() || db.hasImage()) {
            e.acceptTransferModes(TransferMode.COPY);
        }
        e.consume();
    }

    private void handleDragDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        boolean ok = false;
        if (db.hasFiles()) {
            for (File f : db.getFiles()) {
                if (isImageFile(f.getName())) {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) {
                            showImage(img, f.getName());
                            ok = true;
                            break;
                        }
                    } catch (IOException ex) {
                        view.toast().showError("读取失败: " + ex.getMessage());
                    }
                }
            }
        } else if (db.hasImage() && db.getImage() != null) {
            showImage(SwingFXUtils.fromFXImage(db.getImage(), null), "拖入图片");
            ok = true;
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    // ------------------------------------------------------------------ 识别流

    private void showImage(BufferedImage img, String source) {
        currentImage = img;
        lastResult = null;
        view.imagePane().setImage(img);
        view.resultPane().clear();
        log.info("载入图片: {} ({}×{})", source, img.getWidth(), img.getHeight());
        recognize();
    }

    private void recognize() {
        if (currentImage == null) {
            return;
        }
        view.statusBar().setRunning();
        recognition.submit(currentImage, buildConfig(), this::onResult, this::onRecognizeError);
    }

    private OcrConfig buildConfig() {
        return OcrConfig.builder()
                .model(settings.getModel())
                .minConfidence(settings.getMinConfidence())
                .maxSideLen(settings.getMaxSideLen())
                .enableRotate(settings.isEnableRotate())
                .build();
    }

    private void onResult(OcrResult r) {
        lastResult = r;
        view.imagePane().setLines(r.getLines());
        view.resultPane().setResult(r);
        view.statusBar().setIdle();
        view.statusBar().setResultInfo(r.getModel(), r.getLines().size(), r.getElapsedMillis());
        if (r.getLines().isEmpty()) {
            view.toast().show("未识别到文字");
        }
    }

    private void onRecognizeError(Throwable t) {
        log.error("识别失败", t);
        view.statusBar().setError(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        view.toast().showError("识别失败: " + t.getMessage());
    }

    // ------------------------------------------------------------------ 文件

    private void openImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("打开图片");
        fc.getExtensionFilters().add(imageFilter());
        File init = existingDir(settings.getLastOpenDir());
        if (init != null) {
            fc.setInitialDirectory(init);
        }
        File f = fc.showOpenDialog(stage);
        if (f == null) {
            return;
        }
        settings.setLastOpenDir(f.getParentFile().getAbsolutePath());
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                throw new IOException("无法解码图片: " + f.getName());
            }
            showImage(img, f.getName());
        } catch (IOException e) {
            view.toast().showError("打开失败: " + e.getMessage());
        }
    }

    private void exportText() {
        String text = view.resultPane().currentText();
        if (text == null || text.isBlank()) {
            view.toast().show("暂无识别结果可导出");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("导出文本");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件 (*.txt)", "*.txt"));
        fc.setInitialFileName("识别结果.txt");
        File init = existingDir(settings.getLastExportDir());
        fc.setInitialDirectory(init != null ? init : exportsDir().toFile());
        File f = fc.showSaveDialog(stage);
        if (f == null) {
            return;
        }
        try {
            Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
            settings.setLastExportDir(f.getParentFile().getAbsolutePath());
            view.toast().show("已导出: " + f.getName());
        } catch (IOException e) {
            view.toast().showError("导出失败: " + e.getMessage());
        }
    }

    private void saveAnnotated() {
        if (currentImage == null || lastResult == null) {
            view.toast().show("暂无可保存的识别结果");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("保存标注图");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片 (*.png)", "*.png"));
        fc.setInitialFileName("标注图.png");
        File init = existingDir(settings.getLastExportDir());
        fc.setInitialDirectory(init != null ? init : exportsDir().toFile());
        File f = fc.showSaveDialog(stage);
        if (f == null) {
            return;
        }
        try {
            BufferedImage out = AnnotatedImageRenderer.render(currentImage, lastResult);
            ImageIO.write(out, "png", f);
            settings.setLastExportDir(f.getParentFile().getAbsolutePath());
            view.toast().show("已保存: " + f.getName());
        } catch (IOException e) {
            view.toast().showError("保存失败: " + e.getMessage());
        }
    }

    private void openSettings() {
        new SettingsDialog(stage, settings, updated -> {
            settings.copyFrom(updated);
            try {
                SettingsStore.save(jarDir, settings);
                view.toast().show("设置已保存");
            } catch (IOException e) {
                view.toast().showError("设置保存失败: " + e.getMessage());
            }
        }).showAndWait();
    }

    // ------------------------------------------------------------------ 剪贴板

    private void copyText() {
        String text = view.resultPane().currentText();
        if (text == null || text.isBlank()) {
            view.toast().show("暂无可复制的文本");
            return;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
        view.toast().show("已复制全文");
    }

    private void copyLine(String line) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(line);
        Clipboard.getSystemClipboard().setContent(cc);
        view.toast().show("已复制该行");
    }

    // ------------------------------------------------------------------ 工具

    private Path exportsDir() {
        Path dir = jarDir.resolve("exports");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建导出目录失败: {}", e.toString());
        }
        return dir;
    }

    private static File existingDir(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File f = new File(path);
        return f.isDirectory() ? f : null;
    }

    private static FileChooser.ExtensionFilter imageFilter() {
        return new FileChooser.ExtensionFilter(
                "图片文件 (*.png, *.jpg, *.jpeg, *.bmp, *.gif, *.webp)",
                "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp");
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".gif") || lower.endsWith(".webp");
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`（本任务依赖 Task 15 的 `Homepage` 访问器，若先行编译本任务，预期报 `Homepage` 缺少访问器——继续 Task 15 后全量验证）

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/ui/AppController.java
git commit -m "feat: 应用控制器（粘贴/拖放/打开/导出/保存/识别流）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 15: Homepage 重写 + XianFuOCRApplication 生命周期

**Files:**
- Rewrite: `src/main/java/com/bingbaihanji/ocr/ui/homepage/Homepage.java`
- Rewrite: `src/main/java/com/bingbaihanji/ocr/ui/XianFuOCRApplication.java`

**与既有代码的衔接**：`Homepage` 现有 `HeaderBar` 自定义标题栏逻辑（logo/标题/最小化/最大化/关闭 + `Insets(4, 8, 8, 8)` 缩放边框留白）原样保留，只重组内容区。

- [ ] **Step 1: 重写 Homepage**

用以下内容**整体替换** `src/main/java/com/bingbaihanji/ocr/ui/homepage/Homepage.java`：

```java
package com.bingbaihanji.ocr.ui.homepage;

import com.bingbaihanji.ocr.ui.menu.AppMenuBar;
import com.bingbaihanji.ocr.ui.widget.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.InputStream;

// 主窗口：HeaderBar 标题栏 + 菜单/工具行 + 左图右文 + 状态栏 + Toast 覆盖层
public class Homepage extends StackPane {

    private static final double ICON_SIZE = 24;
    private static final double CONTROL_SPACING = 10;
    private static final String TITLE = "显符OCR";

    private static final String ICON_LOGO = "/icon/logo.png";
    private static final String ICON_MIN = "/icon/min.png";
    private static final String ICON_MAX = "/icon/max.png";
    private static final String ICON_CLOSE = "/icon/close.png";

    private final BorderPane rootPane = new BorderPane();
    private final ImagePane imagePane = new ImagePane();
    private final ResultPane resultPane = new ResultPane();
    private final StatusBar statusBar = new StatusBar();
    private final Toast toast = new Toast();
    private final Button openButton = new Button("打开图片");

    public Homepage(AppMenuBar menuBar) {
        setId("homepage");

        HBox topBar = new HBox(createHeaderBar());
        topBar.setPadding(new Insets(4, 8, 8, 8));
        HBox.setHgrow(topBar.getChildren().get(0), Priority.ALWAYS);

        HBox toolRow = new HBox(menuBar, openButton);
        toolRow.setAlignment(Pos.CENTER_LEFT);
        toolRow.setPadding(new Insets(0, 8, 8, 8));
        HBox.setHgrow(menuBar, Priority.ALWAYS);

        SplitPane split = new SplitPane(imagePane, resultPane);
        split.setDividerPositions(0.5);

        StackPane center = new StackPane(split, toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(12));

        rootPane.setTop(new VBox(topBar, toolRow));
        rootPane.setCenter(center);
        rootPane.setBottom(statusBar);
        getChildren().add(rootPane);
    }

    // ------------------------------------------------------------------ 访问器（AppController 装配用）

    public BorderPane rootPane() {
        return rootPane;
    }

    public ImagePane imagePane() {
        return imagePane;
    }

    public ResultPane resultPane() {
        return resultPane;
    }

    public StatusBar statusBar() {
        return statusBar;
    }

    public Toast toast() {
        return toast;
    }

    public Button openButton() {
        return openButton;
    }

    // ------------------------------------------------------------------ 构建

    private HeaderBar createHeaderBar() {
        HeaderBar headerBar = new HeaderBar();

        Label title = new Label(TITLE);
        headerBar.setCenter(title);
        HeaderBar.setDragType(title, HeaderDragType.DRAGGABLE_SUBTREE);

        headerBar.setLeading(createIconView(ICON_LOGO));
        headerBar.setTrailing(createWindowControls());

        return headerBar;
    }

    private HBox createWindowControls() {
        HBox controls = new HBox(CONTROL_SPACING);
        controls.getChildren().addAll(
                createControlButton(ICON_MIN, HeaderButtonType.ICONIFY),
                createControlButton(ICON_MAX, HeaderButtonType.MAXIMIZE),
                createControlButton(ICON_CLOSE, HeaderButtonType.CLOSE));
        return controls;
    }

    private Button createControlButton(String iconPath, HeaderButtonType type) {
        Button button = new Button();
        button.setGraphic(createIconView(iconPath));
        HeaderBar.setButtonType(button, type);
        return button;
    }

    // ------------------------------------------------------------------ 工具

    private ImageView createIconView(String resourcePath) {
        ImageView view = new ImageView(loadImage(resourcePath));
        view.setFitWidth(ICON_SIZE);
        view.setFitHeight(ICON_SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    private Image loadImage(String resourcePath) {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("图标资源未找到: " + resourcePath);
        }
        return new Image(in);
    }
}
```

- [ ] **Step 2: 重写 XianFuOCRApplication**

用以下内容**整体替换** `src/main/java/com/bingbaihanji/ocr/ui/XianFuOCRApplication.java`：

```java
package com.bingbaihanji.ocr.ui;

import com.bingbaihanji.model.cli.NativeBootstrap;
import com.bingbaihanji.ocr.service.RecognitionService;
import com.bingbaihanji.ocr.settings.AppSettings;
import com.bingbaihanji.ocr.settings.SettingsStore;
import com.bingbaihanji.ocr.theme.FxAnim;
import com.bingbaihanji.ocr.theme.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.HeaderBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

// 应用入口：装配设置/识别服务/主窗口，管理生命周期
public class XianFuOCRApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(XianFuOCRApplication.class);

    private Path jarDir;
    private AppSettings settings;
    private RecognitionService recognition;
    private AppController controller;
    private boolean shutDown;

    @Override
    public void init() throws Exception {
        super.init();
        // 原生库引导（必须早于任何 ONNX/OpenCV 调用；init 在 FX 线程外执行）
        NativeBootstrap.bootstrap();
        jarDir = NativeBootstrap.jarDir();
        settings = SettingsStore.load(jarDir);
        recognition = RecognitionService.createDefault(jarDir);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.EXTENDED);
        HeaderBar.setPrefButtonHeight(primaryStage, 0);

        controller = new AppController(primaryStage, recognition, settings, jarDir);

        Scene scene = new Scene(controller.getView(),
                settings.getWindowWidth(), settings.getWindowHeight());
        Theme.apply(scene);

        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icon/logo.png")));
        primaryStage.setOnCloseRequest(e -> shutdown());
        controller.restoreWindowState();

        primaryStage.show();
        FxAnim.fadeIn(controller.getView());
        controller.start();
    }

    @Override
    public void stop() {
        shutdown();
    }

    // 幂等收尾：存窗口状态与配置、释放识别资源
    private void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        if (controller != null) {
            controller.persistWindowState();
        }
        if (settings != null && jarDir != null) {
            try {
                SettingsStore.save(jarDir, settings);
            } catch (IOException e) {
                log.warn("保存设置失败: {}", e.toString());
            }
        }
        if (settings != null && settings.isClearClipboardOnExit()) {
            javafx.scene.input.Clipboard.getSystemClipboard().clear();
        }
        if (recognition != null) {
            recognition.close();
        }
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}
```

- [ ] **Step 3: 全量编译 + 测试**

Run: `mvn test`
Expected: `Tests run: 14, Failures: 0, Errors: 0`（OcrPropertiesTest 2 + JsonTest 4 + SettingsStoreTest 3 + AnnotatedImageRendererTest 1 + RecognitionServiceTest 2 + BoxLayoutTest 2），`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/bingbaihanji/ocr/ui/homepage/Homepage.java src/main/java/com/bingbaihanji/ocr/ui/XianFuOCRApplication.java
git commit -m "feat: 主窗口装配与应用生命周期

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 16: 全量验证与手工验收

**Files:** 无新文件（修复过程中如需改动，随修复提交）

- [ ] **Step 1: 全量测试**

Run: `mvn test`
Expected: `Tests run: 14, Failures: 0, Errors: 0`，`BUILD SUCCESS`

- [ ] **Step 2: 打包**

Run: `mvn -DskipTests package`
Expected: `BUILD SUCCESS`，产出 `target/xianfu-1.0-SNAPSHOT.jar` 并拷贝到 `bin/xianfu-1.0-SNAPSHOT.jar`

- [ ] **Step 3: 启动冒烟**

Run（IDEA 运行 `XianFuOCRApplication.main`，或命令行）：
```
java -cp bin/xianfu-1.0-SNAPSHOT.jar com.bingbaihanji.ocr.ui.XianFuOCRApplication
```
Expected: 暗色主窗口出现，标题栏显示「显符OCR」，无异常栈迹

- [ ] **Step 4: 手工验收清单（逐项确认）**

1. 粘贴：复制一张截图 → 主窗口按 Ctrl+V → 左图显示 → 自动识别 → 右侧出文本与逐行明细
2. 联动：悬停逐行明细 → 左图对应框高亮；点击识别框 → 列表滚动选中该行；点击明细行 → Toast「已复制该行」
3. 缩放平移：滚轮以光标为锚点缩放图片（框跟随）；按住拖拽平移
4. 拖放：拖入一张 PNG → 显示并识别；拖入 .txt → 无副作用
5. 菜单「文件→打开图片…」（Ctrl+O）→ 选择图片 → 识别
6. 菜单「文件→导出文本…」（Ctrl+E）→ 保存 TXT → 打开确认内容与 TextArea 一致（含手工编辑）
7. 菜单「文件→保存标注图…」（Ctrl+S）→ 保存 PNG → 打开确认识别框与文字标签、分辨率与原图一致
8. 设置：改模型为 v6 tiny、置信度 0.8、勾选方向分类 → 重启应用 → 参数保留；识别正常
9. 设置「窗口」：拖动/缩放主窗口后退出 → 重启 → 窗口位置尺寸恢复
10. 坏配置：退出应用，把 `config/settings.json` 改成非法 JSON → 重启 → 应用正常启动且生成 `settings.bad.json`
11. 空态：启动未放图片时显示「拖入图片或按 Ctrl+V 粘贴」占位
12. 确认运行时只在 jar 目录产生 `config/`（及可选 `exports/`），user.home / AppData 无新增文件

- [ ] **Step 5: 修复与提交**

清单未全过则修复后重复 Step 1-4；全部通过后提交（如有修复）：

```bash
git add -A
git commit -m "fix: 计划 A 验收问题修复

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Self-Review 记录

1. **规格覆盖（计划 A 范围）**：主界面布局与联动（T10/T11/T15）、粘贴/拖放/打开/导出/保存（T14）、异步识别+取消（T7/T14）、设置三页（T13）、配置持久化+损坏恢复+窗口状态（T4/T14/T15）、暗色主题动效（T5/T9）、标注图保存（T6）、v6 三档模型（T2）、错误处理（T9 Toast + T14 onError + T4 坏配置 + T15 预热）。截图/贴图/全局热键/快捷键页 → 计划 B（按约定不在本计划）。
2. **占位符**：无 TBD/TODO/「类似 Task N」；每个代码步骤均给完整可编译代码。
3. **类型一致性**：`Homepage` 访问器（`rootPane()/imagePane()/resultPane()/statusBar()/toast()/openButton()`）与 `AppController` 调用一致；`AppSettings` 字段名与 `SettingsStore.toMap/fromMap` 键一致；`RecognitionException` 语义核对为 `RecognitionService.submit`（命名统一）；`OcrPropertiesTest` 使用的 getter 与 `PipelinePaths` 实际签名一致（已核实）。
