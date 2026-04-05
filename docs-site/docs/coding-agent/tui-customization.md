---
sidebar_position: 10
---

# TUI 定制与主题

TUI 不是一个固定死的黑盒，它有两类定制点：

- 使用层定制：主题、配置、显示密度；
- 开发层定制：自定义 `CodingCliTuiFactory`、`TuiRenderer`、`TuiRuntime`。

---

## 1. 现成可用的定制能力

### 1.1 切换主题

启动参数：

```text
--theme <name>
```

会话内命令：

```text
/theme
/theme <name>
```

当前内置主题包括：

- `default`
- `amber`
- `ocean`
- `matrix`
- `github-dark`
- `github-light`

### 1.2 主题文件位置

支持从两个位置读取自定义主题：

- `<workspace>/.ai4j/themes/<name>.json`
- `~/.ai4j/themes/<name>.json`

优先级是：workspace > home > built-in

### 1.3 TUI 配置文件

支持读取：

- `<workspace>/.ai4j/tui.json`
- `~/.ai4j/tui.json`

当前主要字段有：

- `theme`
- `denseMode`
- `showTimestamps`
- `showFooter`
- `maxEvents`
- `useAlternateScreen`

---

## 2. 主题定制建议

如果目标只是调整观感，优先使用主题文件，而不是直接改渲染器。

适合放进主题的通常是：

- 品牌色
- 强调色
- 成功/警告/错误色
- 文本色
- 代码块配色

---

## 3. 开发层扩展点

### 3.1 `CodingCliTuiFactory`

这是最直接的定制入口。

你可以提供自己的 `CodingCliTuiFactory`，决定如何创建：

- `TuiConfig`
- `TuiTheme`
- `TuiRenderer`
- `TuiRuntime`

默认实现使用的是：

- `TuiSessionView` 作为 renderer
- `AppendOnlyTuiRuntime` 或 `AnsiTuiRuntime` 作为 runtime

### 3.2 `TuiRenderer`

如果你想改界面表现，而不是只改配色，可以自定义 `TuiRenderer`。

适合：

- 改布局；
- 改状态栏展示；
- 改 transcript 呈现方式。

### 3.3 `TuiRuntime`

如果你想改刷新模式、主缓冲区策略或 alternate screen 行为，可以改 `TuiRuntime`。

---

## 4. 当前边界

当前 TUI 的主要扩展点在“代码注入型定制”，不是“插件化前端市场”。

也就是说：

- 改主题和配置，已经开箱可用；
- 改渲染器和 runtime，需要你在 Java 层接入；
- 还没有做成独立 UI 插件生态。

---

## 5. 继续阅读

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [命令参考](/docs/coding-agent/command-reference)
