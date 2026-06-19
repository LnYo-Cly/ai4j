# AI4J Agent SDK 完整规划刷新稿

> Task: `2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312`
> Date: 2026-06-20
> Scope: planning-only; no production code changes.

## 1. 本轮规划要解决的问题

用户希望 `ai4j` 不只是一个 Java 调模型 SDK，而是逐步具备“好用的 AI Agent SDK + 类 Codex / Claude Code / Pi 的 Coding Agent CLI/TUI + 可被第三方扩展的插件生态 + 可支撑云端 sandbox agent 产品”的能力。

约束条件：

1. 项目是个人项目，不能和 Spring AI、LangChain4j、AgentScope Java 拼大而全团队资源。
2. 竞争点应放在更低接入成本、更少概念、更好用的 Agent 组装、插件生态、CLI/TUI 体验和远端 agent 产品化能力。
3. 不新增过多 Maven 模块；维护成本必须可控。
4. 已有 `ai4j-agent` Maven 模块，应优先增强它，而不是再发明 `AgentHost` / `Host Kernel` / `ai4j-runtime` 主概念。
5. Sandbox、Runner、Blueprint、插件生命周期都要规划，但不能一次性实现全部。

## 2. 最终心智模型

推荐对外心智：

```text
ai4j
  基础模型 SDK：Provider / Chat / Responses / Streaming / Tool Schema / RAG / MCP

ai4j-agent
  通用 Agent SDK 主入口：Agent / Runtime / Session / Memory / Compact / Event / Lifecycle / Blueprint / Sandbox Binding

ai4j-extension-api
  第三方扩展合同：Tool / Command / Hook / Prompt / Skill / Guardrail / UI / Memory / Compact / SandboxProvider

ai4j-coding
  官方 Coding Agent 能力包：file / shell / git / browser / project run / diff / test runner / artifact

ai4j-cli
  官方终端产品：`ai4j` 命令、TUI、slash commands、provider/model 切换、sandbox 控制、session attach

future optional: ai4j-agent-runner
  可部署到远端 sandbox 的 Agent Runner，仅在 P0-P4 稳定后再决定是否新增
```

关键判断：

- `AgentHost` 这个名字不推荐作为主概念；容易让人误以为要新增一层平台内核。
- `Host Kernel` 也不推荐；它不是 Pi 或 Codex 必须同名复刻的结构。
- 现阶段只用 `ai4j-agent` 承载通用 Agent SDK 能力即可。
- 如果未来确实需要“远端运行器”，再讨论 `ai4j-agent-runner`，且它是产品化部署入口，不是 SDK 核心前置条件。

## 3. 与 Spring AI / LangChain4j / AgentScope Java 的差异化

AI4J 不应该试图在短期内复制所有大框架能力，而应该形成更清晰的小而强定位：

| 方向 | 大框架常见路线 | AI4J 应走路线 |
| --- | --- | --- |
| 接入成本 | 概念多、生态全、配置丰富 | 默认路径短，少样板代码，先让 Java 用户快速跑起来 |
| Agent 能力 | 可能散落在不同抽象或偏平台化 | `ai4j-agent` 作为明确主入口，Session/Memory/Tool/Event 一处聚合 |
| 插件生态 | 多依赖框架机制或 Spring 容器 | ServiceLoader + Manifest + 生命周期 Hook + 可安装插件包 |
| CLI/TUI | 多数 Java SDK 不强调终端 agent 产品 | 直接提供 `ai4j` 命令，靠近 Codex / Claude Code / Pi 的交互体验 |
| 声明式配置 | 大框架可能复杂 | YAML Blueprint 作为低门槛 Agent 模板 |
| Sandbox/Runner | 通常不作为 Java SDK 核心体验 | 抽象先行，帮开发者更快做远端 Agent 产品 |

一句话：AI4J 的卖点不是“大厂级全家桶”，而是“Java 里更容易接入、更容易组装、更容易产品化 Agent”。

## 4. 插件生态方向

Pi 的插件生态启发点在于：不同开发者可以贡献插件，使用者可以安装、组合插件来得到自己想要的 Agent 行为。

AI4J 也应靠近这个方向，但要保持 Java 项目的维护边界：

### 4.1 插件能贡献什么

插件不应只贡献工具。推荐能力集合：

- Tool：模型可调用工具。
- Command：CLI/TUI slash command。
- Prompt：可复用提示词片段。
- Skill：面向 agent 的工作流说明和领域能力包。
- Guardrail：输入/输出/工具执行前后的策略。
- Hook：Agent 生命周期扩展点。
- UI contribution：TUI / dashboard / web demo 的可选展示贡献，先规划不急做。
- Memory provider：长期记忆读写策略。
- Compact strategy：上下文压缩策略。
- SandboxProvider：沙箱后端适配。
- RemoteAgentRunnerProvider：远端 runner 适配，远期。

### 4.2 生命周期 Hook

P0-C 可逐步支持：

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

原则：

