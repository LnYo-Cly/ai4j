# AI4J Agent SDK 架构增强规划

> 记录日期：2026-06-20  
> 任务：`MODULES/agent-runtime/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81`  
> 主模块：`ai4j-agent`  
> 关联模块：`ai4j-extension-api`、`ai4j-coding`、`ai4j-cli`、`docs-site`

## 1. 目标判断

AI4J 的目标不是复刻 Spring AI、LangChain4j、AgentScope Java 的大框架覆盖面，而是把 Java 项目接入 Agent 能力的成本压到最低，同时保留足够的组合能力，让开发者可以从“几行 Java 代码”逐步升级到“可配置 Agent、插件生态、远端沙箱 Runner、Coding Agent CLI/TUI”。

本轮规划坚持以下原则：

1. **不新增核心 Maven 拆分**：继续以 `ai4j-agent` 承载 Agent SDK 核心能力，避免额外的 kernel/host Maven 让个人项目维护成本失控。
2. **不引入新的 “AgentHost / Host Kernel” 顶层概念**：对外概念优先使用现有 `AgentRuntime`、`AgentSession`、`AgentFactory`、`AgentBlueprint`、`AgentToolRegistry`、`AgentMemory`、`AgentSandbox` 等名词。
3. **插件生态靠现有边界扩展**：`ai4j-extension-api` 负责包级发现、manifest、资源声明和显式启用；`ai4j-agent` 负责运行期生命周期、工具、memory、sandbox、runner 等接入点。
4. **沙箱是可选能力，不是默认运行前提**：不使用 sandbox 时就是直接在宿主环境运行；需要安全隔离或云端产品化时，通过 sandbox SPI / runner SPI 接入外部容器、VM、浏览器或托管环境。
5. **docs-site 示例必须跟真实 API 对齐**：不再写不存在的 `Ai4j.chat()` 或其他伪 API；每个示例后续都要能被测试或最小 smoke 证明。

## 2. 三层产品形态

| 层级 | 用户看到的能力 | 模块边界 | 目标 |
| --- | --- | --- | --- |
| Java Agent SDK | Java 代码创建 Agent、Memory、Tool、Workflow、Blueprint | `ai4j-agent` + `ai4j` | 最低接入成本，适合普通 Java 应用 |
| Agent 组装生态 | YAML Agent、第三方插件、Memory/Tool/Sandbox 扩展 | `ai4j-agent` + `ai4j-extension-api` | 让开发者像组装 Pi 类插件一样组装能力 |
| Coding Agent 产品面 | `ai4j` 命令、TUI、/commands、provider/model 切换、workspace 工具、sandbox | `ai4j-coding` + `ai4j-cli` | 形成接近 Codex / Claude Code / OpenCode 的终端体验 |

## 3. 现有模块如何承载

| 能力 | 放置位置 | 原因 |
| --- | --- | --- |
| AgentSession、Memory、Compact、Trace、Workflow、Subagent、Blueprint、Sandbox SPI、Runner SPI 抽象 | `ai4j-agent` | 这些是 Agent 运行时语义，属于 SDK 核心 |
| Provider、Chat/Responses、RAG、MCP、Vector、Image/Audio/Realtime | `ai4j` | 继续作为基础 SDK，不反向依赖 Agent |
| 插件包 manifest、ServiceLoader 发现、资源声明、显式 enable/expose gate | `ai4j-extension-api` | 插件公共契约必须轻、稳定、低依赖 |
| 官方示例插件 | `ai4j-plugin-ask-user` 等独立插件模块 | 给第三方插件作者做参考，不污染核心 |
| Workspace 文件、Shell、Patch、浏览器、Coding Agent outer loop、sandbox 工具路由 | `ai4j-coding` | 这是 coding-agent 场景，不应塞进通用 Agent SDK |
| CLI/TUI、/commands、安装入口、ACP、provider/model 交互 | `ai4j-cli` | 终端体验和宿主交互属于 CLI |
| 用户文档、教程、对比、Cookbook | `docs-site` | 面向用户解释能力，不作为生产逻辑 SSoT |

## 4. 核心路线图

### Phase A：先收敛已有任务和运行态基座

优先把已经开始的任务收口，不重复造新的平行设计。

