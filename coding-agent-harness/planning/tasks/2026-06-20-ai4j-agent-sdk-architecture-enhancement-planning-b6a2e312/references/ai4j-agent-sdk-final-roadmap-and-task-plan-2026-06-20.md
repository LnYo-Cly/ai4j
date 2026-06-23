# AI4J Agent SDK 最终规划与任务记录（2026-06-20）

> Task: `2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312`  
> Scope: planning-only；本文件把本轮关于 `ai4j-agent`、插件生态、YAML Agent、Sandbox/Runner、Memory/Compact、CLI/TUI、docs-site 和 Harness 边界的讨论沉淀为后续实施入口。  
> Owner: coordinator  
> Recording time: 2026-06-20 13:30 +08:00

## 1. 固定结论

1. `ai4j-agent` 继续作为 AI4J 的通用 Agent SDK 主入口；不新增 `AgentHost`、`Host Kernel`、`ai4j-runtime` 作为对外主概念。
2. AI4J 不和 Spring AI、LangChain4j、AgentScope Java 拼“大而全”；差异化应放在“Java Agent 接入成本低、组装成本低、插件生态清晰、CLI/TUI 可产品化、Sandbox/Runner 可选演进”。
3. 插件系统不是只有 tool 注册；第三方应能独立发布插件，使用者能安装、启用、组合插件来达到自己的 Agent 效果。
4. YAML Agent Blueprint 是重要差异化能力：它让小白用户、团队模板、CLI、FlowGram/低代码导出和插件组合共享同一份 Agent 定义。
5. Sandbox 指真实执行隔离环境或远端运行环境，不是 Java 进程内的“安全管理器”；无 sandbox 时就是宿主本地执行，不需要命名为 `local sandbox`。
6. Remote Agent Runner 是后置产品化能力，目标是帮助开发者快速做出“豆包/点点类云端 Agent 产品”的运行端，但必须在 P0-P4 基础稳定后推进。
7. CLI/TUI 继续走 Java + JLine 路线；不引入 Ink/React 作为主栈，也不自研完整渲染引擎。后续做 renderer abstraction、布局、slash command、provider/model 切换和回复渲染增强。
8. Coding Agent Harness 不完整内化进 `ai4j-cli`；保持 skill/外部治理工具形态，AI4J CLI 只做可选轻量识别、状态展示、dashboard/task 跳转或命令桥接。
9. docs-site 必须和真实 API 对齐，不写不存在的 `Ai4j.chat()` 等伪 API；每个能力页都要讲清楚“解决什么、怎么用、限制是什么”。
10. 所有中转平台统一按 `openai-compatible` 类能力描述，不能把具体平台名写成 SDK 架构概念。

## 2. 产品分层

| 层级 | 面向用户的能力 | 主要模块 | 目标 |
| --- | --- | --- | --- |
| Core AI SDK | Provider、Chat/Responses、Streaming、RAG、MCP、Vector、Image/Audio/Realtime | `ai4j` | 提供模型与基础 AI 能力，不承载长程 Agent 状态。 |
| Agent SDK | Agent、Session、Memory、Compact、Tool Registry、Workflow、Trace、Subagent、Permission、Blueprint、Sandbox Binding | `ai4j-agent` | Java 项目用最少概念接入可运行、可恢复、可组合的 Agent。 |
| Extension Ecosystem | Tool、Command、Prompt、Skill、Guardrail、Lifecycle Hook、Memory/Compact/Sandbox Provider | `ai4j-extension-api` + runtime adapters | 让第三方插件可独立发布、安装、启用、组合。 |
| Coding Agent Runtime | 文件、Shell、Git、浏览器、项目运行、测试、diff、artifact、sandbox routing | `ai4j-coding` | 官方 Coding Agent 能力包，通用 Agent SDK 不背负 coding-only 复杂度。 |
| CLI/TUI Product Surface | `ai4j` 终端入口、JLine TUI、slash command、provider/model/session/plugin/sandbox UX | `ai4j-cli` | 靠近 Codex、Claude Code、OpenCode、Pi 的终端使用体验。 |
| Remote Runner Productization | 远端 workspace、shell、browser、event stream、artifact、checkpoint/resume | future optional / contract first | 帮开发者做云端 Agent 产品；不阻塞本地 SDK。 |

## 3. 插件生态规划

### 3.1 插件贡献点

