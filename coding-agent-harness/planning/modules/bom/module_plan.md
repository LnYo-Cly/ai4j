# Version Alignment BOM 模块计划

## 模块身份

- 模块 Key：`bom`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-bom/**`
- 共享面：根 `pom.xml`、模块 POM、release profile
- 依赖模块：无

## 边界

- 可以编辑：BOM POM 和版本对齐说明。
- 禁止编辑：功能模块源码，除非任务明确为跨模块 release 变更。
- 外部依赖：Maven Central consumers、release signing、dependency resolution。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| BOM-01 | 维护版本对齐合同 | planned | none | none |
| BOM-02 | release/package smoke | planned | none | BOM-01 |
| BOM-03 | 下游 starter 消费影响评估 | planned | none | BOM-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| none | planned | coordinator | none | 有模块任务后替换此行。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| BOM package | `mvn -pl ai4j-bom -DskipTests package` | yes |
| 全局 package smoke | `mvn -DskipTests package` | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 Maven package 或 dependency resolution 证据。
- 变更文件：只列 `ai4j-bom/**` 及批准的共享 POM。
- 残余风险：release signing 或 deploy 未执行时必须说明。
- 需要 coordinator 同步：影响模块版本、release docs 或 starter consumers 时同步。
