# AI4J extension plugin scaffold wave 9

## Task ID

`2026-06-09-ai4j-extension-plugin-scaffold-wave-9-1923fbfb`

## 创建日期

2026-06-09

## 一句话结果

为插件作者提供 `ai4j-cli extension init`，一条命令生成可本地编译、可校验的 Java 8 Maven 插件包骨架。

## 完成后能得到什么

第三方开发者可以用 CLI 在空目录生成一个 AI4J plugin package 示例项目，里面包含 `Ai4jExtension` 实现、`ServiceLoader` 注册文件、示例 Tool / Command / Skill / Prompt / Guardrail、本地 validator 测试和 README。这个结果降低插件生态的第一步接入成本，让开发者先拿到能编译、能运行 `ExtensionValidator` 的最小插件工程，再按自己的业务替换工具逻辑和资源内容。

## 交付物

- 可见产物：`ai4j-cli extension init <directory> --id ... --package ... --name ...` 命令。
- 修改位置：`ai4j-cli/` CLI 与测试、根 `README.md`、`docs-site/docs/core-sdk/extension/plugin-packages.md`、Feature / Regression / Cadence 治理记录。
- 验证证据：CLI targeted tests、临时脚手架 Maven smoke、monorepo package、docs-site typecheck/build、harness status。

## 第一眼应该看什么

1. `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java`
2. `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java`
3. `docs-site/docs/core-sdk/extension/plugin-packages.md`
4. 本任务 `progress.md` 的验证记录

## 边界

- 范围内：本地 Maven 插件项目骨架生成、命令参数校验、非空目录拒绝、生成内容文档和测试。
- 范围外：远程 marketplace、CLI 自动安装插件依赖、运行时 jar 热加载、公共 extension API 新增能力。
- 停止条件：若生成项目必须改变公共 API、要求远程依赖安装语义、或需要覆盖用户已有目录，必须暂停并重新确认。

## 完成判断

- `extension init` 可以在空目录生成完整 Java 8 Maven 插件骨架。
- 非空目录默认拒绝，避免覆盖用户已有文件。
- 生成的 test 使用公共 `ExtensionValidator` 校验插件 contract。
- README 与 docs-site 说明 scaffold 命令、生成结构、后续验证命令和边界。
- 目标回归与 harness status 完成并记录在 `progress.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`
- 完成条件：验证证据必须记录到 `progress.md`，提交审查只能走 `task-review`，不能伪造人工确认。

## 当前下一步

更新任务计划和执行策略，然后实现 `CliExtensionCommand` 的 `init` 子命令与 CLI 测试。
