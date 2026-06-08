# AI4J Spring Boot extension configuration wave 4

## Task ID

`2026-06-09-ai4j-spring-boot-extension-configuration-wave-4-cb1cd3f6`

## 创建日期

2026-06-09

## 一句话结果

Spring Boot 用户可以通过 `ai.extensions.enabled` 和 `ai.extensions.tools.expose` 配置显式启用 classpath 上的 AI4J 插件包，并获得 `ExtensionRegistry` / `ExtensionRuntimeSnapshot` bean。

## 完成后能得到什么

本任务把插件生态从普通 Java / Agent / Coding Agent 的手写 `ExtensionRegistry` 推进到 Spring Boot 配置化接入。使用者把第三方插件 jar 放进 classpath 后，可以在 `application.yml` 中显式启用插件包并 allowlist 暴露工具；starter 会创建 registry/snapshot bean，后续业务代码可以把 registry 交给 Agent 或 Coding Agent builder。这个结果降低了 Spring 项目接入插件生态的成本，同时保持 discover / enable / expose 三段式安全门禁。

## 交付物

- 可见产物：`ai.extensions.*` 配置面、`ExtensionRegistry` / `ExtensionRuntimeSnapshot` 自动装配、Spring Boot starter 回归测试、docs-site 插件包说明。
- 修改位置：`ai4j-spring-boot-starter/`、`docs-site/docs/core-sdk/extension/`、root/docs-site README、harness task package、Regression / Feature SSoT。
- 验证证据：`mvn -pl ai4j-spring-boot-starter -am -Dtest=ExtensionAutoConfigurationTest -DfailIfNoTests=false -DskipTests=false test` 已通过；完整验证见 `progress.md`。

## 第一眼应该看什么

先读 `task_plan.md` 确认范围，再读 `progress.md` 的验证证据、`review.md` 的残余风险、`docs-site/docs/core-sdk/extension/plugin-packages.md` 的用户文档，以及 `ExtensionAutoConfigurationTest` 的配置合同。

## 边界

- 范围内：Spring Boot starter 配置绑定和 bean 装配、starter 测试、插件包 docs、回归治理材料。
- 范围外：远程 marketplace、CLI 自动安装、运行时 jar 热加载、provider plugin、自动创建 Agent/Coding Agent。
- 停止条件：如果需要动态安装、热加载、provider 注册或新的 Agent runtime 行为，必须另开任务。

## 完成判断

- `AiExtensionProperties` 绑定 `ai.extensions.enabled` 与 `ai.extensions.tools.expose`。
- `AiConfigAutoConfiguration` 自动创建 `ExtensionRegistry` 和 `ExtensionRuntimeSnapshot`。
- 未启用插件时不会暴露工具；启用不存在插件或 expose 未启用工具会 fail fast。
- docs-site 明确 Spring Boot 配置路径和当前不包含能力。
- RG-005 / RG-007 / RG-008 / harness status 验证完成并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

完成 docs/governance 更新后运行完整验证，并提交到 harness review 队列。
