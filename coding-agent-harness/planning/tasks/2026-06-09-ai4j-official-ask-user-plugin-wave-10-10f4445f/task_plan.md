# AI4J official ask-user plugin wave 10

Task Contract: harness-task/v1
Task Package Index: required

## 目标

新增官方 `ai4j-plugin-ask-user` 插件模块，并把它接入 AI4J Maven reactor、BOM、CI、README、docs-site、回归治理和 harness module registry。

## 范围

- 做什么：实现 host-mediated `ask-user` 插件包，覆盖 tool、command、Skill、Prompt、ServiceLoader、validator、本地测试、文档和治理记录。
- 不做什么：不实现远程插件市场、运行时 jar 热加载、CLI 自动安装依赖、真实 UI、stdin 阻塞、答案持久化或 Agent 恢复协议。
- 主要风险：样板插件文档可能误导用户以为已存在 UI / marketplace 能力；共享 POM/BOM 变更可能影响全仓构建；人工确认门禁不能由 agent 代办。

## 预算选择

选择预算：standard

选择理由：本任务包含新增 Java 模块、docs-site 页面、BOM/CI wiring、Regression SSoT 和 harness module registry，同步面超过一个文件但不需要独立外部系统接入。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension | 插件必须只依赖 extension API 公共合同。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-cli/src/test/java | CLI extension 行为已有回归，官方插件不能假设 CLI 自动安装或热加载。 | coordinator / reviewer |
| C-003 | public-doc | TARGET:docs-site/docs/core-sdk/extension/plugin-packages.md | 插件生态边界和发布建议的文档源。 | coordinator / reviewer |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 新模块需要独立回归面并接入 shared gate。 | coordinator / reviewer |
| C-005 | harness | TARGET:coding-agent-harness/harness.yaml | 新模块需要进入 module registry，供 dashboard 和后续任务定位。 | coordinator / reviewer |

## 步骤

1. 定义 ask-user 插件模块边界：只依赖 `ai4j-extension-api`，以 `Ai4jExtension` + ServiceLoader 提供 tool、command、Skill、Prompt。
2. 接入 Maven reactor、BOM、CI matrix、README 和 docs-site，使用户能从仓库入口理解依赖、启用方式和边界。
3. 新增插件测试，验证 manifest、validator、ServiceLoader、tool / command envelope、Skill / Prompt 资源。
4. 更新 Regression SSoT、Cadence Ledger、engineering/testing standard、harness context 和 module registry。
5. 跑目标回归、共享包构建、docs-site typecheck/build、diff 检查和 harness status。
6. 写 review / walkthrough，提交并推送。

## 验收标准

- [x] `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` 通过。
- [x] `mvn -DskipTests package` 通过，RG-007 记录为 11 reactor projects。
- [x] docs-site typecheck/build 通过，新增 Ask User 文档可被 sidebar 引用。
- [x] `git diff --check` 和 `npx --yes coding-agent-harness status --json .` 通过或残余明确。
- [x] review / progress / walkthrough / lesson_candidates 不再保留模板占位，且没有 agent 伪造 human review confirmation。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：用户要求继续并推送当前主线；写入范围集中在一个新增模块和同步文档/治理表，由 coordinator 串行处理可以避免共享表冲突。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若需要改变 extension API 公共合同、runtime 适配器或远程插件能力，停止并重新确认范围。

## 审查判定

- 是否需要对抗性审查：是，本轮至少执行 self adversarial review，最终人工确认不由 agent 代办。
- 若是，报告文件：`review.md`
- Reviewer：self；human review confirmation pending user-side
- No-finding 要求：无 open P0/P1/P2 finding；若只有范围外残余，必须写 owner/action/status。

## 关联

- 相关 Regression Gate：RG-011、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：Extension API / plugin ecosystem wave 9 已提供 `ai4j-extension-api` 公共合同。

## 模块关联（启用模块并行时填写）

- Module：`ask-user-plugin`
- Step：MOD-02
- Module Plan：`coding-agent-harness/planning/modules/ask-user-plugin/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：ready-for-human-review
- Registry update needed：`ask-user-plugin` 已注册；module_plan 已同步实现状态。
- Harness Ledger update needed：`progress.md`、`review.md`、`walkthrough.md` 由 lifecycle CLI / status 检查消费。
- Closeout / Regression update needed：RG-011 和 RG-007/RG-008 Last Verified 需随最终验证结果更新。
