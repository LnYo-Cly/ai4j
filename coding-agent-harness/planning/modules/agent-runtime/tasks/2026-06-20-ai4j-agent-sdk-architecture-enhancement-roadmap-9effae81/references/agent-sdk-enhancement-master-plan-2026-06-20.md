# AI4J Agent SDK 增强实施总计划

> 记录日期：2026-06-20  
> 任务：`MODULES/agent-runtime/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81`  
> 性质：Harness 规划记录；不改生产代码；后续每个实现切片必须单独开 task / worktree / PR。  

## 1. 一句话目标

把 AI4J 做成一个 **Java 开发者低成本接入 AI / Agent 的 SDK**，并逐步提供接近 Codex、Claude Code、OpenCode、Pi 那类终端交互体验的 `ai4j` Coding Agent CLI/TUI。

AI4J 不和 Spring AI、LangChain4j、AgentScope Java 拼“大而全”。作为个人项目，核心竞争点应是：

1. **接入成本低**：Java 项目用更少概念跑起来。
2. **组装成本低**：Agent、Memory、Compact、Tool、RAG、Workflow、Plugin、Sandbox 可以按需组合。
3. **插件生态清楚**：第三方开发者可独立写插件，用户可安装、启用、组合插件。
4. **声明式 Agent**：YAML Blueprint 让小白用户、CLI、FlowGram/低代码和模板市场共享一份 Agent 定义。
5. **终端产品体验好**：`ai4j` 命令进入交互式 coding agent，支持 provider/model/session/plugin/sandbox/memory 等一等命令。
6. **可选云端化能力**：通过 Sandbox / Remote Runner SPI 帮开发者快速搭建类似豆包、点点类的远端 Agent 产品，但 SDK 本身不绑定某个云平台。

## 2. 固定边界

| 决策 | 结论 |
| --- | --- |
| 核心 Agent 模块 | 继续使用 `ai4j-agent`，不新增 `AgentHost`、`Host Kernel`、`ai4j-runtime` 作为对外主概念。 |
| Maven 拆分 | 不过度拆分。除非后续已有稳定独立产品边界，否则不新增核心 Maven 模块。 |
| Sandbox 命名 | 没有 sandbox 时就是宿主本地执行；不再命名为 `local sandbox`。 |
| 中转平台 | 统一写作 `openai-compatible`；不把任何具体平台名写成 SDK 架构概念。 |
| Harness | 不完整内化进 `ai4j-agent`；可在 `ai4j-cli` 做轻量检测、状态展示和命令桥接。 |
| docs-site | 只写真实 API 和真实命令，不写不存在的 fluent 示例。 |
| 安全 | 不提交 provider token、sandbox secret、本机绝对路径或用户私密上下文。 |

## 3. 分层架构

| 层 | 模块 | 主要职责 |
| --- | --- | --- |
| Core AI SDK | `ai4j` | Provider、Chat/Responses、RAG、MCP、Vector、Image/Audio/Realtime。 |
| Agent SDK | `ai4j-agent` | Agent runtime、Session、Memory、Compact、Workflow、Trace、Subagent、Permission、Blueprint、Sandbox/Runner 抽象。 |
| Extension Contract | `ai4j-extension-api` | 插件 manifest、ServiceLoader、resource、enable/expose gate、贡献点声明。 |
| Official Plugins | `ai4j-plugin-*` | 官方样例和常用插件，不污染核心模块。 |
| Coding Runtime | `ai4j-coding` | 文件、Shell、Git、Patch、浏览器、项目运行/测试、coding outer loop、sandbox routing。 |
| CLI/TUI | `ai4j-cli` | `ai4j` 命令、JLine TUI、slash command、provider/model 切换、ACP、session/runtime UI。 |
| Docs | `docs-site` | 教程、API 说明、命令参考、插件作者指南、路线图。 |

## 4. 能力路线图

### R0：source-backed 调研门禁

目的：避免凭印象复刻 Pi、Codex、Claude Code、OpenCode、Spring AI、LangChain4j、AgentScope Java。

要记录：

- Pi 插件生态和 TUI 交互：插件贡献点、安装/组装方式、TUI provider/model/command/rendering 思路。
- Codex / Claude Code / OpenCode：session、memory、compact、sandbox、approval、CLI/TUI 的公开行为和公开分析。
- Java AI SDK：Spring AI、LangChain4j、AgentScope Java 的真实边界、优势和 AI4J 可差异化点。
- Sandbox provider：CubeSandbox、E2B、Docker/K8s、VM/microVM 等可接入模式。

