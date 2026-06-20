---
sidebar_position: 2
---

# Agent SDK 真实 API 能力矩阵

这页只记录当前 `dev` 分支已经存在、可以从源码定位的 Agent SDK 能力。它的目标是避免文档再次出现“看起来好用但代码里不存在”的伪 API，并帮助开发者快速判断：某个能力现在能不能用、入口类在哪里、该读哪篇文档。

如果你看到示例里出现这里没有列出的入口，例如隐藏式 `Ai4j.chat()`、`ChatClient.openAi(...)` 或把多个能力串成一个不存在的 fluent API，都应该先回到源码确认，而不是直接复制。

## 1. 使用状态怎么读

| 状态 | 含义 |
| --- | --- |
| 可直接使用 | 当前模块已经有公开类或命令，文档可以给出真实代码示例。 |
| SPI / 合同已存在 | Java 合同、DTO 或 manifest 元数据已经存在，但真实 provider / 平台实现由宿主或第三方提供。 |
| Host / CLI 绑定中 | 底层 SDK 有能力，终端或产品体验已有最小入口，但仍不是完整云产品。 |
| 规划中 | 只能写路线图和边界，不能写成已可用 API。 |

## 2. Agent SDK 主链

| 能力 | 当前状态 | 真实入口 | 适合解决什么 | 继续阅读 |
| --- | --- | --- | --- | --- |
| Agent 装配 | 可直接使用 | `Agents`、`AgentBuilder` | 选择 runtime、模型适配、工具、memory、trace、permission、session store | [Quickstart](/docs/agent/quickstart) |
| Agent 执行 | 可直接使用 | `Agent`、`AgentRequest`、`AgentResult` | 一次性 run、stream run、获取 output/tool/steps | [Minimal ReAct Agent](/docs/agent/minimal-react-agent) |
| Runtime 策略 | 可直接使用 | `AgentRuntime`、`ReActRuntime`、`CodeActRuntime`、`DeepResearchRuntime` | 决定任务如何多步推进，不等于 provider 选择 | [Runtime Implementations](/docs/agent/runtime-implementations) |
| 模型适配 | 可直接使用 | `AgentModelClient`、`ChatModelClient`、`ResponsesModelClient` | 把 Agent prompt 适配到 Chat 或 Responses 协议 | [Model Client Selection](/docs/agent/model-client-selection) |
| Prompt 分层 | 可直接使用 | `systemPrompt(...)`、`instructions(...)`、`AgentPrompt` | 区分全局角色约束和任务级指令 | [System Prompt vs Instructions](/docs/agent/system-prompt-vs-instructions) |
| 工具暴露与执行 | 可直接使用 | `AgentToolRegistry`、`ToolExecutor`、`StaticToolRegistry`、`RoutingToolExecutor` | 明确“模型看见什么”和“宿主执行什么”的边界 | [Tools and Registry](/docs/agent/tools-and-registry) |

最小真实入口仍然是 `Agents.react()` + `modelClient(...)` + `model(...)`。文档不要把 Core SDK 的一次 chat 调用包装成 Agent SDK 示例；只有进入 `AgentRuntime` 主循环，才算进入 Agent 层。

## 3. Session / Memory / Compact

