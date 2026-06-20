# FlowGram Demo Backend 模块

## 模块 Key

`flowgram-demo`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-flowgram-demo/` 的 FlowGram starter integration demo backend。

## 完成后能得到什么

该模块让 demo backend 任务独立于 production starter 逻辑。涉及示例接口、demo 配置、端到端展示后端或 FlowGram starter 集成验证时，任务落到 `flowgram-demo`，但生产逻辑仍应回到 `flowgram-starter` 或上游模块。

## 交付物

- 可见产物：demo backend endpoint、配置、集成示例和 demo 验证记录。
- 负责范围：`ai4j-flowgram-demo/`
- 验证证据：`mvn -pl ai4j-flowgram-demo -DskipTests=false test` 或 demo 启动/集成 smoke。

## 第一眼应该看什么

先读 `module_plan.md`，再读 FlowGram starter 的 `module_plan.md` 和相关 demo README。

## 模块职责

负责演示和集成验证，不作为 starter 或 agent runtime 的生产逻辑来源。

## 边界

- 负责：demo backend 源码、配置、测试和模块 POM。
- 共享面：FlowGram starter APIs、webapp demo contract、demo docs。
- 不负责：FlowGram starter production behavior、webapp UI、core SDK API。

## 完成判断

- demo-only 变更不会污染 starter production logic。
- 与 webapp demo 的接口变化有同步记录。
- 验证说明是 backend test、启动 smoke 还是手工 demo。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
