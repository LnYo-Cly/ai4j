# Agent Runtime 模块计划

## 模块身份

- 模块 Key：`agent-runtime`
- 负责人：coordinator
- 分支：`dev`
- 写入范围：`ai4j-agent/**`
- 共享面：`ai4j-extension-api/**`、`ai4j-cli/**`、`ai4j-coding/**`、`docs-site/**`、`docs/05-TEST-QA/**`（仅在任务明确跨模块时）
- 依赖模块：`core-sdk`

## 边界

- 可以编辑：agent runtime 源码、测试、模块 POM，以及任务明确批准的 agent docs。
- 禁止编辑：core SDK、CLI、FlowGram starter 和 demo，除非任务列为跨模块变更。
- 外部依赖：provider credentials、trace sinks、sandbox provider 或外部 orchestration 只通过配置/SPI 接入。
- 当前口径：代码/文档是否合并到 `dev` 与 Harness task 是否完成是两个状态；不要把 `review-confirmation-pending` 误读成代码缺失。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| T-AGENT-BLUEPRINT-SCHEMA-EXPORT-AND-DOCS-HARDENING | Agent Blueprint schema export and docs hardening | active | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-agent-blueprint-schema-export-and-docs-hardening-4741edc1/task_plan.md | none |
| T-AGENT-RUNTIME-BACKLOG-RECONCILIATION-AFTER-RUNNE | Agent Runtime backlog reconciliation after runner merge | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a/task_plan.md | T-AGENT-BLUEPRINT-SCHEMA-EXPORT-AND-DOCS-HARDENING |
| T-AI4J-AGENT-SDK-ARCHITECTURE-ENHANCEMENT-ROADMAP- | AI4J Agent SDK architecture enhancement roadmap | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/task_plan.md | T-AGENT-RUNTIME-BACKLOG-RECONCILIATION-AFTER-RUNNE |
| T-MEMORY-COMPACT-SESSION-API-POLISH-53845A17 | Memory Compact Session API polish | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-memory-compact-session-api-polish-53845a17/task_plan.md | T-AI4J-AGENT-SDK-ARCHITECTURE-ENHANCEMENT-ROADMAP- |
| T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12 | P0-A AgentSession runtime container - Brief | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-a-agentsession-runtime-container-389dbf12/task_plan.md | T-MEMORY-COMPACT-SESSION-API-POLISH-53845A17 |
| T-P0-B-MEMORY-COMPACT-CONTEXT-PROJECTOR-47EFFD57 | P0-B Memory Compact Context Projector - Brief | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-b-memory-compact-context-projector-47effd57/task_plan.md | T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12 |
| T-P0-C-AGENT-PLUGIN-LIFECYCLE-HOOKS-10DF8009 | P0-C Agent plugin lifecycle hooks | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009/task_plan.md | T-P0-B-MEMORY-COMPACT-CONTEXT-PROJECTOR-47EFFD57 |
| T-P0-D-AGENT-APPROVAL-AND-PERMISSION-POLICY-95B57B | P0-D Agent approval and permission policy | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/task_plan.md | T-P0-C-AGENT-PLUGIN-LIFECYCLE-HOOKS-10DF8009 |
| T-P1-A-AGENT-BLUEPRINT-SCHEMA-MODEL-LOADER-VALIDAT | P1-A Agent Blueprint schema model loader validator | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0/task_plan.md | T-P0-D-AGENT-APPROVAL-AND-PERMISSION-POLICY-95B57B |
| T-P1-B-AGENT-BLUEPRINT-TO-AGENTFACTORY-8B418210 | P1-B Agent Blueprint to AgentFactory | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210/task_plan.md | T-P1-A-AGENT-BLUEPRINT-SCHEMA-MODEL-LOADER-VALIDAT |
| T-P1-C-CLI-RUN-AGENT-BLUEPRINT-YAML-377E1F25 | P1-C CLI run Agent Blueprint YAML | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-c-cli-run-agent-blueprint-yaml-377e1f25/task_plan.md | T-P1-B-AGENT-BLUEPRINT-TO-AGENTFACTORY-8B418210 |
| T-P2-A-SANDBOX-SPI-MODEL-C9C66766 | P2-A Sandbox SPI model | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p2-a-sandbox-spi-model-c9c66766/task_plan.md | T-P1-C-CLI-RUN-AGENT-BLUEPRINT-YAML-377E1F25 |
| T-P2-B-AGENTSESSION-SANDBOX-BINDING-E8175553 | P2-B AgentSession sandbox binding | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p2-b-agentsession-sandbox-binding-e8175553/task_plan.md | T-P2-A-SANDBOX-SPI-MODEL-C9C66766 |
| T-P5-REMOTE-AGENT-RUNNER-SPI-CONTRACT-E311D42A | P5 Remote Agent Runner SPI contract - Brief | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p5-remote-agent-runner-spi-contract-e311d42a/task_plan.md | T-P2-B-AGENTSESSION-SANDBOX-BINDING-E8175553 |
| T-PLUGIN-CONTRIBUTION-CONTRACT-EXPANSION-E2B3BCAE | Plugin contribution contract expansion | handoff | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-plugin-contribution-contract-expansion-e2b3bcae/task_plan.md | T-P5-REMOTE-AGENT-RUNNER-SPI-CONTRACT-E311D42A |

## 活跃任务

