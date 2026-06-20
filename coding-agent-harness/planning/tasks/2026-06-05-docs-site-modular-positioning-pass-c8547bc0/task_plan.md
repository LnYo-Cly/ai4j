# docs site modular positioning pass

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-05-docs-site-modular-positioning-pass-c8547bc0/artifacts/preset/2026-06-04T16-36-32-446Z
Task Package Index: required

## 目标

把 AI4J 的模块独立性从工程事实转化为 docs-site 入口卖点：按需取用、最小引入、逐步升级。

## 范围

- 做什么：修改 `intro.md`、`why-ai4j.md`、`feature-map.md`，增加模块化定位、取用路径和升级表。
- 不做什么：不改 Java 代码、不拆 Maven 模块、不调整依赖、不新增页面、不迁移全站目录。
- 主要风险：文案过度承诺模块可独立发布；表格链接或 Markdown 破坏 docs-site build；与前一轮 Feature Map 成熟度标签冲突。

## 预算选择

选择预算：complex

选择理由：虽然业务改动集中在 3 个 Markdown 文件，但它承接用户关于 Pi Agent 卖点的产品定位讨论，并影响 docs-site 的核心价值表达，需要完整 review packet 和可追溯证据。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | `pom.xml` | 确认根 Maven 模块列表。 | coordinator / reviewer |
| C-002 | code | module `pom.xml` files | 确认 `ai4j`、starter、agent、coding、cli、flowgram、bom 的依赖关系。 | coordinator / reviewer |
| C-003 | code | `docs-site/docs/intro.md` | 首页入口文案修改目标。 | coordinator / reviewer |
| C-004 | code | `docs-site/docs/start-here/why-ai4j.md` | Why AI4J 定位文案修改目标。 | coordinator / reviewer |
| C-005 | code | `docs-site/docs/start-here/feature-map.md` | 功能地图和模块取用索引修改目标。 | coordinator / reviewer |
| C-006 | private-plan | `coding-agent-harness/planning/tasks/2026-06-04-docs-site-wave-1-entrance-redesign-54198b78/walkthrough.md` | 前一轮入口改造结论：不删除旧页，不扩大到全站深页。 | coordinator / reviewer |

## 步骤

1. 读取当前三页 docs-site 入口内容和 Maven 模块依赖事实。
2. 在首页增加 `用多少，取多少` 表，表达按目标选择最小模块。
3. 在 Why AI4J 增加 `不是全家桶，而是可渐进升级的 Java AI SDK` 章节。
4. 在 Feature Map 增加 `按模块取用` 表，列出最小模块、依赖关系和适合场景。
5. 补齐任务材料，运行 docs-site build、diff check 和 harness status。
6. 本地提交并进入 review 队列。

## 验收标准

- [x] 首页有面向用户的模块取用表。
- [x] Why AI4J 说明模块独立性的用户价值，而不是只讲目录拆分。
- [x] Feature Map 明确每个模块的最小取用场景和依赖关系。
- [x] `docs-site` 构建通过。
- [x] `git diff --check` 通过。
- [x] harness status 通过，任务材料无模板残留。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：当前分支
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：当前分支
- 未使用 worktree 的原因：只改 3 个 docs-site Markdown 文件和当前任务包，边界小，使用当前 checkout 更直接。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：需要改 Java 模块、修改 Maven 依赖或扩大到全站深页时停止确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：无 P0/P1/P2 重要发现，docs-site build 和 diff check 通过。

## 关联

- 相关 Regression Gate：docs-site build
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-04-docs-site-wave-1-entrance-redesign-54198b78`

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：DOCS-MODULAR-01
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
| Preset Title | docs site modular positioning pass |
