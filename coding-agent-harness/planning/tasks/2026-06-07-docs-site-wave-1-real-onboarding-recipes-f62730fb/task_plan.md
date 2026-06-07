# docs site wave 1 real onboarding recipes

Task Contract: harness-task/v1
Task Package Index: required

## 目标

完成 docs-site canonical Wave 1 onboarding / recipe 重写，让真实 API 主线成为新用户第一路径。

## 范围

- 做什么：更新 `docs-site/docs/start-here`、`spring-boot/configuration-reference.md`、`core-sdk/service-entry-and-registry.md`，新增 OpenAI-compatible/TroveBox recipe，并验证 docs build。
- 不做什么：不改 Java API、不全量同步 i18n、不做视觉重设计、不推送远程。
- 主要风险：文档写出不存在 API，或新增页面未挂 sidebar 导致不可达。

## 预算选择

选择预算：standard

选择理由：内容改动集中在 docs-site canonical onboarding，但需要 build 验证和 harness 收口。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f/design.md | 前置体验升级设计 | coordinator / reviewer |
| C-002 | docs | TARGET:docs-site/docs/start-here | 新用户入口页面 | coordinator / reviewer |
| C-003 | docs | TARGET:docs-site/docs/spring-boot | starter 配置和 profile 页面 | coordinator / reviewer |
| C-004 | docs | TARGET:docs-site/docs/core-sdk/service-entry-and-registry.md | Core SDK 主入口合同 | coordinator / reviewer |
| C-005 | config | TARGET:docs-site/sidebars.ts | 页面导航可达性 | coordinator / reviewer |

## 步骤

1. 启动任务并确认写入范围。
2. 更新 Java / Spring Boot onboarding 页面。
3. 新增 OpenAI-compatible/TroveBox recipe 并挂入 sidebar。
4. 强化 service registry 和 Spring Boot configuration reference。
5. 运行 `npm run build`、文本扫描和 harness status。
6. 写 walkthrough / review 并提交审查。

## 验收标准

- [x] docs-site 不推荐 `ChatClient.openAi` 或 `Ai4j.chat()`。
- [x] Java / Spring Boot / profile / OpenAI-compatible/TroveBox 路径可复制。
- [x] 新增页面在 sidebar 中可达。
- [x] `npm run build` 通过。
- [ ] harness status 通过。

## 工作树（Worktree）

- 路径：same checkout
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：单一 docs-site 切片，无并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若需要新增 Java API 或全量 i18n 同步，暂停确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：review.md 无 open material finding。

## 关联

- 相关 Regression Gate：docs-site build
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：TASKS/2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：wave-1-onboarding
- Module Plan：coding-agent-harness/planning/modules/docs-site/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 自动同步
- Closeout / Regression update needed：walkthrough；docs-site build evidence
