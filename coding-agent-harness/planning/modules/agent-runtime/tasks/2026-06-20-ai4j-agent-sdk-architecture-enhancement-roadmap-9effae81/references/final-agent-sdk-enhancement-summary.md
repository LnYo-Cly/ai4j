# AI4J Agent SDK 增强方向最终摘要

> 记录日期：2026-06-20  
> 来源：用户围绕 `ai4j-agent`、插件生态、Coding Agent CLI/TUI、Sandbox、Memory/Compact、YAML Agent 的连续设计讨论。  
> 任务：`MODULES/agent-runtime/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81`

## 1. 最终目标

AI4J 不应和 Spring AI、LangChain4j、AgentScope Java 比“大而全”。它作为个人项目，更应该把优势放在：

1. Java 接入 AI / Agent 的成本更低。
2. 常见 Agent 能力更容易组装：memory、compact、tools、RAG、workflow、permission、sandbox、plugin。
3. 提供从 Java SDK 到 `ai4j` Coding Agent CLI/TUI 的连续体验。
4. 允许第三方开发者编写插件，使用者安装和组合插件，形成可扩展生态。
5. docs-site 必须讲清每个真实能力，不写不存在的 API 示例。

## 2. 模块边界结论

不新增 `Host Kernel`、`AgentHost` 或新的核心 Agent Maven 模块。继续使用现有模块：

| 能力 | 放置位置 | 说明 |
| --- | --- | --- |
| Agent runtime、session、memory、compact、workflow、trace、subagent、blueprint、sandbox/runner 抽象 | `ai4j-agent` | Agent SDK 核心层 |
| Provider、Chat/Responses、RAG、MCP、Vector、Image/Audio/Realtime | `ai4j` | 基础模型与工具 SDK |
| 插件 manifest、ServiceLoader、资源声明、enable/expose gate | `ai4j-extension-api` | 第三方插件公共契约 |
| 官方示例插件 | `ai4j-plugin-ask-user` 等独立模块 | 给插件作者参考 |
| workspace、shell、file、patch、browser、coding outer loop、sandbox tool routing | `ai4j-coding` | Coding Agent 场景能力 |
| TUI、`ai4j` 命令、/commands、provider/model 切换、ACP、安装入口 | `ai4j-cli` | 终端产品体验 |
| 文档、教程、对比、Cookbook | `docs-site` | 用户可见说明，不作为生产逻辑 SSoT |

关键理由：拆太多 Maven 会增加维护成本；用户和所有开发者都应面对同一套 `ai4j-agent` 核心概念，不再区分“普通用户/进阶用户”的模块入口。

## 3. AgentSession / Memory / Compact 方向

`AgentSession` 要成为真实运行态容器，而不是一次调用的临时对象。后续设计应参考 Codex、Claude Code 等优秀 coding agent 的上下文管理思想，但只参考公开分析和可验证行为，不依赖或复制泄露源码。

建议能力：

| 能力 | 目标 |
| --- | --- |
| Session metadata | session id、title、labels、created/updated、runner/sandbox 摘要 |
| Event log | 用户消息、模型消息、tool call、approval、compact、error、snapshot event |
| Snapshot/restore | 可恢复 session 的稳定状态，不保存 token/secrets |
| Memory store | 短期对话、长期记忆、工具结果摘要、可替换存储 |
| Compact policy | token threshold、手动 compact、run boundary compact、失败回滚 |
| Context projector | 按预算把 memory、RAG、tool results、session state 投影给模型 |
| Compact report | 返回 compact 前后预算、保留/丢弃内容、摘要和可诊断信息 |

设计原则：

- compact 不只是“压缩文本”，而是可解释的上下文投影和状态保留。
- memory 与 compact 不应强绑定某个模型 provider。
- 每次自动 compact 都应能生成报告，便于 CLI/TUI 和 docs-site 展示。
- 不提交 provider token，不把本地路径、sandbox 连接串等敏感信息写入 snapshot。

## 4. YAML Agent / Blueprint 方向

AI4J 应支持用 YAML 声明一个 Agent，降低小白用户和非 Java 代码路径的组装成本；但 Java API 仍是一等能力。

建议 YAML 形态：

```yaml
apiVersion: ai4j.io/v1alpha1
kind: Agent
metadata:
  name: research-assistant
model:
  provider: openai-compatible
  profile: default
instructions:
  system: "你是一个研究助手。"
memory:
  type: in-memory
  compact:
    strategy: token-threshold
tools:
  - ref: weather
plugins:
  - id: ask-user
permissions:
  tools:
    default: ask
sandbox:
  mode: optional
workflow:
  type: react
```

边界：

- YAML 只引用 env/config key，不写真实密钥。
- YAML 是组装层，不执行任意 Java 代码。
- `AgentFactory` 负责把 Blueprint 映射到真实 Agent。
- `ai4j run agent.yaml` 属于 `ai4j-cli`，`ai4j-agent` 不依赖 CLI。

## 5. 插件生态方向

第三方应该可以写插件并单独发布 jar；使用者可以安装、查看、启用、禁用和组合插件。这会显著改善使用体验：开发者不用 fork AI4J，也不用把所有能力写进核心仓库。

插件应分层，不做一个万能接口：

| 插件类型 | 宿主模块 | 能力 |
| --- | --- | --- |
| Resource Plugin | `ai4j-extension-api` | prompt、skill、模板、示例资源 |
| Tool Plugin | `ai4j-agent` | 注册 Agent tool，必须显式 enable/expose |
| Runtime Hook Plugin | `ai4j-agent` | before/after run、tool call、memory compact、trace hook |
| Memory Plugin | `ai4j-agent` | MemoryStore、CompactPolicy、ContextProjector |
| Sandbox Provider Plugin | `ai4j-agent` | SandboxProvider / SandboxSession 实现 |
| CLI Command Plugin | `ai4j-cli` | 增加 `/command`、帮助文本、参数解析 |