| 能力 | 当前状态 | 真实入口 | 适合解决什么 | 继续阅读 |
| --- | --- | --- | --- | --- |
| Session 容器 | 可直接使用 | `Agent.newSession()`、`AgentSession` | 稳定 session id、独立 memory、metadata、event log、snapshot | [Agent Session Runtime](/docs/agent/session-runtime) |
| Session 保存恢复 | 可直接使用 | `AgentSessionStore`、`InMemoryAgentSessionStore`、`Agent.resumeSession(...)` | 跨请求或轻量 demo 恢复 session snapshot | [Agent Session Runtime](/docs/agent/session-runtime) |
| Memory | 可直接使用 | `AgentMemory`、`InMemoryAgentMemory`、`JdbcAgentMemory`、`MemorySnapshot` | 保存运行中输入、模型输出和工具结果 | [Memory and State](/docs/agent/memory-and-state) |
| Compact | 可直接使用 | `SessionCompactPlan`、`CompactPolicy`、`CompactResult`、`SessionCompactReport` | 手动或策略化压缩 session memory，并得到诊断报告 | [Memory Compact Context Projector](/docs/agent/memory-compact-context) |
| Context 投影 | 可直接使用 | `ContextProjector`、`DefaultContextProjector`、`ContextBudget`、`ContextReport` | 按 item 数、近似字符数和 pinned prefix 选择上下文 | [Memory Compact Context Projector](/docs/agent/memory-compact-context) |
| Coding compact UX | Host / CLI 绑定中 | `/memory`、`/compact`、`/compacts`、`CodingSessionCompactor` | 让 coding-agent 会话展示 compact 健康、手动 compact 和历史诊断 | [Compact and Checkpoint](/docs/coding-agent/compact-and-checkpoint) |

`AgentSession` 现在不只是“换一份 memory”。它已经承载 metadata、event log、snapshot、store、compact result 和非敏感 sandbox binding。更重的 workspace、审批、进程、checkpoint、TUI 状态仍属于 `ai4j-coding` / `ai4j-cli`。

## 4. Blueprint / YAML Agent

| 能力 | 当前状态 | 真实入口 | 适合解决什么 | 继续阅读 |
| --- | --- | --- | --- | --- |
| Blueprint DTO | 可直接使用 | `AgentBlueprint` 及 `AgentBlueprint*` 子对象 | 用结构化对象描述模型、指令、memory、tools、plugins、permissions、sandbox、workflow | [Agent Blueprint YAML](/docs/agent/agent-blueprint) |
| YAML loader | 可直接使用 | `AgentBlueprintLoader` | 从 `agent.yaml` 加载单 Agent 定义 | [Agent Blueprint YAML](/docs/agent/agent-blueprint) |
| 校验器 | 可直接使用 | `AgentBlueprintValidator`、`AgentBlueprintValidationReport` | 在运行前检查必填字段、重复项和不支持配置 | [Agent Blueprint YAML](/docs/agent/agent-blueprint) |
| Factory 映射 | 可直接使用 | `AgentFactory`、`AgentFactoryContext` | 由宿主显式提供 `AgentModelClient`、tools、plugin 绑定后创建 `Agent` | [Agent Blueprint YAML](/docs/agent/agent-blueprint) |
| CLI 运行 YAML | 可直接使用 | `ai4j-cli run <agent.yaml> --input <task>`、`AgentBlueprintRunCommand` | 从终端运行一次单 Agent Blueprint | [Agent Blueprint YAML](/docs/agent/agent-blueprint) |

Blueprint 是“组装声明”，不是任意代码执行器。YAML 里只应该引用 provider/profile/plugin/tool 名称，不应该写 token、cookie 或本机路径。

## 5. 插件生态

| 能力 | 当前状态 | 真实入口 | 适合解决什么 | 继续阅读 |
| --- | --- | --- | --- | --- |
| 插件包发现 | 可直接使用 | `Ai4jExtension`、`ServiceLoaderExtensionLoader`、`ExtensionRegistry` | 发现 jar 中的扩展包并生成 runtime snapshot | [Plugin Packages](/docs/core-sdk/extension/plugin-packages) |
| Manifest / capability | 可直接使用 | `ExtensionManifest`、`ExtensionCapability` | 声明插件包身份、版本、能力和配置前缀 | [Extension Overview](/docs/core-sdk/extension/overview) |
| Contribution 元数据 | 可直接使用 | `ExtensionContribution`、`ExtensionContributionType` | 声明 tool、prompt、skill、memory、sandbox provider、runner provider、CLI/UI 等贡献项 | [Plugin Contribution Contract](/docs/agent/plugin-contribution-contract) |
| Tool / Command / Skill / Prompt | 可直接使用 | `ToolRegistry`、`CommandRegistry`、`SkillRegistry`、`PromptRegistry` | 官方插件和第三方插件可贡献可发现资源 | [Plugin Author Cookbook](/docs/core-sdk/extension/plugin-author-cookbook) |
| Lifecycle Hook | 可直接使用 | `AgentLifecycleHook`、`LifecycleHookRegistry`、`AgentLifecycleHookDispatcher` | 观察 run/model/tool/compact 事件 | [Plugin Lifecycle Hooks](/docs/agent/plugin-lifecycle-hooks) |
| Guardrail | 可直接使用 | `ExtensionGuardrail`、`GuardrailRegistry`、`ExtensionGuardrailToolExecutor` | 在工具执行前后增加策略判断 | [Plugin Contribution Contract](/docs/agent/plugin-contribution-contract) |

