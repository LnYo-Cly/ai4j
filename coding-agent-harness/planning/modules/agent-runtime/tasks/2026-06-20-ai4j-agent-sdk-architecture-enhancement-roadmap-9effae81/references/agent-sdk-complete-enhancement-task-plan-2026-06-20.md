# AI4J Agent SDK 完整增强任务规划记录

> 记录日期：2026-06-20
> Harness 任务：`MODULES/agent-runtime/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81`
> 记录性质：任务规划补充；不代表代码已经全部实现。

## 1. 任务目标

把 AI4J 从“已有 Java AI SDK / Agent 能力集合”继续推进成一套更容易落地的 Agent SDK 和 Coding Agent 产品面：

1. Java 开发者可以用低概念成本接入模型、工具、RAG、Memory、Workflow 和 Agent。
2. 使用者可以通过 YAML Blueprint、插件和 CLI/TUI 组装自己的 Agent。
3. 第三方开发者可以编写插件并独立发布，用户可以安装、启用、禁用和组合插件。
4. SDK 提供沙箱和远端 Runner 抽象，帮助开发者做类似云端 Agent 产品的运行环境，但不强绑定某个云平台。
5. 终端输入 `ai4j` 后应逐步形成接近 Codex、Claude Code、OpenCode 一类 coding agent 的交互体验。
6. docs-site 必须围绕真实 API、真实命令和真实能力写清楚，不再出现不存在的伪 API 示例。

## 2. 产品定位

AI4J 不适合和 Spring AI、LangChain4j、AgentScope Java 比“大团队、大生态、大覆盖面”。更合理的定位是：

| 方向 | AI4J 应追求的差异化 |
| --- | --- |
| 接入成本 | 少配置、少概念、示例真实，Java 应用可以快速跑起来。 |
| 组装成本 | Agent、Memory、Compact、Tool、RAG、Workflow、Plugin、Sandbox 可按需叠加。 |
| 插件生态 | 第三方能写，使用者能装，危险能力默认不暴露。 |
| 声明式 Agent | YAML Blueprint 可以服务 CLI、低代码、模板市场和小白用户。 |
| Coding Agent 体验 | `ai4j` 命令进入交互式 agent，能切 provider/model/session/plugin/sandbox/memory。 |
| 云端产品支撑 | 通过 Sandbox / Remote Runner SPI 支持每会话远端环境、浏览器、命令、项目运行和产物收集。 |

## 3. 固定架构决策

| 决策点 | 结论 | 原因 |
| --- | --- | --- |
| Agent SDK 核心模块 | 继续使用 `ai4j-agent` | 避免维护多个核心 Maven，也避免用户面对两套入口。 |
| 新顶层概念 | 不新增 `Host Kernel` / `AgentHost` 作为对外核心名词 | 现有 `AgentRuntime`、`AgentSession`、`AgentFactory`、`AgentBlueprint` 已足够表达。 |
| 开发者分层 | 不再区分“普通用户 / 进阶用户”的模块入口 | 所有使用者本质都是开发者，只是使用深度不同。 |
| Sandbox 默认 | 没有 sandbox 时就是 direct host runtime | 不再使用“local sandbox”这种容易误解的说法。 |
| OpenAI-compatible | 只称 `openai-compatible` | 不把任何中转平台名写成 SDK 架构概念。 |
| Harness 关系 | 不内化进 `ai4j-agent` | Harness 是项目治理与协作系统；AI4J CLI 可做可选适配。 |
| TUI 技术路线 | 短期继续 Java + JLine | 避免 Ink/Node wrapper 或自研 renderer 带来的维护成本。 |
| docs-site | 示例必须来自真实 API / 测试 / 命令 | 文档质量问题优先解决“真实性”和“可执行性”。 |

## 4. 模块边界