安全默认值：

- 插件安装不等于危险能力自动暴露。
- manifest 必须声明能力、权限、资源、版本兼容性。
- Tool / Sandbox / Shell / File / Browser 类能力默认需要显式授权。
- docs-site 需要提供“如何写插件”的完整教程和官方 ask-user 示例。

## 6. Sandbox / Remote Agent Runner 方向

需要区分两种模式：

| 模式 | Agent 在哪里跑 | 工具在哪里执行 | 适合场景 |
| --- | --- | --- | --- |
| Host-driven sandbox tools | 当前 Java 应用进程内 | 外部 sandbox / VM / container / browser | 普通 Java 应用安全执行工具 |
| Remote Agent Runner | sandbox / 远端运行环境内 | 同一个隔离环境 | 豆包、点点类云端 Agent 产品；每个会话有独立远端桌面或项目环境 |

SDK 不需要直接实现完整云平台，但应提供抽象，让团队或插件作者接入 Cubesandbox、容器、VM、浏览器环境或自研 runner。

建议抽象：

| 抽象 | 作用 |
| --- | --- |
| `SandboxSpec` | 镜像、资源、workspace、网络、标签、超时 |
| `SandboxProvider` | 创建、恢复、销毁 sandbox session |
| `SandboxSession` | 非敏感运行摘要、状态、workspace id |
| `SandboxToolRouter` | 把 shell/file/browser/screenshot 等工具路由进 sandbox |
| `AgentRunnerSpec` | 远端 runner 的模型配置、agent blueprint、权限、sandbox 策略 |
| `AgentRunnerClient` | 和远端 runner 通信，获取事件流、结果、日志 |

隔离策略建议：

| 策略 | 适合场景 |
| --- | --- |
| `PER_TASK_EPHEMERAL` | 高风险任务，结束即销毁 |
| `PER_SESSION` | 云端 Agent 产品默认，一个会话一个 sandbox |
| `PER_USER_POOL` | 仅低风险、强 reset 的受控场景 |

重要结论：没有 sandbox 时就是 direct host runtime，不需要再叫 `local sandbox`。

## 7. Coding Agent CLI/TUI 方向

目标是让用户一条命令安装后，在终端输入 `ai4j`，就像使用 Codex、Claude Code、OpenCode 一样进入 coding agent。

短期不切换到 Node Ink，也不自研复杂 renderer。继续基于 Java/JLine，把体验升级放在：

- 全局 `ai4j` 命令入口。
- provider/model/profile 切换。
- `/help`、`/model`、`/provider`、`/extension`、`/extensions`、`/sandbox`、`/memory`、`/compact`、`/permissions`。
- markdown、代码块、diff、tool call、approval prompt 分块渲染。
- CLI Command Plugin 扩展命令。
- TUI render plugin 暂缓，等基础交互稳定后再开放。
- 安装方案另开任务比较 native binary、jbang、npm wrapper、zip script。

## 8. Harness 与 AI4J 的关系

`coding-agent-harness` 继续作为项目治理、长程任务、回归证据和团队协作体系，不直接内化进 `ai4j-agent` 核心。

合理方向：

- Harness Skill 继续给小白用户和 coding agent 工具使用。
- AI4J CLI 后续可以做可选适配：检测 `coding-agent-harness/`、显示当前任务、调用 harness CLI。
- 不强制所有 AI4J 用户安装 harness。
- 适配应放在 `ai4j-cli` 或插件中，不污染 `ai4j-agent`。

## 9. 后续任务优先级

| 顺序 | 任务 | 主模块 | 验证 |
| ---: | --- | --- | --- |
| 0 | 完成和合并当前未收口任务，避免 roadmap 与代码漂移 | harness / active worktrees | harness status + PR checks |
| 1 | Plugin contribution contract expansion | `ai4j-extension-api` + `ai4j-agent` | extension + agent tests |
| 2 | Blueprint schema compatibility and docs hardening | `ai4j-agent` + `docs-site` | blueprint tests + docs build |
| 3 | Sandbox tool routing into `ai4j-coding` | `ai4j-coding` | coding targeted tests |
| 4 | CLI `/sandbox` + `/memory` + `/compact` UX | `ai4j-cli` | CLI targeted tests + manual smoke |
| 5 | Remote Agent Runner SPI | `ai4j-agent` | fake runner tests |
| 6 | One-command install design and prototype | `ai4j-cli` | packaging smoke |
| 7 | docs-site developer-facing completeness pass | `docs-site` | docs build + link check |

## 10. 不做事项

- 不新增 `AgentHost` / `Host Kernel` 顶层概念。
- 不新增核心 Agent Maven 模块。
- 不把 harness 强塞进 `ai4j-agent`。
- 不默认提供重型云 sandbox 平台。
- 不写不存在的 API 示例，比如未实现的 fluent API。
- 不提交 provider token、sandbox secret、local-only config。
- 不把 TUI 渲染扩展过早开放成不稳定插件面。

## 11. 验收方式

每个实现任务都必须独立满足：

1. Harness task package 完整：brief、plan、strategy、findings、progress、review、walkthrough。
2. 使用 dedicated worktree / branch。
3. Java 8 兼容。
4. docs-site 示例和真实 API 一致。
5. 有 targeted regression；新增固定回归面时同步 Regression SSoT 与 Cadence Ledger。
6. sandbox / runner 用 fake provider 做默认验证，真实云服务只作为 opt-in。
