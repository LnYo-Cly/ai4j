# Core SDK 模块

## 模块 Key

`core-sdk`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j/` 核心 SDK 的 provider、Chat/Responses、RAG、MCP、向量、图像、音频和 realtime 能力边界。

## 完成后能得到什么

该模块让 agent 能把核心 SDK 变更和 starter、CLI、demo 变更分开规划。涉及模型提供商、基础协议、RAG、MCP 或通用运行时能力时，任务应先落到 `core-sdk`，再由 coordinator 判断是否需要同步下游 starter、agent runtime 或 docs。

## 交付物

- 可见产物：核心 SDK API、provider adapter、通用能力实现和对应测试。
- 负责范围：`ai4j/`
- 验证证据：`mvn -pl ai4j -DskipTests=false test` 或更小的类级 Maven 测试。

## 第一眼应该看什么

先读 `module_plan.md`，再按任务类型读 `docs/11-REFERENCE/engineering-standard.md` 和 `docs/11-REFERENCE/testing-standard.md`。

## 模块职责

负责生产级 SDK 能力与跨模块公共行为。它是多个上层模块的依赖源，因此 API 兼容性、Java 8 和 provider-facing 测试边界必须优先处理。

## 边界

- 负责：`ai4j/src/main/java`、`ai4j/src/test/java` 和模块本地 POM。
- 共享面：根 `pom.xml`、`ai4j-bom/`、Regression SSoT、跨模块文档。
- 不负责：agent orchestration、CLI/TUI、Spring/FlowGram 自动装配和 demo-only 逻辑。

## 完成判断

- module registry 中 scope、依赖和 owner 与仓库事实一致。
- 核心 SDK 任务有明确的 targeted Maven 验证。
- 影响上层模块时，在任务中显式列出 downstream follow-up。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
