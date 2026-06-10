# AI4J Extension Check Gate

Task Contract: harness-task/v1
Task Package Index: required

## 目标

为 AI4J 插件生态新增发布前 / 接入前硬门禁：`ai4j-cli extension check`，把 validator 与 activation recipe 检查合并成可 CI 化的非零退出命令。

## 范围

- 做什么：新增 CLI `extension check <id> --enable [activation options]`；补 CLI 测试、插件作者/使用者文档、scaffold README 文案、回归治理记录和 harness closeout。
- 不做什么：不改变现有 `plan` 的预览语义，不做远程 marketplace，不做自动依赖安装，不做运行时 jar 热加载，不做 provider 自动注册。
- 主要风险：门禁如果误要求所有插件资源 active，会破坏最小权限接入；如果只输出文本不返回非零，又不能作为 CI gate。

## 预算选择

选择预算：standard

选择理由：涉及 CLI 行为、测试、docs-site 和回归治理，但不需要跨模块架构重写，适合标准任务。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | `check` 需要复用现有 `validate`、`plan`、activation option 解析和输出风格 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionActivationPlan.java | 判断哪些请求资源 active / inactive | coordinator / reviewer |
| C-003 | test | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java | 新增 passing / failing gate regression | coordinator / reviewer |
| C-004 | docs | TARGET:docs-site/docs/core-sdk/extension/plugin-author-cookbook.md | 作者发布前检查入口 | coordinator / reviewer |
| C-005 | docs | TARGET:docs-site/docs/core-sdk/extension/plugin-recipes.md | 使用者接入前检查入口 | coordinator / reviewer |

## 步骤

1. 明确 check gate 语义：validation errors fail；显式请求的 activation items inactive fail；未请求资源不 fail。
2. 在 CLI 中新增 `check` 分支、解析、输出和非零退出判定。
3. 补 CLI tests 覆盖通过、请求资源缺失、未启用插件、validation failure 语义。
4. 更新 docs-site、scaffold README 文案、Feature SSoT、Regression SSoT 和 Cadence Ledger。
5. 运行 targeted Java regression、docs-site gates、harness status 和 diff check，收口 review / walkthrough。

## 验收标准

- [ ] `ai4j-cli extension check cli-test-pack --enable --expose-tool cli.echo ...` 返回 0。
- [ ] 请求不存在的 tool / skill 等资源时 check 返回非零，并指出 inactive requested resource。
- [ ] 未 `--enable` 时 check 返回 argument error。
- [ ] `check` 不改变 `plan` 的返回码语义。
- [ ] 文档和回归治理记录说明新门禁。

## 工作树（Worktree）

- 路径：same checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：当前没有并行 worker，改动范围集中在 CLI / docs / governance，coordinator 可直接串行完成。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果需要改变 extension public API 或自动安装 / marketplace 语义，停止并重新确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self + targeted tests
- No-finding 要求：review.md 无 open material finding

## 关联

- 相关 Regression Gate：RG-004、RG-008、RG-007（按 touched-surface 判定）
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：F-040 AI4J Extension Recipe and Plugin Composition UX

## 模块关联（启用模块并行时填写）

- Module：cli-host / docs-site / extension-api-adjacent
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI closeout
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`
