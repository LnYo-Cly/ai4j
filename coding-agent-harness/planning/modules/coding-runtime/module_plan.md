# Coding Runtime 模块计划

## 模块身份

- 模块 Key：`coding-runtime`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-coding/**`
- 共享面：`docs/11-REFERENCE/testing-standard.md`、CLI 集成文档
- 依赖模块：`core-sdk`

## 边界

- 可以编辑：coding runtime 源码、测试、模块 POM。
- 禁止编辑：CLI host、core SDK、agent runtime，除非任务明确批准。
- 外部依赖：本地 workspace、文件系统、命令执行环境；不得硬编码本机路径或 secrets。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| CODING-01 | 维护 workspace-aware runtime 合同 | planned | none | core-sdk |
| CODING-02 | outer loop / compaction 回归 | planned | none | CODING-01 |
| CODING-03 | CLI host 同步评估 | planned | none | CODING-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| none | planned | coordinator | none | 有模块任务后替换此行。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-coding -DskipTests=false test` | yes |
| 依赖构建 | `mvn -pl ai4j-coding -am -DskipTests package` | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 targeted Maven 测试。
- 变更文件：只列 `ai4j-coding/**` 及批准的共享文件。
- 残余风险：本地 workspace 行为未覆盖时必须说明。
- 需要 coordinator 同步：影响 CLI host 或用户文档时同步。
