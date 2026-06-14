# AI4J CLI Chat First TUI Experience Wave 2

## Task ID

`2026-06-11-ai4j-cli-chat-first-tui-experience-wave-2-62e7ff0b`

## 创建日期

2026-06-11

## 一句话结果

让 `ai4j` CLI 的 chat-first TUI 在 alternate screen 和 JLine 两条路径中都清楚展示 provider、protocol、model、workspace，并把常用 slash command 入口做成更易扫读的一等操作。

## 完成后能得到什么

完成后，用户在终端输入 `ai4j` 后看到的第一屏仍是 chat-first 单屏对话，不会变成 dashboard 或多面板工具页；但顶部上下文、JLine 状态行和 `/` palette 会更接近 Codex / Claude Code / Pi 这类交互：当前 provider、protocol、model、workspace 不需要靠 `/status` 才能确认，`/provider`、`/model`、`/extensions`、`/extension` 等入口也能在命令菜单里被快速发现。下一轮 agent 可基于本任务的测试和 walkthrough 继续做 TUI 键盘交互、扩展资源运行或终端主题增强。

## 交付物

- 可见产物：CLI/TUI 首屏 header、JLine status line、slash command palette 的上下文和入口增强。
- 修改位置：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/tui/`、`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/shell/`、必要的 runner wiring 和测试。
- 验证证据：`ai4j-cli` 相关 targeted JUnit 测试和任务 `progress.md`。

## 第一眼应该看什么

先看 `task_plan.md` 的范围和验收，再看 `progress.md` 的命令证据，最后看 `review.md` 和 `walkthrough.md` 的收口结论。

## 边界

- 范围内：`ai4j-cli` 的 TUI header、JLine 状态行、slash palette 展示、上下文传递和对应测试。
- 范围外：不替换 JLine，不引入 Ink，不重写全屏渲染层，不重构插件系统，不改核心 agent runtime。
- 停止条件：如果实现需要改变 CLI 命令语义、插件激活安全边界或 Java 基线，先暂停确认。

## 完成判断

- TUI header 能同时展示 provider/protocol、model、workspace 和 session。
- JLine status line 能接收并展示 provider/protocol，不只展示 model/workspace。
- Slash palette 能突出 `/provider`、`/model`、`/extensions`、`/extension` 等高频入口。
- 相关 JUnit 测试覆盖新增展示和上下文传递。
- 任务进度、审查和 walkthrough 记录验证证据。

## 执行合同

- Owner：coordinator
- 生命周期状态：已完成
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

已完成；后续只需要基于 `walkthrough.md` 的 residual 判断是否另开真实终端 polish 任务。
