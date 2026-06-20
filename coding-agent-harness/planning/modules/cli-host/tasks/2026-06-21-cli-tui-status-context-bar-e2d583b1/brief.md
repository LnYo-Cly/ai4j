# CLI TUI status context bar

## Task ID

`2026-06-21-cli-tui-status-context-bar-e2d583b1`

## 创建日期

2026-06-21

## 一句话结果

增强 `ai4j` JLine TUI 顶部状态区，让用户进入 coding agent 时一眼看到 provider/model、workspace/session、memory/compact、sandbox、permissions 和 pending approval 上下文。

## 完成后能得到什么

完成后，AI4J CLI/TUI 不再只有单行 provider/model/header，而是有更接近 coding agent 产品的上下文状态栏。它会在不引入 Ink、不自研复杂 renderer、不改变 Java 8/JLine 技术路线的前提下，把现有 session/runtime 信息组织成可读 chips，降低用户在 `/memory`、`/compact`、`/sandbox`、`/permissions` 之间来回查询的成本。

## 交付物

- 可见产物：`TuiSessionView` 状态 header/context row 增强。
- 修改位置：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/tui/TuiSessionView.java`、相关 TUI tests、`docs-site/docs/coding-agent/cli-and-tui.md`。
- 验证证据：CLI targeted tests、docs build、diff check、token scan、Harness status。

## 第一眼应该看什么

1. `task_plan.md`：设计边界和验收标准。
2. `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/tui/TuiSessionViewTest.java`：状态栏行为回归。
3. `docs-site/docs/coding-agent/cli-and-tui.md`：用户可见说明。

## 边界

- 范围内：TUI header/context row 呈现、状态摘要、测试和 docs-site 说明。
- 范围外：不替换 JLine，不引入 Ink，不实现新 slash command，不使用真实 provider token，不改 Agent runtime API。
- 停止条件：如果需要新增跨模块 runtime 字段或改变 public API，另开任务。

## 完成判断

- [x] TUI header 显示第一行身份：AI4J、provider/protocol、model、workspace、session。
- [x] TUI header 显示第二行 context chips：memory、compact、sandbox、permissions/approval。
- [x] 新增/更新 TuiSessionView tests 覆盖 context chips 和 pending approval。
- [x] docs-site CLI/TUI 页面解释状态栏含义。
- [x] 目标验证命令通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交实现 diff，运行 `task-review` 并创建 PR。