| 任务 | 当前事实状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a` | active | coordinator | 本任务 `findings.md`; `gh pr view 118`; path checks | 本轮只校准 backlog/module plan，不改生产代码。 |
| `2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81` | review-confirmation-pending | coordinator / human | `references/agent-sdk-architecture-enhancement-plan.md`; Harness status | 架构规划已落盘，等待人工确认和 closeout。 |
| `2026-06-20-p0-a-agentsession-runtime-container-389dbf12` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-agent/.../session/AgentSessionStore.java`; `docs-site/docs/agent/session-runtime.md` | AgentSession runtime container 基座已存在；剩余 lifecycle 收口。 |
| `2026-06-20-p0-b-memory-compact-context-projector-47effd57` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-agent/.../context/ContextProjector.java`; `docs-site/docs/agent/memory-compact-context.md` | Memory/compact/context projector 基座已存在；下一步进入 API polish。 |
| `2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009` | merged / review-confirmation-pending | coordinator / human | PR #105; `ai4j-extension-api/.../lifecycle/AgentLifecycleHook.java`; `docs-site/docs/agent/plugin-lifecycle-hooks.md` | Hook contract 已存在；后续做插件贡献合同扩展。 |
| `2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-agent/.../permission/AgentPermissionPolicy.java`; `docs-site/docs/agent/approval-permission-policy.md` | Permission policy 基座已存在；真实审批 UX 属于 CLI/host 后续。 |
| `2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-agent/.../blueprint/AgentBlueprint.java`; `docs-site/docs/agent/agent-blueprint.md` | YAML Blueprint schema/loader/validator 基座已存在。 |
| `2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-agent/.../blueprint/AgentFactory.java` | Blueprint 到 AgentFactory 映射基座已存在。 |
| `2026-06-20-p1-c-cli-run-agent-blueprint-yaml-377e1f25` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-cli/.../command/AgentBlueprintRunCommand.java` | CLI `run <agent.yaml>` 基座已存在；后续 UX 属于 cli-host。 |
| `2026-06-20-p2-a-sandbox-spi-model-c9c66766` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-agent/.../sandbox/SandboxProvider.java`; `docs-site/docs/agent/sandbox-spi.md` | Sandbox SPI 基座已存在。 |
| `2026-06-20-p2-b-agentsession-sandbox-binding-e8175553` | merged-on-dev / review-confirmation-pending | coordinator / human | `ai4j-agent/.../session/AgentSessionSandboxBinding.java`; `docs-site/docs/agent/sandbox-spi.md` | Session sandbox binding 已存在；后续只需 lifecycle 收口。 |
| `2026-06-20-p5-remote-agent-runner-spi-contract-e311d42a` | merged-on-dev / review-confirmation-pending | coordinator / human | PR #118; merge commit `5f4426c`; `ai4j-agent/.../runner/AgentRunnerProvider.java`; `docs-site/docs/agent/remote-agent-runner-spi.md` | Remote Runner SPI contract 已合并到 `dev`。 |
| `2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5` | historical review-pending | coordinator / human | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` | 历史 active item；不在本轮实现范围。 |

## 已合并但属于其他模块的关联事实

| 切片 | 所属模块 | 当前事实 | 证据 | 后续归属 |
| --- | --- | --- | --- | --- |
| P3 Coding Sandbox Routing | `coding-runtime` | merged-on-dev | `docs-site/docs/coding-agent/sandbox-routing.md`; `ai4j-coding/.../coding/sandbox` | 后续 workspace/sandbox tool polish 走 `coding-runtime` |
| P4 CLI Sandbox Commands | `cli-host` | merged via PR #116 / dev commit `91e07b1` | `docs-site/docs/coding-agent/command-reference.md`; `gh pr list` history | 后续 `/memory`、`/compact`、TUI 走 `cli-host` |

## 下一步建议队列

| 优先级 | 任务 | 主模块 | 为什么现在做 | 最小验证 |
| ---: | --- | --- | --- | --- |
| 1 | Memory/Compact Session API polish | `agent-runtime` | P0-A/P0-B/P2-B/P5 都依赖稳定 session/memory/compact 语义；这是提升 Java Agent SDK 易用性的核心 | `mvn -pl ai4j-agent -am "-Dtest=*Memory*,*Compact*,*Session*" -DskipTests=false -DfailIfNoTests=false test` + broad `ai4j-agent` tests |
| 2 | Plugin contribution contract expansion | `extension-api` + `agent-runtime` | 插件生态需要更清晰的 tool/memory/sandbox/runner contribution contract | extension + agent targeted tests |
| 3 | docs-site real API completeness pass | `docs-site` | 用户已指出 docs-site 质量要逐点讲清楚，且不能写不存在 API | `npm --prefix docs-site run build` + sample/API audit |
| 4 | CLI `/memory` `/compact` UX | `cli-host` | 把 Agent SDK 的 memory/compact 能力暴露给 coding agent 体验 | `mvn -pl ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test` + manual smoke |
| 5 | One-command install prototype | `cli-host` | 让终端输入 `ai4j` 类似 codex/claude/opencode | packaging smoke；不影响 Java 8 modules |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 规划静态检查 | `git diff --check` | yes |
| Harness 状态 | `npx --yes coding-agent-harness status --json .` | yes |
| Agent 模块测试 | 后续实现任务运行 `mvn -pl ai4j-agent -am -DskipTests=false test` | risk-based |
| Docs build | 后续 docs-site 变更运行 `npm --prefix docs-site run build` | risk-based |

## 交接

- 分支：`docs/agent-runtime-backlog-reconciliation`。
- Commit SHA：本轮提交后填写。
- 检查：记录 `git diff --check` 和 Harness status。
- 变更文件：本任务包 + `coding-agent-harness/planning/modules/agent-runtime/module_plan.md`。
- 残余风险：human review-confirm / closeout 未完成；后续实现任务未创建。
- 需要 coordinator 同步：人工确认后逐个 closeout；下一步创建 `Memory/Compact Session API polish` task。