| 贡献点 | 作用 | 首版策略 |
| --- | --- | --- |
| Tool | 暴露可调用动作。 | 已有 tool 注册能力继续增强权限、schema、显式 expose。 |
| Command | 给 CLI/TUI 增加 slash command 或 host command。 | `ai4j-cli` 提供 command registry/bridge，先不开放复杂 UI 扩展。 |
| Prompt / Skill | 复用行为模板、系统提示、操作说明。 | 作为 extension resources 管理，Blueprint 可引用。 |
| Guardrail | 输入、输出、tool call 前后检查。 | 先定义 contract 和 observation/decision 结果，避免隐式阻断。 |
| Lifecycle Hook | session/turn/model/tool/compact 生命周期观察。 | P0-C 已打基础；默认 observation-first，不强制老插件升级。 |
| Memory / Compact Provider | 接入外部 memory store、摘要策略、context projector。 | P0-B 后再扩展 provider SPI。 |
| Sandbox Provider | 接入 CubeSandbox、Docker/K8s、E2B、内部 VM/microVM 等。 | P2 后开放；必须 fake-testable。 |

### 3.2 插件安全原则

1. 插件默认不自动暴露危险能力；必须显式 install / enable / expose。
2. manifest 必须声明能力、权限、资源、版本兼容范围和默认暴露状态。
3. CLI 必须能解释“插件贡献了什么、需要什么权限、当前启用了什么”。
4. Blueprint 引用插件时只引用插件 id、能力 id 和配置 key，不写 secret。
5. 第三方插件必须能独立打包发布；官方示例插件只作为作者模板，不污染核心模块。

## 4. Session / Memory / Compact 规划

`AgentSession` 应升级为长程 Agent 的运行态容器：

```text
AgentSession
  ├─ identity: sessionId / owner / workspace / metadata
  ├─ event log: user / assistant / model / tool / approval / compact / sandbox / artifact
  ├─ memory: short-term / project / long-term / external refs
  ├─ context: ContextBudget / ContextProjector / ContextReport
  ├─ compact: CompactPolicy / CompactTrigger / CompactResult / retained facts
  ├─ permission: approval decisions and tool policy gates
  ├─ sandbox: optional non-sensitive SandboxBinding summary
  └─ lifecycle: snapshot / checkpoint / restore / resume
```

关键设计：

- Compact 不是截断聊天记录，而是结构化压缩：目标、已完成、进行中、阻塞、关键决策、变更文件、失败命令、验证结果、开放问题都要保留。
- Memory/Compact 属于通用 Agent runtime，不应只存在于 `ai4j-coding`。
- Permission Policy 是工具执行前置 gate；即使进入 sandbox，也不能自动绕过审批。
- Snapshot / event log 不保存 provider token、sandbox secret 或本机绝对路径等敏感信息。
- 可参考 Codex、Claude Code、OpenCode 等公开设计分析，但必须做 R0 source-backed research；不能复制泄露源码或凭印象复刻。

## 5. YAML Agent Blueprint 规划

首版目标是“单 Agent 可声明组装”，不是一次性做 Team/Workflow 全量 DSL。

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

tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe

session:
  memory:
    enabled: true
    scope: project
  compact:
    enabled: true
    trigger:
      contextRatio: 0.75
    strategy: structured-summary

sandbox:
  enabled: false

workflow:
  mode: react
  maxTurns: 20
