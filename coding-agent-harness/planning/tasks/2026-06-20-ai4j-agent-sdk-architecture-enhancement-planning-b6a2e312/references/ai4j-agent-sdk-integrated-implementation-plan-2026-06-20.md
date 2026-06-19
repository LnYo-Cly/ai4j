# AI4J Agent SDK 集成实施规划（最终记录）

> Task: `2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312`  
> Date: 2026-06-20  
> Scope: planning-only；本文件只记录路线和任务拆分，不修改生产代码、不新增 Maven 模块、不写 provider token。

## 1. 本文件要固定什么

本文件把本轮关于 `ai4j-agent`、插件生态、YAML Agent、Sandbox、远端 Runner、CLI/TUI、Memory/Compact 的讨论合并为一份后续实施者可直接读取的计划。

最终结论：

1. `ai4j-agent` 就是 AI4J 的通用 Agent SDK 主入口，不再新增 `AgentHost`、`Host Kernel`、`ai4j-runtime` 作为主概念。
2. AI4J 的差异化不是“大而全”，而是降低 Java Agent 接入和组装成本：少概念、短路径、可插件化、可声明式、可做 coding agent 和远端 sandbox agent 产品。
3. Sandbox 是执行环境绑定和 provider SPI，不是普通 tool；无 sandbox 时就是本地执行，不需要叫 `local sandbox`。
4. 远端 Runner 是后置产品化能力，必须等 Session、Memory/Compact、Blueprint、Sandbox SPI、Coding routing、CLI attach/status 基础稳定后再决定是否新增模块。
5. CLI/TUI 保持 Java + JLine 路线，做 renderer abstraction 和交互增强；不引入 Ink/React 作为主栈，不完整内化 Coding Agent Harness。

## 2. 模块边界

| 模块 | 责任 | 当前规划结论 |
| --- | --- | --- |
| `ai4j` | 基础模型 SDK：Provider、Chat、Responses、Streaming、Tool Schema、RAG、MCP。 | 不承载 Agent 长程运行状态。 |
| `ai4j-agent` | 通用 Agent SDK：Agent、Runtime、Session、Memory、Compact、Event、Lifecycle、Blueprint、Sandbox Binding。 | 本轮增强的核心模块。 |
| `ai4j-extension-api` | 第三方扩展合同：Manifest、Tool、Command、Prompt、Skill、Guardrail、Lifecycle Hook、Memory/Compact/Sandbox provider。 | 公共插件合同放这里；运行编排不放这里。 |
| `ai4j-coding` | 官方 Coding Agent 能力包：file、shell、git、browser、project run、test runner、diff、artifact。 | 后续感知 sandbox；无 sandbox 时保持本地语义。 |
| `ai4j-cli` | 官方终端产品：`ai4j` 命令、TUI、slash commands、provider/model/session/plugin/sandbox UX。 | 靠近 Codex / Claude Code / OpenCode / Pi 的交互体验，但不双栈重写。 |
| `docs-site` | 开发者文档站。 | 每个能力必须讲清问题、最小例子、API/YAML、限制、FAQ、下一步。 |
| future optional `ai4j-agent-runner` | 远端 sandbox 内运行 Agent 的产品化入口。 | P5 再决策；不能阻塞 P0-P4。 |

## 3. P0：先完成 Agent SDK 内核

P0 的目标是让 `ai4j-agent` 有可长程运行、可恢复、可压缩、可被插件观察和治理的基础。

| 子任务 | 状态认知 | 目标 | 不做 |
| --- | --- | --- | --- |
| P0-A AgentSession runtime container | 已有任务与实现记录，生命周期仍在 review / closeout 队列中。 | session id、metadata、event log、snapshot、store、resume。 | 不接真实 sandbox。 |
| P0-B Memory / Compact / Context Projector | 已有任务与实现记录，生命周期仍在 review / closeout 队列中。 | `ContextBudget`、`ContextProjector`、`ContextReport`、`CompactPolicy`、`CompactResult`、session compact state。 | 不把 coding-specific checkpoint 全量上移。 |
| P0-C Plugin Lifecycle Hooks | 已有任务与实现记录，生命周期仍在 review / closeout 队列中。 | observation-first lifecycle hooks，覆盖 turn/model/tool/compact。 | 不强迫老插件升级，不先做可变拦截器。 |
| P0-D Approval / Permission Policy | 已有任务与实现记录，生命周期仍在 review / closeout 队列中。 | tool execution 前的 host-side policy gate。 | 不创建 VM/容器/远端执行环境。 |

P0 通过后，`AgentSession`、Memory/Compact、Hook、Permission Policy 会成为后续 Blueprint、Sandbox、Coding Agent、CLI 的基础，而不是各层各自发明状态模型。

## 4. P1：Agent Blueprint YAML

目标：让用户用一份 YAML 声明单 Agent，适合小白用户、团队模板、插件组合、CLI 运行和未来 FlowGram/低代码导出。

拆分：