| 模块 | 承载能力 | 不应承载 |
| --- | --- | --- |
| `ai4j` | Provider、Chat/Responses、RAG、MCP、Vector、Image/Audio/Realtime | Agent session、CLI/TUI、sandbox runner 控制面。 |
| `ai4j-agent` | Agent runtime、Session、Memory、Compact、Workflow、Trace、Subagent、Permission、Blueprint、Sandbox/Runner 抽象 | 具体 CLI 命令、workspace 文件工具、前端低代码页面。 |
| `ai4j-extension-api` | 插件 manifest、ServiceLoader、资源声明、capability/permission 声明、enable/expose gate | 具体 Agent 运行时实现和危险工具默认暴露。 |
| `ai4j-plugin-*` | 官方示例插件和常用插件 | 核心 SDK 必须依赖的逻辑。 |
| `ai4j-coding` | workspace、shell、file、git、patch、browser、project run/test、coding outer loop、sandbox tool routing | 通用 Agent API 和终端 UI。 |
| `ai4j-cli` | `ai4j` 命令、JLine TUI、slash command、provider/model 切换、ACP、安装入口、可选 harness 适配 | Agent 核心运行时语义。 |
| `docs-site` | 教程、API 说明、命令参考、插件作者指南、roadmap、cookbook | 生产逻辑和不可验证示例。 |

## 5. 运行时核心：Session / Memory / Compact

目标：`AgentSession` 成为长程 Agent 的运行态容器，而不是一次请求的临时对象。

| 能力 | 规划内容 | 验收点 |
| --- | --- | --- |
| Session metadata | session id、title、labels、owner、workspace、runner/sandbox 摘要 | 不保存 token、secret、临时连接串。 |
| Event log | user/model/tool/approval/compact/error/snapshot events | 可用于 CLI/TUI 回放和调试。 |
| Snapshot / restore | 稳定状态恢复，不包含 provider key | 可做 deterministic tests。 |
| Memory store | 短期对话、长期记忆、工具结果摘要、可替换 store | 不绑定单一 provider。 |
| Compact policy | token threshold、manual compact、run boundary compact、失败回滚 | 自动 compact 生成 report。 |
| Context projector | 按预算组合 memory、RAG、tool result、session state | 有 ordering / budget tests。 |
| Compact report | 说明保留、丢弃、摘要、预算变化 | CLI/TUI 可展示可诊断结果。 |

设计参考可以吸收 Codex、Claude Code 等公开行为和公开分析中的优秀上下文管理思想，但不能复制泄露源码，也不能把未验证的网络说法写成既定事实。source-backed 结论应进入 R0 调研 digest。

## 6. 声明式 Agent：YAML Blueprint

目标：让小白用户、CLI、FlowGram/低代码、模板市场和 Java 开发者可以围绕同一份 Agent 定义协作。

首版建议只支持单 Agent，不急于表达复杂 team/workflow DSL。

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
- YAML 不执行任意 Java 代码。
- `AgentFactory` 负责把 Blueprint 映射成真实 Agent。
- `ai4j run agent.yaml` 属于 `ai4j-cli`，`ai4j-agent` 不反向依赖 CLI。
- schema 要可导出，便于 docs-site、IDE 补全和测试 fixture 共用。

## 7. 插件生态规划

插件生态应像“能力贡献点集合”，而不是一个万能 `Plugin` 接口。

| 插件类型 | 宿主模块 | 作用 | 安全默认 |
| --- | --- | --- | --- |
| Resource Plugin | `ai4j-extension-api` | Prompt、Skill、模板、示例资源 | 安装后只作为资源，不自动执行。 |
| Tool Plugin | `ai4j-agent` | 注册 Agent tool | 必须显式 enable/expose。 |
| Runtime Hook Plugin | `ai4j-agent` | session/turn/tool/model/compact 生命周期观察 | observation-first，避免破坏主流程。 |
| Guardrail Plugin | `ai4j-agent` | 输入、输出、tool call 检查 | 返回 allow/ask/deny 和原因。 |
| Memory Plugin | `ai4j-agent` | MemoryStore、CompactPolicy、ContextProjector | 不保存敏感字段。 |
| Sandbox Provider Plugin | `ai4j-agent` | SandboxProvider / SandboxSession 实现 | 危险执行默认需要权限策略。 |
| CLI Command Plugin | `ai4j-cli` | 增加 `/command`、help、参数解析 | 先开放命令，不急于开放任意 TUI 渲染。 |

插件任务的验收重点：

1. 第三方 jar 可以被发现。
2. manifest 能声明 capability、permission、resources、版本兼容范围。
3. 用户能查看、启用、禁用插件。
4. 安装插件不等于危险能力自动暴露。
5. docs-site 有“写一个插件”的完整教程和官方示例插件参考。

## 8. Sandbox 与 Remote Agent Runner

