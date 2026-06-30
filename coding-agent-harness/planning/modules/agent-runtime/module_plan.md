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
| T-AGENT-DURABLE-SESSION-STORE-JDBC-RESUME-CACHE-PE | Agent durable session store (JDBC) + resume cache persistence | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-24-agent-durable-session-store-jdbc-resume-cache-pe-2cc9cf5d/task_plan.md | none |
| T-AGENT-FAILURE-RECOVERY-VIA-RESUMABLE-MODEL-TOOL- | Agent failure recovery via resumable model/tool decorators | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-24-agent-failure-recovery-via-resumable-model-tool-bf9ec3b7/task_plan.md | T-AGENT-DURABLE-SESSION-STORE-JDBC-RESUME-CACHE-PE |
| T-AGENT-NODE-IO-CAPTURE-NODE-LEVEL-REPLAY-4A61B820 | Agent node IO capture + node-level replay | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-24-agent-node-io-capture-node-level-replay-4a61b820/task_plan.md | T-AGENT-FAILURE-RECOVERY-VIA-RESUMABLE-MODEL-TOOL- |
| T-AGENT-OBSERVABILITY-ENHANCEMENT-57C03F6B | Agent observability enhancement | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-22-agent-observability-enhancement-57c03f6b/task_plan.md | T-AGENT-NODE-IO-CAPTURE-NODE-LEVEL-REPLAY-4A61B820 |
| T-AI4J-AGENT-SDK-ARCHITECTURE-ENHANCEMENT-ROADMAP- | AI4J Agent SDK architecture enhancement roadmap | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/task_plan.md | T-AGENT-OBSERVABILITY-ENHANCEMENT-57C03F6B |
| T-MESSAGES-MODEL-CLIENT-0F5BAD51 | Anthropic MessagesModelClient for agent | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-23-messages-model-client-0f5bad51/task_plan.md | T-AI4J-AGENT-SDK-ARCHITECTURE-ENHANCEMENT-ROADMAP- |
| T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12 | P0-A AgentSession runtime container - Brief | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-a-agentsession-runtime-container-389dbf12/task_plan.md | T-MESSAGES-MODEL-CLIENT-0F5BAD51 |
| T-P0-B-MEMORY-COMPACT-CONTEXT-PROJECTOR-47EFFD57 | P0-B Memory Compact Context Projector - Brief | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-b-memory-compact-context-projector-47effd57/task_plan.md | T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12 |
| T-P0-C-AGENT-PLUGIN-LIFECYCLE-HOOKS-10DF8009 | P0-C Agent plugin lifecycle hooks | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009/task_plan.md | T-P0-B-MEMORY-COMPACT-CONTEXT-PROJECTOR-47EFFD57 |
| T-P0-D-AGENT-APPROVAL-AND-PERMISSION-POLICY-95B57B | P0-D Agent approval and permission policy | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/task_plan.md | T-P0-C-AGENT-PLUGIN-LIFECYCLE-HOOKS-10DF8009 |
| T-P1-A-AGENT-BLUEPRINT-SCHEMA-MODEL-LOADER-VALIDAT | P1-A Agent Blueprint schema model loader validator | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0/task_plan.md | T-P0-D-AGENT-APPROVAL-AND-PERMISSION-POLICY-95B57B |
| T-P1-B-AGENT-BLUEPRINT-TO-AGENTFACTORY-8B418210 | P1-B Agent Blueprint to AgentFactory | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210/task_plan.md | T-P1-A-AGENT-BLUEPRINT-SCHEMA-MODEL-LOADER-VALIDAT |
| T-P1-C-CLI-RUN-AGENT-BLUEPRINT-YAML-377E1F25 | P1-C CLI run Agent Blueprint YAML | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-c-cli-run-agent-blueprint-yaml-377e1f25/task_plan.md | T-P1-B-AGENT-BLUEPRINT-TO-AGENTFACTORY-8B418210 |
| T-P2-A-SANDBOX-SPI-MODEL-C9C66766 | P2-A Sandbox SPI model | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p2-a-sandbox-spi-model-c9c66766/task_plan.md | T-P1-C-CLI-RUN-AGENT-BLUEPRINT-YAML-377E1F25 |
| T-P2-B-AGENTSESSION-SANDBOX-BINDING-E8175553 | P2-B AgentSession sandbox binding | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p2-b-agentsession-sandbox-binding-e8175553/task_plan.md | T-P2-A-SANDBOX-SPI-MODEL-C9C66766 |
| T-P2-C-DAYTONA-SANDBOX-PROVIDER-7263B5B5 | P2-C Daytona sandbox provider | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-21-p2-c-daytona-sandbox-provider-7263b5b5/task_plan.md | T-P2-B-AGENTSESSION-SANDBOX-BINDING-E8175553 |
| T-P2-D-E2B-SANDBOX-PROVIDER-7DFDB7C6 | P2-D E2B sandbox provider | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-21-p2-d-e2b-sandbox-provider-7dfdb7c6/task_plan.md | T-P2-C-DAYTONA-SANDBOX-PROVIDER-7263B5B5 |
| T-TAMPER-EVIDENT-HASH-CHAINED-SESSION-EVENT-LOG-98 | Tamper-evident hash-chained session event log | merged | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-24-tamper-evident-hash-chained-session-event-log-98f15dcd/task_plan.md | T-P2-D-E2B-SANDBOX-PROVIDER-7DFDB7C6 |
| T-TOOL-INTERCEPTOR-HOOKS-PI-ALIGNED-OBSERVE-BLOCK- | Tool interceptor hooks (pi-aligned: observe/block/modify/route-to-sandbox) | active | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-30-tool-interceptor-hooks-pi-aligned-observe-block-d042a8cd/task_plan.md | T-TAMPER-EVIDENT-HASH-CHAINED-SESSION-EVENT-LOG-98 |

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
| `2026-06-23-messages-model-client-0f5bad51` | review | coordinator | `tasks/2026-06-23-messages-model-client-0f5bad51/progress.md` | agent 层 MessagesModelClient 委托 IMessagesService（原生 Anthropic 线协议）；worktree feature/anthropic-native-surface |
| `2026-06-21-p2-d-e2b-sandbox-provider-7dfdb7c6` | done | coordinator | `tasks/2026-06-21-p2-d-e2b-sandbox-provider-7dfdb7c6/progress.md` | E2B sandbox provider：Connect server-streaming 执行 + X-API-Key/Bearer 双鉴权；PR #142 merged 7dcd445；15 离线 + live 烟测全绿 |

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