| 子任务 | 目标 | 依赖 | 交付 |
| --- | --- | --- | --- |
| A1 完成 P2-B AgentSession sandbox binding | `AgentSession` 可保存非敏感 sandbox binding 摘要 | P2-A 已合并 | PR、测试、docs-site、回归记录 |
| A2 清理 P0/P1/P2 未合并任务队列 | 确认哪些已 merge、哪些只需 review/PR、哪些 supersede | Harness status + module_plan | 更新任务状态，避免路线图漂移 |
| A3 修正 docs-site API 示例规则 | 以后不写不存在的 API；示例必须可追溯到代码 | 现有 docs-site | 文档贡献规则或任务内 checklist |

### Phase B：Session / Memory / Compact 完整化

目标：让 `AgentSession` 成为真实运行态容器，而不是一次请求的临时对象。

| 能力 | 设计要点 | 验证 |
| --- | --- | --- |
| Session runtime context | session id、metadata、event log、snapshot、restore、sandbox binding、permission decisions、trace context | `ai4j-agent` targeted tests |
| Memory Store | 短期对话、长期记忆、工具结果摘要、可替换存储 | in-memory + fake store tests |
| Compact Policy | token threshold、manual compact、run boundary compact、失败回滚 | deterministic compact tests |
| Context Projector | 按预算把 memory、RAG、tool results、session state 投影成模型上下文 | report + ordering tests |
| Docs | “什么时候用 memory / compact / session snapshot”写清楚 | docs-site build |

### Phase C：YAML Agent Blueprint

目标：用户可以用一个 YAML 定义 Agent，但 Java API 仍是第一等能力。

建议 schema：

