# AI4J Agent SDK 执行级路线图与调研门禁

> Task: `2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312`  
> Date: 2026-06-20  
> Scope: planning-only；不修改生产代码、不新增 Maven 模块、不写 provider token。

## 1. 本文件的用途

本文件把前面关于 `ai4j-agent`、插件生态、YAML Agent、Sandbox、远端 Agent Runner、CLI/TUI 和 docs-site 的讨论，整理成后续可直接执行的 Harness 路线图。

它不是新的架构名词，也不是一次性大 PR 的范围。它的作用是：

1. 固定当前产品判断：AI4J 的核心竞争点是降低 Java Agent 接入和组装成本。
2. 固定模块边界：优先增强已有 `ai4j-agent`，不要新增 `AgentHost` / `Host Kernel` 作为主概念。
3. 固定实施顺序：P0 内核优先，Blueprint / Sandbox / CLI / Runner 分阶段推进。
4. 固定调研门禁：凡是对标 Pi、Codex、Claude Code、OpenCode、Spring AI、LangChain4j、AgentScope Java 的结论，必须有公开资料或本仓实测支撑。

## 2. 产品定位

AI4J 不应该和 Spring AI、LangChain4j、AgentScope Java 拼“大厂级全家桶”。更现实、也更有特色的定位是：

> 面向 Java 开发者的轻量 AI Agent SDK：更少概念、更短接入路径、更容易组合插件、更容易做出 coding agent 和远端 sandbox agent 产品。

对外表达应避免“企业采用”这类生硬措辞，改成：

- 适合 Java 应用快速接入模型能力。
- 适合开发者用少量代码组装 Agent。
- 适合团队沉淀自己的插件、工具和 Agent 模板。
- 适合需要 CLI/TUI 或远端执行环境的 Agent 产品原型。

## 3. 模块边界

| 模块 | 定位 | 本路线中的职责 |
| --- | --- | --- |
| `ai4j` | 基础模型 SDK | Provider、Chat、Responses、Streaming、Tool Schema、RAG、MCP 等模型层能力。 |
| `ai4j-agent` | 通用 Agent SDK 主入口 | Agent、Runtime、Session、Memory、Compact、Event、Lifecycle、Blueprint、Sandbox Binding。 |
| `ai4j-extension-api` | 第三方扩展合同 | Manifest、Tool、Command、Prompt、Skill、Guardrail、Lifecycle Hook、Memory/Compact/Sandbox provider contract。 |
| `ai4j-coding` | 官方 coding agent 能力包 | file、shell、git、browser、project run、test runner、diff、artifact；后续感知 sandbox。 |
| `ai4j-cli` | 官方终端产品 | `ai4j` 命令、TUI、slash commands、provider/model 切换、session resume、插件与 sandbox 控制。 |
| `docs-site` | 开发者文档站 | 每个能力点讲清楚：概念、最小例子、API、限制、对比、常见问题。 |
| future optional `ai4j-agent-runner` | 远端运行器 | 仅在 P0-P4 稳定后决定；用于部署到 VM/容器/microVM sandbox 中运行 Agent。 |

## 4. 调研门禁

后续凡是“对标某个框架/产品”的设计，先做调研任务，不要凭印象写实现。

| Gate | 调研对象 | 必须回答 | 产物 |
| --- | --- | --- | --- |
| R0-PI | Pi / pi-agent / pi-sdk 公开资料、示例和插件包 | 插件能贡献什么？安装和组装路径是什么？TUI 哪些交互值得借鉴？哪些不适合 Java 单栈？ | task-local research digest |
| R0-CODING-CLI | Codex、Claude Code、OpenCode 等公开文档/博客/实测 | session、compact、permission/sandbox、tool call、diff 渲染和 slash command 的共同模式是什么？ | CLI/TUI research notes |
| R0-JAVA-SDK | Spring AI、LangChain4j、AgentScope Java 官方文档 | AI4J 应避开什么复杂度？哪些能力必须覆盖？哪些可做差异化？ | comparison matrix |
| R0-SANDBOX | CubeSandbox、E2B、Docker/K8s/internal sandbox 等公开 API | 最小 sandbox contract 如何抽象？session/task 隔离怎么做？artifact 如何回收？ | Sandbox SPI design input |

