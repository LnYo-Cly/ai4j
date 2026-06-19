---
sidebar_position: 4
---

# AI4J Agent SDK Roadmap

这一页说明 `ai4j-agent` 接下来要怎样从“可用的 Agent runtime”升级为更完整的 Java Agent SDK。

先明确一个边界：这里是技术路线图，不代表所有能力都已经发布。当前已经存在的能力包括 `Agent`、`AgentBuilder`、`AgentRuntime`、`AgentSession`、memory、runtime、workflow、team、trace 等；下面的路线是后续要逐步补强的方向。

## 1. 最重要的架构判断

`ai4j-agent` 不需要再新增一个叫 `AgentHost`、`Host Kernel` 或 `ai4j-runtime` 的主概念。

更合适的心智是：

```text
ai4j
  基础模型 SDK：Chat / Responses / Provider / Streaming / Tool Schema

ai4j-agent
  通用 Agent SDK：Agent / Runtime / Session / Memory / Compact / Plugin / Event

ai4j-extension-api
  插件合同：Tool / Command / Hook / Prompt / Skill / UI / SandboxProvider

ai4j-coding
  Coding Agent 能力包：file / shell / git / browser / workspace / diff / project run

ai4j-cli
  终端产品：CLI / TUI / ACP host / session command
```

也就是说，`ai4j-agent` 是通用 Agent SDK 主入口；`ai4j-coding` 和 `ai4j-cli` 是建立在它之上的更具体产品层。

## 2. 当前已有基础

`ai4j-agent` 已经不是空白模块。它已经具备：

- `Agent` / `AgentBuilder` / `AgentRuntime` / `AgentSession` / `AgentContext`
- `AgentMemory` / `MemoryCompressor`
- `ReActRuntime` / `CodeActRuntime` / `DeepResearchRuntime`
- `AgentToolRegistry` / `ToolExecutor`
- workflow / subagent / team orchestration
- trace / event publisher
- extension bridge

后续不是“推倒重写”，而是在这些基础上补齐长期运行、声明式组装、插件生命周期和沙箱运行边界。

## 3. P0：先补强 Agent SDK 内核

P0 不追求花哨 API，而是先把长程 Agent 的基础状态模型打稳。

### P0-A：`AgentSession` 升级为运行态容器

当前 `AgentSession` 更接近轻量状态派生入口。后续需要把它升级成一次长程 Agent 任务的运行态容器：

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

第一版不应该一次做完所有内容。当前 P0-A 基础已经落到 `AgentSession` 及 `io.github.lnyocly.ai4j.agent.session` 包中：

- session id / metadata
- session snapshot
- session event log
- in-memory session store
- `AgentBuilder.sessionStore(...)` 与 `Agent.resumeSession(...)`
- 与现有 `Agent.run(...)` 兼容

使用细节见 [Agent Session Runtime](/docs/agent/session-runtime)。

### P0-B：Memory、Compact、Context Projector 分层

P0-B 基础已经落地：`ContextBudget`、`ContextProjector`、`ContextReport`、`CompactPolicy`、`CompactResult`、`AgentSession.compact(...)` 和 session snapshot compact state。使用细节见 [Memory Compact Context Projector](/docs/agent/memory-compact-context)。

这一层必须拆清楚三层：

| 层 | 职责 |
| --- | --- |
| `SessionEventLog` | 完整事件历史：用户输入、模型输出、工具调用、工具结果、错误、artifact 等 |
| `Memory` | 跨轮或跨会话的稳定信息，例如偏好、项目约定、历史经验 |
| `ModelContext` / `WorkingContext` | 本轮真正发给模型的上下文 |

P0-B 核心对象包括：

- `AgentSessionStore`
- `AgentSessionEventLog`
- `ContextProjector`
- `CompactPolicy`
- `CompactResult`
- `ContextBudget`
- `ContextReport`

Compact 结果应尽量结构化，不只是自然语言摘要。至少要保留：

- 已完成事项
- 未完成事项
- 关键决策
- 修改过的文件或 artifact
- 失败命令
- 测试结果
- 用户确认
- sandbox 状态
- open questions

### P0-C：插件生命周期和工具执行生命周期

插件不应该只贡献工具，还应该能参与 Agent 运行生命周期。

P0-C 基础已经落地：`ai4j-extension-api` 增加 `io.github.lnyocly.ai4j.extension.lifecycle` 公共合同，`ai4j-agent` 在 ReAct/Base runtime、CodeAct runtime 和 `AgentSession.compact(...)` 中触发观察型 Hook。使用细节见 [Plugin Lifecycle Hooks](/docs/agent/plugin-lifecycle-hooks)。

当前支持：

```text
BEFORE_TURN
AFTER_TURN
BEFORE_MODEL_REQUEST
AFTER_MODEL_RESPONSE
BEFORE_TOOL_CALL
AFTER_TOOL_CALL
ON_COMPACT
```

`SESSION_START` 和 `SESSION_END` 作为事件类型保留，但首版不自动触发。原因是当前 Agent 还没有稳定的显式 close/end 生命周期。

插件能贡献的能力可以扩展为：

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

