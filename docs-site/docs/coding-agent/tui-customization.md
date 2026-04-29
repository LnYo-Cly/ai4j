---
sidebar_position: 10
---

# TUI 定制与主题

当前 TUI 不是一个“只能改颜色的黑盒”，但它也还不是插件化 UI 平台。

最准确的理解是：

- 使用层已经有一套可直接落地的 theme / config 机制
- Java 层还保留了 renderer / runtime / factory 级扩展点
- 但这些扩展仍然是代码注入式定制，不是前端插件市场

---

## 1. 先看 TUI 装配链

当前 TUI 相关装配大致会走这条链：

```text
CodeCommand -> CodingCliSessionRunner
  -> TuiConfigManager.load(...)
  -> TuiConfigManager.resolveTheme(...)
  -> DefaultCodingCliTuiFactory.create(...)
  -> TuiSessionView
  -> AppendOnlyTuiRuntime 或 AnsiTuiRuntime
```

这里最重要的是：

- theme、config、renderer、runtime 是分层装配的
- 不是一个 `TUI` 对象自己把所有事情都做完

这也是为什么你能分别改：

- 配置
- 主题
- 渲染器
- runtime 行为

---

## 2. 当前可直接使用的定制点

最直接可用的是两层：

- theme
- `tui.json`

### 2.1 CLI 启动参数里的 theme override

```text
--theme <name>
```

这个值来自 `CodeCommandOptionsParser`，随后被 `TuiConfigManager.load(overrideTheme)` 读入。

一个很关键的细节是：

- `--theme` 只是本次启动的 in-memory override
- 它不会自动落盘到 `tui.json`

### 2.2 会话内 `/theme`

当前 `/theme` 命令支持：

```text
/theme
/theme <name>
```

当你执行 `/theme <name>` 时，`CodingCliSessionRunner.applyTheme(...)` 会调用：

- `TuiConfigManager.switchTheme(themeName)`

而这个方法会：

1. 校验主题是否存在
2. 读取当前配置
3. 把 `config.theme` 切到新主题
4. 保存到 workspace `tui.json`

所以 `/theme` 是持久化切换，不是一次性预览。

---

## 3. 主题查找顺序不是拍脑袋决定的

`TuiConfigManager.resolveTheme(name)` 当前查找顺序非常明确：

1. `<workspace>/.ai4j/themes/<name>.json`
2. `~/.ai4j/themes/<name>.json`
3. 内置资源 `/io/github/lnyocly/ai4j/tui/themes/<name>.json`
4. 如果还找不到且不是 `default`，回退到 `default`
5. 最后再兜底成代码生成的默认主题

所以当前主题优先级是：

- workspace 自定义
- home 自定义
- built-in
- hardcoded fallback

这意味着团队完全可以：

- 在仓库里放一份 repo-specific theme

而不影响用户 home 目录里的其他 TUI 使用场景。

---

## 4. 配置文件本身有两层，但不是字段级 merge

当前支持的配置文件位置是：

- `<workspace>/.ai4j/tui.json`
- `~/.ai4j/tui.json`

很多人会下意识以为：

- home 配置提供默认值
- workspace 配置只覆盖个别字段

但当前 `TuiConfigManager.merge(base, override)` 不是字段级 merge。

它的行为更接近：

- 如果 workspace config 存在，就直接用 workspace config
- 否则才回退到 home config

所以当前语义是：

- 文件级 override

而不是：

- 字段级层叠合并

这点非常关键，因为它决定了你不能假设：

- home 里设置了 `showFooter=false`
- workspace 只写了 `theme=ocean`
- 最后一定会保留 `showFooter=false`

当前实现不保证这种字段级继承。

---

## 5. `tui.json` 当前真正控制哪些行为

`TuiConfig` 当前核心字段包括：

- `theme`
- `denseMode`
- `showTimestamps`
- `showFooter`
- `maxEvents`
- `useAlternateScreen`

同时 `TuiConfigManager.normalize(config)` 会保证：

- 空 theme 回退成 `default`
- `maxEvents <= 0` 时回退成 `10`

所以这里也不是“任意值原样放行”，而是有最小归一化规则。

---

## 6. built-in themes 只是起点，不是全部

当前 `TuiConfigManager` 内置的主题名字是：

- `default`
- `amber`
- `ocean`
- `matrix`
- `github-dark`
- `github-light`

但 `listThemeNames()` 会把三类来源合并：

1. built-in names
2. home 目录自定义 theme
3. workspace 目录自定义 theme

这也是为什么 `/theme` 列出来的不一定只有内置主题。

它会把用户和仓库自定义的主题一并列出来。

---

## 7. 主题文件里哪些字段真的重要

`TuiTheme` 会被 `TuiConfigManager.normalize(theme, fallbackName)` 补齐大量默认值。

当前重点字段包括：