验收要求：

- 只引用公开资料、官方文档或本仓实测。
- 不把某个中转平台名称写成 SDK 概念；统一使用 `openai-compatible`。
- 不使用、不提交、不打印 provider token。

## 5. P0：Agent 内核优先

P0 的目标是让 `ai4j-agent` 从“能跑 Agent”升级为“可长程运行、可压缩上下文、可被插件参与生命周期”的稳定内核。

| 子任务 | 状态 | 范围 | 验收 |
| --- | --- | --- | --- |
| P0-A AgentSession runtime container | 已有任务，review 中 | session id、metadata、event log、snapshot、store、resume | 不重复实现；后续只补缺口。 |
| P0-B Memory Compact Context Projector | 已有任务，review 中 | `ContextBudget`、`ContextProjector`、`ContextReport`、`CompactPolicy`、`CompactResult`、runtime projection | targeted/broad agent tests、docs-site build、task package closeout。 |
| P0-C Plugin Lifecycle Hooks | 当前 worktree 已有实现待收尾 | lifecycle hook contract、runtime dispatch、hook error policy、extension snapshot/inspection | extension-api + ai4j-agent tests、docs page、PR/CI/merge。 |
| P0-D Approval / Permission Policy 梳理 | 新任务 | 把本地 permission sandbox、tool approval、remote sandbox approval 的边界说清楚 | 不改变行为，先补设计和最小 API 缝隙。 |

P0 不做：

- 不做真实 sandbox provider。
- 不做 YAML graph DSL。
- 不做远端 runner。
- 不做 Node/Ink TUI 重写。

## 6. P1：YAML Agent Blueprint

目标：让小白用户或团队可以用一个 YAML 定义可运行 Agent，而不是必须写 Java 组装代码。

首版只做单 Agent：

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

拆分：

| 子任务 | 范围 | 不做 |
| --- | --- | --- |
| P1-A Blueprint schema/model/loader/validator | Java 8 model、YAML loader、validator、fixtures、错误信息 | 不创建 Agent |
| P1-B Blueprint to AgentFactory | 将 Blueprint 转成 `AgentBuilder` / runtime config | 不接 CLI |
| P1-C CLI `ai4j run agent.yaml` | 最小运行入口和错误渲染 | 不做 TUI 全量重构 |
| P1-D Team/Workflow Blueprint | 多 Agent / graph / FlowGram bridge | 后置，等单 Agent 稳定 |

## 7. P2：Sandbox SPI

目标：提供真实沙箱/远端执行环境的最小合同，让用户或第三方可以接 CubeSandbox、Docker、K8s、E2B 或内部系统。

核心判断：

- Sandbox 不是普通 tool，而是 `AgentSession` / coding session 的执行环境绑定。
- 无 sandbox 时就是本地执行，不需要叫 `local sandbox`。
- 有 sandbox 时，执行型工具自动路由到 sandbox。
- 审批策略不能因为进入 sandbox 自动放开。
- 默认按 task/session 隔离执行环境，用户/项目数据做持久化。

最小类型：

```text
SandboxProvider
SandboxSession
SandboxSpec
SandboxCommand
SandboxResult
SandboxArtifact
SandboxEvent
```

拆分：

| 子任务 | 范围 | 验收 |
| --- | --- | --- |
| P2-A Sandbox SPI model | provider/session/spec/command/result/artifact/timeout/cancel | fake provider tests |
| P2-B AgentSession sandbox binding | session metadata/snapshot/event log 记录 sandbox binding | resume 不丢 binding 摘要 |
| P2-C Sandbox plugin contribution | extension-api 允许插件贡献 provider | manifest/validator tests |

## 8. P3：ai4j-coding sandbox routing

目标：官方 coding tools 感知 sandbox。

| 工具 | 无 sandbox | 有 sandbox |
| --- | --- | --- |
| file | 本地 workspace | sandbox workspace |
| shell | 本机 shell + approval | sandbox command + approval |
| git | 本地 git | sandbox git |
| browser | 本机/外部浏览器能力 | sandbox browser 或 provider capability |
| project run/test | 本地命令 | sandbox command + artifact |

验收：

- fake sandbox 覆盖 shell/file/git/project run/test runner。
- 本地行为保持兼容。
- approval、timeout、cancel、artifact 结果可测试。

