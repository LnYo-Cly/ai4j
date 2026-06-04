# FlowGram Demo Backend 模块计划

## 模块身份

- 模块 Key：`flowgram-demo`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-flowgram-demo/**`
- 共享面：FlowGram starter contracts、webapp demo API expectations
- 依赖模块：`flowgram-starter`

## 边界

- 可以编辑：demo backend 源码、测试、配置、模块 POM。
- 禁止编辑：FlowGram starter production logic、webapp UI，除非任务明确批准。
- 外部依赖：demo runtime environment、local ports、FlowGram example data。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| FLOWDEMO-01 | 维护 demo backend contract | planned | none | flowgram-starter |
| FLOWDEMO-02 | starter integration smoke | planned | none | FLOWDEMO-01 |
| FLOWDEMO-03 | webapp contract sync | planned | none | FLOWDEMO-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| none | planned | coordinator | none | 有模块任务后替换此行。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-flowgram-demo -DskipTests=false test` | risk-based |
| demo smoke | backend 启动或接口 smoke 记录 | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 backend test 或 smoke。
- 变更文件：只列 demo backend 目录及批准的共享文件。
- 残余风险：没有实际启动 demo 时必须说明。
- 需要 coordinator 同步：接口影响 webapp demo 或 starter docs 时同步。
