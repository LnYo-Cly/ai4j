# FlowGram Starter 模块计划

## 模块身份

- 模块 Key：`flowgram-starter`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-flowgram-spring-boot-starter/**`
- 共享面：`ai4j-agent/**` API、FlowGram demo contracts、trace docs
- 依赖模块：`agent-runtime`

## 边界

- 可以编辑：FlowGram starter 源码、测试、模块 POM。
- 禁止编辑：demo backend、webapp、agent runtime 内核，除非任务明确批准。
- 外部依赖：FlowGram runtime semantics、task API consumers、trace bridge contracts。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| FLOWSTART-01 | 维护 FlowGram starter contract | planned | none | agent-runtime |
| FLOWSTART-02 | task API / trace bridge 回归 | planned | none | FLOWSTART-01 |
| FLOWSTART-03 | demo backend 同步 | planned | none | FLOWSTART-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| none | planned | coordinator | none | 有模块任务后替换此行。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test` | yes |
| 依赖构建 | `mvn -pl ai4j-flowgram-spring-boot-starter -am -DskipTests package` | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 starter targeted test。
- 变更文件：只列 FlowGram starter 目录及批准的共享文件。
- 残余风险：demo 或外部 FlowGram 运行时未验证时必须说明。
- 需要 coordinator 同步：影响 agent runtime、demo backend 或 docs 时同步。
