# AI4J Agent SDK architecture enhancement roadmap - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### ai4j-agent 应继续作为 Agent SDK 核心

- 背景：用户明确担心拆分过多后个人项目维护成本过高，也不希望引入新的 Host/Kernel 式命名。
- 发现：当前 `AGENT.md` 已把 `AgentRuntime`、`AgentMemory`、`AgentToolRegistry`、`AgentWorkflow`、Trace、Subagent 等 Agent 运行时能力归在 `ai4j-agent`；`engineering-standard.md` 也明确 `ai4j-agent` owns agent runtime concerns。
- 影响：后续增强应优先扩展现有 `ai4j-agent` 包结构，而不是新增核心 Maven。
- 后续：实现任务只在真正出现可独立发布、可选依赖、稳定边界时再考虑新官方插件模块。

### Sandbox 有两类产品形态，SDK 不应默认托管云平台

- 背景：用户提到 Codex `/sandbox`、豆包/小红书点点类云端 Agent 产品，希望 AI4J 能帮助开发者快速实现类似远端 Agent 产品。
- 发现：可以区分 host-driven sandbox tools 与 remote Agent runner。前者 Agent 在应用进程内，工具执行进 sandbox；后者 Agent runner 自身在 sandbox/远端环境中运行。
- 影响：`ai4j-agent` 需要抽象 sandbox/session/runner 语义，`ai4j-coding` 负责 workspace 工具路由，`ai4j-cli` 提供 `/sandbox` UX；不需要 AI4J 自己先提供完整云控制平面。
- 后续：P2-B 之后先做 `ai4j-coding` sandbox tool routing，再做 CLI `/sandbox`，最后再设计 remote runner SPI。

### 插件生态必须第三方可写、使用者可安装、默认安全

- 背景：用户认可 Pi 类插件生态方向，希望不同开发者可以客制化插件，使用者可以安装/组装。
- 发现：现有 `ai4j-extension-api` 已适合承载轻量 manifest、ServiceLoader、资源声明和显式 enable/expose gate；运行时贡献点应在 `ai4j-agent` / `ai4j-cli` 分层扩展。
- 影响：插件不应是一个万能接口，而应分 Resource、Tool、Runtime Hook、Memory、Sandbox Provider、CLI Command 等类别。
- 后续：单独开任务扩展插件 contribution contract，并配套官方最小示例和 docs-site 教程。

### CLI/TUI 先坚持 Java 可维护路线

- 背景：用户比较 JLine、Ink、自研 renderer、Pi/Codex/Claude Code 的 TUI 体验。
- 发现：本仓库 `ai4j-cli` 已有 JLine 相关实现和回归记忆；引入 Ink 会把 Java CLI 变成 Node/JS runtime wrapper，自研 renderer 成本更高。
- 影响：短期继续 JLine，把体验升级放在布局、命令面板、provider/model 切换、渲染分块、approval prompt 和插件 command 注册上。
- 后续：安装体验再比较 native binary、jbang、npm wrapper、zip script。

### docs-site 必须只写真实 API

- 背景：用户明确指出此前出现过自己没写过、项目中不存在的 API 示例。
- 发现：docs-site 的质量问题不是文案长度，而是示例与实现不一致、特色能力讲解不完整。
- 影响：后续 docs-site 任务必须要求示例可追溯到测试、demo 或真实类名；禁止编造 convenience API。
- 后续：每个功能页采用“问题 -> 最小真实示例 -> 进阶配置 -> 边界 -> 测试/排障”的结构。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Agent SDK 核心模块 | 继续使用 `ai4j-agent` | 降低维护成本，符合现有模块标准 | 新增核心 kernel/host Maven | accepted |
| 对外核心命名 | 使用 `AgentRuntime`、`AgentSession`、`AgentFactory`、`AgentBlueprint` 等现有语义 | 避免引入用户不认可的新顶层概念 | 新增 AgentHost 式概念 | accepted |
| 插件生态 | `ai4j-extension-api` 做包契约，`ai4j-agent`/`ai4j-cli` 做运行期贡献点 | 第三方可写，核心依赖轻 | 把所有插件能力塞进一个接口 | accepted |
| Sandbox | 抽象 + 可选 provider/plugin；无 sandbox 即 direct host runtime | 兼顾普通 SDK 和云端 Agent 产品 | 默认内置重型 sandbox runtime | accepted |
| Remote Runner | 作为后续 SPI，不先做云控制平台 | SDK 可帮助产品方接入，但不承担平台运营复杂度 | 直接实现完整云端 Agent 平台 | accepted |
| CLI/TUI | 继续 JLine 路线，优先补交互体验 | 符合 Java 项目维护成本和已有测试 | Ink wrapper 或自研 renderer | accepted |
| Harness 关系 | 保持 Skill/外部治理工具为主，CLI 后续可选适配 | 不强制所有 SDK 用户接受 Harness | 把 Harness 内化进 `ai4j-agent` | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| One-command install 用 native binary、jbang、npm wrapper 还是 zip script | 后续单独调研；当前不拍板 | coordinator | CLI packaging 任务开始前 |
| 是否需要官方 sandbox provider 模块 | 先提供 SPI 和 fake/test provider；真实 provider 由插件或后续官方示例承载 | coordinator | Sandbox provider 任务开始前 |
| 是否开放 TUI render plugin | 先开放 CLI Command Plugin；TUI 渲染扩展等体验稳定后再开放 | coordinator | CLI/TUI 插件任务前 |
| P0/P1/P2 已有任务哪些需要 supersede | 先完成 P2-B，再用 Harness status 和 PR 状态逐项核对 | coordinator | 下一轮 backlog reconciliation |
### 完整任务规划应成为后续实现任务的首读材料

- 背景：用户要求把 `ai4j-agent` 改进完善增强方向、Memory/Compact、YAML Agent、插件生态、Sandbox/Remote Runner、CLI/TUI、docs-site 质量要求整体总结并记录下来。
- 发现：已有 master plan 分散记录了 R0/P0-P7 队列，但后续 agent 仍需要一份单文件入口，避免从长对话中提取结论或遗漏“不要新增 Host Kernel / 不拆核心 Maven / 不写伪 API / sandbox 非默认”等关键边界。
- 影响：新增 `references/agent-sdk-complete-enhancement-task-plan-2026-06-20.md` 作为后续实现任务首读材料；实现前仍必须以最新 `origin/dev`、PR 状态和 `harness status --json` 校准。
- 后续：每个实现切片继续单独建 task/worktree/PR，不能把本规划当成代码已完成的证明。

### 云端 Agent Runner 与 Coding Agent CLI 产品化规划已补充

- 背景：用户继续追问 ai4j-agent 的完整增强方向，包括 sandbox/远端运行环境、memory/compact、YAML Agent、插件生态、CLI/TUI、安装入口和 docs-site 质量。
- 发现：AI4J 应提供 Sandbox / Remote Agent Runner 抽象和 fake/test provider，帮助开发者构建云端 Agent 产品；但不应首版内置重型云控制平台，也不应把 Harness 内化为 SDK 核心能力。
- 影响：新增 `references/agent-sdk-cloud-runner-cli-product-plan-2026-06-21.md`，作为后续实现任务读取的产品化补充规划；推荐先做 backlog reconciliation 和 source-backed research digest，再推进 session/memory/compact、sandbox routing、CLI/TUI、one-command install 和 docs-site completeness。
- 后续：实现任务必须从最新 `origin/dev` 校准，单独创建 Harness task、worktree 和 PR；不能把本规划视为代码已实现证明。
