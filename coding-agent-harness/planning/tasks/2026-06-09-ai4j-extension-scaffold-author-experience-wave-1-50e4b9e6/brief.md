# AI4J Extension Scaffold Author Experience Wave 11

## Task ID

`2026-06-09-ai4j-extension-scaffold-author-experience-wave-1-50e4b9e6`

## 创建日期

2026-06-09

## 一句话结果

`ai4j-cli extension init` 生成的第三方插件项目，从第一版开始就包含作者发布清单、使用者接入步骤、验证命令和安全边界说明。

## 完成后能得到什么

第三方开发者用 CLI 生成插件骨架后，不需要再从 docs-site 拼接零散步骤：README 会列出插件坐标、manifest id、tools / commands / skills / prompts / guardrails 清单、使用者 enable/expose 接入方式、CLI validate / inspect / resource / run 本地验证路径，以及发布前必须声明的权限、副作用和环境变量。docs-site 同步补一份 author cookbook，解释从 scaffold 到替换业务逻辑、校验、发布说明的最短闭环。

## 交付物

- 可见产物：增强后的 scaffold README；docs-site 插件作者 cookbook；CLI scaffold 回归测试。
- 修改位置：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/ExtensionScaffoldGenerator.java`、`ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java`、`docs-site/docs/core-sdk/extension/`、harness/SSoT 记录。
- 验证证据：CLI targeted test、CLI module test、docs-site typecheck/build、diff check、harness status。

## 第一眼应该看什么

先读 `task_plan.md` 的边界，再看 `ExtensionScaffoldGenerator.renderReadme(...)` 和新增 docs-site cookbook；验证证据记录在 `progress.md`。

## 边界

- 范围内：改进本地 Maven 插件 scaffold 的作者说明与 docs-site authoring 文档；补 CLI 回归测试。
- 范围外：远程插件市场、CLI 自动写依赖、运行时热加载 jar、公共 extension API 扩容、Agent/Coding Agent 执行语义变更。
- 停止条件：需要新增公共 manifest 字段、改变 enable/expose 门禁或引入远程安装语义时，必须停止并重新确认。

## 完成判断

- `extension init` 生成的 README 包含作者发布清单、使用者集成路径、验证命令和权限/副作用声明。
- docs-site 有独立 author cookbook，并从 Plugin Packages 页面可达。
- CLI scaffold 测试覆盖新增 README 合同。
- Java/docs/harness 验证通过，且没有引入远程 marketplace / auto-install / hotload 暗示。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

更新 task plan / execution strategy 后，修改 scaffold README 与 docs-site author cookbook。