- Hook 默认 optional，不强迫老插件升级。
- 公共接口优先放 `ai4j-extension-api`。
- 运行编排仍由 `ai4j-agent` 控制。
- 官方插件只做少量示范，例如 ask-user / todo / memory / sandbox fake provider。
- 第三方插件可以独立发布，使用者按 manifest 安装和启用。

## 5. Memory / Compact / Session 方向

需要拆清三种状态：

| 层 | 作用 | 不应该做什么 |
| --- | --- | --- |
| SessionEventLog | 完整事件账本：用户输入、模型输出、工具调用、工具结果、错误、artifact、审批 | 不直接塞满模型上下文 |
| Memory | 跨轮/跨会话稳定信息：偏好、项目惯例、历史经验、用户确认 | 不等同于每轮聊天历史 |
| ModelContext / WorkingContext | 本轮真正发给模型的上下文窗口 | 不保存完整事实历史 |

### 5.1 AgentSession 应成为运行态容器

`AgentSession` 应承载：

```text
sessionId
metadata
eventLog
memory snapshot
compact state
plugin state
tool execution state
sandbox binding
artifact refs
checkpoint / resume / fork / rewind
```

P0-A 已经完成基础：session id / metadata / event log / snapshot / store / resume。

### 5.2 Compact 结果必须结构化

Compact 不是一句自然语言摘要。至少要保存：

- goal
- completed / done
- in-progress / pending
- blocked
- decisions
- changed artifacts
- failed commands
- test results
- user confirmations
- sandbox state
- open questions
- context report

后续 P0-B 的 `CompactPolicy` / `CompactResult` / `ContextProjector` 应围绕这个结构落地。

## 6. YAML Agent Blueprint

可以设计 `AI4J Agent Blueprint`，首版使用 YAML，因为它适合：

- 小白用户配置 Agent。
- 文档示例复制。
- 插件组合。
- CLI 一键运行。
- 低代码 UI 或 FlowGram 导出。
- 团队共享 Agent 模板。

第一版只做单 Agent，不要一开始做复杂 graph DSL。

示例：

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

分阶段：

- P1-A：schema / model classes / loader / validator / fixtures。
- P1-B：factory，把 Blueprint 转成 `AgentBuilder`。
- P1-C：CLI 支持 `ai4j run agent.yaml`。
- P1-D：Team Blueprint / Workflow Blueprint / FlowGram bridge 后置。

## 7. Sandbox 方向

用户讨论中的 sandbox 包括两类：

### 7.1 本地 permission sandbox

类似 Codex CLI 本机限制：文件写入、网络、审批、命令执行权限。这不是 VM，只是 host policy。

### 7.2 真实远端 sandbox

类似豆包、点点、Devin、Codex Cloud 类产品背后的 VM / 容器 / microVM：

- 安装依赖
- 运行项目
- 打开浏览器
- 截图
- 写文件
- 运行测试
- 收集 artifact
- 可销毁或冻结

### 7.3 对 AI4J 的结论

AI4J SDK 有必要预留真实 sandbox 抽象，但没必要官方维护一堆 provider。

推荐：

```text
执行环境按 session/task 隔离
数据按 user/project 持久化
```

也就是：不同用户/不同任务不要共用一个沙箱仅靠 userId 目录隔离；默认每个任务或 session 一个隔离 sandbox。同一任务里的 subagent 可共享 sandbox；任务结束后保存 artifact / snapshot，然后销毁或冻结。

### 7.4 最小抽象

```text
SandboxProvider
SandboxSession
SandboxSpec
SandboxCommand
SandboxResult
SandboxArtifact
```

原则：

- Sandbox 绑定在 `AgentSession` 或 coding session 上。
- 执行型工具自动感知 sandbox：shell / file / git / browser / project run / test runner。
- 无 sandbox = 本地执行。
- 有 sandbox = 沙箱执行。
- 审批策略不能因为 sandbox 存在而自动放开。
- CubeSandbox / Docker / E2B / K8s / 公司内部沙箱由插件或业务方实现。

## 8. 远端 Agent Runner 方向

对于一个 Agent SDK，立刻做到“整个 Agent Runner 跑在沙箱”不是 P0 必需，但如果目标是帮助小厂或开发者快速做云端 Agent 产品，则应规划这条路。

目标结构：

```text
用户 Web/App/CLI
  -> 控制端后端
  -> 远端 sandbox
  -> ai4j-agent-runner
  -> workspace / shell / browser / project run / artifacts
```

Runner 职责：

- 运行 `ai4j-agent` loop。
- 管理 session。
- 接入 coding tools。
- 运行 shell。
- 操作文件和 git。
- 打开浏览器和截图。
- 收集 artifact。
- 通过 HTTP / WebSocket / JSON-RPC 流式发送事件。

但它必须后置：等 Session、Memory、Blueprint、Sandbox SPI、Coding tool routing 稳定后再决定是否新增 `ai4j-agent-runner` Maven 模块。

## 9. CLI/TUI 方向

