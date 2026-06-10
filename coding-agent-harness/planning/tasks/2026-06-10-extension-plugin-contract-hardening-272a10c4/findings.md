# Extension plugin contract hardening - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Extension API 不应新增 JSON 运行时依赖

- 背景：review finding 要求 validator 真实识别坏 JSON schema；`ai4j-agent` 已有 fastjson2，但 `ai4j-extension-api` 目前没有生产运行时依赖。
- 发现：把 fastjson2 下沉到 extension API 会扩大第三方插件作者的传递依赖面；本轮只需要验证当前 mapper 依赖的最小 JSON object 结构。
- 影响：新增包内 `ExtensionToolSchemaValidator`，使用轻量 JSON parser 检查合法 JSON、根 `type=object`、`properties` / `required` / `enum` / `items` 基础形状。
- 后续：如果未来要支持完整 JSON Schema，应作为独立 API / dependency 设计任务处理。

### Enable 仍是非工具资源的整包信任边界

- 背景：review finding 指出 command、Skill、Prompt、Guardrail 当前没有 tool 类似的 allowlist。
- 发现：补 command/resource/guardrail 级 allowlist 会影响 `ExtensionRuntimeSnapshot`、Spring Boot properties、CLI 命令和 docs，已超出本轮 contract hardening。
- 影响：本轮不引入新的权限模型，只在 docs-site 明确 `enable(...)` 对非 tool 资源的整包信任语义，并保留后续独立设计空间。
- 后续：如要做细粒度 allowlist，建议单独开 API 设计和迁移任务。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 公共 ID/name 契约 | 新增 `requireExtensionId` / `requireToolName` / `requireCommandName` / `requireResourceName` / `requireGuardrailName` | `requireId` 仍被 validation issue code/message 和 classpath path 使用，不能直接收紧 | 全面收紧 `requireId` | accepted |
| Tool schema 校验 | 在 extension API 内部实现最小 JSON object/schema shape validator | 避免新增 fastjson2 传递依赖，同时挡住当前 mapper 无法稳定消费的 schema | 给 extension API 添加 fastjson2 或完整 JSON Schema 引擎 | accepted |
| Scaffold 回归 | 在 CLI 单测中用 `JavaCompiler` 编译生成 main/test 源码，并用 `ServiceLoader` 查找生成 extension | 比纯文本断言更接近真实插件项目，且不依赖本地 Maven install 发布物 | 在单测中运行外部 generated Maven 项目 | accepted |
| 非工具资源 allowlist | 本轮只文档化边界，不实现新权限模型 | API / config / runtime 范围较大，不能塞进契约修复任务 | 同时新增 command/resource/guardrail allowlist | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要完整 JSON Schema 引擎 | 当前不需要；AI4J tool mapper 只消费核心字段，最小结构校验足够覆盖本轮 review finding | maintainer | 未来 schema 能力扩展前 |
