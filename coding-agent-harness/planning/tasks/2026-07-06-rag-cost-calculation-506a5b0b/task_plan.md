# rag cost calculation

Task Contract: harness-task/v1
Task Package Index: required

## 目标

用最小变更完成 token cost 计算任务：确认现有 trace cost 能力已满足需求，补齐用户可直接复制的配置文档，不新增重复 SDK 抽象。

## 范围

- 做什么：检查 `TracePricingResolver` 到 `TraceMetrics` / exporter 的已有链路；在 docs-site trace 文档补最小配置示例；运行 trace cost 定向测试、docs-site build、package smoke；同步回归治理和任务 closeout。
- 不做什么：默认价格表、价格自动更新、预算告警、成本 dashboard、RAG 专属 cost 字段、`AgentResult` / `AgentModelResult` cost 字段、第二套 pricing API。
- 主要风险：模型价格高频变化，SDK 内置价格表会快速过期；把 cost 放进普通 result 会把 trace/observability 与业务返回值混在一起。

## 预算选择

选择预算：simple

选择理由：当前 `origin/main` 已经有 production path 和测试覆盖，本任务只需要验证、文档化和治理收口。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/TracePricing.java | 已存在的单价配置模型 | coordinator |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentTraceListener.java | `metricsFromUsage(...)` 里已有 cost 计算 | coordinator |
| C-003 | test | TARGET:ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTraceListenerTest.java | 已覆盖 input/output/total cost 断言 | coordinator |
| C-004 | docs | TARGET:docs-site/docs/agent/trace-observability.md | 用户可见配置入口 | coordinator |

## 步骤

1. 核对现有 trace cost 实现和测试，确认是否需要新增代码。
2. 若已有实现足够，只补 docs-site 最小 usage 示例，不引入新 public API。
3. 运行 trace cost 定向测试、docs-site typecheck/build、monorepo package smoke 和 diff hygiene。
4. 更新 Regression SSoT / Cadence Ledger、task progress、review、walkthrough。
5. 提交、PR、合并后清理 worktree。

## 验收标准

- [x] 未新增重复 pricing/cost 抽象。
- [x] trace 文档包含 `TracePricingResolver` 可复制配置片段。
- [x] `mvn -pl ai4j-agent -am "-Dtest=AgentTraceListenerTest,LangfuseTraceExporterTest" -DskipTests=false -DfailIfNoTests=false test` 通过。
- [x] `npm run typecheck` / `npm run build` in `docs-site/` 通过。
- [x] `mvn -DskipTests package` 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\rag-cost-calculation`
- 分支：`feature/rag-cost-calculation`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用；已使用独立 worktree，避免触碰主 checkout 的脏改动。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：发现需要新增 public API 或改变 result contract 时停止并回到用户确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：确认没有重复 cost abstraction、没有内置漂移价格表、docs 示例与现有 API 对齐。

## 关联

- 相关 Regression Gate：RG-002 targeted、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：用户已同意按“生产级/企业级”顺序处理 cost 计算缺口；当前 main 已包含实现，故本任务转为验证与文档化。

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI closeout
- Closeout / Regression update needed：已更新 `docs/05-TEST-QA/Regression-SSoT.md` 与 `docs/05-TEST-QA/Cadence-Ledger.md`
