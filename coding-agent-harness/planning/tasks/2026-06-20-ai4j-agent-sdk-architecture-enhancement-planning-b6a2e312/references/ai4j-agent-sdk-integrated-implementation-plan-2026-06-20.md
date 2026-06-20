# AI4J Agent SDK 集成实施规划（最终记录）

> Task: `2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312`  
> Date: 2026-06-20  
> Scope: planning-only；本文件只记录路线、边界和任务拆分，不修改生产代码、不新增 Maven 模块、不写 provider token。

## 1. 本文件固定的结论

本文件把本轮关于 `ai4j-agent`、插件生态、YAML Agent、Sandbox、远端 Runner、CLI/TUI、Memory/Compact、docs-site 的讨论合并为后续实施者可直接读取的计划。

最终结论：

1. `ai4j-agent` 就是 AI4J 的通用 Agent SDK 主入口，不再新增 `AgentHost`、`Host Kernel`、`ai4j-runtime` 作为主概念。
2. AI4J 不和 Spring AI、LangChain4j、AgentScope Java 拼“大而全”；核心差异是降低 Java Agent 接入、组装、运行和产品化成本。
3. 特色方向不是堆概念，而是：短路径 Java API、声明式 YAML、插件生态、Session/Memory/Compact、可选真实 sandbox、好用 CLI/TUI、远端 Agent Runner 产品化路径。
4. Sandbox 是真实执行环境绑定和 provider SPI，不是普通 tool；无 sandbox 时就是本地执行，不需要叫 `local sandbox`。
5. 远端 Runner 是后置产品化能力，必须等 Session、Memory/Compact、Blueprint、Sandbox SPI、Coding routing、CLI attach/status 基础稳定后再决定是否新增模块。
6. CLI/TUI 保持 Java + JLine 路线，做 renderer abstraction 和交互增强；不引入 Ink/React 作为主栈，不完整内化 Coding Agent Harness。
7. 后续对标 Pi、Codex、Claude Code、OpenCode、Spring AI、LangChain4j、AgentScope Java、Sandbox provider 时，必须先做 R0 source-backed research，不能凭印象复刻。
8. Sponsor、中转平台或具体厂商名不能变成 SDK 概念；模型兼容层统一表达为 `openai-compatible`。

## 2. 产品定位

推荐对外表达：

> AI4J 是面向 Java 开发者的轻量 AI Agent SDK：用更少概念、更短路径，把模型、工具、插件、记忆、上下文压缩、声明式 Agent、Coding Agent 和可选远端执行环境组合起来。

面向开发者的价值：

| 价值 | 说明 |
| --- | --- |
| 接入成本低 | Java 开发者不需要理解一堆框架概念才能跑一个 Agent。 |
| 使用成本低 | 默认路径短，复杂能力逐步启用；不把用户逼进大型框架心智。 |
| 可组合 | 第三方插件可贡献 tools、commands、prompts、skills、guardrails、lifecycle hooks、memory/compact/sandbox providers。 |
| 可声明 | YAML Agent Blueprint 让小白用户、团队模板、CLI 和未来 FlowGram/低代码导出都能复用同一份 Agent 定义。 |
| 可产品化 | 同一套 Session/Blueprint/Sandbox/Coding/CLI 基础可以向“云端 Agent 产品”演进。 |

禁用表达：不要写“企业采用”这类生硬字眼；不要把任何 sponsor 或中转平台名字写进 SDK 架构概念。

## 3. 模块边界

| 模块 | 责任 | 本规划结论 |
| --- | --- | --- |
| `ai4j` | 基础模型 SDK：Provider、Chat、Responses、Streaming、Tool Schema、RAG、MCP、向量、图像、音频、realtime。 | 不承载 Agent 长程运行状态。 |
| `ai4j-agent` | 通用 Agent SDK：Agent、Runtime、Session、Memory、Compact、Event、Lifecycle、Blueprint、Sandbox Binding。 | 本轮增强的核心模块。 |
| `ai4j-extension-api` | 第三方扩展合同：Manifest、Tool、Command、Prompt、Skill、Guardrail、Lifecycle Hook、Memory/Compact/Sandbox provider。 | 公共插件合同放这里；运行编排不放这里。 |
| `ai4j-coding` | 官方 Coding Agent 能力包：file、shell、git、browser、project run、test runner、diff、artifact。 | 后续感知 sandbox；无 sandbox 时保持本地语义。 |
| `ai4j-cli` | 官方终端产品：`ai4j` 命令、TUI、slash commands、provider/model/session/plugin/sandbox UX。 | 靠近 Codex / Claude Code / OpenCode / Pi 的交互体验，但不双栈重写。 |
| `docs-site` | 开发者文档站。 | 每个能力必须讲清问题、最小例子、API/YAML、限制、FAQ、下一步。 |
| future optional `ai4j-agent-runner` | 远端 sandbox 内运行 Agent 的产品化入口。 | P5 再决策；不能阻塞 P0-P4。 |

