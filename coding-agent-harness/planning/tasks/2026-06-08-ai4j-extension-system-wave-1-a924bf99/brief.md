# AI4J extension system wave 1

## Task ID

`2026-06-08-ai4j-extension-system-wave-1-a924bf99`

## 创建日期

2026-06-08

## 一句话结果

新增 `ai4j-extension-api` 轻量模块，为第三方扩展包提供 manifest、ServiceLoader discovery、显式 enable/expose 门禁和中立资源注册合同。

## 完成后能得到什么

项目现在有一个独立于 core SDK、Agent runtime、CLI 和 Spring Boot starter 的扩展 API 模块。第三方开发者可以只依赖这个轻量 artifact 实现 `Ai4jExtension`，声明能力后通过 ServiceLoader 被发现；使用者侧则可以先 inspect/discover，再显式 enable package，并且只有 allowlist 的 tool 才会进入可暴露快照。这个结果为后续 CLI inspect、Spring Boot 配置绑定、Agent/Coding runtime 适配和官方样板插件提供稳定底座。

## 交付物

- 可见产物：`ai4j-extension-api/` 模块、RG-010 回归 gate、CI module matrix 更新、harness 模块登记。
- 修改位置：根 `pom.xml`、`ai4j-bom/pom.xml`、`.github/workflows/java-regression.yml`、`AGENTS.md`、`docs/05-TEST-QA/`、`docs/11-REFERENCE/`、`coding-agent-harness/` context/module/task 文件。
- 验证证据：`mvn -pl ai4j-extension-api -DskipTests=false test` 通过 7 个测试；`mvn -DskipTests package` 通过根 POM + 9 个 Java/BOM 模块。

## 第一眼应该看什么

1. `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionRegistry.java`
2. `ai4j-extension-api/src/test/java/io/github/lnyocly/ai4j/extension/ExtensionRegistryTest.java`
3. `coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-system-wave-1-a924bf99/walkthrough.md`

## 边界

- 范围内：新增轻量扩展 API 模块、公共合同、deterministic tests、Maven/BOM/CI/回归台账/harness context 同步。
- 范围外：CLI `ai4j extension list/inspect/enable`、Spring Boot `ai4j.extensions.*` 配置绑定、AgentToolRegistry 适配、Coding skill/prompt 实际加载、Marketplace、runtime jar download、hot reload、provider plugin。
- 停止条件：如果需要把扩展能力接入 agent/CLI/Spring 运行时，必须开后续任务；本任务只交付公共合同和本地门禁。

## 完成判断

- `ai4j-extension-api` 是独立 Maven module，并进入根 reactor、BOM 和 Java CI matrix。
- Extension discovery、enable、tool expose 三个门禁在 API 和测试中分离。
- 未声明 capability 的注册会失败；重复 extension id、未知 enable、未注册 tool expose 都不会静默通过。
- RG-010 和 package smoke 证据已记录。
- 任务包包含 progress、review、walkthrough 和 lesson routing。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并通过 `task-review` 提交材料包。

## 当前下一步

等待人工确认 review packet；后续推荐开 Wave 2 任务实现 CLI inspect 和 Spring Boot 配置绑定。
