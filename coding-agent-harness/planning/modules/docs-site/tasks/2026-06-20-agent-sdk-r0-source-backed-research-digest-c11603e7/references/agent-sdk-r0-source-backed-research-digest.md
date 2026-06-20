# AI4J Agent SDK R0 source-backed research digest

> 记录日期：2026-06-20
> 任务：`MODULES/docs-site/2026-06-20-agent-sdk-r0-source-backed-research-digest-c11603e7`
> 目的：把 Pi、Codex、Claude Code、OpenCode、Java AI SDK 和 sandbox provider 的公开资料结论转化为 AI4J 后续设计约束。
> 重要边界：本文件是资料 digest，不代表 AI4J 已实现全部能力；实现仍以代码、测试和 docs-site 真实 API 为准。

## 1. 结论摘要

AI4J 的下一阶段不应从“像谁”开始，而应从这些公开产品暴露出的稳定模式中抽象：

| 观察对象 | 稳定模式 | 对 AI4J 的落地要求 |
| --- | --- | --- |
| Pi | CLI/TUI、SDK、session、extensions、skills、tools、run modes 是一组可组合面 | `ai4j-cli` 需要一等交互入口；插件不能只停留在资源包，必须能贡献 tool/command/hook。 |
| Codex CLI | 终端 coding agent 需要安装、TUI、模型切换、审批、安全沙箱、子代理、MCP 等闭环 | AI4J CLI 要把 provider/model/permissions/sandbox/memory 做成可发现命令，而不是隐藏配置。 |
| Claude Code | slash commands、memory、hooks、subagents、settings/permissions 构成长期可维护的 agent 操作系统 | AI4J 的 memory/compact、hook、subagent、permission 必须是稳定运行时语义，不只是 UI 功能。 |
| OpenCode | 配置目录、agents、plugins、commands、permissions、providers 都是文件化/可扩展能力 | AI4J 的 YAML Blueprint 和插件 manifest 应可读、可审查、可版本控制。 |
| Spring AI / LangChain4j / AgentScope Java | Java AI SDK 竞争者覆盖 ChatClient、Advisor、AI Services、RAG、Memory、Tools、multi-agent | AI4J 的差异化不是“大而全”，而是更低接入成本、更清楚的 Agent 组装和 coding-agent 产品体验。 |
| E2B / Daytona / Modal / CubeSandbox | sandbox provider 强调 lifecycle、filesystem、commands、snapshots、terminal/browser、SDK/API | AI4J 应提供 `SandboxProvider` / `SandboxSession` / `AgentRunnerClient` 抽象和 fake provider 测试，不默认绑定云平台。 |

## 2. Pi / pi-agent / pi-sdk 观察

### 2.1 可信来源

- Pi coding-agent package docs：<https://pt-act-pi-mono.mintlify.app/packages/coding-agent>
- Pi SDK docs：<https://pi.dev/docs/latest/sdk>

### 2.2 已确认能力

公开文档显示，Pi coding-agent 是一个 terminal-based coding agent，包含 interactive TUI、session management、extension system、内置读写编辑执行等工具、多种运行模式，并提供 SDK 用法。

可确认的设计点：

| 设计点 | 公开资料依据 | AI4J 启发 |
| --- | --- | --- |
| 全局 CLI | `npm install -g ...` 后运行 `pi` | AI4J 应有 one-command install 后的全局 `ai4j`。 |
| TUI | 文档明确 interactive mode 有 editor、autocomplete、streaming responses | AI4J JLine TUI 应先做输入体验、流式渲染和命令可发现性。 |
| Session | JSONL session、tree structure、branching、resume、auto-save | `AgentSession` 要有 event log、snapshot、branch/resume 的长期方向。 |
| Extension | TypeScript extension 可注册 tool、command、keyboard shortcut、event hook、UI | AI4J 插件贡献点要分层，至少 Tool / Command / Hook / Resource。 |
| Skills | 支持 Agent Skills 风格 `SKILL.md` | AI4J 可保留 Skill 作为 agent 使用说明/能力包，不必把它等同于 Java plugin。 |
| 多模式 | interactive、print、JSON、RPC、SDK | AI4J CLI 后续可分 interactive、print/run、JSON event stream、SDK/ACP。 |
| Context files | 自动加载 AGENTS.md / CLAUDE.md | AI4J CLI 应读取 AGENTS.md，并支持 harness 项目的任务上下文。 |

### 2.3 对 AI4J 的设计约束

