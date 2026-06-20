# AI4J CLI Chat First TUI Experience Wave 2

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在不替换 JLine 和不扩张到 agent runtime 的前提下，把 `ai4j` CLI 的 chat-first TUI 做到上下文清晰、命令入口易发现、两条终端路径表现一致。

## 范围

- 做什么：增强 `TuiSessionView` header 和 slash palette；扩展 `JlineShellTerminalIO` status context；补齐 `CodingCliSessionRunner` 到 JLine 的 provider/protocol 传递；添加 targeted JUnit 测试。
- 不做什么：不重写渲染层，不引入 Ink，不做 dashboard，不重构插件系统，不改变 provider/model 命令语义，不改变核心 agent runtime。
- 主要风险：终端状态行默认关闭时仍要保证 inline slash palette 可见；新增展示不能让小终端首屏变成信息噪声；已有 extension 命令接入 diff 不能被覆盖。

## 预算选择

选择预算：standard

选择理由：涉及 CLI/TUI 生产代码、测试和 harness 收口，但范围集中在一个模块内，不需要独立 worktree 或长程合同。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc | PUBLIC:docs/11-REFERENCE/engineering-standard.md | 确认 CLI/TUI 边界、Java 8 和模块归属 | coordinator / reviewer |
| C-002 | public-doc | PUBLIC:docs/11-REFERENCE/testing-standard.md | 确认 CLI/TUI targeted regression 策略 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/tui/TuiSessionView.java | alternate screen TUI header、feed、composer、palette 渲染入口 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/shell/JlineShellTerminalIO.java | JLine status line 和 inline slash palette 渲染入口 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | session context 构建和 terminal wiring | coordinator / reviewer |

## 步骤

1. 诊断现有 diff 和上下文通道，确认 provider/protocol 已在 `TuiRenderContext` 与 `ManagedCodingSession` 中可用。
2. 实现 `TuiSessionView` header 和 slash palette 展示增强。
3. 扩展 `JlineShellTerminalIO` session context 与 status line，使 provider/protocol 进入 JLine 路径。
4. 补充 `TuiSessionViewTest`、`JlineShellTerminalIOTest`，必要时更新 slash command 相关测试。
5. 运行 targeted Maven regression，记录证据并收口 review/walkthrough。

## 验收标准

- [x] TUI header 展示 provider/protocol、model、workspace、session，且没有恢复旧的 focus/overlay 噪声。
- [x] JLine status line/current status 能展示 provider/protocol、model、workspace，且旧的三参数调用仍兼容。
- [x] `/` palette 对 provider/model/extensions/extension 入口的展示可读，选中项仍可被 Tab/Enter 使用。
- [x] Targeted JUnit 测试通过，失败时记录原因和 residual。

## 工作树（Worktree）

- 路径：same checkout
- 分支：current branch
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本轮改动集中在 `ai4j-cli` 的少量渲染/测试文件，已有未提交 diff 需要原地保护和延续。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果需要改变 CLI 命令语义、插件安全边界或渲染技术栈，先暂停确认。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self，本任务完成后提交人工确认
- No-finding 要求：无 open P0/P1/P2 material finding，测试证据已记录。

## 关联

- 相关 Regression Gate：CLI/TUI targeted regression
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-11-ai4j-cli-tui-extension-projection-e9fa99d9`

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：已由 `task-complete` 收口 / 不适用
- Closeout / Regression update needed：`progress.md`、`review.md`、`walkthrough.md`
