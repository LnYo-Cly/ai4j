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
| T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12 | P0-A AgentSession runtime container - Brief | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-a-agentsession-runtime-container-389dbf12/task_plan.md | none |
| T-P0-B-MEMORY-COMPACT-CONTEXT-PROJECTOR-47EFFD57 | P0-B Memory Compact Context Projector - Brief | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-b-memory-compact-context-projector-47effd57/task_plan.md | T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12 |
| T-P0-C-AGENT-PLUGIN-LIFECYCLE-HOOKS-10DF8009 | P0-C Agent plugin lifecycle hooks | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009/task_plan.md | T-P0-B-MEMORY-COMPACT-CONTEXT-PROJECTOR-47EFFD57 |
| T-P0-D-AGENT-APPROVAL-AND-PERMISSION-POLICY-95B57B | P0-D Agent approval and permission policy | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/task_plan.md | T-P0-C-AGENT-PLUGIN-LIFECYCLE-HOOKS-10DF8009 |
| T-P1-A-AGENT-BLUEPRINT-SCHEMA-MODEL-LOADER-VALIDAT | P1-A Agent Blueprint schema model loader validator | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0/task_plan.md | T-P0-D-AGENT-APPROVAL-AND-PERMISSION-POLICY-95B57B |
| T-P1-B-AGENT-BLUEPRINT-TO-AGENTFACTORY-8B418210 | P1-B Agent Blueprint to AgentFactory | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210/task_plan.md | T-P1-A-AGENT-BLUEPRINT-SCHEMA-MODEL-LOADER-VALIDAT |
| T-P1-C-CLI-RUN-AGENT-BLUEPRINT-YAML-377E1F25 | P1-C CLI run Agent Blueprint YAML | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-c-cli-run-agent-blueprint-yaml-377e1f25/task_plan.md | T-P1-B-AGENT-BLUEPRINT-TO-AGENTFACTORY-8B418210 |
| T-P2-A-SANDBOX-SPI-MODEL-C9C66766 | P2-A Sandbox SPI model | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p2-a-sandbox-spi-model-c9c66766/task_plan.md | T-P1-C-CLI-RUN-AGENT-BLUEPRINT-YAML-377E1F25 |
| T-P2-B-AGENTSESSION-SANDBOX-BINDING-E8175553 | P2-B AgentSession sandbox binding | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p2-b-agentsession-sandbox-binding-e8175553/task_plan.md | T-P2-A-SANDBOX-SPI-MODEL-C9C66766 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-20-p0-a-agentsession-runtime-container-389dbf12` | implementation-verified | coordinator | `mvn -pl ai4j-agent -am -DskipTests=false test`; `npm run build` in `docs-site` | P0-A adds AgentSession metadata/event log/snapshot/store/resume foundations; PR/CI/merge pending. |
| `2026-06-20-p0-b-memory-compact-context-projector-47effd57` | implementation-verified | coordinator | `mvn -pl ai4j-agent -am -DskipTests=false test`; `npm run build` in `docs-site` | P0-B adds ContextProjector, ContextBudget, ContextReport, CompactPolicy, CompactResult, and session compact snapshot foundations. |
| `2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009` | merged | coordinator | `mvn -pl ai4j-extension-api,ai4j-agent -am -DskipTests=false test`; `npm run build` in `docs-site` | P0-C optional lifecycle hook contract and runtime dispatch merged via PR #105. |
| `2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5` | implementation-verified | coordinator | `mvn -pl ai4j-agent -am "-Dtest=AgentApprovalPermissionPolicyTest" -DskipTests=false -DfailIfNoTests=false test`; `mvn -pl ai4j-agent -am -DskipTests=false test`; `npm run build` in `docs-site` | P0-D adds host-side tool approval / permission policy foundation; task-review/PR pending. |
| `2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0` | planning-recorded | coordinator | `references/agent-blueprint-p1a-execution-plan.md`; `task_plan.md`; `visual_map.md` | P1-A will add single Agent YAML Blueprint schema/model/loader/validator; implementation should continue in `.worktrees/feature/agent-blueprint-schema-loader`. |
| `2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210` | implementation-verified | coordinator | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintFactoryTest" -DskipTests=false -DfailIfNoTests=false test`; `mvn -pl ai4j-agent -am -DskipTests=false test`; `npm run build` in `docs-site` | P1-B adds host-supplied `AgentFactory` / `AgentFactoryContext`, deterministic mapping tests, and docs-site Agent Blueprint update; task-review/PR pending. |
| `2026-06-20-p1-c-cli-run-agent-blueprint-yaml-377e1f25` | implementation-verified | coordinator | `mvn -pl ai4j-cli -am "-Dtest=AgentBlueprintRunCommandTest,Ai4jCliTest" -DskipTests=false -DfailIfNoTests=false test`; `mvn -pl ai4j-cli -am -DskipTests=false test`; `npm --prefix docs-site run build` | P1-C adds top-level `ai4j-cli run <agent.yaml>` and host-side provider/profile resolution with no-token/no-real-sandbox boundaries; task-review/PR pending. |
| `2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5` | review-pending | coordinator | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` | Historical active item; no changes in this P0-A branch. |

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