```yaml
apiVersion: ai4j.io/v1alpha1
kind: Agent
metadata:
  name: research-assistant
model:
  provider: openai-compatible
  profile: default
instructions:
  system: "你是..."
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
- YAML 是组装层，不承载任意 Java 代码。
- Java `AgentFactory` 负责把 Blueprint 映射为真实 `Agent`。
- CLI `ai4j run agent.yaml` 属于 `ai4j-cli`，不能让 `ai4j-agent` 依赖 CLI。

### Phase D：插件生态

插件生态应分层，避免一次性把所有东西塞进一个万能 Plugin 接口。

| 插件类型 | 宿主 | 说明 |
| --- | --- | --- |
| Resource Plugin | `ai4j-extension-api` | Prompt、Skill、模板、示例资源 |
| Tool Plugin | `ai4j-agent` | 向 `AgentToolRegistry` 暴露工具，必须显式 enable/expose |
| Runtime Hook Plugin | `ai4j-agent` | before/after run、tool call、memory compact、trace hook |
| Memory Plugin | `ai4j-agent` | 提供 MemoryStore、CompactPolicy、ContextProjector |
| Sandbox Provider Plugin | `ai4j-agent` | 提供 SandboxProvider / SandboxSession 实现 |
| CLI Command Plugin | `ai4j-cli` | 增加 `/command`、命令说明、参数解析、帮助信息 |

关键体验：

1. 第三方开发者可以单独发布插件 jar。
2. 使用者可以安装、查看、启用、禁用插件。
3. 插件默认不自动暴露危险工具。
4. 插件 manifest 必须声明能力、权限、资源、版本兼容性。
5. docs-site 必须提供“写一个插件”的完整教程和示例仓库结构。

### Phase E：Sandbox / Remote Agent Runner

需要区分两种模式：

| 模式 | Agent 在哪里跑 | 工具在哪里执行 | 适合场景 |
| --- | --- | --- | --- |
| Host-driven sandbox tools | 应用进程内 | 外部 sandbox/VM/container/browser | Java 应用给 Agent 安全工具执行能力 |
| Remote Agent Runner | sandbox/远端运行环境内 | 同一个隔离环境 | 豆包/点点类云端 Agent 产品、每个会话有远端桌面/项目环境 |

SDK 不应该默认提供完整云平台，但应该提供抽象：

| 抽象 | 作用 |
| --- | --- |
| `SandboxSpec` | 描述镜像、资源、workspace、网络、标签、超时等 |
| `SandboxProvider` | 创建、恢复、销毁 sandbox session |
| `SandboxSession` | 保存非敏感运行摘要、状态、workspace id |
| `SandboxToolRouter` | 把 Shell、文件、浏览器、截图等工具路由进 sandbox |
| `AgentRunnerSpec` | 描述远端 runner 的模型配置、agent blueprint、权限、sandbox 策略 |
| `AgentRunnerClient` | 与远端 runner 通信，获取事件流、结果、日志 |

资源隔离策略：

| 策略 | 说明 | 默认建议 |
| --- | --- | --- |
| `PER_TASK_EPHEMERAL` | 每个任务独立 sandbox，结束销毁 | 高风险工具执行默认 |
| `PER_SESSION` | 每个 Agent 会话一个 sandbox，保留工作区 | 云端 Agent 产品默认 |
| `PER_USER_POOL` | 用户级 sandbox 池，多会话复用 | 只适合低风险、强 reset 的受控场景 |

注意：不要把 `local sandbox` 当默认概念；没有 sandbox 就是 direct host runtime。

### Phase F：Coding Agent CLI/TUI

目标体验：终端输入 `ai4j` 就进入可交互 coding agent，具备 provider/model 切换、清晰会话区、命令面板、工具执行反馈和 markdown/code 渲染。

| 能力 | 设计方向 |
| --- | --- |
| 终端渲染 | 继续 Java 侧 JLine 路线，先不引入 Node Ink，也不自研复杂 renderer |
| `/command` | `/help`、`/model`、`/provider`、`/extension`、`/extensions`、`/sandbox`、`/memory`、`/compact`、`/permissions` |
| Provider/model 切换 | 运行时 profile 列表、当前 provider/model 显示、失败时可回退 |
| 回复渲染 | markdown、代码块、diff、tool call、approval prompt 分块显示 |
| 插件扩展 | CLI Command Plugin 可注册命令和帮助文本；TUI 渲染扩展先谨慎开放 |
| 安装 | 后续比较 native binary、jbang、npm wrapper、zip script，目标是一条命令安装后可直接运行 `ai4j` |

TUI 规划不放进 `ai4j-agent`，而是 `ai4j-cli` 主导、`ai4j-coding` 提供 workspace 能力、`ai4j-agent` 提供会话和运行时语义。

### Phase G：Harness 与 AI4J CLI 的关系

`coding-agent-harness` 继续作为项目治理/长程任务/回归证据体系，不内化进 `ai4j-agent` 核心。AI4J CLI 可以后续提供可选适配：

- 如果项目存在 `coding-agent-harness/`，CLI 可以读取任务状态、展示当前任务、调用既有 harness CLI。
- 不强制所有 AI4J 用户安装 harness。
- Harness 的 Skill 仍适合给小白用户和 Coding Agent 工具使用，用来降低协作成本。
- 任何 harness 适配都应放在 `ai4j-cli` 或插件里，而不是污染 `ai4j-agent`。

## 5. 建议任务队列

| 顺序 | 任务 | 主模块 | 依赖 | 验证 |
| ---: | --- | --- | --- | --- |
| 0 | 修复并合并 P2-B AgentSession sandbox binding | `ai4j-agent` | P2-A | agent tests + docs build + harness status |
| 1 | Agent SDK backlog reconciliation | harness / `agent-runtime` | 当前所有 P0/P1/P2 任务 | harness status + module_plan update |
| 2 | Memory/Compact Session API polish | `ai4j-agent` | P0-A/P0-B | targeted + broad agent tests |
| 3 | Plugin contribution contract expansion | `ai4j-extension-api` + `ai4j-agent` | P0-C | extension + agent tests |
| 4 | Blueprint schema compatibility and docs hardening | `ai4j-agent` + `docs-site` | P1-A/P1-B/P1-C | blueprint tests + docs build |
| 5 | Sandbox tool routing into `ai4j-coding` | `ai4j-coding` | P2-A/P2-B | coding targeted tests |
| 6 | CLI `/sandbox` + `/memory` + `/compact` UX | `ai4j-cli` | task 5 | cli targeted tests + manual smoke |
| 7 | Remote Agent Runner SPI | `ai4j-agent` | Sandbox SPI stable | fake runner tests |
| 8 | One-command install design and prototype | `ai4j-cli` | TUI commands stable | packaging smoke |
| 9 | docs-site developer-facing completeness pass | `docs-site` | APIs stable | docs build + link check |

## 6. 验收标准

后续每个实现任务都必须满足：

1. 真实 API 和 docs-site 示例一致。
2. 不提交 provider key、token、local path 或 sandbox config secrets。
3. Java 8 编译兼容。
4. 公共 API 有 owner module tests。
5. CLI/TUI 改动有 targeted tests，必要时加手动 smoke 记录。
6. Sandbox/runner 改动默认用 fake provider 验证；真实云服务只作为 opt-in。
7. 每个任务都更新 task-local progress/review/walkthrough；新增固定回归面时同步 Regression SSoT 与 Cadence Ledger。

