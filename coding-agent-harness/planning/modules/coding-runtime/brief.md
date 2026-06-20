# Coding Runtime 模块

## 模块 Key

`coding-runtime`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-coding/` 的 coding-agent runtime、workspace-aware tools、outer loop 和 compaction。

## 完成后能得到什么

该模块把 coding-agent 专用运行时和通用 agent runtime 分开治理。涉及 workspace 工具、代码执行外循环、上下文压缩、coding session 行为的任务应落到 `coding-runtime`，再由 coordinator 评估 CLI host 是否需要同步。

## 交付物

- 可见产物：coding runtime API、workspace 工具、outer-loop/compaction 行为和测试。
- 负责范围：`ai4j-coding/`
- 验证证据：`mvn -pl ai4j-coding -DskipTests=false test`

## 第一眼应该看什么

先读 `module_plan.md`，再读 `docs/11-REFERENCE/engineering-standard.md` 和 `docs/11-REFERENCE/testing-standard.md`。

## 模块职责

负责 coding-agent 运行时，不直接承担 CLI/TUI 展示、ACP host 或 core SDK provider 行为。

## 边界

- 负责：`ai4j-coding/src/main/java`、`ai4j-coding/src/test/java` 和模块 POM。
- 共享面：CLI runtime 集成、workspace 安全边界、测试标准。
- 不负责：CLI command/TUI、Spring starters、FlowGram demo。

## 完成判断

- coding runtime 任务可独立在 `ai4j-coding/` 内实现和验证。
- CLI 影响被显式列为 follow-up 或跨模块任务。
- workspace 相关风险有测试或 residual 记录。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
