# AI4J extension runtime adapter wave 3

## Task ID

`2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5`

## 创建日期

2026-06-09

## 一句话结果

AI4J extension 插件包资源可以通过显式 enable/expose 门禁进入 Agent 与 Coding Agent 的 tool loop。

## 完成后能得到什么

本任务完成后，第三方插件作者可以用 `ai4j-extension-api` 提供 tool spec 和 executor；使用者把插件 jar 放进 classpath 后，通过 `ExtensionRegistry.discover().enable(...).exposeTool(...)` 明确授权，再用 Agent / Coding Agent builder 的 `.extensions(...)` 接入运行时。Agent 主循环、Coding Agent session 和已有 tool result 回传流程不需要为每个插件单独改代码。

文档站同步增加 plugin package 页面，说明使用者接入、开发者实现、ServiceLoader 注册、安全门禁、发布建议和当前不包含的能力，避免把插件生态误写成 marketplace、hotload 或 provider 自动注册。

## 交付物

- 可见产物：
  - Agent / Coding Agent `.extensions(...)` builder 入口。
  - `ExtensionAgentTools` adapter 包。
  - docs-site `core-sdk/extension/plugin-packages.md`。
  - README 与 docs-site README 的插件生态入口。
- 修改位置：
  - `ai4j-agent/**`
  - `ai4j-coding/**`
  - `docs-site/**`
  - `README.md`
  - harness task package / Feature SSoT / regression ledger
- 验证证据：
  - `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test`
  - `mvn -pl ai4j-coding -am "-Dtest=CodingAgentBuilderTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test`
  - 后续完整验证记录见 `progress.md`

## 第一眼应该看什么

1. `task_plan.md`：范围、验收标准和明确不做的 marketplace / hotload / provider plugin。
2. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/extension/ExtensionAgentTools.java`：runtime adapter 入口。
3. `ai4j-agent/src/test/java/io/github/lnyocly/agent/ExtensionAgentToolsTest.java`：agent loop 与 enable/expose 门禁回归。
4. `ai4j-coding/src/test/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilderTest.java`：coding session 插件工具回归。
5. `docs-site/docs/core-sdk/extension/plugin-packages.md`：对外文档边界。

## 边界

- 范围内：Agent / Coding Agent runtime adapter、测试、插件包文档、治理证据。
- 范围外：远程 marketplace、CLI 自动安装插件、运行时热加载 jar、provider 自动注册、Spring Boot 配置化插件装配。
- 停止条件：如果需要引入远程安装、第三方包仓库、Spring Boot 配置属性或 provider 插件化，需要另开任务和用户确认。

## 完成判断

- 已暴露插件工具能进入 Agent 模型 tool 列表并被执行。
- 只启用但未暴露的插件工具不会进入模型 tool 列表。
- Coding Agent session 能同时保留内置 workspace tools 和 extension tools。
- docs-site 对 plugin package 的当前能力和边界表述准确。
- 目标 Java 回归、docs-site 构建和 harness status 已记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并提交 Agent Review Submission。

## 当前下一步

完成治理记录和剩余验证，然后用 harness CLI 提交 review packet，最后提交并推送。
