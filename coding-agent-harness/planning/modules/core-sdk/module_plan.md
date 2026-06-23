# Core SDK 模块计划

## 模块身份

- 模块 Key：`core-sdk`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j/**`
- 共享面：根 `pom.xml`、`ai4j-bom/**`、`docs/05-TEST-QA/**`、`docs/11-REFERENCE/**`
- 依赖模块：无

## 边界

- 可以编辑：核心 SDK 源码、测试、模块 POM。
- 禁止编辑：starter、CLI、demo、docs-site 和 webapp demo，除非任务显式扩展范围。
- 外部依赖：provider endpoints、MCP servers、vector stores 和 live credentials 只通过环境配置使用。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| CORE-01 | 维护核心 SDK 行为合同 | planned | none | none |
| CORE-02 | 同步上游 provider / protocol 变化 | in_progress | `tasks/2026-06-22-add-anthropic-messages-adapter-3876ee40/task_plan.md` | CORE-01 |
| CORE-03 | 下游模块影响评估 | planned | none | CORE-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-22-add-anthropic-messages-adapter-3876ee40` | review | coordinator | `tasks/2026-06-22-add-anthropic-messages-adapter-3876ee40/progress.md` | 新增手写 Anthropic Messages 适配器（已实现+单测+live 烟测通过）；CORE-02 |
| `2026-06-23-anthropic-native-messages-surface-5914b973` | review | coordinator | `tasks/2026-06-23-anthropic-native-messages-surface-5914b973/progress.md` | 抽 IMessagesService 一等公民 + thinking + 类型化异常；CORE-02；worktree feature/anthropic-native-surface |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j -DskipTests=false test` | yes |
| 全局 package smoke | `mvn -DskipTests package` | risk-based |

## 交接

- 分支：模块任务使用 `feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 Maven 命令和结果。
- 变更文件：只列出 `ai4j/**` 及显式共享文件。
- 残余风险：live-provider 未验证时必须说明。
- 需要 coordinator 同步：API 或依赖变化影响 BOM、starter、CLI 或 docs 时同步。
