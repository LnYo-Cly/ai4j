---
sidebar_position: 5
---

# Agent SDK R0 公开资料调研

这一页记录 AI4J Agent SDK 后续设计会参考哪些公开资料，以及这些资料最终转化成哪些工程约束。

它不是功能发布公告，也不表示下列能力都已经完成。每个实现状态仍以真实 API、测试、命令和对应文档页为准。

## 一句话结论

AI4J 不应该照搬 Spring AI、LangChain4j、AgentScope Java、Pi、Codex、Claude Code 或 OpenCode。更合理的方向是吸收这些项目已经证明有价值的模式，然后落到 AI4J 自己的模块边界里：

- `ai4j-agent`：Session、Memory、Compact、Blueprint、Permission、Plugin hook、Sandbox/Runner 抽象。
- `ai4j-extension-api`：插件 manifest、资源、capability、显式 enable/expose gate。
- `ai4j-coding`：workspace、shell、file、git、browser、project run/test 和 sandbox tool routing。
- `ai4j-cli`：全局 `ai4j` 入口、JLine TUI、slash command、provider/model/session/plugin/sandbox/memory UX。
- `docs-site`：只写真实 API 和真实命令，不再写不存在的示例。

## 公开资料来源

| 来源 | 主要参考页 | 用途 |
| --- | --- | --- |
| Pi coding-agent | [Pi coding-agent package docs](https://pt-act-pi-mono.mintlify.app/packages/coding-agent) | 参考 TUI、session、extensions、skills、tools、run modes 的组合方式。 |
| Pi SDK | [Pi SDK docs](https://pi.dev/docs/latest/sdk) | 参考 SDK 与 CLI/agent 产品面的关系。 |
| Codex CLI | [Codex CLI docs](https://developers.openai.com/codex/cli) | 参考终端 coding agent 的安装、TUI、模型切换、审批、安全和 MCP。 |
| Codex security | [Agent approvals and security](https://developers.openai.com/codex/agent-approvals-security) | 参考 sandbox 与 approval 的关系。 |
| Claude Code | [Claude Code overview](https://code.claude.com/docs/en/overview) | 参考 coding agent 的公开能力边界。 |
| Claude slash commands | [Slash commands](https://code.claude.com/docs/en/agent-sdk/slash-commands) | 参考 `/compact`、`/clear`、custom commands 等操作面。 |
| Claude hooks | [Hooks reference](https://code.claude.com/docs/en/hooks) | 参考 session、prompt、tool、permission、compact 等事件 hook。 |
| Claude memory | [Memory](https://code.claude.com/docs/en/memory) | 参考长期 context / instruction 文件和 `/memory`。 |
| OpenCode config | [OpenCode config](https://opencode.ai/docs/config/) | 参考文件化配置和项目/用户配置边界。 |
| OpenCode agents | [OpenCode agents](https://opencode.ai/docs/agents/) | 参考 primary agent 与 subagent 配置。 |
| OpenCode plugins | [OpenCode plugins](https://opencode.ai/docs/plugins/) | 参考插件 hook、custom tool、permission/session/tool/TUI 事件。 |
| OpenCode permissions | [OpenCode permissions](https://opencode.ai/docs/permissions/) | 参考 allow / ask / deny 权限策略。 |
| Spring AI | [ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)、[Advisors](https://docs.spring.io/spring-ai/reference/api/advisors.html) | 参考 Spring 生态里的 ChatClient、Advisor、memory/RAG 拦截模式。 |
| LangChain4j | [LangChain4j docs](https://docs.langchain4j.dev/) | 参考 Java AI Services、tools、memory、RAG 和 agents。 |
| AgentScope Java | [AgentScope Java intro](https://java.agentscope.io/v2/en/intro.html) | 参考 JVM agent framework、memory、HITL、多 agent 能力。 |
| E2B | [E2B docs](https://e2b.dev/docs) | 参考 sandbox lifecycle、filesystem、commands、terminal、snapshots。 |
| Daytona | [Daytona docs](https://www.daytona.io/docs/) | 参考 composable computers、process/code execution、PTY、VNC、snapshots。 |
| Modal | [Modal sandboxes](https://modal.com/docs/guide/sandboxes) | 参考隔离执行环境。 |
| CubeSandbox | [CubeSandbox](https://cubesandbox.com/) | 只作为待补充 sandbox 来源；当前公开资料不足以推导具体 API。 |

## Pi 给 AI4J 的启发

Pi 公开文档里最值得参考的是“一个 agent 产品面由多层能力组成”：

| Pi 公开能力 | AI4J 应转化成什么 |
| --- | --- |
| 全局 `pi` CLI | 后续一条命令安装后，终端输入 `ai4j` 进入交互式 agent。 |
| interactive TUI | `ai4j-cli` 继续用 JLine 做输入体验、流式渲染、命令面板和状态展示。 |
| session management | `AgentSession` 需要 event log、snapshot、resume，后续再考虑 branch/fork。 |
| extensions | Java 插件要能贡献 tool、command、hook、resource，而不是只做资源包。 |
| skills | Skill 更适合做 agent 使用说明/能力包，不等同于 Java runtime plugin。 |
| 多运行模式 | AI4J CLI 后续可区分 interactive、print/run、JSON event stream、ACP/SDK。 |

不能直接照搬的点：Pi 的具体 TUI renderer、extension isolation 和安全实现没有从当前公开页面完整确认，AI4J 不应凭猜测复制内部设计。

## Codex / Claude Code / OpenCode 给 AI4J 的启发

| 方向 | 公开模式 | AI4J 设计要求 |
| --- | --- | --- |
| CLI/TUI | Codex、Claude Code、OpenCode 都把终端交互作为主要入口 | `ai4j` 不应只是命令集合，应该进入可交互 session。 |
| Slash command | `/model`、`/compact`、`/memory`、custom commands 等成为可发现操作 | AI4J 要把 provider/model/session/memory/compact/sandbox/permissions 做成一等命令。 |
| Memory/Compact | Claude 文档把 memory 与 compact 作为长期 context 操作面 | AI4J compact 必须有报告和预算解释，不只是文本摘要。 |
| Hooks/Plugins | Claude hooks、OpenCode plugins 都覆盖 session/tool/permission/compact 等事件 | AI4J runtime hook 要有事件模型，并能与 permission/guardrail 联动。 |
| Permission | Codex approval/security、OpenCode allow/ask/deny 都把安全做成独立层 | 插件安装和 tool 注册不代表自动允许危险操作。 |
| Sandbox | Codex 把 sandbox 与 approval/security 放在同一安全模型里 | AI4J `/sandbox` 要与 `SandboxProvider`、tool routing、permission policy、session binding 联动。 |
| 文件化配置 | OpenCode agents/plugins/commands/providers 都可配置 | AI4J Blueprint 和 plugin manifest 应可读、可审查、可版本控制。 |

## Java AI SDK 对比结论

### Spring AI

Spring AI 的优势是 Spring 生态、ChatClient、Advisors、Boot integration 和成熟配置模式。AI4J 不应贬低它，也不应照搬 Spring 依赖。AI4J 的核心 Agent SDK 应保持普通 Java 可用，Spring Boot starter 只负责自动装配。

### LangChain4j

LangChain4j 覆盖 AI Services、tools、chat memory、RAG 和 agents。AI4J 不需要复制大工具箱路线，更应该强调：

- `AgentSession` 运行态容器。
- YAML Blueprint 声明式组装。
- 插件 enable/expose 和权限边界。
- Coding Agent CLI/TUI。
- Sandbox / Remote Runner 抽象。

### AgentScope Java

AgentScope Java 公开强调 ReAct、tool calling、memory、multi-agent、MCP/A2A 和 HITL。AI4J 可以借鉴两个点：

1. memory 至少区分 session-bound 短期上下文和跨 session 长期记忆。
2. HITL/approval 需要明确暂停点：工具执行前、工具执行后继续推理前。

## Sandbox provider 结论

E2B、Daytona、Modal 等资料说明，真实 sandbox 不是一个 boolean 开关，而是一组生命周期和资源能力：

- create / resume / destroy
- filesystem upload/download
- command/process/code execution
- terminal / PTY
- browser / preview / VNC
- snapshot / checkpoint
- artifact / logs
- quota / timeout / network policy

AI4J 应先抽象这些合同，再让插件或团队接入具体 provider：

| 抽象 | 用途 |
| --- | --- |
| `SandboxSpec` | 镜像、资源、workspace、网络、超时、标签。 |
| `SandboxProvider` | 创建、恢复、销毁 sandbox。 |
| `SandboxSession` | 保存非敏感 id、状态、workspace 和 lifecycle 摘要。 |
| `SandboxCommand` / `SandboxResult` | 命令执行请求和 stdout/stderr/exit code/artifact refs。 |
| `SandboxArtifact` | 文件、截图、日志、构建产物引用。 |
| `SandboxToolRouter` | 将 `ai4j-coding` 的 shell/file/browser/project 工具路由进 sandbox。 |
| `AgentRunnerClient` | 远端 agent runner 的 event stream、cancel、logs、artifact、checkpoint。 |

## 设计边界

这些结论会约束后续任务：

1. 不新增 `Host Kernel` / `AgentHost` 作为对外主概念。
2. 不新增核心 Agent Maven 模块；继续使用 `ai4j-agent`。
3. 不把 Harness 内化进 `ai4j-agent`；CLI 可做可选适配。
4. 不默认官方内置重型云 sandbox 平台；先做 SPI 和 fake provider。
5. 不把任何 OpenAI-compatible 中转平台名写成 SDK 架构概念。
6. 不写不存在的 Java API 示例。
7. 不把泄露源码作为设计依据。
8. 不为了像某个 JS/TS 项目而放弃 Java 8 / Maven / JLine 维护边界。

## 后续任务队列

| 顺序 | 任务 | 主模块 | 证据来源 |
| ---: | --- | --- | --- |
| 1 | `/memory` 与 `/compact` CLI UX | `ai4j-cli` + `ai4j-agent` | Claude `/compact`、`/memory`；Pi/OpenCode session/compaction 思路。 |
| 2 | 插件贡献契约扩展 | `ai4j-extension-api` + `ai4j-agent` | Pi extensions、OpenCode plugins、Claude hooks。 |
| 3 | Blueprint schema hardening | `ai4j-agent` + `docs-site` | OpenCode agents/config、Pi config/session、Claude subagents。 |
| 4 | Sandbox provider SPI hardening | `ai4j-agent` | E2B/Daytona/Modal lifecycle/filesystem/commands/snapshot。 |
| 5 | Coding sandbox tool routing | `ai4j-coding` | sandbox provider 必须承载 shell/file/browser/project tools。 |
| 6 | CLI `/sandbox` status/attach/disable | `ai4j-cli` | Codex approval/security 与 terminal UX。 |
| 7 | Remote Agent Runner SPI | `ai4j-agent` | 云端 Agent 产品需要 event stream、workspace、artifact、checkpoint。 |
| 8 | one-command install | `ai4j-cli` | Pi/Codex 都有明确全局 CLI 安装入口。 |
| 9 | docs-site completeness pass | `docs-site` | 竞品文档都按功能解释边界、配置、示例和排错。 |

完整的任务内 digest 保存在 Harness 任务包：

`coding-agent-harness/planning/modules/docs-site/tasks/2026-06-20-agent-sdk-r0-source-backed-research-digest-c11603e7/references/agent-sdk-r0-source-backed-research-digest.md`
