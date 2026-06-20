# Core SDK configuration and invocation experience upgrade design

Task Contract: harness-task/v1
Task Package Index: required

## 目标

形成 Core SDK 配置与调用体验升级设计，保证后续实现能降低接入成本，同时不破坏现有对象链主合同。

## 范围

- 做什么：审计 `Configuration`、`AiService`、`AiServiceRegistry`、`AiConfig`、Spring starter、OpenAI-compatible provider 配置、docs-site 示例和现有 tests，输出可实施设计。
- 不做什么：不直接新增 Java API，不恢复 `ChatClient`，不新增 `Ai4j.chat()` 大门面，不改 docs-site 正文，不推送远程。
- 主要风险：把“减少样板”误做成遮蔽 Tool/MCP/RAG/Memory 边界的半成品 API。

## 预算选择

选择预算：standard

选择理由：本轮是设计任务，不改业务代码，但需要跨 core/starter/docs 示例审计并形成后续实现合同。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763/design.md | 前置调用合同审计，规定不要新增隐藏式 Chat facade | coordinator / reviewer |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java | 配置聚合与 builder 能力现状 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java | 单实例服务入口和能力暴露方式 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java | 多实例/profile 的正式入口 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j | Spring Boot 配置绑定和自动配置现状 | coordinator / reviewer |
| C-006 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai | OpenAI-compatible 配置和 provider 行为 | coordinator / reviewer |
| C-007 | docs | TARGET:docs-site | 当前 public docs 的调用示例和入口表达 | coordinator / reviewer |
| C-008 | tests | TARGET:ai4j/src/test/java; TARGET:ai4j-spring-boot-starter/src/test/java | 现有测试能否覆盖升级设计 | coordinator / reviewer |

## 步骤

1. 读取前置审计结论，固定“不新增隐藏式 facade”的设计边界。
2. 审计配置对象、registry、starter 和 OpenAI-compatible 配置现状。
3. 扫描 docs-site / README 示例，找出现有接入成本高或表达不清的点。
4. 设计升级分层：配置 helpers、profile/registry 体验、Spring Boot binding、recipe 文档、需评审 API。
5. 写入 `design.md`、`findings.md`、`visual_map.md`、`review.md`、`walkthrough.md` 并提交审查。

## 验收标准

- [x] `design.md` 明确升级原则、建议改动、拒绝项和实现波次。
- [x] `findings.md` 记录源码证据与 accepted decisions。
- [x] 设计覆盖 Plain Java、Spring Boot、多 provider/profile、中转平台、recipe 文档。
- [x] 明确哪些事项不需要新 API、哪些必须单独 API 评审。
- [x] `git diff --check` 与 `harness status --json .` 通过。

## 工作树（Worktree）

- 路径：same checkout
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：设计任务只写当前 task package。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若需要新增公开 API 或修改业务源码，先停在设计结论等待确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：review.md 无 open material finding。

## 关联

- 相关 Regression Gate：governance/design-only；不触发 Java executable gate
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：TASKS/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763

## 模块关联（启用模块并行时填写）

- Module：core-sdk
- Step：design-only
- Module Plan：coding-agent-harness/planning/modules/core-sdk/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 自动同步
- Closeout / Regression update needed：walkthrough only；Regression SSoT 不适用