产物：`references/research-digest-*.md`，只记录公开资料结论和链接，不复制泄露源码。

### P0：AgentSession / Memory / Compact 内核

目标：`AgentSession` 成为长程 Agent 运行态容器，而不是一次调用的临时对象。

核心对象：

- `AgentSession`：session id、owner、workspace、metadata、event log、snapshot。
- `AgentMemory` / MemoryStore：短期上下文、长期记忆、工具结果摘要。
- `ContextBudget` / `ContextProjector`：按预算把 session、memory、RAG、tool result 投影给模型。
- `CompactPolicy` / `CompactResult` / `CompactReport`：结构化压缩和可诊断报告。

验收：

- 不依赖具体 provider。
- 不保存 token、sandbox secret。
- CLI/TUI 能展示 memory/compact 健康摘要。
- docs-site 给出真实 Java 示例与限制。

### P1：YAML Agent Blueprint

目标：让用户能用一份 YAML 声明单 Agent 组装。

首版范围：

- schema/model/loader/validator。
- `AgentFactory` 把 YAML 映射为真实 `Agent`。
- CLI 支持 `ai4j run agent.yaml`。
- schema 可导出，方便 IDE 补全和文档引用。

不做：

- 不执行任意 Java 代码。
- 不在 YAML 写 token。
- 不急于做 Team/Workflow 全量 DSL。

### P2：插件生态

目标：第三方开发者可以独立发布插件，用户可以安装、查看、启用、禁用和组合插件。

贡献点：

| 类型 | 作用 | 首版策略 |
| --- | --- | --- |
| Tool | 暴露 Agent 可调用动作 | 必须显式 enable/expose。 |
| Command | 增加 CLI slash command 或 host command | 先支持文本命令和 help，不急于开放 TUI render plugin。 |
| Prompt / Skill | 提供提示词、操作模板、行为说明 | 作为 extension resources 管理，Blueprint 可引用。 |
| Lifecycle Hook | session/turn/tool/model/compact 生命周期观察 | observation-first，避免老插件强制升级。 |
| Guardrail | 输入、输出、tool call 检查 | 先定义 contract 和 decision 结果。 |
| Memory / Compact Provider | 接入外部 memory store 或 compact 策略 | 在 P0 稳定后推进。 |
| Sandbox Provider | 接入真实 sandbox / VM / container | 在 P4/P5 基础稳定后推进。 |

安全默认：

- 安装不等于暴露危险能力。
- manifest 必须声明能力、权限、资源、版本兼容范围。
- Shell/File/Browser/Sandbox 类能力默认需要明确授权。

### P3：Sandbox SPI

目标：给 Java 应用和 CLI 提供真实执行隔离接入点，但不绑定具体云平台。

两种模式：

| 模式 | Agent 在哪里跑 | 工具在哪里执行 | 用途 |
| --- | --- | --- | --- |
| Host-driven sandbox tools | 宿主 Java/CLI 进程 | 外部 sandbox / VM / container | 现有 Java 应用安全执行工具。 |
| Remote Agent Runner | 远端隔离环境 | 同一个远端环境 | 做云端 Agent 产品。 |

核心抽象：

- `SandboxSpec`
- `SandboxProvider`
- `SandboxSession`
- `SandboxCommand`
- `SandboxResult`
- `SandboxArtifact`
- `SandboxEvent`
- `SandboxBinding`

默认验证：fake provider / fake session / local fixture；真实 provider 只作为 opt-in 示例。

### P4：Coding Runtime sandbox routing

目标：`ai4j-coding` 的 shell/file/git/browser/project run/test 能按策略路由到 sandbox。

原则：

- Permission Policy 仍然生效；进入 sandbox 不代表自动放行危险命令。
- metadata-only attach 不能静默回退本地执行。
- 工具结果要保留 stdout/stderr/exit code/artifact refs。
- session event log 要记录 sandbox routing 决策和非敏感摘要。

### P5：Remote Agent Runner SPI

目标：帮助开发者快速做“豆包/点点类云端 Agent 产品”的运行端合同。

先做 contract，不先做重型云平台：

- `AgentRunnerSpec`
- `AgentRunnerClient`
- event stream
- workspace file ops
- shell/project run/test
- browser/screenshot
- artifact collection
- checkpoint/freeze/resume
- cancel/timeout/quota

推进门禁：

