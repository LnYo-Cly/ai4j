# AI4J extension plugin scaffold wave 9 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 现有 extension API 足够生成插件骨架

- 背景：脚手架如果需要新公共 API，会扩大 Wave 9 范围。
- 发现：现有 `Ai4jExtension`、`ExtensionManifest`、`ExtensionContext`、Tool / Command / Skill / Prompt / Guardrail registry、`ExtensionValidator` 已能覆盖一个完整示例插件。
- 影响：本轮只在 `ai4j-cli` 生成项目文件，不改 `ai4j-extension-api`。
- 后续：生成项目的 validator test 直接调用 `ExtensionRegistry.of(new ...())` 和 `ExtensionValidator.validate(...)`。

### 脚手架必须拒绝非空目录

- 背景：用户明确要求不要覆盖已有业务文档、历史任务、回归记录或用户改动。
- 发现：插件脚手架生成多个固定路径文件；若目标目录已有内容，安全默认值应拒绝。
- 影响：`extension init` 默认只允许不存在目录或空目录。
- 后续：CLI test 覆盖非空目录拒绝。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 脚手架归属 | `ai4j-cli` | 这是开发者入口命令，不需要污染公共 runtime API。 | 放入 `ai4j-extension-api` 的 generator 类 | accepted |
| 生成项目类型 | Java 8 Maven jar | 当前仓库是 Java 8 Maven monorepo，插件包也是普通 jar 依赖。 | Gradle、多模板、多语言 | accepted |
| 覆盖策略 | 非空目录拒绝 | 安全且符合“不覆盖用户改动”。 | `--force` 覆盖 | accepted |
| 市场/安装语义 | 不实现 | 当前稳定路径是用户自行管理 Maven/Gradle 依赖。 | CLI install / marketplace / hot load | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要远程插件市场或自动安装 | 本轮不做，后续单独规划 | product owner | Wave 9 closeout 后 |