## 4. 插件生态设计方向

第三方应该能像 Pi 一样写插件、发布插件、让使用者安装并组装出自己的 Agent 效果。AI4J 不应只把插件理解为 tool 注册。

| 插件贡献点 | 用途 | 首要落点 |
| --- | --- | --- |
| Tool | 给 Agent 增加可调用动作。 | `ai4j-extension-api` + `ai4j-agent` runtime adapter |
| Command | 给 CLI/TUI 增加 slash command 或 host command。 | `ai4j-cli` command bridge |
| Prompt / Skill | 给 Agent 增加可复用行为模板。 | extension resources |
| Guardrail | 输入、输出、tool call 前后检查。 | extension-api contract + runtime dispatch |
| Lifecycle Hook | turn/model/tool/compact/session 生命周期观察或治理。 | P0-C |
| Memory / Compact Provider | 接入外部记忆、摘要、长期知识。 | P0-B 后续扩展 |
| SandboxProvider | 接入 CubeSandbox、Docker/K8s、E2B、内部 VM/microVM 等。 | P2 |

插件系统验收重点：

- 第三方插件可独立打包和发现。
- 默认安全：显式 enable、显式 expose、权限可见。
- 插件能力能被 YAML Blueprint 引用。
- CLI 能列出、检查、启用、禁用或解释插件贡献。

## 5. Session / Memory / Compact / Permission / Sandbox 的关系

`AgentSession` 应是长程 Agent 运行态容器，而不是简单的一轮请求包装。

```text
AgentSession
  ├─ metadata / status / owner / workspace
  ├─ event log: user, assistant, model, tool, approval, compact, sandbox, artifact
  ├─ memory state: short-term / project / optional external memory
  ├─ compact state: budget, strategy, summaries, retained facts
  ├─ permission policy: tool approval and host-side permission gate
  ├─ sandbox binding: optional external execution environment summary
  └─ snapshot / resume / checkpoint
```

设计原则：

1. Memory 和 Compact 是 Agent runtime 能力，不应只散落在 coding-specific 层。
2. Compact 不是简单截断；要有 `ContextBudget`、`ContextProjector`、`ContextReport`、`CompactPolicy`、`CompactResult`。
3. Permission Policy 是所有工具执行前的 host-side gate，不能因为 sandbox 存在就自动放开。
4. Sandbox binding 只记录非敏感摘要，snapshot 不能写 secret。

## 6. P0：Agent SDK 内核

P0 的目标是让 `ai4j-agent` 有可长程运行、可恢复、可压缩、可被插件观察和治理的基础。

| 子任务 | 目标 | 不做 | 状态认知 |
| --- | --- | --- | --- |
| P0-A AgentSession runtime container | session id、metadata、event log、snapshot、store、resume。 | 不接真实 sandbox。 | 已有任务与实现记录，后续按 Harness lifecycle 收口。 |
| P0-B Memory / Compact / Context Projector | `ContextBudget`、`ContextProjector`、`ContextReport`、`CompactPolicy`、`CompactResult`、session compact state。 | 不把 coding-specific checkpoint 全量上移。 | 已有任务与实现记录，后续按 Harness lifecycle 收口。 |
| P0-C Plugin Lifecycle Hooks | observation-first lifecycle hooks，覆盖 turn/model/tool/compact。 | 不强迫老插件升级，不先做可变拦截器。 | 已有实现/合并记录，后续看 Harness 队列补 closeout。 |
| P0-D Approval / Permission Policy | tool execution 前的 host-side policy gate。 | 不创建 VM/容器/远端执行环境。 | 已有任务与实现记录，后续补 closeout / docs / regression。 |

P0 通过后，`AgentSession`、Memory/Compact、Hook、Permission Policy 会成为后续 Blueprint、Sandbox、Coding Agent、CLI 的基础，而不是各层各自发明状态模型。

## 7. P1：Agent Blueprint YAML

目标：让用户用一份 YAML 声明单 Agent，适合小白用户、团队模板、插件组合、CLI 运行和未来 FlowGram/低代码导出。

首版 YAML 示例：

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

拆分：

