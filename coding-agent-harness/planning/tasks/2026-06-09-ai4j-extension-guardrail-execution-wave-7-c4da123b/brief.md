# AI4J extension guardrail execution wave 7

## Task ID

`2026-06-09-ai4j-extension-guardrail-execution-wave-7-c4da123b`

## 创建日期

2026-06-09

## 一句话结果

已启用插件注册的 Guardrail 会在 Agent / Coding Agent 执行 tool call 前生效，并能阻止 extension tool 或 Coding Agent 内置工具实际运行。

## 完成后能得到什么

本任务完成后，AI4J 的插件生态不再只是展示 Guardrail 能力，而是在真实运行时执行。普通 Agent 调用暴露的插件工具前会先评估 Guardrail；Coding Agent 调用内置 workspace tools 或 extension tools 前也会先评估 Guardrail。被拒绝的调用会作为 `TOOL_ERROR` 返回给 Agent loop，实际工具 executor 不会执行。文档同步说明 `tool.execute` 请求契约、Agent / Coding Agent 执行点，以及 CLI `extension run/resource` 仍属于人工显式路径。

## 交付物

- 可见产物：`ExtensionGuardrailToolExecutor`、Agent / Coding Agent Guardrail 接线、targeted tests、docs-site 插件文档、README 入口、回归治理记录。
- 修改位置：`ai4j-agent/`、`ai4j-coding/`、`docs-site/docs/core-sdk/extension/plugin-packages.md`、`README.md`、`docs/05-TEST-QA/`、`docs/09-PLANNING/`。
- 验证证据：`progress.md` 记录 Agent / Coding Agent targeted tests、monorepo package、docs-site typecheck/build、diff check 和 harness status。

## 第一眼应该看什么

先读 `task_plan.md` 确认范围和验收标准，再读 `findings.md` 理解为什么 Coding Agent `newSession()` 和 delegated child session 都需要独立包装。代码重点看 `ExtensionGuardrailToolExecutor.java`、`CodingAgent.java` 和 `DefaultCodingRuntime.java`。验证证据看 `progress.md`，审查结论看 `review.md`。

## 边界

- 范围内：Agent / Coding Agent tool execution 前的 extension Guardrail 执行、测试、文档和回归台账。
- 范围外：CLI `extension run/resource` 的 Guardrail 拦截、marketplace、自动安装、jar hotload、provider plugin、live provider behavior。
- 停止条件：需要改变 approval、workspace 写边界、CLI command 拦截或新增非 `tool.execute` Guardrail action 时，必须另开任务。

## 完成判断

- `ExtensionAgentToolsTest` 覆盖 extension tool 被 Guardrail 拒绝且 executor 未执行。
- `CodingAgentBuilderTest` 覆盖内置 `bash` 被 Guardrail 拒绝且命令未执行。
- docs-site 和 README 明确 Agent / Coding Agent Guardrail 执行语义与 CLI 边界。
- Regression SSoT / Cadence Ledger 记录 RG-002、RG-003、RG-007、RG-008 证据。
- `review.md` 无 open material finding，`lesson_candidates.md` 完成 checked-none 判定。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`
- 完成条件：验证证据必须记录到 `progress.md`，Agent Review Submission 由 `harness task-review` 写入；人工确认不能由 agent 代办。

## 当前下一步

运行 `harness task-review` 写入 Agent Review Submission，然后推送提交，等待人工确认。
