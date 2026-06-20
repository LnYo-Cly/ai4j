# docs site information architecture redesign

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/artifacts/preset/2026-06-04T11-53-19-657Z
Task Package Index: required

## 目标

完成 docs-site 信息架构重构设计，使 AI4J 文档能完整展示所有特色能力，同时让第一次访问的 Java 用户优先看到低门槛接入路径和可复制运行示例。

## 范围

- 做什么：盘点现有 docs-site 目录、侧边栏和重复主线；设计目标 IA、功能状态标签、页面模板、迁移波次和验收标准。
- 不做什么：不批量移动文档文件，不删除旧页面，不重写 README 或 docs-site 正文，不修改 Docusaurus 配置。
- 主要风险：如果把所有功能继续平铺在入口页，会削弱“更简单”的定位；如果直接删除旧目录，会造成链接断裂和历史内容丢失。

## 预算选择

选择预算：complex

选择理由：docs-site 当前有 232 个 markdown 文件，涉及 Start Here、Core SDK、Spring Boot、RAG、MCP、Agent、Coding Agent、FlowGram 和方案页，设计必须覆盖全站信息架构而不是单页改写。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc | TARGET:docs-site/docs/intro.md | 当前文档站首页的定位和入口职责 | coordinator / reviewer |
| C-002 | code | TARGET:docs-site/sidebars.ts | 当前侧边栏暴露的主导航和隐藏/重复入口判断 | coordinator / reviewer |
| C-003 | public-doc | TARGET:docs-site/docs/start-here/why-ai4j.md | 当前 Why AI4J 叙事质量和重心 | coordinator / reviewer |
| C-004 | public-doc | TARGET:docs-site/docs/core-sdk/strengths-and-differentiators.md | 当前差异化表达的边界与问题 | coordinator / reviewer |
| C-005 | generated-inventory | TARGET:coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/references/docs-site-current-inventory.md | 当前目录计数、重复主线和迁移风险 | coordinator / reviewer |
| C-006 | design | TARGET:coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/references/docs-site-redesign-design.md | 目标信息架构和迁移波次 | coordinator / reviewer |

## 步骤

1. 盘点现有 docs-site 目录、侧边栏和核心入口页。
2. 识别重复主线、旧页面遗留和“百科化入口”风险。
3. 设计目标 IA：入口层、快速成功层、功能总览层、功能详解层、参考层、方案层。
4. 定义页面合同：首页、Feature Map、Quickstart、Capability、Reference、Solution。
5. 制定分阶段迁移计划：先入口和 Feature Map，再核心能力，再高级模块，最后清理旧目录。
6. 记录验证证据和 review packet，等待用户确认是否进入实施阶段。

## 验收标准

- [x] 当前 docs-site markdown 总量和一级目录分布已记录。
- [x] 重复主线和迁移风险已在 `findings.md` / inventory 中列明。
- [x] 目标 IA 保留所有特色能力入口，并用成熟度标签区分 stable / advanced / preview / experimental。
- [x] 页面模板能指导后续实际改写，不只是抽象建议。
- [x] 本任务不改动 docs-site 正文和 URL，实施阶段需要另行确认。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：本轮只写 task-local 设计材料，不改 docs-site 源文件，不需要并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：一旦进入实际 docs-site 内容重构、URL 变更或删除旧页，必须另开实施阶段并由用户确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：设计边界清楚，无会阻塞提交给用户确认的 open finding。

## 关联

- 相关 Regression Gate：DOCS-001 docs-site information architecture design-only check
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 自动更新
- Closeout / Regression update needed：设计任务不改变 Regression SSoT；实施阶段如修改 docs-site 构建门禁再更新。

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | docs site information architecture redesign |
