# FlowGram Starter 模块

## 模块 Key

`flowgram-starter`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-flowgram-spring-boot-starter/` 的 FlowGram integration、task APIs、trace bridge 和 starter runtime support。

## 完成后能得到什么

该模块把 FlowGram 集成和通用 Spring starter 分开管理。涉及 FlowGram task API、trace bridge、starter-side runtime support 或 workflow integration 的任务应落到 `flowgram-starter`，并同步 agent runtime 与 demo backend 的影响。

## 交付物

- 可见产物：FlowGram starter integration、task API、trace bridge、starter tests。
- 负责范围：`ai4j-flowgram-spring-boot-starter/`
- 验证证据：`mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test`

## 第一眼应该看什么

先读 `module_plan.md`，再读 `docs/11-REFERENCE/engineering-standard.md` 和 `docs/11-REFERENCE/testing-standard.md`。

## 模块职责

负责 FlowGram starter 层的生产逻辑与 runtime bridge，不把 demo backend 作为 source of truth。

## 边界

- 负责：FlowGram starter 源码、测试和模块 POM。
- 共享面：agent runtime、core SDK、FlowGram demo backend、trace docs。
- 不负责：demo-only endpoint、webapp UI、通用 Spring Boot starter。

## 完成判断

- FlowGram starter 任务明确区分 starter runtime 与 demo 行为。
- trace bridge 或 task API 变化同步到 agent runtime/demo。
- 验证覆盖 starter 层而不只依赖 demo 手测。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