| 子任务 | 范围 | 当前状态 / 下一步 |
| --- | --- | --- |
| P1-A schema/model/loader/validator | Java 8 DTO、YAML loader、validator、fixtures、稳定错误码。 | 已完成并合入过；Harness lifecycle 仍需后续确认/closeout。 |
| P1-B Blueprint to AgentFactory | host-supplied context -> `AgentBuilder` / `Agent`；不读 token、不读 profile secret、不创建真实 sandbox。 | 当前工作树：`.wt/p1b`，分支：`feature/agent-blueprint-factory`，应优先收尾。 |
| P1-C CLI `ai4j run agent.yaml` | 读取 YAML、校验、渲染错误、构造 Agent 并运行。 | P1-B 合并后再开。 |
| P1-D Team / Workflow Blueprint | 多 Agent、handoff、nodes/edges、FlowGram bridge。 | 后置；单 Agent 稳定前不做。 |

Blueprint 首版原则：

- `provider: openai-compatible` 是通用概念，不写任何中转平台名称作为 SDK 概念。
- Blueprint 不保存 provider token；token 来自 env、宿主配置或 secret store。
- `sandbox.enabled=true` 只能表达声明和 guard，真实 provider 绑定属于 P2/P3/P4。

## 5. P2：Sandbox SPI

目标：提供真实沙箱/远端执行环境的最小合同，让用户或第三方能接 CubeSandbox、Docker/K8s、E2B、公司内部沙箱等。

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

不做：

- 不官方维护多个 provider。
- 不把 sandbox 做成普通 `run_in_sandbox` tool。
- 不让 sandbox 自动绕过 approval / permission policy。

## 6. P3：`ai4j-coding` 接入 Sandbox Routing

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

## 7. P4：CLI/TUI 体验

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

## 8. P5：远端 Agent Runner 决策

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

## 9. R0 调研门禁

后续凡是“对标某个框架/产品”的实现，先做调研任务，不凭印象复刻。

| Gate | 对象 | 必须回答 | 输出 |
| --- | --- | --- | --- |
| R0-PI | Pi / pi-agent / pi-sdk 公开资料、示例和插件包 | 插件能贡献什么？如何安装和组合？TUI 哪些交互值得借鉴？哪些不适合 Java 单栈？ | task-local research digest |
| R0-CODING-CLI | Codex、Claude Code、OpenCode 等公开文档/博客/实测 | session、compact、permission/sandbox、tool call、diff 渲染和 slash command 的共同模式是什么？ | CLI/TUI research notes |
| R0-JAVA-SDK | Spring AI、LangChain4j、AgentScope Java 官方文档 | AI4J 必须覆盖哪些能力？应该避开什么复杂度？差异化如何表达？ | comparison matrix |
| R0-SANDBOX | CubeSandbox、E2B、Docker/K8s/internal sandbox 公开 API | 最小 sandbox contract 如何抽象？session/task 隔离和 artifact 回收怎么做？ | Sandbox SPI design input |

## 10. docs-site 同步要求

每个能力页必须能让用户“用起来”，不是只展示 roadmap。

页面结构要求：

1. 这个能力解决什么问题。
2. 最小可运行示例。
3. 核心 API / YAML 字段。
4. 与其他能力的关系。
5. 限制和不做什么。
6. 常见错误。
7. 下一步链接。

措辞要求：

- 不写“企业采用”。
- Sponsor 或中转平台只能作为 README 赞助信息，不作为 SDK 架构概念。
- 不展示 provider token。
- OpenAI-compatible 统一作为通用协议/接口兼容概念。

## 11. 当前仓库状态与实际下一步

根据本轮 Harness status 与 git worktree 诊断：

- 主仓：`G:\My_Project\java\ai4j-sdk`，当前分支 `main`。
- 未跟踪路径：`.wt/p1b/` 是 Git worktree，不应当作业务源文件直接提交到 main。
- 当前活跃实现：`G:\My_Project\java\ai4j-sdk\.wt\p1b`，分支 `feature/agent-blueprint-factory`。
- 当前任务：`MODULES/agent-runtime/2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210`。
- 下一步优先级：先收尾 P1-B，而不是继续新增总规划任务。

P1-B 收尾顺序：

1. 修复 `.wt/p1b/docs-site/docs/agent/agent-blueprint.md` 中的 markdown fence / P1-A wording 问题。
2. 复跑 targeted test：`mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintFactoryTest" -DskipTests=false -DfailIfNoTests=false test`。
3. 复跑 broad agent regression：`mvn -pl ai4j-agent -am -DskipTests=false test`。
4. docs-site 若变更，跑 `npm run build`。
5. 更新 P1-B task package、Regression SSoT / Cadence Ledger（如固定回归面改变）。
6. `harness status --json .` 无 failure 后提交、推送、PR、CI、merge、清理 worktree。

## 12. 不要做的事

- 不要重复新建总规划任务。
- 不要一次性实现 P0-P5。
- 不要新增 `AgentHost` / `Host Kernel` / `ai4j-runtime` 主概念。
- 不要过早新增 `ai4j-agent-runner` Maven 模块。
- 不要把 Pi 的实现细节当作已验证事实；先做 R0 调研。
- 不要完整内化 Coding Agent Harness 到 `ai4j-cli`。
- 不要把 sandbox 设计成普通 tool。
- 不要把 provider token 写入测试、fixture、文档或日志。
- 不要把任何 OpenAI-compatible 中转平台名称变成 SDK 概念名。