1. 插件生态不要只做“能安装 jar”。用户需要能看到插件贡献了哪些 tool、command、hook、skill/resource。
2. TUI 扩展要谨慎。Pi 文档有 UI extension，但 AI4J 维护成本更高，首版只开放 CLI command plugin 和 runtime hooks 更稳。
3. Session branching 是高级能力。AI4J 首先要把 `AgentSession` event log、snapshot、compact report 做稳，再做 fork/branch。
4. SDK 与 CLI 共享 session/runtime 模型。不要让 CLI 自己维护一套与 `ai4j-agent` 分裂的会话语义。

### 2.4 Source gap

Pi 公开文档可支撑“插件贡献点丰富、TUI/SDK/session 组合完整”的判断；但它的具体内部 TUI renderer、extension isolation、安全策略不能从当前公开页面完整确认。AI4J 后续不能照搬未验证内部实现。

## 3. Codex CLI 观察

### 3.1 可信来源

- Codex CLI docs：<https://developers.openai.com/codex/cli>
- Codex GitHub：<https://github.com/openai/codex>
- Agent approvals & security：<https://developers.openai.com/codex/agent-approvals-security>

### 3.2 已确认能力

Codex CLI 官方文档把它定义为在本地终端运行的 coding agent，可读取、修改和运行当前目录代码。安装方式包含 standalone installer、npm、Homebrew 和 release binary；运行入口是 `codex`。文档还把交互式 TUI、`/model`、image input、local code review、subagents、web search、MCP、approval modes 等作为 CLI 能力入口。

对 AI4J 的约束：

| Codex 公开模式 | AI4J 应落地成 |
| --- | --- |
| 一个终端入口启动交互式 agent | `ai4j` 默认进入 chat/coding TUI，而不是只显示帮助。 |
| approval/security 是独立概念 | `AgentPermissionPolicy` 与 `/permissions` 应成为 SDK/CLI 共同语义。 |
| sandbox 与审批配套 | `/sandbox` 不能只是状态展示，要与 tool routing、permission decision、session binding 联动。 |
| subagents 是并行复杂任务入口 | AI4J 的 subagent/team 不应只在文档里，要能被 CLI 或 Blueprint 调用。 |
| MCP 是扩展工具边界 | AI4J 插件与 MCP 要分清：插件是本地能力包，MCP 是外部工具协议。 |

### 3.3 不应照搬的点

- Codex 是 Rust CLI，AI4J 当前是 Java 8 Maven monorepo；不应为了像 Codex 而重写技术栈。
- Codex 的具体 sandbox 实现与平台能力不可直接作为 AI4J 默认设计。AI4J 应先做抽象和 fake provider。

## 4. Claude Code 观察

### 4.1 可信来源

- Overview：<https://code.claude.com/docs/en/overview>
- Slash commands in SDK：<https://code.claude.com/docs/en/agent-sdk/slash-commands>
- Hooks reference：<https://code.claude.com/docs/en/hooks>
- Memory：<https://code.claude.com/docs/en/memory>
- Subagents：<https://code.claude.com/docs/en/sub-agents>

### 4.2 已确认能力

Claude Code 公开文档把它描述为能读代码、改文件、运行命令并集成开发工具的 agentic coding tool。它公开说明了 slash commands、`/compact`、`/clear`、custom commands、hooks、CLAUDE.md/auto memory、subagents 等操作面。

关键启发：

| Claude Code 公开模式 | AI4J 设计含义 |
| --- | --- |
| `/compact` 返回 compaction boundary 和 metadata | AI4J compact 不应只返回一段摘要，应有 `CompactReport` / `ContextReport`。 |
| CLAUDE.md 是 context，不是强制配置 | AI4J 的 AGENTS/skill/prompt 也应和 permission/hook enforcement 分离。 |
| Hooks 覆盖 session、prompt、tool、permission、compact、worktree 等事件 | AI4J runtime hook 需要事件模型，不能只有 before/after run 两个点。 |
| Subagents 有独立描述、工具访问和管理界面 | AI4J subagent/team 应有可声明的权限、模型和上下文边界。 |
| `/memory` 可查看和编辑已加载 memory/instruction 文件 | AI4J CLI 的 `/memory` 应展示 loaded context、session memory、compact 状态。 |

### 4.3 对 AI4J 的约束

1. Memory/Compact 是 Agent runtime 能力，不只是 CLI 命令。
2. Hook 输出可影响权限决策；AI4J 的 hook/guardrail/permission 要能串联。
3. Project instructions 与 enforced policy 分离：AGENTS.md、skills、prompt templates 只提供上下文；危险操作仍由 permission/sandbox 控制。
4. Subagent 不是简单多线程；它需要任务描述、工具集、memory/context、handoff evidence。

## 5. OpenCode 观察

### 5.1 可信来源

