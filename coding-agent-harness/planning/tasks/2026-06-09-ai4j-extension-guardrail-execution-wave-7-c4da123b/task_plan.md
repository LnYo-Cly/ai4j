# AI4J extension guardrail execution wave 7

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让已启用 AI4J 插件注册的 Guardrail 在 Agent / Coding Agent 执行 tool call 前生效，避免 Guardrail 只停留在 extension API snapshot 中但不影响实际运行时。

## 范围

- 做什么：在 `ai4j-agent` 中增加 Guardrail tool executor wrapper；让普通 Agent、Coding Agent 主会话和 delegated child session 在实际 tool executor 前评估已启用插件 Guardrail；补充 Agent / Coding Agent 回归测试、docs-site 插件文档、README 入口和回归治理记录。
- 不做什么：不做 CLI `extension run/resource` 的 Guardrail 拦截；不做 marketplace、自动安装、jar hotload、provider plugin；不改变现有 approval、workspace 写入边界或 live provider 行为。
- 主要风险：Coding Agent 会在 `newSession()` 和 delegated session 中重建 tool executor，若只在 builder 层包装会被绕过；Guardrail 不能扩大工具可见性，也不能替代现有 tool policy / approval gate。

## 预算选择

选择预算：complex

选择理由：该任务横跨 `ai4j-agent`、`ai4j-coding`、docs-site、README 与回归治理，改变 Agent tool execution 安全边界，需要完整任务包、审查和多模块证据。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | design | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | 确认 Guardrail 属于插件生态的独立资源，不需要 exposeTool 才能执行。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/guardrail | Guardrail public contract、request / decision 语义。 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent | Agent tool loop、tool executor merge 和 extension adapter 边界。 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding | Coding Agent 主会话、delegated child session、built-in tools 和 tool policy 边界。 | coordinator / reviewer |
| C-005 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | Java 8、模块边界和安全敏感 tool execution 约束。 | coordinator / reviewer |
| C-006 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | touched surface 回归命令和 evidence depth。 | coordinator / reviewer |

## 步骤

1. Agent runtime：新增 `ExtensionGuardrailToolExecutor`，把 Guardrail request 映射为 `action=tool.execute`、`target=<toolName>`，deny 时阻断 delegate executor。
2. Agent / Coding Agent 接线：普通 Agent build path、Coding Agent `newSession()`、DefaultCodingRuntime delegated child session 都在实际 tool executor 前应用 Guardrail。
3. 回归测试：覆盖 extension tool 被 Guardrail 拒绝且 executor 未执行，以及 Coding Agent 内置 `bash` 被 Guardrail 拒绝且命令未运行。
4. 文档治理：更新 docs-site 插件页、README、Feature SSoT、Regression SSoT、Cadence Ledger 与本任务包。
5. 验证、提交 `task-review` 并推送。

## 验收标准

- [x] 普通 Agent 调用已暴露 extension tool 时，已启用 Guardrail 可在 executor 前拒绝。
- [x] Coding Agent 调用内置 workspace tool 时，已启用 Guardrail 可在 shell / 文件工具执行前拒绝。
- [x] Guardrail 不需要 `exposeTool(...)`，但仍必须来自已启用插件；它不会自动新增模型可见工具。
- [x] CLI `extension run/resource` 在文档中保持人工显式命令 / 资源路径边界，不误写成 Agent tool loop。
- [x] targeted Java tests、package smoke、docs-site typecheck/build、harness status 和 diff check 通过或记录残余。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本轮由 coordinator 单线修改，范围集中在 Agent / Coding Agent runtime wrapper、测试和文档治理，无并行 worker 写入。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若需要改变 CLI command/resource 拦截、approval policy 或 provider plugin 语义，必须停下另开任务。

## 审查判定

- 是否需要对抗性审查：是，采用 coordinator self-review 的 architecture/security/regression challenge。
- 若是，报告文件：`review.md`
- Reviewer：self；人工确认仍由 review queue 处理，agent 不代办。
- No-finding 要求：无 P0/P1/P2 阻塞发现；残余必须记录。

## 关联

- 相关 Regression Gate：RG-002、RG-003、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-09-ai4j-extension-skill-prompt-resources-wave-6-3c37dd11`

## 模块关联（启用模块并行时填写）

- Module：agent-runtime / coding-runtime / docs-site
- Step：Wave 7
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 lifecycle CLI / task-review 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`walkthrough.md`
