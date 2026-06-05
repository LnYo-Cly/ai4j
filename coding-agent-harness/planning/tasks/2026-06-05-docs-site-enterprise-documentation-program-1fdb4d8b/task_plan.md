# docs-site 文档重构总任务

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b/artifacts/preset/2026-06-05T03-27-44-497Z
Task Package Index: required

## 目标

把 docs-site 的正式阅读路径、模块总览、生产接入辅助页面和 legacy 迁移边界收口到一套可构建、可继续迭代的文档结构。

## 范围

- 做什么：docs-site canonical map、sidebar/include/footer、入口链接、Core/Agent/Coding Agent/FlowGram 总览页、Reference/Security/Operations/Migration/Troubleshooting/Comparison 页面。
- 不做什么：删除 legacy `getting-started/`、`ai-basics/`、`guides/`；修改 Java 代码；发布远程仓库；承诺未验证的 provider 能力。
- 主要风险：旧目录强内容很多，不能一次性删除；sidebar 新增页面会触发 Docusaurus 翻译 key 或断链检查；新增 docs 文件被 `.gitignore` 的 `docs/` 规则隐藏，提交时需要 `git add -f`。

## 预算选择

选择预算：complex

选择理由：该任务涉及文档 IA、多个顶层模块、legacy 迁移策略、生产接入辅助页、构建验证和 harness 收口，不是单页修文。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc | TARGET:AGENTS.md | repo 规则、docs-site 构建和 harness 生命周期要求 | coordinator |
| C-002 | public-doc | TARGET:docs-site/sidebars.ts | 确定 canonical 导航和未挂载强内容 | coordinator |
| C-003 | public-doc | TARGET:docs-site/docs/start-here/feature-map.md | 当前能力地图和成熟度语义 | coordinator |
| C-004 | public-doc | TARGET:docs-site/docs/core-sdk/overview.md | Core SDK 主入口重写面 | coordinator |
| C-005 | public-doc | TARGET:docs-site/docs/agent/overview.md | Agent 主入口重写面 | coordinator |
| C-006 | public-doc | TARGET:docs-site/docs/coding-agent/overview.md | Coding Agent 主入口重写面 | coordinator |
| C-007 | public-doc | TARGET:docs-site/docs/flowgram/overview.md | FlowGram 主入口重写面 | coordinator |
| C-008 | review | subagent:019e95cd-25ef-7610-b044-480d7aa21a2b | Core SDK / MCP / ai-basics 只读审计 | coordinator |
| C-009 | review | subagent:019e95cd-61ed-7b11-ab65-ace9b2b91cad | Agent / Coding Agent / FlowGram 只读审计 | coordinator |
| C-010 | review | subagent:019e95cd-9fec-72f0-86f0-be0063f41b12 | 全站 IA、legacy 和生产辅助页只读审计 | coordinator |

## 步骤

1. 关闭上一批 docs-site 任务并确认本轮 task-start。
2. 并行只读审计 Core/MCP、Agent/Coding/FlowGram、全站 IA 与 legacy 重复面。
3. 建立 canonical / legacy 文档地图，不删除旧目录。
4. 新增版本、发布、安全、生产检查、迁移、排障、选型对比页面。
5. 更新 Docusaurus include、sidebar、footer、Start Here、FAQ、Glossary 和关键入口链接。
6. 重写 Core SDK、Agent、Coding Agent、FlowGram 总览页，让它们先回答“适合谁、怎么开始、边界是什么”。
7. 运行 `npm run build`，修复构建错误。
8. 补齐 task materials、review、progress、lesson routing，提交本地 commit。

## 验收标准

- [x] 新增页面不使用生硬营销式措辞。
- [x] sidebar 中新增页面可被 Docusaurus 收录，重复 translation key 已修复。
- [x] Start Here、FAQ、Glossary、Core overview 的 MCP 正式入口指向顶层 `mcp/`。
- [x] Core/Agent/Coding Agent/FlowGram 总览页已改成用户路径优先。
- [x] Spring Boot / Solutions 总览页已改成同一入口结构。
- [x] `docs-site` 下 `npm run build` 通过。
- [ ] 变更被正确纳入 git，包括被 ignore 的新增 docs 文件。
- [ ] harness task-review / human review / task-complete 收口。

## 工作树（Worktree）

- 路径：不适用
- 分支：当前工作树
- Worker owner：coordinator；只读审计使用 subagent
- Worker handoff commit required：no
- Coordinator integration branch：当前分支
- 未使用 worktree 的原因：本轮写入由 coordinator 串行整合，避免多 worker 同时修改 sidebar 和入口页。

## 长程任务判定

- 是否属于长程任务：是
- 若是，合同文件：未单独创建；用户已明确授权继续执行，且本轮拆成可验证波次。
- 连续执行权限：已授权
- Stop Condition 摘要：遇到旧目录删除、未验证 API 承诺或远程推送时必须暂停确认。

## 审查判定

- 是否需要对抗性审查：是，本轮至少需要 self review + build 证据；人工确认后才能 closeout。
- 若是，报告文件：`review.md`
- Reviewer：self + human
- No-finding 要求：无 P0/P1/P2 阻塞发现；构建通过。

## 关联

- 相关 Regression Gate：docs-site build
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：docs-site information architecture redesign、docs-site wave 1 entrance redesign、docs-site modular positioning pass

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：docs-site canonical + production-readiness docs wave
- Module Plan：TARGET:coding-agent-harness/planning/modules/docs-site/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-review
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 后续重建
- Closeout / Regression update needed：review / walkthrough / task-complete 阶段补齐

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | docs-site 文档重构总任务 |