- Config：<https://opencode.ai/docs/config/>
- Agents：<https://opencode.ai/docs/agents/>
- Plugins：<https://opencode.ai/docs/plugins/>
- Permissions：<https://opencode.ai/docs/permissions/>
- Commands：<https://opencode.ai/docs/commands/>
- Providers：<https://opencode.ai/docs/providers/>

### 5.2 已确认能力

OpenCode 文档显示它把 config、agents、commands、modes、plugins 都放在标准配置目录下；agents 分 primary agents 与 subagents；plugins 可从本地 JS/TS 文件或 npm 加载，能 hook 事件、增加 custom tools、处理 permission/session/tool/TUI 等事件；permission 规则解析为 allow/ask/deny。

对 AI4J 的启发：

| OpenCode 模式 | AI4J 落地 |
| --- | --- |
| `.opencode` / global config / project config | AI4J 应支持 user config + project config + Blueprint，且优先级清楚。 |
| Agents 可配置 prompt、model、tool access | AI4J Blueprint 需要 model/profile、instructions、tools、permissions。 |
| Plugins 支持 npm 与本地文件 | AI4J Java 插件应支持 jar/classpath；CLI 可后续支持本地目录插件索引。 |
| Permission allow/ask/deny | AI4J 权限模型已应使用类似三态策略。 |
| Compaction hook | AI4J compact 要暴露 hook 或 projector 扩展点。 |

### 5.3 对 AI4J 的约束

OpenCode 的强项是“文件化配置 + 插件事件 + 权限规则”。AI4J 不能只把插件做成 ServiceLoader 自动注册；必须让用户能在项目配置或 Blueprint 中明确启用、禁用和限制插件能力。

## 6. Java AI SDK 对比

### 6.1 Spring AI

可信来源：

- ChatClient：<https://docs.spring.io/spring-ai/reference/api/chatclient.html>
- Advisors：<https://docs.spring.io/spring-ai/reference/api/advisors.html>

已确认：Spring AI 提供 ChatClient fluent API，并通过 Advisors API 把 memory、RAG 等生成式 AI pattern 做成可复用拦截/增强链。它天然适合 Spring Boot 生态和企业级 Spring 应用。

AI4J 差异化：

- 不和 Spring AI 拼 Spring 生态覆盖，而是降低普通 Java / CLI / Agent SDK 的组装成本。
- `ai4j-spring-boot-starter` 可以借鉴 autoconfig 体验，但核心 Agent 能力不应依赖 Spring。
- AI4J 的 docs-site 必须承认 Spring AI 在 Spring 生态中的优势，不要写贬低式对比。

### 6.2 LangChain4j

可信来源：

- LangChain4j docs：<https://docs.langchain4j.dev/>
- Introduction：<https://docs.langchain4j.dev/intro/>
- AI Services docs source：<https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/ai-services.md>
- RAG docs source：<https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/rag.md>

已确认：LangChain4j 覆盖 prompt templating、chat memory、output parsing、Agents、Tools、RAG；AI Services 是高层 API，支持 chat memory、tools 和 RAG。

AI4J 差异化：

- 不复制 LangChain4j 的大工具箱路线。
- 强调 AgentSession、Blueprint、插件启用/权限、Coding Agent CLI/TUI、sandbox/runner 这些更贴近 agent 产品的能力。
- 对 Java API 示例必须保持真实，不强行写不存在的 fluent API。

### 6.3 AgentScope Java

可信来源：

- AgentScope Java intro：<https://java.agentscope.io/v2/en/intro.html>
- GitHub：<https://github.com/agentscope-ai/agentscope-java>
- Memory docs：<https://github.com/agentscope-ai/agentscope-java/blob/main/docs/en/task/memory.md>
- HITL docs：<https://github.com/agentscope-ai/agentscope-java/blob/master/docs/en/task/hitl.md>

已确认：AgentScope Java 是 JVM agent framework，公开强调 ReAct reasoning、tool calling、memory management、multi-agent collaboration、MCP/A2A 等能力；memory 文档区分 short-term / long-term memory；HITL 文档描述 reasoning / acting 两个暂停点。

AI4J 差异化：

- AgentScope Java 的定位更接近完整 agent framework/platform；AI4J 应保持更轻、更易接入。
- 可以借鉴 HITL pause points：AI4J approval/permission 应支持“模型决定工具后、执行前”和“工具执行后、继续推理前”的介入点。
- memory 设计应至少区分 session-bound 短期 memory 和跨 session 长期 memory。

## 7. Sandbox provider 观察

### 7.1 可信来源

- E2B docs：<https://e2b.dev/docs>
- Daytona docs：<https://www.daytona.io/docs/>
- Modal sandboxes：<https://modal.com/docs/guide/sandboxes>
- CubeSandbox docs/home：<https://cubesandbox.com/>