- `brand`
- `accent`
- `success`
- `warning`
- `danger`
- `text`
- `muted`
- `panelBorder`
- `panelTitle`
- `badgeForeground`
- `codeBackground`
- `codeBorder`
- `codeText`
- `codeKeyword`
- `codeString`
- `codeComment`
- `codeNumber`

这说明主题不只是“主色 + 辅色”。

它已经覆盖了：

- transcript
- panel
- badge
- code block
- syntax highlight

如果你只改一两个字段，其他字段会继续沿用 normalize 后的默认值。

---

## 8. `DefaultCodingCliTuiFactory` 真正决定了什么

这是当前最值得直接读的开发层入口。

它在 `create(...)` 里做四件事：

1. 读取 `TuiConfig`
2. 解析 `TuiTheme`
3. 构造 `TuiSessionView` 作为 renderer
4. 根据 terminal 和 `useAlternateScreen` 选择 runtime

也就是说，它不只是“生产一个 TUI 对象”，而是在决定：

- 用什么配置
- 用什么主题
- 用什么 renderer
- 用什么 runtime backend

---

## 9. 为什么会在 `AppendOnlyTuiRuntime` 和 `AnsiTuiRuntime` 之间分叉

当前 runtime 选择规则是：

- 如果 `useAlternateScreen=false` 且 terminal 是 `JlineTerminalIO`
  - 使用 `AppendOnlyTuiRuntime`
- 否则
  - 使用 `AnsiTuiRuntime`

这意味着 `useAlternateScreen` 不是一个纯视觉偏好。

它会直接影响 runtime backend 的选择。

工程上可以这样理解：

### `AppendOnlyTuiRuntime`

更接近：

- 追加式终端输出
- 对 JLINE 终端更友好

### `AnsiTuiRuntime`

更接近：

- 带 renderer 的完整屏幕刷新模型
- alternate screen 或非 JLINE 终端的统一后备路径

所以不要把 `useAlternateScreen` 理解成“只改一下终端清屏方式”。

---

## 10. `/theme` 切换时，真正更新了哪些对象

`CodingCliSessionRunner.applyTheme(...)` 当前会：

1. 切 `TuiConfigManager.switchTheme(...)`
2. 重新 `resolveTheme(...)`
3. 如果是 `JlineShellTerminalIO`，更新 shell terminal 的 theme styler
4. 如果当前有 TUI renderer，也调用 `tuiRenderer.updateTheme(config, theme)`
5. 刷新当前会话输出提示

这说明 theme 切换不是“下次打开 TUI 再生效”。

它会即时影响：

- shell transcript 样式
- renderer 使用的主题
- 后续 TUI 呈现

---

## 11. 更深一层的扩展点分别适合什么

### 改配色、品牌风格、代码高亮颜色

优先改：

- `TuiTheme`

### 改显示密度、时间戳、footer、事件数

优先改：

- `TuiConfig`

### 改布局、状态栏结构、消息板块呈现

优先改：

- `TuiRenderer`
- 当前默认实现是 `TuiSessionView`

### 改屏幕刷新模式、alternate screen 策略、运行时交互壳

优先改：

- `TuiRuntime`
- 或更上一层的 `CodingCliTuiFactory`

这四层不要混改，否则最后很难判断某个行为到底是配置、主题、渲染器还是 runtime 导致的。

---

## 12. 当前边界是什么

当前 TUI 的扩展边界可以直接概括成一句话：

- 开箱可配置，但还不是插件生态

也就是说：

- theme 和 `tui.json` 已经是正式使用层能力
- `CodingCliTuiFactory`、`TuiRenderer`、`TuiRuntime` 是开发层扩展点
- 还没有做成“用户下载一个 UI 插件包就能热插拔”的系统

所以如果你要做深度定制，当前预期仍然应该是：

- 在 Java 层接入自定义实现

---

## 13. 最容易踩坑的 5 个点

### 13.1 以为 home 和 workspace `tui.json` 会字段级 merge

当前不是，workspace 文件存在时更接近整体覆盖。

### 13.2 以为 `--theme` 会自动落盘

当前只是启动时 override，不会保存。

### 13.3 以为 `useAlternateScreen` 只是观感开关

它会直接改变 runtime backend 选择。

### 13.4 只改 theme，却期待布局也变

布局属于 renderer 层，不属于 theme 层。

### 13.5 只改 renderer，却忽略 terminal backend 差异

`AppendOnlyTuiRuntime` 和 `AnsiTuiRuntime` 的交互模型并不完全相同。

---

## 14. 这页最该记住的结论

- 当前 TUI 定制分成 config、theme、renderer、runtime 四层
- theme 查找顺序是 workspace > home > built-in > default fallback
- `tui.json` 当前更接近文件级 override，不是字段级 merge
- `--theme` 是一次性 override，`/theme` 才会持久化写回 workspace config
- `useAlternateScreen` 会影响 runtime backend，而不只是视觉模式

---

## 15. 继续阅读

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [命令参考](/docs/coding-agent/command-reference)
3. [Runtime Architecture](/docs/coding-agent/runtime-architecture)
