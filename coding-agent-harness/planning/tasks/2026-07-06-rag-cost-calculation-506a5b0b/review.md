# Review：rag cost calculation

## 结论

No material findings. 当前 main 已经有 trace cost 计算能力；本任务正确选择了“文档化 + 验证”，没有新增重复 pricing abstraction。

## Review 范围

| 项 | 结论 |
| --- | --- |
| Runtime code | 未改动；已有 `TracePricingResolver` -> `TraceMetrics` path |
| Public API | 未新增 |
| Docs | 新增 copyable resolver snippet |
| Tests | trace cost/export targeted pass |
| Regression governance | RG-007/RG-008/SRB-064 已同步 |

## Evidence

| ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | code | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentTraceListener.java` | `metricsFromUsage(...)` 按每百万 token 单价计算 input/output/total cost |
| E-002 | test | `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTraceListenerTest.java` | 已断言 cost metrics 聚合 |
| E-003 | test | `ai4j-agent/src/test/java/io/github/lnyocly/ai4j/agent/trace/LangfuseTraceExporterTest.java` | 已断言 `cost_details` projection |
| E-004 | command | TARGET:. | trace targeted test passed, 6 tests |
| E-005 | command | TARGET:docs-site | `npm ci`, `npm run typecheck`, `npm run build` passed |
| E-006 | command | TARGET:. | `mvn -DskipTests package` passed, 11 reactor projects |

## Confidence Challenge

1. 是否应该新增 `ModelPricing` / `CostCalculator`？否。现有 `TracePricing` 和 `TraceMetrics` 已解决 trace/observability cost 计算，新增会制造双轨 API。
2. 是否应该内置默认价格表？否。模型价格变化频繁，默认表容易误导；用户应注入 resolver。
3. 是否应该把 cost 放进 `AgentResult`？本任务不做。当前需求是 observability/cost tracing；业务 result contract 另行设计。

## Findings

| ID | Severity | Finding | Disposition |
| --- | --- | --- | --- |
| F-001 | none | No material finding | Accepted |

## Residual Risk

| Risk | Owner | Accepted | Follow-up |
| --- | --- | --- | --- |
| 用户需要自己维护价格表 | user/app owner | yes | 文档说明价格漂移和 resolver 返回 `null` 的行为 |