需要区分两种形态：

| 形态 | Agent 在哪里跑 | 工具在哪里执行 | 适合场景 |
| --- | --- | --- | --- |
| Host-driven sandbox tools | 当前 Java / CLI 进程 | 外部 sandbox、VM、container、browser | 普通 Java 应用或 CLI 安全执行工具。 |
| Remote Agent Runner | 远端隔离环境或 sandbox 内 | 同一个远端环境 | 豆包、点点类云端 Agent 产品，每会话有独立远端环境。 |

AI4J 不应先做完整云控制平台，但要提供抽象：

| 抽象 | 职责 |
| --- | --- |
| `SandboxSpec` | 镜像、资源、workspace、网络、标签、超时。 |
| `SandboxProvider` | 创建、恢复、销毁 sandbox session。 |
| `SandboxSession` | 非敏感状态摘要、workspace id、生命周期状态。 |
| `SandboxCommand` / `SandboxResult` | 命令执行请求和 stdout/stderr/exit code/artifact refs。 |
| `SandboxArtifact` | 文件、截图、日志、构建产物引用。 |
| `SandboxBinding` | AgentSession 中保存的非敏感绑定摘要。 |
| `SandboxToolRouter` | 将 shell/file/browser/screenshot 等工具路由进 sandbox。 |
| `AgentRunnerSpec` | 远端 runner 的模型、blueprint、权限、sandbox 策略。 |
| `AgentRunnerClient` | 事件流、结果、日志、取消、checkpoint、artifact 收集。 |

隔离策略建议：

| 策略 | 使用场景 | 默认建议 |
| --- | --- | --- |
| `PER_TASK_EPHEMERAL` | 高风险命令或一次性任务 | 高风险工具默认。 |
| `PER_SESSION` | 云端 Agent 产品，一个会话一个环境 | 产品化默认。 |
| `PER_USER_POOL` | 低风险、强 reset、有配额的用户池 | 谨慎开放，不做首选。 |

关键边界：sandbox 是真实隔离环境，例如容器、VM、浏览器或托管远端环境；不使用 sandbox 时就是在宿主直接执行。进入 sandbox 不代表跳过 permission policy。

## 9. Coding Agent CLI/TUI 规划

目标：用户安装后输入 `ai4j`，进入交互式 coding agent。

短期路线：

- 继续 Java + JLine。
- 不引入 Ink 作为主栈。
- 不自研复杂 terminal renderer。
- 先做 view model、renderer abstraction、slash command parser、交互状态和自动化测试。

核心命令：

| 命令 | 作用 |
| --- | --- |
| `/provider` | 查看 / 切换 provider profile。 |
| `/model` | 查看 / 切换当前模型。 |
| `/session` | 查看 / 切换 / 新建 session。 |
| `/memory` | 查看 memory store、预算、投影摘要。 |
| `/compact` | 手动 compact 或查看 compact 策略。 |
| `/checkpoint` | 创建或查看 session/workspace checkpoint。 |
| `/plugins` / `/extension` | 查看插件、资源和启用状态。 |
| `/sandbox` | 查看、attach、disable、status sandbox。 |
| `/permissions` | 查看当前工具权限策略。 |
| `/help` | 命令面板和快捷键说明。 |

体验验收：

1. provider/model/profile 当前状态清楚。
2. slash palette 可发现。
3. markdown、代码块、diff、tool call、approval、error 分块渲染。
4. memory/compact/sandbox/session 状态能一眼读懂。
5. 插件命令可注册文本 help 和 parser。
6. one-command install 后全局有 `ai4j` 入口。

## 10. docs-site 规划

每个能力页统一结构：

1. 这个能力解决什么问题。
2. 什么时候该用，什么时候不该用。
3. 最小可运行 Java / YAML / CLI 示例。
4. 核心 API / 字段解释。
5. 与其他模块的关系。
6. 安全边界和限制。
7. 常见错误和排查。
8. 测试、demo 或源码入口。
9. 下一步链接。

禁止项：

- 不写不存在的 API。
- 不用 roadmap 代替教程。
- 不使用“企业采用”这类生硬营销词。
- 不把具体中转平台名写成 SDK 架构概念。
- 不让 docs-site 成为生产逻辑的唯一事实来源。

## 11. 推荐实施队列

