# ai4j dynamic workflow host runtime

## Task ID

`2026-07-06-ai4j-dynamic-workflow-host-runtime-ef15599f`

## 创建日期

2026-07-06

## 一句话结果

给 `ai4j-agent` 增加一个可选的 dynamic workflow host runtime，让独立插件返回的 host-mediated envelope 可以在 AI4J 宿主侧被解析、执行并输出结构化 trace。

## 完成后能得到什么

用户和下一轮 agent 可以直接拿到一条完整链路：第三方 dynamic workflow 插件仍只依赖 `ai4j-extension-api` 并返回 envelope；AI4J 宿主通过 `ai4j-agent` 的可选 runtime 解析 envelope 中的 script/args，提供 `phase()`、`log()`、`agent()`、`parallel()` 和 `pipeline()` primitive，并把执行结果、阶段、日志、agent 调用和错误统一返回为 JSON。这样既保留插件独立性，又证明 SDK 暴露的 host 能力足够支撑 Claude Code style dynamic workflow。

## 交付物

- 可见产物：`ai4j-agent` dynamic workflow runtime API、host tool wrapper、JUnit 回归、docs-site 文档更新。
- 修改位置：`ai4j-agent/src/main/java/.../dynamicworkflow/`、`ai4j-agent/src/test/java/.../dynamicworkflow/`、`docs-site/docs/core-sdk/extension/dynamic-workflow-plugin.md`。
- 验证证据：`mvn -pl ai4j-agent -am -Dtest=DynamicWorkflow*Test -DskipTests=false test`，以及必要的 docs-site typecheck/build。

## 第一眼应该看什么

先读本任务 `task_plan.md` 的边界，再读新增的 `NashornDynamicWorkflowExecutor` / `DynamicWorkflowHostToolExecutor` 测试，最后读 docs-site 的“Host runtime”章节。

## 边界

- 范围内：`ai4j-agent` 宿主执行层、可注入 agent bridge、envelope parser、host tool wrapper、确定性本地测试、docs-site 使用说明。
- 范围外：不把独立插件并入 SDK reactor；不改 `ai4j-extension-api`，除非实现证明现有 API 确实不够；不实现后台 `/workflows` 管理、resume journal、per-agent worktree isolation 或模型 tier 调度。
- 停止条件：如果需要新增危险执行权限、引入非 Java 8 基线、或必须改 extension API 才能继续，需要暂停说明。

## 完成判断

- [x] 插件 envelope 可以被 host runtime 解析并执行。
- [x] `phase()`、`log()`、`agent()`、`parallel()`、`pipeline()` 至少有确定性测试覆盖。
- [x] agent bridge 可注入，默认实现不硬编码 provider key、CLI、worktree 或外部服务。
- [x] host tool wrapper 能把 `workflow` tool 的 pending envelope 转成 execution result JSON。
- [x] docs-site 说明插件/host 边界和最小接入方式。

## 执行合同

- Owner：coordinator
- 生命周期状态：未开始
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

梳理现有 `ai4j-agent` CodeAct / workflow / extension executor 边界，在 `ai4j-agent` 新增最小 dynamic workflow runtime。
