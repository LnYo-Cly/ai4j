# docs site wave 1 entrance redesign

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-04-docs-site-wave-1-entrance-redesign-54198b78/artifacts/preset/2026-06-04T12-05-19-453Z
Task Package Index: required

## 目标

完成 docs-site Wave 1 入口修正：把首页、Why AI4J 和 Start Here sidebar 调整为低门槛 Java AI 接入导向，并新增 Feature Map 作为完整能力索引。

## 范围

- 做什么：重写 `intro.md` 和 `why-ai4j.md`，新增 `start-here/feature-map.md`，在 `sidebars.ts` 的 Start Here 中挂载新页。
- 不做什么：不迁移旧目录、不删除旧页面、不改 Docusaurus 主题、不改代码模块、不补全部深页内容。
- 主要风险：新增页面存在断链；文案把 preview 能力写成稳定承诺；入口页过度泛化，仍不能回应“如何更好用”。

## 预算选择

选择预算：complex

选择理由：本任务虽然只改 4 个 docs-site 文件，但它承接前置信息架构设计，影响文档站入口、用户定位判断和后续 docs-site 扩展路线，需要完整任务包、参考资料索引、审查记录和 walkthrough。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | private-plan | `coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/references/docs-site-redesign-design.md` | 前置设计结论：入口、Feature Map、能力页分层，不删除旧内容 | coordinator / reviewer |
| C-002 | private-plan | `coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/references/docs-site-current-inventory.md` | 现有 docs-site 页面盘点和可链接页面边界 | coordinator / reviewer |
| C-003 | private-plan | `coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/references/docs-site-page-contracts.md` | 首页、Why、Feature Map 的页面职责合同 | coordinator / reviewer |
| C-004 | code | `docs-site/docs/intro.md` | 首页现状和目标修改文件 | coordinator / reviewer |
| C-005 | code | `docs-site/docs/start-here/why-ai4j.md` | Why AI4J 现状和目标修改文件 | coordinator / reviewer |
| C-006 | code | `docs-site/sidebars.ts` | Start Here sidebar 挂载位置 | coordinator / reviewer |

## 步骤

1. 根据前置设计任务确认 Wave 1 只修改 4 个 docs-site 文件。
2. 重写首页，改成 Java / Spring Boot / Feature Map 三条入口，并保留模块地图。
3. 重写 `Why AI4J`，明确个人项目与大生态框架的差异化定位。
4. 新增 `Feature Map`，按成熟度列出全部主要能力和阅读路径。
5. 更新 `sidebars.ts`，把 `feature-map` 放入 Start Here。
6. 运行 docs-site 构建、diff 检查和 harness status，记录证据。
7. 提交本地 commit，进入 `task-review` 等待人工确认。

## 验收标准

- [x] 首页不再把所有功能堆成泛泛介绍，而是直接导向三条成功路径。
- [x] `Why AI4J` 说明 AI4J 与 Spring AI、LangChain4j、AgentScope Java 的现实边界。
- [x] 新增 `Feature Map` 并覆盖主要能力、成熟度和深链入口。
- [x] sidebar 已挂入 `start-here/feature-map`。
- [x] `docs-site` 构建通过。
- [x] `git diff --check` 通过。
- [ ] lifecycle 进入 review 队列。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：当前分支
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：当前分支
- 未使用 worktree 的原因：变更范围很小且只涉及 docs-site 入口文件，使用同一 checkout 能降低共享文档冲突；没有授权 worker 写入，也没有必要拆并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：构建断链、范围扩大到深页迁移或需要产品承诺时停止确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：self-review 无 P0/P1/P2 重要发现，且构建证据通过。

## 关联

- 相关 Regression Gate：docs-site build
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-04-docs-site-information-architecture-redesign-6c91ba27`

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：EXEC-01
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task-review / task-complete 时由 CLI 处理
- Closeout / Regression update needed：`walkthrough.md`

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | docs site wave 1 entrance redesign |
