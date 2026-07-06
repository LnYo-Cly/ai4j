# RAG token-aware context assembler

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在 core SDK 中提供一个可选 token-aware RAG context assembler，解决默认拼接所有 final hits 可能超出模型上下文的问题。

## 范围

- 做什么：新增 `TokenAwareRagContextAssembler`、覆盖 token budget/首 hit 截断/默认兼容测试、更新 docs-site 和回归记录。
- 不做什么：不改变默认 `DefaultRagContextAssembler`，不新增 `RagQuery` 策略字段，不新增 starter 配置，不引入新依赖。
- 主要风险：token 计数依赖模型名映射；未知模型名必须安全 fallback。

## 预算选择

选择预算：standard

选择理由：涉及 core SDK、docs-site、Regression/Cadence，但实现本身是单类可选增强。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/DefaultRagContextAssembler.java | 确认默认 citation/context 拼接行为 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/token/TikTokensUtil.java | 复用已有 jtokkit token 计数，不新增依赖 | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md | 记录用户可见用法 | coordinator |

## 步骤

1. 新增可选 assembler，复用现有 `RagContextAssembler` 扩展点。
2. 增加最小单元测试覆盖预算、截断、非法预算。
3. 更新 docs-site、Regression SSoT、Cadence Ledger。
4. 跑 RG-001、RG-008、RG-007，提交 PR。

## 验收标准

- [x] `TokenAwareRagContextAssemblerTest` 通过。
- [x] `DefaultRagServiceTest` 仍通过。
- [x] `mvn -pl ai4j -am -DskipTests=false test` 通过。
- [x] docs-site typecheck/build 通过。
- [x] `mvn -DskipTests package` 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\rag-token-aware-context`
- 分支：`feature/rag-token-aware-context`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：发现需要改变默认 RAG 行为或新增配置系统时暂停。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：`review.md` 无阻塞发现。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI 重建
- 前置任务：RAG incremental ingest / online judge 已在 `main` 中

## 模块关联（启用模块并行时填写）

- Module：base
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 已同步
- Closeout / Regression update needed：已更新 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`
