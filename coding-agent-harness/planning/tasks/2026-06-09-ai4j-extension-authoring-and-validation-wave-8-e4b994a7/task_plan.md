# AI4J extension authoring and validation wave 8

Task Contract: harness-task/v1
Task Package Index: required

## 目标

补齐 AI4J 插件生态的作者/使用者本地闭环：第三方插件包可以被公共 validator 与 CLI 校验，docs-site/README 能指导小白开发者完成编写、安装、启用、暴露和验证。

## 范围

- 做什么：在 `ai4j-extension-api` 新增 validation report / issue / validator；在 `ai4j-cli extension` 增加 `validate <id>` 和 `validate --all`；更新插件包文档、README、Feature SSoT、Regression SSoT 和 Cadence Ledger；补 Java 与 docs-site 回归。
- 不做什么：不做远程 marketplace、插件自动安装、运行时 jar 热加载、provider plugin、Maven archetype、发布真实第三方插件。
- 主要风险：validator 不能误导为安全扫描或市场审核；CLI 校验不能改变现有 discover / enable / expose 运行时安全语义；Java 8 兼容必须保持。

## 预算选择

选择预算：complex

选择理由：本轮横跨 extension API 公共契约、CLI 用户入口、docs-site/README 和回归治理；虽然实现应保持轻量，但它改变第三方插件作者和使用者的主路径。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension | 现有 manifest、ServiceLoader、registry、runtime snapshot、resource resolver 是 validator 的公共契约基础。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | CLI 已有 list / inspect / run / resource 子命令，本轮只补 validate，不重写命令模型。 | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/extension/plugin-packages.md | 插件生态主文档必须写清作者/使用者路径和当前边界。 | coordinator / reviewer |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md | extension API / CLI / docs-site touched-surface 回归必须记录。 | coordinator / reviewer |

## 步骤

1. 更新任务包和 Feature SSoT，明确 Wave 8 的本地插件生态闭环范围。
2. 在 `ai4j-extension-api` 实现 validation issue/report/validator，并补单测覆盖 pass、warning/error、apply 失败。
3. 在 `ai4j-cli extension` 增加 `validate <id>` 和 `validate --all`，复用 API validator，并补 CLI 测试。
4. 更新 docs-site 插件包页面与 README，说明第三方插件作者和使用者怎么安装、检查、启用和暴露。
5. 运行 touched-surface 回归，更新 Regression SSoT / Cadence Ledger / walkthrough / review，并提交推送。

## 验收标准

- [ ] `ExtensionValidator` 能检查 manifest 完整性、capability 与贡献资源一致性、tool input schema 基础形状、Skill / Prompt 资源是否存在。
- [ ] `ai4j-cli extension validate <id>` 输出 extension、status、errors、warnings 和 issue 列表；有 error 时返回非零。
- [ ] `ai4j-cli extension validate --all` 能检查 classpath 上所有发现的插件。
- [ ] 校验不会自动 enable/expose 工具，也不会执行插件 command。
- [ ] docs-site/README 不出现“企业采用”这类用户明确反感的措辞。

## 工作树（Worktree）

- 路径：不适用
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：当前 worktree 干净，变更范围集中在 extension API / CLI / docs，用户要求继续一起做完。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果要引入远程安装、热加载或自动执行第三方代码，必须另开任务。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + human review queue
- No-finding 要求：Agent Review Submission 必须没有 open material finding；Human Review Confirmation 不由 agent 代办。

## 关联

- 相关 Regression Gate：RG-010、RG-004、RG-007、RG-008
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-extension-authoring-and-validation-wave-8-e4b994a7/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：Wave 2-7 extension ecosystem tasks，尤其是 `2026-06-09-ai4j-extension-guardrail-execution-wave-7-c4da123b`

## 模块关联（启用模块并行时填写）

- Module：extension-api / cli-host / docs-site
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/extension-api/module_plan.md`、`coding-agent-harness/planning/modules/cli-host/module_plan.md`、`coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：Feature SSoT、Regression SSoT、Cadence Ledger
- Harness Ledger update needed：task plan、review、walkthrough
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`
