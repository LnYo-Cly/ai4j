# Agent Runtime 模块

## 模块 Key

`agent-runtime`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-agent/` 的 agent runtime、workflow、trace、memory、subagent 和 team orchestration。

## 完成后能得到什么

该模块让 agent 能独立处理 agent 编排能力，而不把运行时策略塞进 core SDK 或 demo。凡涉及 workflow 执行、trace 结构、memory 行为、subagent/team orchestration 的任务，应以 `agent-runtime` 为主模块，并评估 core SDK 和 FlowGram starter 的影响。

## 交付物

- 可见产物：agent runtime API、工作流执行、trace/memory 能力和对应测试。
- 负责范围：`ai4j-agent/`
- 验证证据：`mvn -pl ai4j-agent -DskipTests=false test`

## 第一眼应该看什么

先读 `module_plan.md`，再读 `AGENT.md` 和 `docs/11-REFERENCE/engineering-standard.md`。

## 模块职责

负责 agent 行为和编排运行时，不把 demo 流程或 starter wiring 作为生产逻辑来源。

## 边界

- 负责：`ai4j-agent/src/main/java`、`ai4j-agent/src/test/java` 和模块 POM。
- 共享面：core SDK API、FlowGram starter bridge、trace 相关文档。
- 不负责：CLI host、coding workspace tools、Spring Boot auto-configuration。

## 完成判断

- agent runtime 任务能独立定位到 `ai4j-agent/`。
- 对 core SDK 或 FlowGram starter 的影响在任务计划中明确。
- 验证证据覆盖 workflow/trace/memory 受影响面。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
