# rag cost calculation

## Task ID

`2026-07-06-rag-cost-calculation-506a5b0b`

## 创建日期

2026-07-06

## 一句话结果

确认当前 `origin/main` 已有 trace token cost 计算能力，不新增第二套 pricing API；本任务只补可复制的 `TracePricingResolver` 文档示例，并完成 docs / trace cost / package 验证。

## 完成后能得到什么

用户可以在 Agent trace 中按模型名配置自定义 token 单价，自动得到 `inputCost`、`outputCost`、`totalCost`、`currency`，并通过现有 OTel / Langfuse exporter 输出。因为模型价格会漂移，SDK 仍不内置价格表；使用者按自己的 provider、合同价或计费口径提供 resolver。本任务避免了重复新增 `ModelPricing` / `CostCalculator` 等抽象。

## 交付物

- 可见产物：trace observability 文档中的最小 `TracePricingResolver` 配置片段。
- 修改位置：`docs-site/docs/agent/trace-observability.md`、`docs-site/i18n/zh-Hans/docusaurus-plugin-content-docs/current/agent/trace-observability.md`、回归治理记录和任务包。
- 验证证据：trace cost/export 定向测试、docs-site typecheck/build、monorepo package smoke、`git diff --check`。

## 第一眼应该看什么

1. `docs-site/docs/agent/trace-observability.md` 的 cost 示例。
2. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentTraceListener.java` 中 `metricsFromUsage(...)`。
3. `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTraceListenerTest.java` 的 cost 断言。
4. 本任务 `walkthrough.md` 的验证表。

## 边界

- 范围内：验证已有 trace cost 计算路径；补 docs-site 使用示例；同步 RG-007/RG-008/SRB-064 证据；记录 no-new-abstraction 决策。
- 范围外：新增默认价格表、预算告警、成本 dashboard、RAG 专属 cost API、`AgentResult` cost 字段、第二套 `ModelPricing` / `CostCalculator` 抽象。
- 停止条件：如果用户要求在 `AgentResult` 直接返回 cost，另开 API 设计任务，不在本 docs/verification 任务里顺手加。

## 完成判断

- 当前代码层已有 `TracePricing` / `TracePricingResolver` / `TraceMetrics` cost 字段，且未新增重复抽象。
- docs-site 中有可复制的 resolver 示例，并说明每百万 token 单价和无内置价格表原因。
- `AgentTraceListenerTest` / `LangfuseTraceExporterTest` cost 相关测试通过。
- docs-site typecheck/build 与 monorepo package smoke 通过。
- 任务 walkthrough、review、回归治理记录齐全。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中，待 `task-complete` 收口
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`review.md`、`lesson_candidates.md`、`walkthrough.md`
- 完成条件：验证证据记录到 `progress.md`，提交后运行 `task-complete`

## 当前下一步

完成最终 `git diff --check`，提交 docs / governance / task package，然后运行 harness `task-complete`。
