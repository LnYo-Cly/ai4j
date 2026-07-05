# ai4j dynamic workflow plugin

Task Contract: harness-task/v1
Task Package Index: required

## 目标

把 Pi dynamic workflows 的核心 plugin 生态模式落成 AI4J 的独立样板插件仓库，并在 ai4j-sdk docs-site 中保留清晰的独立仓库接入说明。

## 范围

- 做什么：对比两个 Pi dynamic workflow plugin 实现；创建 `G:\My_Project\java\ai4j-plugin-dynamic-workflow` 独立 Maven 插件仓库；在 ai4j-sdk docs-site 增加动态 workflow 插件说明和侧边栏入口；记录验证与收口材料。
- 不做什么：不把 `ai4j-plugin-dynamic-workflow` 加入 ai4j-sdk reactor / BOM；不在插件内执行 JavaScript、创建 subagent、创建 worktree、实现后台 workflow manager 或模型 tier 路由。
- 主要风险：`ai4j-extension-api:2.4.0` 尚未公开发布时，独立仓库 CI / 用户本地干净 Maven 缓存会解析失败；需要先从 ai4j main 安装 parent POM 与 extension API。

## 预算选择

选择预算：standard

选择理由：本任务跨一个独立插件仓库与 ai4j-sdk docs-site，但实现面保持最小，适合 standard 任务包记录计划、验证、审查和 walkthrough。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | external | URL:https://github.com/Michaelliv/pi-dynamic-workflows | 参考小核心契约：`workflow` tool、literal `export const meta`、agent / parallel / pipeline / phase primitive。 | coordinator / reviewer |
| C-002 | external | URL:https://github.com/QuintinShaw/pi-dynamic-workflows | 参考生产化能力边界，并明确哪些能力应留给 AI4J host/runtime 后续实现。 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-extension-api | 独立插件需要遵守 AI4J extension API manifest、ServiceLoader、tool / command / Skill / Prompt 约束。 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-plugin-ask-user | 官方最小样板插件，用于保持 envelope、resource、测试组织方式一致。 | coordinator / reviewer |
| C-005 | docs | TARGET:docs-site/docs/core-sdk/extension | docs-site 插件生态文档入口和侧边栏位置。 | coordinator / reviewer |

## 步骤

1. 对比两个 Pi 实现，选择 Michaelliv 的最小核心 contract 作为 AI4J 首版边界，把 QuintinShaw 的后台、resume、tier、worktree 等能力记录为 host/runtime 后续方向。
2. 创建独立 Maven 仓库 `G:\My_Project\java\ai4j-plugin-dynamic-workflow`，实现 extension manifest、`workflow` tool、`workflow` command、Skill、Prompt、ServiceLoader 和 JUnit 4 测试。
3. 调整 ai4j-sdk docs-site：新增 Dynamic Workflow Plugin 文档、更新 extension overview、plugin packages 和 sidebar，明确它是独立仓库而非 reactor / BOM 模块。
4. 运行插件 Maven 测试、干净 Maven 缓存兼容验证、docs-site typecheck / build、`git diff --check`。
5. 提交独立插件仓库和 ai4j-sdk docs / harness task 更新。

## 验收标准

- [x] 独立仓库包含可编译的 Java 8 Maven 插件、GitHub Actions、README、LICENSE、ServiceLoader、Skill、Prompt 和 JUnit 4 测试。
- [x] 插件只返回 host-mediated workflow request envelope，不在插件层执行脚本或绕过 host policy。
- [x] docs-site 清楚说明该插件单独发布，不假设已进入 ai4j-bom。
- [x] `mvn -DskipTests=false test` 在独立插件仓库通过。
- [x] 干净 Maven 缓存下，先以 `-Droot.publish.skip=false` 安装 ai4j parent + extension-api 后，独立插件测试通过。
- [x] docs-site `npm run typecheck` 与 `npm run build` 通过。

## 工作树（Worktree）

- 路径：`.worktrees/feature/dynamic-workflow-plugin`
- 分支：`feature/dynamic-workflow-plugin`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`feature/dynamic-workflow-plugin`
- 未使用 worktree 的原因：已使用 ai4j-sdk 专用 worktree；独立插件仓库在 `G:\My_Project\java\ai4j-plugin-dynamic-workflow` 单独初始化。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：插件测试、docs build、diff check 任一失败且无法在本轮修复时停止并记录 residual。

## 审查判定

- 是否需要对抗性审查：是，轻量 self-review
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：无 P0/P1/P2 阻塞发现；CI dependency resolution 风险必须有修复或 residual。

## 关联

- 相关 Regression Gate：docs-site build；独立插件 Maven unit tests；clean Maven dependency resolution probe。
- 审查报告：`coding-agent-harness/planning/tasks/2026-07-06-ai4j-dynamic-workflow-plugin-d652ef2e/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：docs-site / extension-api ecosystem
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：本任务 closeout 后可由 lifecycle CLI / governance rebuild 刷新
- Closeout / Regression update needed：任务本地 walkthrough 已记录；本轮没有改变 ai4j-sdk 固定 regression surface，无需更新 Regression SSoT / Cadence Ledger
