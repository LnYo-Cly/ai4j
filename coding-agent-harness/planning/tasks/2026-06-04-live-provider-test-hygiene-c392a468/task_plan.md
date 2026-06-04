# live provider test hygiene

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-04-live-provider-test-hygiene-c392a468/artifacts/preset/2026-06-04T10-51-54-397Z
Task Package Index: required

## 目标

把真实 provider / 外部 provider 测试从默认本地回归中隔离出来，并让显式 live 验证只通过 env-only 凭据和 `-P live-provider-tests` 运行。

## 范围

- 做什么：配置 Surefire category/profile，标记 live tests，移除默认/嵌入式 credential-like key 和 API key system property 回退，更新回归治理文档。
- 不做什么：不运行真实 provider 调用，不配置密钥，不修复与 live provider hygiene 无关的 agent runtime 行为。
- 主要风险：根 POM 不覆盖 `ai4j` 独立模块；`ai4j-agent` 完整本地 gate 暴露既有 `HandoffPolicyTest` 失败。

## 预算选择

选择预算：complex

选择理由：横跨 root build、core/agent/coding 测试、回归治理文档和 harness task closeout，且需要验证默认/opt-in 两条测试路径。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:pom.xml | 根 POM 给继承模块提供 Surefire profile/category 配置 | coordinator/reviewer |
| C-002 | code | TARGET:ai4j/pom.xml | `ai4j` 不继承根 POM，需要单独配置同名 profile/category | coordinator/reviewer |
| C-003 | code | TARGET:ai4j/src/test/java; TARGET:ai4j-agent/src/test/java; TARGET:ai4j-coding/src/test/java | live provider tests 和本地 fixture tests 的边界 | coordinator/reviewer |
| C-004 | governance | TARGET:coding-agent-harness/governance/regression; TARGET:docs/05-TEST-QA; TARGET:docs/11-REFERENCE/testing-standard.md | 固定 gate、触发规则和 runbook 投影 | coordinator/reviewer |

## 步骤

1. 扫描 live/provider/credential 风险测试与 POM Surefire 入口。
2. 添加 `LiveProviderTest` category、`live-provider-tests` profile 和 env-only test helper。
3. 标记 core/agent/coding live tests，移除默认 key、system-property API key 回退、本机路径和本机代理引用。
4. 运行 default local、live profile smoke、compile/package 和 scan 验证。
5. 更新 Regression SSoT、Cadence、testing standard、task progress/review/walkthrough。

## 验收标准

- [x] `mvn -pl ai4j -DskipTests=false test` 默认通过，live tests 不进入默认测试集。
- [x] `mvn -pl ai4j-coding -DskipTests=false test` 默认通过，MiniMax live usage test 不进入默认测试集。
- [x] `mvn -pl ai4j -P live-provider-tests -Dtest=DoubaoTest -DskipTests=false test` 在无凭据时 clean skip。
- [x] `mvn -pl ai4j-agent -P live-provider-tests -Dtest=CodeActRuntimeTest -DskipTests=false test` 在无凭据时 clean skip。
- [x] `mvn -pl ai4j-coding -P live-provider-tests -Dtest=MinimaxCodingAgentTeamWorkspaceUsageTest -DskipTests=false test` 在无凭据时 clean skip。
- [x] `rg` credential scan 只剩本地单元测试 fake key，不剩 live provider 默认 key / `*.api.key` 回退 / 本机绝对路径。
- [x] RG-002 的 `HandoffPolicyTest` 失败作为 R-008 残余路由，不混入 live-provider hygiene 目标。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：单 coordinator 串行修改共享 POM、测试和治理文档；未授权可写 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：需要真实凭据或修复非本任务行为时停止并路由残余。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：无本任务目标阻塞发现；R-008 作为外部残余。

## 关联

- 相关 Regression Gate：RG-001、RG-002、RG-003、LV-001、LV-002
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-04-regression-baseline-live-split-b2f834db`

## 模块关联（启用模块并行时填写）

- Module：core-sdk / agent-runtime / coding-runtime / governance
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：task review / closeout 后由 CLI 更新
- Closeout / Regression update needed：已更新 Regression SSoT、Cadence、testing standard

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | live provider test hygiene |
