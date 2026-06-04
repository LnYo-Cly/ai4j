# Spring Boot Starter 模块计划

## 模块身份

- 模块 Key：`spring-starter`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-spring-boot-starter/**`
- 共享面：`ai4j/**` API、`ai4j-bom/**`、starter docs
- 依赖模块：`core-sdk`

## 边界

- 可以编辑：Spring Boot starter 源码、测试、模块 POM。
- 禁止编辑：core SDK 行为、FlowGram starter、demo 逻辑，除非任务明确批准。
- 外部依赖：Spring Boot auto-configuration behavior and application context tests。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| SPRING-01 | 维护 starter wiring 合同 | planned | none | core-sdk |
| SPRING-02 | 配置属性与 auto-configuration 回归 | planned | none | SPRING-01 |
| SPRING-03 | BOM/docs 同步 | planned | none | SPRING-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| none | planned | coordinator | none | 有模块任务后替换此行。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-spring-boot-starter -DskipTests=false test` | yes |
| 依赖构建 | `mvn -pl ai4j-spring-boot-starter -am -DskipTests package` | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 starter targeted test。
- 变更文件：只列 starter 目录及批准的共享文件。
- 残余风险：未覆盖真实 Spring app 时必须说明。
- 需要 coordinator 同步：影响 core SDK、BOM 或 docs 时同步。
