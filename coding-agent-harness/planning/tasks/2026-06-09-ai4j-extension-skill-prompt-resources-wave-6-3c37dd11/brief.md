# AI4J extension skill prompt resources wave 6

## Task ID

`2026-06-09-ai4j-extension-skill-prompt-resources-wave-6-3c37dd11`

## 创建日期

2026-06-09

## 一句话结果

AI4J 插件贡献的 Skill / Prompt 资源在显式启用后可以被 CLI 检查，并能作为 Coding Agent 的只读可读上下文资源使用。

## 完成后能得到什么

本任务完成后，第三方插件不只可以注册工具和命令，也可以把 `SKILL.md` 与 prompt template 打包进 jar。使用者显式启用插件后，CLI 能读取资源正文以验证打包是否正确；Coding Agent 会在系统提示中看到可用 Skill / Prompt 清单，并按需通过 `read_file` 读取资源文件。资源只加入只读根，不扩大 workspace 写权限，为后续官方样板插件和第三方插件生态打下可用基础。

## 交付物

- 可见产物：CLI `extension resource --enable <id> <skill|prompt> <name>`；Coding Agent `<available_skills>` / `<available_prompts>` 资源清单。
- 修改位置：`ai4j-extension-api/`、`ai4j-coding/`、`ai4j-cli/`、`docs-site/docs/core-sdk/extension/plugin-packages.md`、Regression SSoT / Cadence Ledger。
- 验证证据：targeted JUnit、monorepo package、docs-site typecheck/build、harness status、diff check。

## 第一眼应该看什么

先读 `task_plan.md` 的范围，再读 `review.md` 和 `progress.md` 的证据。代码入口从 `CodingExtensionResources`、`CodingContextPromptAssembler`、`CliExtensionCommand` 和 `ExtensionResourceResolver` 开始。

## 边界

- 范围内：extension Skill / Prompt 资源来源、读取、Coding Agent 投影、CLI resource 命令、测试、文档和回归治理。
- 范围外：插件安装、marketplace、hotload、provider plugin、guardrail enforcement、完整 RAG / memory / MCP plugin API。
- 停止条件：如果必须扩大到执行权限、远程下载安装或安全策略拦截，另开任务。

## 完成判断

- `Ai4jCliTest` 覆盖 resource 命令必须 `--enable`，并能读取 Skill / Prompt 正文。
- `CodingSkillSupportTest` 覆盖 extension Skill / Prompt 注入、可读和不可写。
- docs-site 插件页说明使用者路径、开发者路径和当前边界。
- Regression SSoT / Cadence Ledger 记录本次新增回归面。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行最终 package/docs/harness 验证，提交并进入 Agent Review Submission。
