# AI4J Agent SDK 实施拆解路线图

## 1. 任务来源

本路线图承接已提交待人工确认的架构规划任务：

- Source task：`2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312`
- Source artifact：`coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-enhancement-plan.md`

用户已在对话中认可该规划，并要求继续完成任务拆解、创建 worktree、更新 docs-site 技术文档、自测、推送和 PR。

## 2. 总原则

1. 不新增 `AgentHost` / `Host Kernel` / `ai4j-runtime` 主概念。
2. `ai4j-agent` 是通用 Agent SDK 主入口；增强优先发生在现有 Maven 模块内。
3. `ai4j-coding` 继续承载 workspace-aware coding 能力；`ai4j-cli` 继续承载终端产品体验。
4. Sandbox 抽象进入运行环境和工具执行边界，不只是一个普通 tool。
5. 远端 Agent Runner 是产品化方向，必须晚于 Session、Memory、Blueprint、Sandbox 抽象稳定。
6. 每个阶段都必须单独建 Harness 任务、单独验证、单独更新 docs-site 或 reference。

## 3. 实施队列

### P0-A：AgentSession 运行态容器

| 字段 | 内容 |
| --- | --- |
| 目标模块 | `ai4j-agent` |
| 目标 | 将 `AgentSession` 从轻量派生入口升级为长程任务运行态容器。 |
| 主要 API | `AgentSessionState`、`AgentSessionStore`、`AgentSessionEventLog`、`AgentSessionSnapshot`、`AgentSessionCheckpoint` |
| 不做 | 不接 sandbox provider，不改 CLI/TUI，不做 YAML Blueprint。 |
| 验证 | `mvn -pl ai4j-agent -DskipTests=false test`；新增 session snapshot/store 单测。 |
| 文档 | `docs-site/docs/agent/session-runtime-roadmap.md` 或更新 `agent/memory-and-state.md`。 |

拆分建议：

1. 盘点当前 `AgentSession` / `AgentMemory` / runtime 依赖。
2. 引入只读 snapshot 与事件模型，不破坏现有 `Agent.run(...)`。
3. 加入 session id、metadata、artifact refs、checkpoint refs。
4. 提供 in-memory store；JDBC/文件 store 后置。

### P0-B：Memory / Compact / Context Projector

| 字段 | 内容 |
| --- | --- |
| 目标模块 | `ai4j-agent` |
| 目标 | 把 memory、event log、model context、compact 结果拆成可解释的结构化层。 |
| 主要 API | `AgentMemoryStore`、`ContextProjector`、`CompactPolicy`、`CompactResult`、`ContextBudget`、`ContextReport` |
| 不做 | 不把 coding-specific checkpoint 全部上移到通用 agent；不引入模型供应商绑定。 |
| 验证 | 新增 compact policy、context budget、projection 单测；现有 memory 单测保持通过。 |
| 文档 | 更新 `agent/memory-and-state.md`，新增与 `coding-agent/compact-and-checkpoint.md` 的边界说明。 |

关键验收：Compact 结果必须结构化，至少保留 goal、done、in-progress、blocked、decisions、changed artifacts、failed commands、test results、open questions。

### P0-C：Agent 插件生命周期和 Tool Execution Lifecycle

| 字段 | 内容 |
| --- | --- |
| 目标模块 | `ai4j-agent` + `ai4j-extension-api` |
| 目标 | 插件从工具/资源扩展升级为 Agent 生命周期扩展。 |
| 主要 API | `AgentPlugin`、`AgentLifecycleHook`、`ToolExecutionHook`、hook context/result 类型 |
| 不做 | 不一次性把所有第三方插件迁移；不要求所有 hook 都必须实现。 |
| 验证 | extension-api contract tests；agent hook ordering tests；sample plugin smoke。 |
| 文档 | 更新 `core-sdk/extension/*` 与 `agent/tools-and-registry.md`。 |

建议 hook：`onSessionStart`、`beforeTurn`、`afterTurn`、`beforeModelRequest`、`afterModelResponse`、`beforeToolCall`、`afterToolCall`、`onCompact`、`onSessionEnd`。

### P1：Agent Blueprint YAML

| 字段 | 内容 |
| --- | --- |
| 目标模块 | `ai4j-agent`，必要时轻量接入 `ai4j-coding` |
| 目标 | 支持声明式单 Agent 组装。 |
| 主要 API | `AgentBlueprint`、`AgentBlueprintLoader`、`AgentBlueprintValidator`、`AgentFactory` |
| 不做 | 不先做 Team/Workflow 全量 DSL；不绑定 FlowGram 导出。 |
| 验证 | YAML fixture tests；非法配置 validator tests；Java 8 兼容。 |
| 文档 | 新增 `agent/agent-blueprint.md`，给出完整 YAML 示例。 |

