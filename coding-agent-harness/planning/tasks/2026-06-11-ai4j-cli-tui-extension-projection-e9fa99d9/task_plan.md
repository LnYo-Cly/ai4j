# AI4J CLI TUI extension projection

Task Contract: harness-task/v1
Task Package Index: required

## 目标

把现有 Java extension 能力投影到 AI4J CLI 的 TUI 前台，让用户可以在同一条交互链路里发现、检查和执行扩展命令。

## 范围

- 做什么：补齐 `/extensions` 与 `/extension ...` 的 TUI 入口、命令面板、帮助和补全；复用现有 `CliExtensionCommand`；补测试；更新任务包证据。
- 不做什么：不重写 extension API、不改扩展注册语义、不做 docs-site 大改、不做 Pi/Claude 级别的整套 TUI 重构。
- 主要风险：TUI 输入补全和 CLI 子命令参数语义不一致，容易引入“看得见但跑不通”的假入口。

## 预算选择

选择预算：standard

选择理由：变更集中在 ai4j-cli 一个模块，但涉及运行时、补全、帮助、命令面板和回归，属于标准任务而不是小修补。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java；TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java；TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java；TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java | 这四个文件分别负责 TUI 补全、TUI 执行投影、现有 extension CLI 逻辑和建议测试。 | coordinator / reviewer / worker |

## 步骤

1. 诊断当前 TUI 与 extension CLI 的接入点，确认不重写核心命令逻辑。
2. 在 `SlashCommandController` 中补齐 extension 建议与补全规则。
3. 在 `CodingCliSessionRunner` 中接入 `/extensions` 和 `/extension ...`，并跑回归。

## 验收标准

- [x] TUI 根建议、帮助和命令面板能看到 extension 入口。
- [x] `/extensions` 可以在 TUI 中执行并展示扩展列表。
- [x] `/extension inspect|plan|check|validate|run|resource ...` 复用现有 CLI 实现。
- [x] `mvn -pl ai4j-cli -am -DskipTests=false test` 通过。
- [x] `/extension ...` 参数解析保留 Windows 路径反斜杠，并支持带空格/转义引号参数。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：当前 checkout branch
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：n/a
- 未使用 worktree 的原因：变更范围只在 ai4j-cli 一个模块内，且已通过定向回归，直接在当前 checkout 处理更省上下文。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：n/a
- 连续执行权限：不适用
- Stop Condition 摘要：如果要改 extension 核心协议、docs-site 或更广泛的 CLI runtime，必须先暂停。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：reviewer 无重要阻塞发现

## 关联

- 相关 Regression Gate：`mvn -pl ai4j-cli -am -DskipTests=false test`
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：不适用
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：`coding-agent-harness/planning/tasks/2026-06-11-ai4j-cli-tui-extension-projection-e9fa99d9/task_plan.md`、`review.md`、closeout pending
- Closeout / Regression update needed：`progress.md`、`walkthrough.md`