插件“安装/发现”不等于“自动启用危险能力”。Tool、sandbox、runner、CLI/UI 这类高影响贡献必须继续走显式 enable/expose/binding/permission。

## 6. Permission / Sandbox / Remote Runner

| 能力 | 当前状态 | 真实入口 | 适合解决什么 | 继续阅读 |
| --- | --- | --- | --- | --- |
| 工具审批策略 | 可直接使用 | `AgentPermissionPolicy`、`AgentPermissionToolExecutor`、`AgentPermissionDecision` | 在工具执行前 allow/deny/ask，保留 host 可控边界 | [Approval Permission Policy](/docs/agent/approval-permission-policy) |
| Sandbox SPI | SPI / 合同已存在 | `SandboxProvider`、`SandboxSession`、`SandboxSpec`、`SandboxCommand`、`SandboxResult` | 让宿主或第三方实现外部 VM/container/browser 执行环境 | [Agent Sandbox SPI](/docs/agent/sandbox-spi) |
| Session sandbox 摘要 | 可直接使用 | `AgentSessionSandboxBinding`、`AgentSession.bindSandbox(...)` | 把非敏感 provider/session/workspace 摘要写进 session snapshot/event log | [Agent Sandbox SPI](/docs/agent/sandbox-spi) |
| Coding sandbox routing | Host / CLI 绑定中 | `CodingAgentBuilder.sandbox(...)`、`SandboxShellCommandExecutor` | 把 coding shell 执行路由到 live `SandboxSession` | [Coding Agent Sandbox Routing](/docs/coding-agent/sandbox-routing) |
| CLI `/sandbox` | Host / CLI 绑定中 | `/sandbox status`、`/sandbox attach`、`/sandbox disable` | 显示或记录当前 coding session 的 sandbox binding | [Command Reference](/docs/coding-agent/command-reference) |
| Remote Agent Runner | SPI / 合同已存在 | `AgentRunnerProvider`、`AgentRunnerSession`、`AgentRunnerSpec`、`AgentRunnerRequest`、`AgentRunnerResult` | 完整 Agent loop 在远端 runner / sandbox / 托管工作区里执行 | [Remote Agent Runner SPI](/docs/agent/remote-agent-runner-spi) |

AI4J 当前不内置官方云 runner、VM、Docker、K8s 或真实 sandbox 平台。SDK 提供合同；真实 provider 由宿主、插件或业务系统实现。

## 7. Workflow / SubAgent / Team

