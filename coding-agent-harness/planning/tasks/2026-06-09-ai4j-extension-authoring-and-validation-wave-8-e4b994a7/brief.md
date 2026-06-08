# AI4J extension authoring and validation wave 8

## Task ID

`2026-06-09-ai4j-extension-authoring-and-validation-wave-8-e4b994a7`

## 创建日期

2026-06-09

## 一句话结果

AI4J 插件包作者和使用者可以用稳定的公共 validator 与 `ai4j-cli extension validate` 检查 classpath 上的插件包，并按 docs-site 文档完成第三方插件的编写、安装、启用、暴露和资源验证。

## 完成后能得到什么

完成后，第三方开发者不只知道“实现 `Ai4jExtension` + `ServiceLoader`”，还可以在插件项目或宿主应用里运行同一套校验：manifest 是否完整、工具 schema 是否可用、Skill / Prompt classpath 资源是否存在、声明 capability 与实际贡献资源是否一致。使用者也能在引入 Maven / Gradle 依赖后先 `list / inspect / validate`，再决定是否 `enable` 和 `exposeTool`。这补齐插件生态的本地闭环，但不引入远程 marketplace、自动安装或热加载。

## 交付物

- 可见产物：公共 `ExtensionValidator` 报告模型、CLI `extension validate` 子命令、插件作者/使用者文档。
- 修改位置：`ai4j-extension-api/`、`ai4j-cli/`、`docs-site/docs/core-sdk/extension/plugin-packages.md`、`README.md`、harness / regression 治理文档。
- 验证证据：extension API 单测、CLI targeted tests、monorepo package、docs-site typecheck/build、harness status。

## 第一眼应该看什么

先看 `task_plan.md` 的范围和验收标准，再看 `review.md` 的证据表；代码入口是 `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/validation/ExtensionValidator.java` 和 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java`。

## 边界

- 范围内：插件 authoring/validation 公共契约、CLI 校验入口、插件包 docs-site 与 README 更新、相关本地回归。
- 范围外：远程插件市场、自动下载安装、运行时 jar 热加载、provider plugin、发布中央仓库、修复既有 R-008。
- 停止条件：如果需要改变插件加载安全语义、自动执行第三方代码、或绕过 enable/expose 门禁，必须停止并重新设计。

## 完成判断

- `ai4j-extension-api` 提供可复用 validation report，第三方插件测试可直接调用。
- `ai4j-cli extension validate <id>` 与 `--all` 能检查 classpath 插件包并给出 pass/warn/fail。
- 校验只报告问题，不改变 discover / enable / expose 运行时语义。
- docs-site 和 README 写清楚第三方插件的安装、组装和验证路径。
- 相关 Java / docs-site / harness 回归证据已记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

材料包已提交 Agent Review Submission；下一步等待 Human Review Confirmation，agent 不代办人工确认。
