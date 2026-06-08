# AI4J extension authoring and validation wave 8 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Validator 应属于 extension API，而不是 CLI

- 背景：Wave 8 目标是让第三方插件作者和宿主使用者都能检查插件包，而不是只给 CLI 一个展示功能。
- 发现：`ai4j-extension-api` 已拥有 manifest、runtime inspection、resource resolver 和 capability contract；CLI 只是其中一个消费面。
- 影响：校验规则落在 `io.github.lnyocly.ai4j.extension.validation`，CLI 只复用 `ExtensionValidator` 输出报告，避免未来第三方插件测试和 CLI 各维护一套规则。
- 后续：无。

### Validation 不是安全审计

- 背景：插件校验容易被误读为“安装前安全扫描”或“官方市场审核”。
- 发现：当前 validator 能检查 manifest 完整性、capability 与贡献资源一致性、tool schema 基础形状、Skill / Prompt classpath 资源和 `apply(...)` 失败，但无法证明第三方代码是否安全可信。
- 影响：docs-site 明确写出 `validate` 会调用 `apply(...)` 收集 runtime 贡献，但不会执行 command、不会暴露 tool 给模型，也不会替宿主判断插件是否可信。
- 后续：如果后续要做安全分级、签名、仓库信誉或权限审计，必须另开设计任务。

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Validator 所在模块 | `ai4j-extension-api` | 校验规则是插件公共契约，第三方测试和 CLI 都应复用同一套 API | 放在 `ai4j-cli`，会让非 CLI 用户无法复用 | accepted |
| CLI 子命令 | `ai4j-cli extension validate <id>\|--all` | 与现有 `list / inspect / run / resource` 风格一致，并支持单插件和全量 classpath 校验 | 把校验塞进 `inspect --runtime`，会混淆展示和验收语义 | accepted |
| 退出码 | 有 error 返回 `2`，只有 warning 返回 `0` | 便于插件项目 CI 阻断明显坏包，同时不因建议项阻断开发 | warning 也返回非零，容易让早期插件 authoring 过重 | accepted |
| JSON 依赖 | 不引入 | Java 8 基线下保持 extension API 轻量，tool schema 只做基础形状校验 | 引入 JSON Schema 解析器，本轮范围过大 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否做远程插件市场 / 自动安装 | 不做；当前只做 classpath 插件 authoring 与 validation 闭环 | coordinator | 后续独立任务 |
| 是否修复 R-008 broad suite blocker | 不做；本轮只记录为既有残余 | coordinator | R-008 修复任务 |