| 能力 | 当前状态 | 真实入口 | 适合解决什么 | 继续阅读 |
| --- | --- | --- | --- | --- |
| 顺序 workflow | 可直接使用 | `SequentialWorkflow`、`AgentNode` | 固定节点链路 | [Workflow StateGraph](/docs/agent/workflow-stategraph) |
| 状态图 workflow | 可直接使用 | `StateGraphWorkflow`、`StateRouter`、`WorkflowContext` | 条件路由、循环、显式状态推进 | [Workflow StateGraph](/docs/agent/workflow-stategraph) |
| SubAgent | 可直接使用 | `SubAgentDefinition`、`StaticSubAgentRegistry`、`SubAgentToolExecutor` | 把另一个 Agent 作为受治理工具暴露给主 Agent | [SubAgent Handoff Policy](/docs/agent/subagent-handoff-policy) |
| Handoff 治理 | 可直接使用 | `HandoffPolicy`、`HandoffContext` | 控制深度、超时、重试、deny/fallback、输入过滤 | [SubAgent Handoff Policy](/docs/agent/subagent-handoff-policy) |
| Agent Team | 可直接使用 | `AgentTeamBuilder`、`AgentTeam`、`AgentTeamTaskBoard`、`AgentTeamMessageBus` | 多成员围绕任务板协作、派发和汇总 | [Agent Teams](/docs/agent/agent-teams) |

SubAgent 是“另一个 Agent 作为工具”；Team 是“多成员围绕任务板协作”。两者不要混用成同一种概念。

## 8. CLI / TUI 已有真实入口

| 能力 | 当前状态 | 真实入口 | 适合解决什么 | 继续阅读 |
| --- | --- | --- | --- | --- |
| CLI 主入口 | 可直接使用 | `ai4j-cli` module、分发包中的 `ai4j` launcher | 终端进入 coding agent 或运行子命令 | [Install and Release](/docs/coding-agent/install-and-release) |
| Provider / model 切换 | 可直接使用 | `/providers`、`/provider`、`/model`、`CliProviderConfigManager` | 保存、切换和覆盖 provider/model/profile | [Provider Profiles](/docs/coding-agent/provider-profiles) |
| Slash command palette | 可直接使用 | `SlashCommandController` | `/help`、`/status`、`/extension`、`/memory`、`/sandbox` 等命令补全和执行入口 | [Command Reference](/docs/coding-agent/command-reference) |
| TUI 渲染 | 可直接使用 | `TuiSessionView`、`TuiRenderer`、`CliThemeStyler`、JLine terminal IO | 终端内显示 session、tool、markdown/code/diff 等状态 | [CLI and TUI](/docs/coding-agent/cli-and-tui) |
| Extension CLI | 可直接使用 | `extension list/inspect/plan/check/validate/run/resource`、`/extension` | 查看和显式运行插件命令或资源 | [Extension Plugin Packages](/docs/core-sdk/extension/plugin-packages) |

短期路线仍是 Java/JLine。不要把 Node Ink、React/JSX terminal renderer 或第三方 TUI 框架写成 AI4J 当前实现。

## 9. 文档编写规则

后续更新 Agent 文档时，先按这张清单自查：

1. 示例里的类名、方法名必须能在 `ai4j-agent`、`ai4j-extension-api`、`ai4j-coding` 或 `ai4j-cli` 源码中找到。
2. 如果只是 SPI 或 manifest 元数据，要明确写“合同已存在，真实 provider 由宿主/第三方实现”。
3. 如果能力只在 CLI/TUI 有最小 UX，不要写成完整云端产品。
4. 不要在示例里写真实 token、cookie、账号、个人本机路径或 sandbox 连接串。
5. 新增用户可见行为时，至少更新对应 docs-site 页面，并跑 `npm run build`。

## 10. 下一步怎么选

- 想先接 Java Agent：读 [Quickstart](/docs/agent/quickstart)。
- 想配置 YAML Agent：读 [Agent Blueprint YAML](/docs/agent/agent-blueprint)。
- 想做长会话：读 [Agent Session Runtime](/docs/agent/session-runtime) 和 [Memory Compact Context Projector](/docs/agent/memory-compact-context)。
- 想做插件生态：读 [Plugin Contribution Contract](/docs/agent/plugin-contribution-contract)。
- 想做沙箱或云端 Agent 产品：读 [Agent Sandbox SPI](/docs/agent/sandbox-spi) 和 [Remote Agent Runner SPI](/docs/agent/remote-agent-runner-spi)。
- 想做 Codex / Claude Code 类终端体验：读 [Coding Agent](/docs/coding-agent/overview)。