用户希望终端输入 `ai4j` 就像输入 `codex`、`claude`、`opencode` 一样进入 Coding Agent。

推荐体验目标：

- 一条命令安装，终端命令名为 `ai4j`。
- 默认进入 chat-first TUI。
- 支持 provider/model 切换。
- 支持 slash command palette。
- 支持 session list / resume / attach。
- 支持插件列表、启用、禁用。
- 支持 `/sandbox` 状态和控制。
- 回复渲染支持 markdown、code block、tool call、diff、approval、progress。

### 9.1 JLine vs Ink

当前 Java CLI 使用 JLine 是合理的，尤其考虑 Java 8 Maven 单栈和本项目维护成本。

- Ink/React TUI 视觉和组件生态更强，但会引入 Node/TS 运行栈，和 Java SDK 单命令分发冲突更大。
- 自研完整渲染层成本过高，不建议。
- 可采用 JLine + 轻量分区渲染 + renderer abstraction，先达到好用，而不是一开始追求 Pi 级插件化 UI。

### 9.2 Harness 是否内化到 CLI

不建议把 Coding Agent Harness 完整内化进 `ai4j-cli`。

推荐方式：

- Harness 保持 skill / 外部工程方法论原状。
- `ai4j-cli` 只做轻量识别和桥接：识别 `coding-agent-harness/`，展示任务状态，辅助打开 dashboard 或读取 task packet。
- 不复制 Harness 的任务治理、dashboard、模板系统到 CLI 内部。

理由：

- Harness 是长程工程治理体系，复杂度很高。
- CLI 是用户交互产品，内化会显著增加维护成本。
- 轻量桥接就能获得价值：让 coding agent 在有 harness 的项目里更稳。

## 10. 实施队列

### P0-A AgentSession runtime container

状态：已完成基础实现并进入 review / ready-to-confirm。

后续只在发现缺口时补强，不应重复做。

### P0-B Memory Compact Context Projector

目标：落地 `ContextBudget`、`ContextProjector`、`ContextReport`、`CompactPolicy`、`CompactResult`，并将 runtime prompt 构建接入可控投影。

最小验收：

- projector 保留 pinned prefix 和 recent tail。
- runtime 使用 projector 控制 prompt items。
- compact result 结构化保存关键字段。
- session snapshot 能保存 compact state。
- docs-site 增加 memory/compact/context 页面。

### P0-C Plugin Lifecycle Hooks

目标：让插件能参与 Agent 生命周期，不只贡献 Tool/Command。

最小验收：

- extension API 增加 optional lifecycle hook contract。
- agent runtime 触发关键 hook。
- ask-user 或 fake plugin 有 smoke test。
- hook ordering 和异常策略可测试。

### P1 Agent Blueprint YAML

目标：声明式单 Agent 配置。

最小验收：

- YAML schema / Java model / loader / validator。
- fixture tests。
- `AgentFactory` 可由 blueprint 生成 Agent。
- 文档给出完整示例和限制。

### P2 Sandbox SPI

目标：真实沙箱/远端执行环境的最小合同。

最小验收：

- fake sandbox provider。
- session binding。
- command/result/artifact/timeout/cancel 合同。
- 不接真实 provider。

### P3 ai4j-coding Sandbox Routing

目标：coding tools 自动感知 sandbox。

最小验收：

- shell/file/git/project run/test runner 可路由 fake sandbox。
- 无 sandbox 时本地行为不变。
- 审批策略仍有效。

### P4 ai4j-cli Sandbox UX + TUI 增强

目标：靠近 Codex / Claude Code / Pi 的交互体验。

最小验收：

- `/sandbox` 命令族。
- provider/model/session/plugin 状态在 TUI 可见。
- markdown/code/tool/diff 渲染更稳定。
- slash command parser / rendering tests。

### P5 Remote Agent Runner Decision

目标：决定是否新增 `ai4j-agent-runner`，并先写协议合同。

最小验收：

- event stream contract。
- artifact contract。
- fake sandbox e2e。
- 明确是否新增 Maven module。

## 11. 不要做的事

- 不要把所有能力一次性塞进一个 PR。
- 不要新增过多模块制造维护负担。
- 不要官方绑定某个 OpenAI-compatible 中转平台名称；统一叫 `openai-compatible`。
- 不要把 sandbox 做成普通 tool。
- 不要把 `ai4j-coding` 的 checkpoint/compact 逻辑机械上移。
- 不要把 Harness 完整塞进 CLI。
- 不要在测试、文档、fixture 里写 provider token。

## 12. 建议下一步

当前已有 P0-B worktree：

```text
.worktrees/feature/agent-memory-compact-context
feature/agent-memory-compact-context
```

因此下一步应继续 P0-B，而不是重新开规划：

1. 补 P0-B 单测。
2. 跑 `mvn -pl ai4j-agent "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false test`。
3. 跑 `mvn -pl ai4j-agent -am -DskipTests=false test`。
4. 更新 docs-site memory/compact/context 页面。
5. 补齐 P0-B module task package。
6. PR / CI / merge。