执行前必须以最新 `origin/dev`、当前 PR 状态和 `npx --yes coding-agent-harness status --json .` 为准，不从过期对话推断状态。

| 顺序 | 任务 | 主模块 | 输出 | 验证 |
| ---: | --- | --- | --- | --- |
| 0 | Backlog / review queue reconciliation | Harness | 区分已合并、待人工确认、需 closeout、已 supersede 的任务 | `harness status --json .` |
| 1 | R0 source-backed research digest | docs-site / Harness | Pi、Codex、Claude Code、OpenCode、Java SDK、Sandbox provider 的公开资料 digest | docs build + source links |
| 2 | Session / Memory / Compact API polish | `ai4j-agent` | stable AgentSession + memory + compact reports | `mvn -pl ai4j-agent -am -DskipTests=false test` |
| 3 | Blueprint schema compatibility hardening | `ai4j-agent` | schema/model/loader/validator/export 的兼容测试 | blueprint targeted tests |
| 4 | Plugin contribution contract expansion | `ai4j-extension-api` + `ai4j-agent` | tool/hook/guardrail/memory/sandbox/command contribution model | extension + agent tests |
| 5 | Sandbox provider SPI + session binding hardening | `ai4j-agent` | fake provider、SandboxBinding、permission interaction | sandbox targeted tests |
| 6 | Coding sandbox tool routing | `ai4j-coding` | shell/file/browser/project tools 可路由到 sandbox | coding targeted tests |
| 7 | CLI `/sandbox` `/memory` `/compact` UX | `ai4j-cli` | 一等 slash command、status block、ACP 对齐 | cli targeted tests + smoke |
| 8 | Remote Agent Runner SPI | `ai4j-agent` | AgentRunnerSpec/Client/event stream/fake runner | fake runner tests |
| 9 | One-command install prototype | `ai4j-cli` | native/jbang/npm/zip 方案选型和最小安装包 | packaging smoke |
| 10 | CLI/TUI interaction polish | `ai4j-cli` | provider/model/session/plugin/sandbox 状态栏和渲染 | parser/view model tests + smoke |
| 11 | docs-site completeness pass | `docs-site` | 每个真实能力页讲清楚并对齐 API | `npm --prefix docs-site run build` |

## 12. 每个实现切片的固定门禁

1. 新建或复用 Harness task package。
2. 非平凡实现使用 dedicated worktree / branch。
3. 保持 Java 8 兼容。
4. 不提交 provider token、sandbox secret、local-only config 或个人路径。
5. 新增 public API 必须有 owner module tests。
6. CLI/TUI 改动至少覆盖 parser、view model、runtime dispatch、ACP 或文档一致性之一。
7. docs-site 改动运行 `npm --prefix docs-site run build`。
8. 新增固定回归面时同步 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`。
9. 每个任务 closeout 前更新 task-local `review.md`、`walkthrough.md`、`lesson_candidates.md` 和 progress evidence。

## 13. 当前开放决策

| 决策 | 当前倾向 | 何时拍板 |
| --- | --- | --- |
| one-command install 方案 | 先比较 native binary、jbang、npm wrapper、zip script | `ai4j-cli` packaging task |
| 是否官方提供真实 sandbox provider | 先做 SPI + fake provider；真实 provider 用插件或示例承载 | Sandbox provider task |
| TUI render plugin 是否开放 | 先开放 CLI Command Plugin，render plugin 暂缓 | CLI/TUI 基础体验稳定后 |
| YAML 是否扩展到 team/workflow DSL | 首版只做 single Agent | Blueprint v1 稳定后 |
| Harness 适配进入 CLI 的深度 | 先检测和展示，可选调用外部 harness CLI | CLI harness-adapter task |

## 14. 下一位 Agent 的执行入口

1. 先运行 `git status --short --branch` 和 `npx --yes coding-agent-harness status --json .`。
2. 查看 `coding-agent-harness/planning/modules/agent-runtime/module_plan.md`，不要凭旧对话判断任务状态。
3. 如果继续调研，优先完成 R0 source-backed digest，避免对 Pi/Codex/Claude/OpenCode 做无来源设计。
4. 如果继续实现，优先选择队列中第一个未完成且依赖满足的切片，单独创建 worktree 和 task package。
5. 所有 docs-site 示例必须能追到真实类、测试、命令或 demo。