1. P0 Session/Memory/Compact 稳定。
2. P1 Blueprint 可生成 Agent。
3. P3 Sandbox SPI 有 fake provider 和 session binding。
4. P4 Coding tools 可 sandbox routing。
5. CLI 有 `/sandbox status/attach/disable` 等基础 UX。

### P6：CLI/TUI 产品体验

目标：用户安装后终端输入 `ai4j`，进入接近 Codex / Claude Code / OpenCode / Pi 的 coding agent 体验。

技术路线：

- 继续 Java + JLine。
- 不引入 Ink/React 作为主栈。
- 不自研完整 terminal renderer。
- 做 renderer abstraction、view model、parser tests 和可验证交互。

重点命令：

- `/provider`
- `/model`
- `/session`
- `/memory`
- `/compact`
- `/compacts`
- `/checkpoint`
- `/plugins` / `/extension`
- `/sandbox`
- `/permissions`
- `/help`

体验目标：

- provider/model/profile 切换清晰。
- slash palette 可发现。
- markdown/code/diff/tool call/approval/error 分块渲染。
- memory/compact/sandbox/session 状态可一眼读懂。
- one-command install 后有全局 `ai4j` 入口。

### P7：docs-site 完整化

目标：docs-site 从 roadmap 变成“开发者照着能用”的文档。

每个能力页统一结构：

1. 解决什么问题。
2. 什么时候该用，什么时候不该用。
3. 最小可运行 Java/YAML/CLI 示例。
4. 核心 API / 字段解释。
5. 与其他模块关系。
6. 安全边界和限制。
7. 常见错误和排查。
8. 下一步链接。

禁止：

- 不写不存在 API。
- 不用 roadmap 代替教程。
- 不出现生硬的“企业采用”式措辞。
- 不把具体中转平台名写成 SDK 架构概念。

## 5. 推荐实施顺序

| 顺序 | 任务 | 主模块 | 说明 |
| ---: | --- | --- | --- |
| 0 | Backlog / review queue 收敛 | Harness | 当前多项已合并实现处于 `ready-to-confirm`，先区分生命周期债务和真实缺口。 |
| 1 | R0 source-backed research | Harness / docs | Pi、Codex、Claude Code、OpenCode、Java SDK、Sandbox provider 调研。 |
| 2 | `/memory` + compact CLI UX | `ai4j-cli` | 已规划；补齐 memory/compact 一等诊断入口。 |
| 3 | One-command install prototype | `ai4j-cli` | 让用户安装后输入 `ai4j` 即可进入 CLI/TUI。 |
| 4 | CLI/TUI interaction polish | `ai4j-cli` | provider/model/session/plugin/sandbox 状态栏和渲染。 |
| 5 | docs-site real API completeness | `docs-site` | 把每个能力页讲清楚并和真实 API 对齐。 |
| 6 | Sandbox provider contribution contract | `ai4j-extension-api` + `ai4j-agent` | 让第三方 sandbox provider 插件可接入。 |
| 7 | Remote Runner SPI hardening | `ai4j-agent` | fake runner tests + event stream fixture。 |
| 8 | FlowGram / Blueprint bridge | `ai4j-flowgram-*` + `docs-site` | 在单 Agent Blueprint 稳定后再做低代码导出/导入。 |

## 6. 每个实现任务的固定门禁

1. 新建或复用 Harness task package。
2. 使用 dedicated worktree / branch。
3. 保持 Java 8 兼容。
4. 不提交 token / secret / local-only config。
5. 新增 public API 必须有 owner-module tests。
6. CLI/TUI 改动覆盖 parser/view model/runtime dispatch/ACP 或文档一致性。
7. docs-site 改动运行 `npm --prefix docs-site run build`，必要时加 `typecheck`。
8. 新增固定回归面时同步 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`。
9. 收口必须更新 task-local `review.md`、`walkthrough.md` 和 progress evidence。

## 7. 当前立即下一步

从最新 `origin/dev` 看，P0/P1/P2/P3/P4 多个基础能力已经存在，下一步不应重复旧规划。推荐顺序：

1. 先执行 `/memory` + compact command UX 任务，让 memory/compact 成为 CLI 一等体验入口。
2. 并行准备 R0 调研 digest，尤其是 Pi 插件/TUI 和 Codex/Claude/OpenCode 的公开设计分析。
3. 然后进入 one-command install / CLI/TUI polish。
4. 同步做 docs-site completeness pass，确保每个真实能力都能被用户看懂、用起来。

本文件是规划记录，不代表已实现上述所有能力。