第一版只支持：model、instructions、plugins、tools、session.memory、session.compact、workflow.mode、maxTurns。

### P2：Sandbox SPI

| 字段 | 内容 |
| --- | --- |
| 目标模块 | 抽象优先放 `ai4j-agent` 或 `ai4j-extension-api`；具体执行适配在 `ai4j-coding`。 |
| 目标 | 建立真实沙箱/远端执行环境的最小合同。 |
| 主要 API | `SandboxProvider`、`SandboxSession`、`SandboxSpec`、`SandboxCommand`、`SandboxResult`、`SandboxArtifact` |
| 不做 | 不官方维护多个 provider；不把 sandbox 伪装成普通 tool。 |
| 验证 | fake sandbox provider 单测；session binding tests；timeout/cancel tests。 |
| 文档 | 新增 `agent/sandbox-and-runner-roadmap.md` 或 `coding-agent/sandbox.md`。 |

第一版重点是抽象和 fake provider，不接 CubeSandbox/Docker/E2B/K8s 真实实现。

### P3：`ai4j-coding` 接入 Sandbox

| 字段 | 内容 |
| --- | --- |
| 目标模块 | `ai4j-coding` |
| 目标 | file / shell / git / browser / project run / test runner 自动感知 session sandbox。 |
| 不做 | 不改变无 sandbox 时的本地执行语义。 |
| 验证 | 本地执行回归 + fake sandbox tool-routing tests。 |
| 文档 | 更新 `coding-agent/tools-and-approvals.md`、`coding-agent/session-runtime.md`。 |

关键原则：无 sandbox = 本地执行；有 sandbox = 执行型工具进入 sandbox；审批策略仍由 host / coding policy 控制。

### P4：`ai4j-cli` `/sandbox` 体验

| 字段 | 内容 |
| --- | --- |
| 目标模块 | `ai4j-cli` |
| 目标 | 提供类似 Codex 的 sandbox 控制入口。 |
| 命令 | `/sandbox`、`/sandbox status`、`/sandbox enable <provider>`、`/sandbox disable`、`/sandbox attach` |
| 验证 | slash command parser tests；TUI rendering smoke；必要时 tmux 交互验证。 |
| 文档 | 更新 `coding-agent/cli-and-tui.md`、`coding-agent/command-reference.md`。 |

### P5：远端 Agent Runner

| 字段 | 内容 |
| --- | --- |
| 目标模块 | 可选新模块，待 P0-P4 稳定后再决定是否新增 `ai4j-agent-runner`。 |
| 目标 | 帮开发者快速做出云端 Agent 产品：远端 workspace、shell、browser、artifacts、事件流。 |
| 不做 | 不在当前阶段新增模块；不阻塞本地 SDK。 |
| 验证 | 协议 contract tests；event stream fixture；fake sandbox e2e。 |
| 文档 | 新增 runner productization guide。 |

## 4. 推荐第一批任务顺序

1. `P0-A AgentSession runtime container`
2. `P0-B Memory Compact Context Projector`
3. `P0-C Plugin Lifecycle Hooks`
4. `P1 Agent Blueprint YAML`
5. `P2 Sandbox SPI`
6. `P3 Coding Sandbox Routing`
7. `P4 CLI Sandbox Commands`
8. `P5 Remote Agent Runner Decision`

## 5. Regression Gate 建议

| 改动类型 | 最小回归 |
| --- | --- |
| `ai4j-agent` API / runtime | `mvn -pl ai4j-agent -DskipTests=false test` |
| `ai4j-extension-api` contract | `mvn -pl ai4j-extension-api -DskipTests=false test` |
| 插件样例 | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` |
| `ai4j-coding` 工具路由 | `mvn -pl ai4j-coding -DskipTests=false test` |
| CLI slash / TUI | `mvn -pl ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test` |
| docs-site | `cd docs-site; npm run build` |

## 6. 不立即执行的事项

- 不直接新增 `ai4j-agent-runner` Maven 模块。
- 不直接接真实云沙箱 provider。
- 不把用户 provider token 写入仓库或文档。
- 不把 `ai4j-coding` 已有 compact/checkpoint 逻辑机械上移到通用 agent。
