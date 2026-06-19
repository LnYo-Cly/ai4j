# Agent Runtime 模块计划

## 模块身份

- 模块 Key：`agent-runtime`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-agent/**`
- 共享面：`AGENT.md`、`docs/11-REFERENCE/**`、trace 或 workflow 相关 docs
- 依赖模块：`core-sdk`

## 边界

- 可以编辑：agent runtime 源码、测试、模块 POM。
- 禁止编辑：core SDK、CLI、FlowGram starter 和 demo，除非任务列为跨模块变更。
- 外部依赖：provider credentials、trace sinks 或外部 orchestration 只通过配置接入。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12 | P0-A AgentSession runtime container | active | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-a-agentsession-runtime-container-389dbf12/task_plan.md | none |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5` | review-pending | coordinator | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` | Wave 3 adds extension tool adapter and `.extensions(...)` AgentBuilder entry; full suite still has existing R-008 blocker. |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-agent -DskipTests=false test` | yes |
| 依赖构建 | `mvn -pl ai4j-agent -am -DskipTests package` | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 agent runtime targeted test。
- 变更文件：只列 `ai4j-agent/**` 及批准的共享文件。
- 残余风险：live provider 或外部 trace sink 未跑时必须说明。
- 需要 coordinator 同步：影响 FlowGram starter、CLI 或 docs 时同步。
