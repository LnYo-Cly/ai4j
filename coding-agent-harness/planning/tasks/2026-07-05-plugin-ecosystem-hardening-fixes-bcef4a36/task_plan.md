# plugin ecosystem hardening fixes

Task Contract: harness-task/v1
Task Package Index: required

## 目标

依次修复插件生态 review 发现的可合并问题：版本漂移、runtime 可见性、资源隔离、payload 尺寸上限和权限边界文档。

## 范围

- 做什么：修改 extension-api、ask-user 官方插件、CLI、coding extension resources、docs-site 和回归台账中与插件生态 hardening 直接相关的内容。
- 不做什么：不实现完整权限引擎、不改变 lifecycle hook 为可变拦截器、不引入插件远端市场/安装协议、不扩大到非插件模块重构。
- 主要风险：资源读取收紧可能破坏依赖宿主 classloader 兜底的旧插件；CLI/runtime 输出变更需要测试覆盖；docs 需避免暗示自动权限执行。

## 预算选择

选择预算：standard

选择理由：跨 4 个 Java 模块加 docs/governance，需 task-local review、回归证据和 walkthrough，但实现切片明确，不需要 complex artifacts。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-extension-api | 插件 manifest、validator、resource resolver 的 owning API | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-plugin-ask-user | 官方样例插件和 ask_user envelope 行为 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli | 插件 scaffold、inspect/resource UX 和测试 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-coding | Coding Agent materialize Skill/Prompt 的插件资源消费侧 | coordinator / reviewer |
| C-005 | docs | TARGET:docs-site/docs/core-sdk/extension;TARGET:docs-site/docs/agent/plugin-lifecycle-hooks.md | 用户可见插件生态说明和信任边界 | coordinator / reviewer |
| C-006 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md;TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 固定回归面与本任务证据 | coordinator / reviewer |

## 步骤

1. 核对 worktree、远端 main、当前 diff 和 review 点。
2. 实现窄修复：2.4.0 版本对齐、CLI lifecycleHooks 输出、strict resource reads、ask_user payload cap、permission docs。
3. 运行 owning-module 与消费侧回归：extension-api、ask-user、CLI、coding、monorepo package、docs-site。
4. 更新 Regression SSoT / Cadence Ledger、review、walkthrough 和 lesson decision。
5. 提交实现并准备 PR/合并。

## 验收标准

- [x] `main` 与 `origin/main` 核对一致后在隔离 worktree 修复。
- [x] 所有点名修复都有代码或文档落点。
- [x] 目标 Maven 回归、package smoke、docs-site build/typecheck 均通过。
- [x] Regression SSoT / Cadence Ledger 记录 2026-07-05 证据。
- [x] review 无 open material finding，walkthrough 可复现。

## 工作树（Worktree）

- 路径：`.worktrees/fix/plugin-ecosystem-hardening`
- 分支：`fix/plugin-ecosystem-hardening`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用，已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：出现跨模块架构变更或需要凭据/发布权限时暂停。

## 审查判定

- 是否需要对抗性审查：是，使用 self-review + regression evidence。
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：无 P0/P1/P2 open material finding。

## 关联

- 相关 Regression Gate：RG-010、RG-011、RG-003 targeted、RG-004 targeted、RG-007、RG-008；Cadence SRB-060。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：用户 review 后要求“依次修复”。

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle commands 已同步 generated ledger
- Closeout / Regression update needed：已更新 `docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`walkthrough.md`