## 9. P4：CLI/TUI 体验

目标：终端输入 `ai4j` 后，体验靠近 Codex / Claude Code / OpenCode / Pi 这类 coding agent，而不是传统 Java demo CLI。

当前选择：

- 保持 Java + JLine 路线。
- 不引入 Ink/React 作为主 TUI，避免 Node/TS 双栈维护。
- 不自研完整渲染引擎；做轻量 renderer abstraction。

体验拆分：

| 子任务 | 范围 |
| --- | --- |
| P4-A install/run packaging | 一条命令安装，`ai4j` 作为主命令；文档清楚说明 Maven/zip/native-image 的路径。 |
| P4-B TUI layout | chat-first、status bar、provider/model/session/plugin/sandbox 状态、tool call panel。 |
| P4-C slash commands | `/provider`、`/model`、`/plugins`、`/extension`、`/session`、`/compact`、`/sandbox`、`/help`。 |
| P4-D reply rendering | markdown、code block、diff、tool call、approval、progress、error。 |
| P4-E Harness bridge | 仅识别 `coding-agent-harness/`、显示任务状态、打开 dashboard/任务包；不内化 Harness 模板和治理系统。 |

## 10. P5：远端 Agent Runner 决策

目标：帮助开发者快速做出“云端 Agent 产品”的运行端，但必须后置。

先写协议，不急着新增模块：

```text
client/web/cli
  -> control backend
  -> sandbox provider
  -> ai4j-agent-runner
  -> event stream / artifacts / screenshots / workspace
```

Runner 应提供：

- session lifecycle
- event stream
- tool execution
- workspace file operations
- shell/project run/test
- browser/screenshot capability
- artifact collection
- checkpoint/freeze/resume

决策门禁：

- P0 Session/Memory/Hook 已稳定。
- P1 Blueprint 可生成 Agent。
- P2 Sandbox SPI 有 fake provider。
- P3 Coding tools 能路由 sandbox。
- P4 CLI 能 attach/resume/status。

只有这些条件满足后，才决定是否新增 `ai4j-agent-runner` Maven 模块。

## 11. docs-site 同步路线

docs-site 必须按“用户能不能用起来”重构，而不是堆功能列表。

每个能力页必须包含：

1. 这个能力解决什么问题。
2. 最小可运行示例。
3. 核心 API / YAML 字段。
4. 与其他能力的关系。
5. 限制和不做什么。
6. 常见错误。
7. 下一步链接。

建议新增或增强页面：

- Agent SDK Roadmap
- Session Runtime
- Memory / Compact / Context
- Plugin Lifecycle Hooks
- Plugin Authoring Guide
- Agent Blueprint YAML
- Sandbox SPI
- Coding Agent Tools
- CLI/TUI Guide
- Provider configuration with `openai-compatible`

措辞要求：

- 不写“企业采用”。
- 不把 Sponsor 或中转平台写成 SDK 架构概念。
- 不使用 provider token 示例。

## 12. 当前实际下一步

根据当前仓库状态，下一步不是再做总规划，而是收尾已有工作：

1. **先处理 P0-C worktree**：`G:/My_Project/java/ai4j-sdk/.worktrees/feature/agent-plugin-lifecycle-hooks` 已有未提交实现，应 final harness status、stage、commit、task-review、push、PR、CI、merge。
2. **确认 P0-B 状态**：P0-B 已进入 review/pending PR 状态；若还未推送合并，按任务包收口。
3. **再开 P0-D 或 P1-A**：P0-C/P0-B 合并后，优先开 `P0-D Approval / Permission Policy` 或 `P1-A Blueprint schema/model/loader/validator`，不要并行制造过多未合并分支。

## 13. 不要做的事

- 不要重复创建新的总规划任务。
- 不要一次性实现 P0-P5。
- 不要把 Pi 的实现细节当作已验证事实；先调研。
- 不要把 Harness 完整塞进 `ai4j-cli`。
- 不要新增过多 Maven 模块。
- 不要把 sandbox 设计成一个普通工具。
- 不要把 provider token 写入测试、文档或日志。
- 不要把任何 OpenAI-compatible 中转平台名称变成 SDK 概念名。