| 子任务 | 范围 | 当前状态 / 下一步 |
| --- | --- | --- |
| P1-A schema/model/loader/validator | Java 8 DTO、YAML loader、validator、fixtures、稳定错误码。 | 已完成并合入过；Harness lifecycle 继续收口。 |
| P1-B Blueprint to AgentFactory | host-supplied context -> `AgentBuilder` / `Agent`；不读 token、不读 profile secret、不创建真实 sandbox。 | 已完成并合并；PR #109，merge commit `908e410f946563dd204caad2cb3bcb0430edfd96`。 |
| P1-C CLI `ai4j run agent.yaml` | 读取 YAML、校验、解析 host provider/profile、构造 Agent 并运行一次。 | 已完成并合并；PR #110，merge commit `384edd11424884e308c047f7e2a4b20997e95e49`。 |
| P1-D Team / Workflow Blueprint | 多 Agent、handoff、nodes/edges、FlowGram bridge。 | 后置；单 Agent 稳定前不做。 |

首版原则：

- `provider: openai-compatible` 是通用概念，不写任何中转平台名称作为 SDK 概念。
- Blueprint 不保存 provider token；token 来自 env、宿主配置或 secret store。
- `sandbox.enabled=true` 在 P1 只能表达声明和 guard，真实 provider 绑定属于 P2/P3/P4。

## 8. P2：Sandbox SPI

目标：提供真实沙箱/远端执行环境的最小合同，让用户或第三方能接 CubeSandbox、Docker/K8s、E2B、公司内部 VM/microVM 等。

核心设计：

```text
AgentSession / CodingSession
  -> optional SandboxBinding
  -> SandboxProvider
  -> SandboxSession
  -> command / file / browser / artifact capability
```

最小类型：

- `SandboxProvider`
- `SandboxSession`
- `SandboxSpec`
- `SandboxCommand`
- `SandboxResult`
- `SandboxArtifact`
- `SandboxEvent`

拆分建议：

| 子任务 | 范围 | 验收 |
| --- | --- | --- |
| P2-A Sandbox SPI model | provider/session/spec/command/result/artifact/timeout/cancel。 | fake provider tests；Java 8；无真实外部依赖。 |
| P2-B AgentSession sandbox binding | session metadata/snapshot/event log 记录 sandbox binding 摘要。 | resume 不丢 binding 摘要；不把 secret 写进 snapshot。 |
| P2-C Sandbox plugin contribution | extension-api / plugin manifest 允许第三方贡献 provider。 | manifest/validator/discovery tests。 |

沙箱隔离建议：

- 对不可信代码、shell、浏览器、文件系统操作，默认按 task/session 创建独立沙箱。
- 可以在用户/项目层持久化缓存或 workspace snapshot，但不要让多个用户共享同一个可写运行环境。
- 真正执行环境由 provider 负责，SDK 提供抽象、事件、权限和 artifact 合同。

不做：

- 不官方维护多个 provider。
- 不把 sandbox 做成普通 `run_in_sandbox` tool。
- 不让 sandbox 自动绕过 approval / permission policy。

## 9. P3：`ai4j-coding` 接入 Sandbox Routing

目标：执行型 coding tools 自动感知 sandbox。

| 工具 | 无 sandbox | 有 sandbox |
| --- | --- | --- |
| file | 本地 workspace | sandbox workspace |
| shell | 本机 shell + approval | sandbox command + approval |
| git | 本地 git | sandbox git |
| browser | 本机/宿主浏览器能力 | sandbox browser capability |
| project run/test | 本地命令 | sandbox command + artifact |

验收：

1. fake sandbox 覆盖 shell/file/git/project run/test runner。
2. 无 sandbox 时本地行为保持兼容。
3. approval、timeout、cancel、artifact 结果可测试。
4. sandbox 结果必须能进入 session event log / artifact refs。

## 10. P4：CLI/TUI 体验

目标：终端输入 `ai4j` 后，是一个接近 Codex / Claude Code / OpenCode / Pi 的 coding agent 交互体验。

当前技术选择：

- 主栈继续 Java + JLine。
- 不引入 Ink/React 作为主 TUI，避免 Node/TS 双栈和分发复杂度。
- 不自研完整渲染引擎；做轻量 renderer abstraction。
- Harness 保持独立 skill / 工程方法论，CLI 只做轻量识别和桥接。

拆分建议：

| 子任务 | 范围 |
| --- | --- |
| P4-A packaging | 一条命令安装；终端命令名为 `ai4j`；文档说明 Maven/zip/native-image 路径。 |
| P4-B TUI layout | chat-first、status bar、provider/model/session/plugin/sandbox 状态、tool call panel。 |
| P4-C slash commands | `/provider`、`/model`、`/plugins`、`/extension`、`/session`、`/compact`、`/sandbox`、`/help`。 |
| P4-D reply rendering | markdown、code block、diff、tool call、approval、progress、error。 |
| P4-E Harness bridge | 检测 `coding-agent-harness/`、显示任务状态、打开 dashboard/任务包；不复制 Harness 模板和治理系统。 |

需要先做 R0 调研：Pi / Codex / Claude Code / OpenCode 的插件、TUI、slash command、provider/model 切换、回复渲染模式必须有公开资料或本仓实测支撑。