这部分要同时考虑 `ai4j-agent` 和 `ai4j-extension-api` 的边界：公共合同放到 extension API，运行时编排仍由 `ai4j-agent` 控制。首版 Hook 是 observation-first，不是 prompt/tool/model response 的可变拦截器。

## 4. P1：Agent Blueprint YAML

Java API 适合动态构建 Agent，但用户也需要声明式、可分享、可模板化的配置方式。

第一版可以只做单 Agent Blueprint：

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

P1 的目标不是做一个完整低代码平台，而是先提供：

- YAML schema
- loader
- validator
- factory
- fixture tests

Team Blueprint、Workflow Blueprint、FlowGram 导出可以后置。

## 5. P2：Sandbox SPI

这里的 sandbox 不是简单的“再加一个 shell tool”。

需要区分两类能力：

| 类型 | 说明 |
| --- | --- |
| 本地 permission sandbox | 类似 CLI 的文件写入、网络、审批限制，不一定是 VM |
| 真实远端 sandbox | 云端 VM / 容器 / microVM，可安装依赖、运行项目、打开浏览器、保存 artifact |

AI4J 更应该先做抽象，不应该官方维护一堆具体 provider。

建议最小合同：

- `SandboxProvider`
- `SandboxSession`
- `SandboxSpec`
- `SandboxCommand`
- `SandboxResult`
- `SandboxArtifact`

原则：

- Sandbox 绑定到 `AgentSession` 或 coding session 的运行环境。
- file / shell / git / browser / project run 等执行型工具自动感知 sandbox。
- 无 sandbox 时保持本地执行。
- 有 sandbox 时进入 sandbox 执行。
- 具体 CubeSandbox / Docker / E2B / K8s / 公司内部沙箱由插件或业务方实现。

## 6. P3：`ai4j-coding` 接入 sandbox

`ai4j-agent` 负责通用运行态；`ai4j-coding` 负责 workspace-aware coding 工具。

因此 sandbox 真正影响最多的是：

- file
- shell
- git
- browser
- test runner
- project run
- artifact collection

接入时必须保持一条兼容原则：

```text
没有 sandbox = 当前本地执行语义不变
有 sandbox = 执行型工具自动路由到 sandbox
```

审批仍然由 coding tool policy / host policy 控制，不能因为进入 sandbox 就默认放开所有危险能力。

## 7. P4：CLI `/sandbox` 体验

CLI/TUI 层应该提供明确、可见、可切换的 sandbox 状态。

推荐命令：

```text
/sandbox
/sandbox status
/sandbox enable <provider>
/sandbox disable
/sandbox attach
```

TUI 上至少要让用户知道：

- 当前是否启用 sandbox
- provider 是什么
- workspace 在哪里
- 最近一次命令是在本地还是 sandbox 执行
- sandbox 是否可恢复或已销毁

如果后续需要测试交互体验，可以使用 tmux 驱动 CLI，验证输入命令和输出渲染。

## 8. P5：远端 Agent Runner

远端 Runner 不是当前必须立刻新增的 Maven 模块，但它是后续产品化方向。

目标场景是：开发者希望快速做出类似云端 Agent 产品的能力：

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

Runner 职责包括：

- 运行 `ai4j-agent` loop
- 管理 session
- 执行 coding tools
- 操作 workspace
- 运行 shell
- 打开浏览器
- 截图
- 收集 artifacts
- 流式发送事件

但它必须晚于 P0-P4。否则会过早把 SDK 内核、coding tools、沙箱、产品协议绑死。

## 9. 推荐实施顺序

| 顺序 | 任务 | 最小回归 |
| --- | --- | --- |
| 1 | P0-A AgentSession runtime container | `mvn -pl ai4j-agent -DskipTests=false test` |
| 2 | P0-B Memory / Compact / Context Projector | `mvn -pl ai4j-agent "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false test` + `mvn -pl ai4j-agent -am -DskipTests=false test` |
| 3 | P0-C Plugin Lifecycle Hooks | `mvn -pl ai4j-extension-api -DskipTests=false test` + `mvn -pl ai4j-agent -DskipTests=false test` |
| 4 | P1 Agent Blueprint YAML | `mvn -pl ai4j-agent -DskipTests=false test` |
| 5 | P2 Sandbox SPI | fake provider tests + `ai4j-agent` / `ai4j-extension-api` tests |
| 6 | P3 Coding Sandbox Routing | `mvn -pl ai4j-coding -DskipTests=false test` |
| 7 | P4 CLI Sandbox Commands | `mvn -pl ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test` |
| 8 | P5 Runner Decision | contract tests after module decision |

## 10. 哪些事现在不要做

- 不要一次性新增 `ai4j-agent-runner` 模块。
- 不要先接真实云沙箱 provider。
- 不要把 `ai4j-coding` 的所有 compact/checkpoint 逻辑直接搬到 `ai4j-agent`。
- 不要在文档里写死某个 OpenAI-compatible 中转平台名称作为 SDK 概念。
- 不要把 provider token 写进配置示例、测试 fixture 或文档。

更稳的路线是：先把 `ai4j-agent` 的长期运行内核做扎实，再让 Blueprint、Sandbox、Coding Agent、CLI 和 Runner 逐层接上来。