```

边界：

1. Blueprint 是声明式组装层，不执行任意 Java 代码。
2. provider token 只来自 env、宿主配置或 secret store，不能写进 YAML fixture 或文档。
3. `sandbox.enabled=true` 首版可以表达意图和校验；真实 provider 绑定由 P2/P3/P4 承担。
4. FlowGram/低代码导出可作为后续 bridge，但单 Agent schema 稳定前不做复杂编排。

## 6. Sandbox 与 Remote Runner 规划

### 6.1 两种模式

| 模式 | Agent 在哪里跑 | 工具在哪里执行 | 适合场景 |
| --- | --- | --- | --- |
| Host-driven sandbox tools | Java 应用或 CLI 宿主进程内 | 外部 sandbox / VM / container / browser | 给现有 Java 应用或 CLI 增加安全工具执行能力。 |
| Remote Agent Runner | sandbox / 远端运行环境内 | 同一个隔离环境 | 做云端 Agent 产品：远端桌面、项目环境、shell、浏览器、artifact。 |

### 6.2 Sandbox 抽象

P2/P3/P4 需要稳定以下最小合同：

- `SandboxSpec`：镜像、资源、workspace、网络、标签、超时、持久化策略。
- `SandboxProvider`：创建、恢复、连接、销毁 sandbox session。
- `SandboxSession`：运行态 handle，只暴露非敏感摘要和能力声明。
- `SandboxCommand` / `SandboxResult`：命令、超时、取消、stdout/stderr、exit code、artifact refs。
- `SandboxArtifact`：文件、截图、日志、构建产物、下载链接或内部引用。
- `SandboxEvent`：创建、执行、失败、取消、销毁、资源回收。
- `SandboxBinding`：写入 `AgentSession` 的非敏感绑定摘要。

### 6.3 隔离策略

| 策略 | 说明 | 建议默认 |
| --- | --- | --- |
| `PER_TASK_EPHEMERAL` | 每个任务独立 sandbox，结束销毁。 | 高风险 shell、未知代码执行、CI 类任务默认。 |
| `PER_SESSION` | 每个 Agent 会话一个 sandbox，保留工作区和 artifact。 | 云端 Agent 产品默认。 |
| `PER_USER_POOL` | 用户级 sandbox 池，多会话复用。 | 仅适合强 reset、强配额、低风险场景；不作为默认。 |

默认不建议多个用户共用一个可写 sandbox。云端产品形态中，常见设计是每个 session 或 task 有独立环境，最多复用只读镜像层、依赖缓存或受控 workspace snapshot。

### 6.4 Remote Runner 决策

Remote Runner 不应现在就新增大 Maven 模块。先写协议合同，满足以下门禁后再决定：

1. P0 Session/Memory/Compact/Hook/Permission 稳定。
2. P1 Blueprint 可稳定生成 Agent。
3. P2 Sandbox SPI 有 fake provider 和 session binding。
4. P3 Coding tools 已能 sandbox routing。
5. P4 CLI 有 attach/status/disable 等基础 UX。

未来 runner 能力：event stream、workspace file ops、shell/project run/test、browser/screenshot、artifact collection、checkpoint/freeze/resume、quota/timeout/cancel、tenant isolation。

## 7. CLI/TUI 规划

目标体验：用户一条命令安装后，在终端输入 `ai4j`，进入像 Codex / Claude Code / OpenCode / Pi 一样的 coding agent 交互入口。

### 7.1 技术路线

- 主栈：Java + JLine。
- 不选：Node Ink / React TUI 作为主栈；除非未来独立前端壳有明确收益。
- 不做：自研复杂 terminal renderer。
- 要做：renderer abstraction、markdown/code/diff/tool-call 分块渲染、状态栏、命令面板、可测试的 parser 和 view model。

### 7.2 交互能力

| 能力 | 规划 |
| --- | --- |
| Provider / Model 切换 | `/provider`、`/model`、profile 列表、当前状态展示、失败回退。 |
| Session | `/session`、snapshot/resume、memory/compact 状态。 |
| Plugin | `/plugins`、`/extension`、插件贡献能力展示、启用/禁用/权限解释。 |
| Sandbox | `/sandbox status`、`/sandbox attach`、`/sandbox disable`；真实 create/list/destroy 后置。 |
| Permission | tool call approval、危险命令解释、策略来源展示。 |
| Reply Rendering | markdown、代码块、diff、tool call、approval、progress、error 分块显示。 |
| Harness Bridge | 检测 `coding-agent-harness/`，显示当前任务状态或打开 dashboard；不复制治理体系。 |

## 8. docs-site 规划

文档必须从“写了 roadmap”升级到“开发者照着能用”。每个能力页面统一结构：

1. 这个能力解决什么问题。
2. 什么时候应该用，什么时候不应该用。
3. 最小可运行 Java 示例或 YAML 示例。
4. 核心 API / YAML 字段解释。
5. 和其他模块的关系。
6. 限制、不做什么、安全边界。
7. 常见错误和排查。
8. 下一步链接。

禁止事项：

- 不写不存在的 API。
- 不写“企业采用”这类生硬定位。
- 不把 sponsor 或中转平台名称当成 SDK 概念。
- 不用 roadmap 替代教程。

建议页面队列：

- Agent SDK Roadmap
- Session Runtime
- Memory / Compact / Context Projector
- Plugin Lifecycle Hooks
- Plugin Authoring Guide
- Agent Blueprint YAML
- Sandbox SPI
- Coding Agent Sandbox Routing
- CLI/TUI Guide
- Provider configuration with `openai-compatible`
- Remote Agent Runner Roadmap

## 9. 当前仓库状态校正

本记录按 2026-06-20 13:30 +08:00 的本地诊断校正旧规划中的“下一步”：

| 分支 / worktree | 当前事实 | 影响 |
| --- | --- | --- |
| root `main` at `83e75ae` | 已包含 P1-B/P1-C、P2 Sandbox SPI/binding、P3 coding sandbox routing 等后续基础提交。 | 旧规划里“下一步 P2-A”已过期；在 `main` 继续时应从 P4/P5/docs/runner 队列判断。 |
| `.worktrees/feature/cli-sandbox-commands` on `dev` at `91e07b1` | 已包含 P4 `/sandbox status/attach/disable` metadata-only CLI 命令；不实现真实 provider bridge；metadata-only attach 不会静默回退本地执行。 | 若以 `dev` 为集成基线，下一步可推进 Remote Runner SPI、one-command install、docs-site completeness 或 R0 research。 |
| Harness status | root `main` 与 `dev` 均已有通过记录；本次新增规划材料后需重新跑 root status。 | 本文件只做 planning record，不代表人工 review confirm。 |

## 10. 后续任务队列（当前推荐）

| 优先级 | 任务 | 主模块 | 依赖 | 验收 |
| ---: | --- | --- | --- | --- |
| 1 | R0-PI / Coding CLI source-backed research | harness / docs task | 无；但影响 CLI/TUI 和插件规划 | 公开资料 digest，明确 Pi 插件/TUI 可借鉴点和不适合 Java 单栈的点。 |
| 2 | Agent Runtime Backlog Reconciliation | harness / agent-runtime | 已合并 P0-P4 多任务 | 逐项确认 review/closeout/PR/merge 状态，避免 dashboard 与代码漂移。 |
| 3 | Remote Agent Runner SPI contract | `ai4j-agent` | P2/P3/P4 基础 | fake runner tests；event stream fixture；不新增真实云依赖。 |
| 4 | One-command install design/prototype | `ai4j-cli` | CLI entry 稳定 | 安装后终端 `ai4j` 可运行；packaging smoke；Windows 友好。 |
| 5 | CLI/TUI interaction polish | `ai4j-cli` | R0 research + current JLine 基座 | provider/model/session/plugin/sandbox 状态栏，slash palette，reply rendering tests。 |
| 6 | docs-site completeness pass | `docs-site` | 当前真实 API 稳定 | 每个核心能力页补最小可用示例、限制、FAQ；`npm --prefix docs-site run build` 通过。 |
| 7 | Sandbox provider contribution contract | `ai4j-extension-api` + `ai4j-agent` | Sandbox SPI 稳定 | manifest/discovery/validator tests；第三方 provider fake plugin。 |
| 8 | Remote Runner product guide | `docs-site` | Runner contract 草案 | 解释如何做云端 Agent 产品，不承诺官方托管平台。 |

## 11. 执行原则

- 每个实现任务都必须新建或复用 Harness task package，并记录 progress / review / walkthrough。
- 代码任务用独立 worktree；不要在总规划任务里混入生产代码。
- 新增固定回归面时，同步 `docs/05-TEST-QA/Regression-SSoT.md` 与 `docs/05-TEST-QA/Cadence-Ledger.md`。
- Sandbox / Runner 改动先用 fake provider / fake runner 测试，真实服务只作为 opt-in 示例。
- CLI/TUI 改动至少覆盖 parser/view model/targeted regression，必要时补手动 smoke。
- docs-site 改动必须跑 `npm --prefix docs-site run build`。

## 12. 本规划任务的收口边界

本文件只完成“规划记录”。不执行以下事项：

- 不修改 Java 生产代码。
- 不新增 Maven 模块。
- 不接真实 sandbox provider。
- 不创建远端 runner 服务。
- 不提交 provider token 或本地 secret。
- 不代替人工 review confirmation。

下一轮如果进入实现，优先从“R0 source-backed research / backlog reconciliation / Remote Agent Runner SPI contract / one-command install / docs-site completeness”中选一个小切片单独开任务。