## 11. P5：远端 Agent Runner 决策

目标：帮助开发者快速做出“云端 sandbox agent 产品”的运行端，但必须后置。

推荐先写协议合同，不急着新增 Maven 模块：

```text
client/web/cli
  -> control backend
  -> sandbox provider
  -> ai4j-agent-runner
  -> event stream / artifacts / screenshots / workspace
```

Runner 需要能力：

- session lifecycle
- event stream
- tool execution
- workspace file operations
- shell/project run/test
- browser/screenshot capability
- artifact collection
- checkpoint/freeze/resume

决策门禁：

1. P0 Session/Memory/Hook/Permission 已稳定。
2. P1 Blueprint 可生成 Agent。
3. P2 Sandbox SPI 有 fake provider。
4. P3 Coding tools 能路由 sandbox。
5. P4 CLI 能 attach/resume/status。

## 12. R0 调研门禁

后续凡是“对标某个框架/产品”的实现，先做调研任务，不凭印象复刻。

| Gate | 对象 | 必须回答 | 输出 |
| --- | --- | --- | --- |
| R0-PI | Pi / pi-agent / pi-sdk 公开资料、示例和插件包 | 插件能贡献什么？如何安装和组合？TUI 哪些交互值得借鉴？哪些不适合 Java 单栈？ | task-local research digest |
| R0-CODING-CLI | Codex、Claude Code、OpenCode 等公开文档/博客/实测 | session、compact、permission/sandbox、tool call、diff 渲染和 slash command 的共同模式是什么？ | CLI/TUI research notes |
| R0-JAVA-SDK | Spring AI、LangChain4j、AgentScope Java 官方文档 | AI4J 必须覆盖哪些能力？应该避开什么复杂度？差异化如何表达？ | comparison matrix |
| R0-SANDBOX | CubeSandbox、E2B、Docker/K8s/internal sandbox 公开 API | 最小 sandbox contract 如何抽象？session/task 隔离和 artifact 回收怎么做？ | Sandbox SPI design input |

## 13. docs-site 同步要求

每个能力页必须能让用户“用起来”，不是只展示 roadmap。

页面结构要求：

1. 这个能力解决什么问题。
2. 最小可运行示例。
3. 核心 API / YAML 字段。
4. 与其他能力的关系。
5. 限制和不做什么。
6. 常见错误。
7. 下一步链接。

建议页面队列：

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

## 14. 当前仓库状态与实际下一步

根据本轮 PR / CI / Harness 诊断：

- 主仓：`G:\My_Project\java\ai4j-sdk`，当前分支 `main` 已对齐 `origin/main` 的 P1-C merge 后状态。
- P1-B 已完成并合并：PR #109，merge commit `908e410f946563dd204caad2cb3bcb0430edfd96`。
- P1-C 已完成并合并：PR #110，merge commit `384edd11424884e308c047f7e2a4b20997e95e49`。
- P1-C CI 通过：build、java-regression、module-tests(ai4j/agent/cli/coding/extension/plugin/starters/demos)、package-smoke 均 pass。
- 未跟踪路径：`.wt/` 是 Git worktree 容器，不应当作业务源文件提交；P1-C worktree 可清理。
- 下一步优先级：启动 P2-A Sandbox SPI model，而不是继续新增总规划任务。

P2-A 启动顺序：

1. 创建 `feature/agent-sandbox-spi-model` 分支和独立 worktree。
2. 用 Harness 创建 `P2-A Sandbox SPI model` module task，并 `task-start`。
3. 在 `ai4j-agent` 内定义 Java 8 兼容的 sandbox SPI model：provider/session/spec/command/result/artifact/event/timeout/cancel。
4. 提供 fake provider tests，证明不依赖真实外部 sandbox。
5. 更新 docs-site `Sandbox SPI` 技术页与 Agent SDK Roadmap。
6. 更新 Regression SSoT / Cadence Ledger（如新增固定回归面）。
7. targeted/broad/docs/Harness 验证后提交、PR、CI、merge、清理 worktree。

## 15. 不要做的事

- 不要重复新建总规划任务。
- 不要一次性实现 P0-P5。
- 不要新增 `AgentHost` / `Host Kernel` / `ai4j-runtime` 主概念。
- 不要过早新增 `ai4j-agent-runner` Maven 模块。
- 不要把 Pi 的实现细节当作已验证事实；先做 R0 调研。
- 不要完整内化 Coding Agent Harness 到 `ai4j-cli`。
- 不要把 sandbox 设计成普通 tool。
- 不要把 provider token 写入测试、fixture、文档或日志。
- 不要把任何 OpenAI-compatible 中转平台名称变成 SDK 概念名。
