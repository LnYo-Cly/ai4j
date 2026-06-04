# docs site information architecture redesign - 进度

## 状态：审查中

## 进度记录

证据使用 `type:path:summary` 格式。

### [2026-06-04 11:53] - task-start

- 做了什么：开始 docs-site 信息架构重构设计：先盘点现有目录、入口页和文档定位，只提交设计与迁移计划，不直接大规模改正文。
- 验证结果：task lifecycle 进入 `in_progress`，CLI 自动提交。
- 下一步：盘点 docs-site 结构。
- 证据：command:TARGET:.:`npx --yes coding-agent-harness task-start ...`

### [2026-06-04 11:56] - docs-site inventory

- 做了什么：读取 `docs-site/sidebars.ts`、`docs-site/docusaurus.config.ts`、`docs-site/docs/intro.md`、`start-here/why-ai4j.md`、`core-sdk/strengths-and-differentiators.md`，统计 docs-site markdown 分布。
- 验证结果：`docs-site/docs` 有 232 个 markdown；当前 sidebar 主线存在，但 `ai-basics`、`getting-started`、`guides` 仍被 include，形成旧主线残留。
- 下一步：将盘点结果写入 `references/docs-site-current-inventory.md`。
- 证据：command:TARGET:docs-site:`Get-ChildItem -Path docs-site/docs -Recurse -File -Filter *.md`; command:TARGET:docs-site/sidebars.ts:sidebar scan

### [2026-06-04 12:02] - design packet

- 做了什么：写入 docs-site 重构设计、当前盘点和页面写作合同。
- 验证结果：设计包明确保留所有特色功能入口，但用 Feature Map 和 stable/advanced/preview/experimental 标签拆分展示层级。
- 下一步：补齐 review / walkthrough 并运行 harness status。
- 证据：report:TARGET:coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/references/docs-site-redesign-design.md:目标 IA、迁移波次和验收标准; report:TARGET:coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/references/docs-site-page-contracts.md:页面合同

## 残余

- 本任务只提交设计，不改 docs-site 源文件；实际实施 Wave 1 需要用户确认后另行执行。
- 本轮未运行 `docs-site` build，因为没有修改 docs-site 源文件；实施阶段每波都必须跑 `npm run build`。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 自动更新
- 负责人：coordinator

### [2026-06-04 12:03] - task-review

- 做了什么：docs-site IA redesign design packet ready for human review: current inventory, target layered IA, Feature Map strategy, status labels, page contracts, and migration waves are documented; implementation is intentionally deferred pending approval.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