### 7.2 已确认模式

E2B 文档明确提供 isolated sandboxes，让 agents 安全执行代码、处理数据、运行工具，并包含 sandbox lifecycle、persistence、snapshots、filesystem、commands、interactive terminal、SSH 等主题。Daytona 文档把 sandbox 描述为隔离、专用 kernel/filesystem/network stack 的 composable computers，并提供 SDK/API/CLI、filesystem、process/code execution、PTY、VNC、preview、snapshots 等能力。Modal 提供 sandboxes 用于隔离执行。CubeSandbox 当前公开抓取到的信息较少，只能作为待补充来源。

AI4J 应抽象出的最小合同：

| 抽象 | 必要性 |
| --- | --- |
| `SandboxSpec` | 描述镜像、资源、workspace、网络、环境变量引用、超时。 |
| `SandboxProvider` | 创建、恢复、销毁、列出 sandbox。 |
| `SandboxSession` | 保存非敏感 id、状态、workspace、lifecycle 摘要。 |
| `SandboxCommand` / `SandboxResult` | shell/process/code execution 的请求和结果。 |
| `SandboxFileSystem` | 读写、上传、下载、watch、artifact refs。 |
| `SandboxTerminal` | PTY/interactive terminal 或其能力声明。 |
| `SandboxSnapshot` | snapshot/checkpoint/resume 能力。 |
| `SandboxToolRouter` | 把 `ai4j-coding` 工具路由到 sandbox。 |
| `AgentRunnerClient` | 远端 agent runner 的 event stream、cancel、logs、artifacts、checkpoint。 |

### 7.3 隔离策略

| 策略 | 建议用途 |
| --- | --- |
| `PER_TASK_EPHEMERAL` | 高风险、一次性工具执行，结束销毁。 |
| `PER_SESSION` | 云端 Agent 产品默认，一个会话一个隔离环境。 |
| `PER_USER_POOL` | 低风险和强 reset 的受控环境；不作为首版推荐。 |

## 8. AI4J 后续任务拆解建议

| 顺序 | 切片 | 主模块 | 依据 |
| ---: | --- | --- | --- |
| 1 | Memory / Compact CLI UX | `ai4j-cli` + `ai4j-agent` | Claude `/compact`、`/memory` 和 Pi/OpenCode compaction 思路。 |
| 2 | Plugin contribution contract expansion | `ai4j-extension-api` + `ai4j-agent` | Pi/OpenCode 插件能贡献 tool/command/hook，AI4J 需 Java 化。 |
| 3 | Blueprint schema hardening | `ai4j-agent` + `docs-site` | OpenCode/Pi 配置化 agent、Claude subagent config 启发。 |
| 4 | Sandbox provider SPI hardening | `ai4j-agent` | E2B/Daytona/Modal 均围绕 lifecycle/filesystem/commands/snapshot。 |
| 5 | Coding sandbox tool routing | `ai4j-coding` | sandbox provider 必须能承载 shell/file/browser/project tools。 |
| 6 | CLI `/sandbox` status/attach/disable | `ai4j-cli` | Codex approvals/security + Pi/Codex/OpenCode CLI 产品面。 |
| 7 | Remote Agent Runner SPI | `ai4j-agent` | 云端 Agent 产品需要 runner event stream、workspace、artifact、checkpoint。 |
| 8 | One-command install | `ai4j-cli` | Pi/Codex 都有明确全局 CLI 安装入口。 |
| 9 | docs-site completeness pass | `docs-site` | 对比资料证明文档必须解释功能边界、配置、真实示例和排错。 |

## 9. 不能从资料中直接推出的事项

- 不能声称 AI4J 必须使用 TypeScript/Ink；Pi/OpenCode 的 TypeScript 生态不等于 Java 项目的最佳方案。
- 不能声称真实 sandbox provider 必须官方内置；资料只证明 sandbox 抽象有价值。
- 不能声称 AI4J 已达到 Codex/Claude/OpenCode 体验；当前只能作为路线图目标。
- 不能使用泄露源码作为设计依据；Claude Code 只引用公开文档和公开网页。
- 不能把某个 OpenAI-compatible 中转平台写成架构概念。

## 10. 后续文档写作规则

1. docs-site 能力页必须链接真实 API、类名、命令或测试。
2. 竞品对比要写“AI4J 的差异化位置”，不要写贬低竞品的大话。
3. 插件、sandbox、runner 的文档必须分“已实现 / 规划中 / 不做”三类。
4. 每个 CLI/TUI 命令页必须说明是否当前可用、是否需要 provider key、是否会执行 shell/file/browser 等危险动作。
5. 任何 source-backed 结论如果来源不足，必须显式标注 source gap。
