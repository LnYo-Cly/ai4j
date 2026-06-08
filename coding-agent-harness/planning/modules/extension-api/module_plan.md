# Extension API 模块计划

## 模块身份

- 模块 Key：`extension-api`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-extension-api/**`
- 共享面：根 `pom.xml`、`ai4j-bom/**`、`.github/workflows/java-regression.yml`、`docs/05-TEST-QA/**`、`docs/11-REFERENCE/**`
- 依赖模块：无

## 边界

- 可以编辑：扩展 API 源码、测试、模块 POM。
- 禁止编辑：core SDK、agent、coding、CLI、starter、demo、docs-site 的运行时接入，除非任务显式扩展范围。
- 外部依赖：默认不引入运行时依赖；公共合同必须保持第三方插件可轻量依赖。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| EXT-01 | 维护 manifest / discovery / enable / expose 合同 | in_progress | `coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-system-wave-1-a924bf99/task_plan.md` | none |
| EXT-02 | 下游 runtime 适配设计 | planned | none | EXT-01 |
| EXT-03 | CLI / Spring Boot inspect 和配置接入 | planned | none | EXT-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-08-ai4j-extension-system-wave-1-a924bf99` | in_progress | coordinator | `mvn -pl ai4j-extension-api -DskipTests=false test` | Wave 1 只交付公共合同和本地门禁，不接入运行时宿主 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-extension-api -DskipTests=false test` | yes |
| 全局 package smoke | `mvn -DskipTests package` | shared build / BOM changes required |

## 交接

- 分支：模块任务使用 `feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 Maven 命令和结果。
- 变更文件：只列出 `ai4j-extension-api/**` 及显式共享文件。
- 残余风险：runtime adapter 未接入时必须说明。
- 需要 coordinator 同步：API 或依赖变化影响 BOM、CI、starter、CLI 或 docs 时同步。
