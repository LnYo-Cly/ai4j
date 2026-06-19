# AI4J Agent SDK 架构增强规划

## 1. 背景与目标

本任务记录 2026-06-20 围绕 `ai4j-agent` 的架构增强讨论，目标不是立即改代码，而是为后续实施任务提供稳定路线图。

核心结论：`ai4j-agent` 已经是一个 Maven 模块，不应再引入 `AgentHost`、`Host Kernel`、`ai4j-runtime` 等新的主概念。更合适的定位是：

> `ai4j-agent` 是 ai4j 的通用 Agent SDK 主入口；它需要从“Agent 能力集合”升级为“可插件化、可会话化、可声明式组装、可远端沙箱运行的 Agent SDK”。

## 2. 推荐模块心智

```text
ai4j
  基础模型 SDK：Chat / Responses / Provider / Streaming / Tool Schema

ai4j-agent
  通用 Agent SDK：Agent / Runtime / Session / Memory / Compact / Plugin / Event

ai4j-extension-api
  插件合同：Tool / Command / Hook / Prompt / Skill / UI / SandboxProvider

ai4j-coding
  官方 Coding Agent 能力包：file / shell / git / browser / workspace / diff / project run

ai4j-cli
  官方终端产品：类似 Codex / Claude Code / Pi 的 CLI/TUI

未来可选：ai4j-agent-runner
  可部署到远端沙箱里的 Agent Runner
```

## 3. 当前已有基础

当前 `ai4j-agent` 已经具备较多基础能力：

- `Agent` / `AgentBuilder` / `AgentRuntime` / `AgentSession` / `AgentContext`
- `AgentMemory` / `MemoryCompressor`
- `ReActRuntime` / `CodeActRuntime` / `DeepResearchRuntime`
- `AgentEvent` / `AgentToolRegistry`
- workflow / subagent / team / trace
- `ExtensionAgentTools` 等 extension bridge

因此后续不是“新建一个 runtime 模块”，而是强化现有 `ai4j-agent` 的内聚入口和长期运行能力。

## 4. AgentSession 升级方向

`AgentSession` 应从 `runtime + context` 的薄包装升级为长程 Agent 任务的运行态容器：

```text
AgentSession =
  sessionId
  + event log
  + memory
  + compact state
  + plugin state
  + tool execution state
  + sandbox binding
  + artifact state
  + checkpoint
  + resume / fork / rewind
```

关键原则：Session 不只是聊天历史，而是一次长程 Agent 任务的事实容器。

## 5. Memory / Compact / Context 分层

需要拆清楚三类状态：

```text
SessionEventLog
  完整事件历史：用户输入、模型输出、工具调用、工具结果、文件变化、错误等。

Memory
  跨会话长期信息：偏好、项目惯例、历史踩坑、稳定经验。

ModelContext / WorkingContext
  本轮真正发给模型的上下文。
```

建议引入或强化的概念：

- `AgentSessionStore`
- `AgentEventLog`
- `AgentMemoryStore`
- `ContextProjector`
- `CompactPolicy`
- `CompactResult`
- `CompactHook`
- `MemoryExtractor`
- `MemoryRetriever`
- `ContextBudget`
- `ContextReport`

Compact 结果应结构化，而不是只生成自然语言摘要。建议保留：

- 已完成事项
- 未完成事项
- 关键决策
- 修改过的文件
- 失败命令
- 测试结果
- 用户确认
- sandbox 状态
- open questions
- artifacts

参考对象包括 Codex、Claude Code、LangGraph、LlamaIndex、OpenAI Agents SDK 等公开设计模式；不依赖泄露源码。

## 6. 插件生命周期增强

插件系统需要从“工具/资源扩展”升级为 Agent 运行生命周期扩展。

建议支持 hooks：

```text
onSessionStart
beforeTurn
afterTurn
beforeModelRequest
afterModelResponse
beforeToolCall
afterToolCall
onCompact
onSessionEnd
```

插件可贡献能力：

- Tool
- Command
- Prompt
- Skill
- Guardrail
- UI contribution
- Memory provider
- Compact strategy
- SandboxProvider
- RemoteAgentRunnerProvider

## 7. Sandbox 规划

需要区分两类 sandbox：

### 7.1 本地 sandbox / permission

类似 Codex CLI 的本机限制：文件写入、网络、审批等。这不一定是 VM。

### 7.2 真实远端 sandbox

类似豆包、点点、Devin、Codex Cloud 类产品的云端 VM / 容器 / microVM：

