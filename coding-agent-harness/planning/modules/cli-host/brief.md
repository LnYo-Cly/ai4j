# CLI Host 模块

## 模块 Key

`cli-host`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-cli/` 的 CLI、TUI、ACP host 和 session/runtime integration。

## 完成后能得到什么

该模块让命令行体验、TUI 和 ACP host 变更从 coding runtime 中分离。涉及命令入口、交互 UI、session 管理或 runtime integration 的任务应以 `cli-host` 为主，并检查 `coding-runtime` 合同是否变化。

## 交付物

- 可见产物：CLI command、TUI/ACP host、session integration 和测试。
- 负责范围：`ai4j-cli/`
- 验证证据：`mvn -pl ai4j-cli -DskipTests=false test`

## 第一眼应该看什么

先读 `module_plan.md`，再读 `docs/11-REFERENCE/engineering-standard.md` 和 `docs/11-REFERENCE/testing-standard.md`。

## 模块职责

负责用户面 CLI host 与 runtime 接线，不把 coding runtime 核心逻辑写进 CLI。

## 边界

- 负责：`ai4j-cli/src/main/java`、`ai4j-cli/src/test/java` 和模块 POM。
- 共享面：coding runtime API、docs/CLI 使用文档、发布脚本。
- 不负责：core SDK provider、agent workflow、starter auto-configuration。

## 完成判断

- CLI 任务能明确区分 host wiring 与 runtime behavior。
- 交互行为有可复查的命令或测试证据。
- 影响 coding runtime 时同步到对应模块计划。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
