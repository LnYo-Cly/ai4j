# Ask User Plugin 模块

## 模块 Key

`ask-user-plugin`

## 创建日期

2026-06-09

## 一句话结果

维护官方 `ask-user` 插件包，让 AI4J 插件生态始终有一个可编译、可测试、可发布的参考模块。

## 完成后能得到什么

模块健康运行时，插件作者能直接参考一个完整的 Maven jar 插件结构：`Ai4jExtension`
实现、`META-INF/services` 注册、tool、command、Skill、Prompt、validator 测试和 ServiceLoader
测试。使用者能通过 Maven / BOM 引入 `ai4j-plugin-ask-user`，再按 `discover -> enable -> exposeTool`
门禁把 `ask_user` 暴露给 Agent / Coding Agent。它改善的是插件生态的可学习性和可验证性，而不是
引入远程市场、运行时 jar 热加载或 UI 交互实现。

## 交付物

- 可见产物：`ai4j-plugin-ask-user/`、docs-site 官方插件页、BOM 版本对齐项。
- 负责范围：官方 ask-user 插件源码、资源、测试和文档入口。
- 验证证据：`mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test`，共享构建变更时追加 `mvn -DskipTests package`。

## 第一眼应该看什么

先读 `module_plan.md`，再看 `ai4j-plugin-ask-user/src/main/java/io/github/lnyocly/ai4j/plugin/askuser/AskUserExtension.java` 和 `docs-site/docs/core-sdk/extension/ask-user-plugin.md`。

## 模块职责

负责官方插件样板本身。它需要独立管理，因为它是下游开发者学习插件包结构的参考实现，也会作为可发布 artifact 进入 BOM 版本对齐。

## 边界

- 负责：`ai4j-plugin-ask-user/` 源码、资源、测试和模块 README。
- 共享面：根 `pom.xml`、`ai4j-bom/pom.xml`、README、docs-site、Regression SSoT、Cadence Ledger、harness context。
- 不负责：`ai4j-extension-api` 公共合同、Agent/Coding Agent 适配器、CLI 插件命令实现、远程插件市场或 UI 渲染。

## 完成判断

- 模块仅依赖 `ai4j-extension-api` 和测试依赖。
- 插件 manifest、ServiceLoader、tool、command、Skill、Prompt 都有本地测试覆盖。
- 文档清楚说明宿主介导提问，不承诺阻塞 UI。
- BOM 和根 POM 与模块状态一致。

## 当前工作

当前任务：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-official-ask-user-plugin-wave-10-10f4445f/task_plan.md`。