- 安装依赖
- 运行项目
- 打开浏览器
- 截图
- 生成文件
- 保存 artifacts

`ai4j` 更应该预留第二类能力，但不应官方维护一堆具体 provider。

### 7.3 设计原则

- Sandbox 不应只是 `tool: run_in_sandbox`。
- `AgentSession` 应绑定 sandbox。
- 执行型工具自动感知 sandbox：shell / file / git / browser / project run / test runner。
- 无 sandbox = 本地执行；有 sandbox = 沙箱执行。
- 不需要设计单独的 `local backend`。

建议抽象：

- `SandboxProvider`
- `SandboxSession`
- `SandboxSpec`
- `SandboxCommand`
- `SandboxResult`

具体 CubeSandbox / Docker / E2B / K8s / 公司内部沙箱由插件或业务方实现。

## 8. 远端 Agent Runner 规划

如果目标是帮助小厂或开发者快速做出豆包/点点/Devin/Codex Cloud 类远端 Agent 产品，需要规划可选的 `ai4j-agent-runner`。

结构：

```text
用户 Web/App/CLI
  ↓
控制端后端
  ↓
远端 sandbox
  ↓
ai4j-agent-runner
  ↓
shell / browser / workspace / project run / artifacts
```

Runner 职责：

- 运行 `ai4j-agent` loop
- 管理 session
- 执行 coding tools
- 操作文件
- 运行 shell
- 打开浏览器
- 截图
- 收集 artifacts
- 流式发送事件

该能力不应替代 `ai4j-agent`，而是产品化远端 Agent 的可运行入口。

## 9. 沙箱分配策略

不建议所有用户共用一个沙箱并靠 userId 目录隔离。推荐：

```text
执行环境按 session/task 隔离
数据按 user/project 持久化
```

即：每个任务/会话一个隔离 sandbox；用户项目 workspace 可持久化；任务结束后保存 artifact / snapshot；沙箱销毁或冻结。同一任务里的多个 subagent 可以共享 sandbox，不同用户/不同任务必须隔离。

## 10. Agent Blueprint 声明式配置

建议引入 `AI4J Agent Blueprint`，以 YAML 作为首个承载格式，未来可扩展到 JSON、UI builder 或 FlowGram 导出。

定位：

- Java API 适合动态构建 Agent。
- YAML Blueprint 适合配置化、模板化、分享、低代码 UI、远端 Agent 产品。

P0 示例：

```yaml
version: ai4j.agent/v1
id: coding-assistant
name: Coding Assistant

model:
  provider: openai-compatible
  profile: default
  model: gpt-4.1

instructions:
  system: |
    You are a careful coding agent.

plugins:
  - id: ask-user
  - id: todo
  - id: browser

tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe
  - ref: coding.git

session:
  memory:
    enabled: true
    scope: project
  compact:
    enabled: true
    trigger:
      contextRatio: 0.75
    strategy: structured-summary
    preserve:
      - instructions
      - open_decisions
      - changed_files
      - failed_commands
      - test_results

sandbox:
  enabled: false

workflow:
  mode: react
  maxTurns: 20
```

阶段：

- P0：单 Agent Blueprint。
- P1：Team Blueprint，支持 agents / handoff / roles / team。
- P2：Workflow Blueprint，支持 nodes / edges / conditions / graph orchestration，并与 FlowGram 打通。

## 11. 推荐实施优先级

### P0：增强 ai4j-agent 核心

- `AgentSession`
- `SessionEventLog`
- `MemoryStore`
- `CompactPolicy`
- `ContextProjector`
- `Plugin Lifecycle`
- `Tool Execution Lifecycle`

### P1：Agent Blueprint

- YAML schema
- loader
- validator
- factory

### P2：Sandbox 抽象

- `SandboxProvider`
- `SandboxSession`
- `SandboxSpec`
- `SandboxCommand`
- `SandboxResult`

只做抽象，不绑定具体厂商。

### P3：ai4j-coding 接入 sandbox

- shell
- file
- git
- browser
- test
- project run

有 sandbox 就进沙箱，没有就本地执行。

### P4：ai4j-cli 提供 `/sandbox`

- `/sandbox`
- `/sandbox status`
- `/sandbox enable <provider>`
- `/sandbox disable`
- `/sandbox attach`

### P5：远端 Agent Runner

- `ai4j-agent-runner`
- HTTP / WebSocket / JSON-RPC 协议
- event stream
- artifact
- browser screenshot
- remote workspace

## 12. 本任务输出边界

本任务只记录规划，不修改生产代码。后续实现应拆成多个独立任务，按模块边界分别推进。
