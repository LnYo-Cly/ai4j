# Agent Runtime backlog reconciliation after runner merge - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### PR #118 已合并到 `dev`

- 背景：P5 Remote Agent Runner SPI 是 Agent SDK 路线图里的最后一个基础切片；合并后 backlog 需要从“实现推进”切到“状态收口/下一轮 polish”。
- 发现：`gh pr view 118` 返回 `state=MERGED`，`baseRefName=dev`，`headRefName=feature/agent-runner-spi`，merge commit 为 `5f4426c9909ffa62851c40bacbc3617c87700287`，mergedAt 为 `2026-06-20T06:37:26Z`。
- 影响：`P5 Remote Agent Runner SPI contract` 不应再作为待实现项；它应标记为 `merged-on-dev / review-confirmation-pending / closeout-pending`。
- 后续：只保留人工确认与 closeout，不重复实现 runner SPI。

### P0/P1/P2/P5 关键代码与文档均存在于当前 worktree

- 背景：`module_plan.md` 仍有多处 `implementation-verified`、`planning-recorded`、`PR pending` 等旧状态，需要用当前文件事实校准。
- 发现：以下关键路径均存在：
  - P0-A：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/AgentSessionStore.java`
  - P0-B：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/context/ContextProjector.java`
  - P0-C：`ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/lifecycle/AgentLifecycleHook.java`
  - P0-D：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/permission/AgentPermissionPolicy.java`
  - P1-A：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/AgentBlueprint.java`
  - P1-B：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/AgentFactory.java`
  - P1-C：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/AgentBlueprintRunCommand.java`
  - P2-A：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/SandboxProvider.java`
  - P2-B：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/AgentSessionSandboxBinding.java`
  - P5：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runner/AgentRunnerProvider.java`
  - docs：`docs-site/docs/agent/{session-runtime,memory-compact-context,plugin-lifecycle-hooks,approval-permission-policy,agent-blueprint,sandbox-spi,remote-agent-runner-spi}.md`
- 影响：后续不应按“缺基座”推进，而应进入 API polish、插件贡献合同和 docs completeness。
- 后续：module plan 状态表改为 `merged-on-dev` + lifecycle residual，而不是 implementation pending。

### 当前没有指向 `dev` 的 open PR

- 背景：如果仍有 open PR，module plan 需要保留 integration risk。
- 发现：`gh pr list --base dev --state open` 返回空列表。
- 影响：当前 backlog 的主要风险不是 PR 堆积，而是 Harness 人工确认/closeout 队列未收口，以及下一步切片未明确。
- 后续：以 Harness review/closeout 清理和下一轮 implementation task 为主。

### P3/P4 已在 coding/cli 模块落地，不应继续算 agent-runtime 待实现项

- 背景：路线图里 sandbox routing 和 `/sandbox` 命令跨越 `ai4j-coding`、`ai4j-cli`，容易被 agent-runtime backlog 重复承接。
- 发现：`docs-site/docs/coding-agent/sandbox-routing.md`、`docs-site/docs/coding-agent/command-reference.md` 和 `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/sandbox` 存在；`git log` 显示相关切片随 `91e07b1 feat(cli): expose sandbox session commands` 进入 `dev`。
- 影响：agent-runtime 后续只跟踪它提供的 Sandbox/Runner 抽象；coding/cli UX 继续由对应模块承接。
- 后续：如要继续做 `/memory`、`/compact`、安装体验或全屏 TUI，应创建 `cli-host` / `coding-runtime` 模块任务。

### 下一步 true implementation slice 应是 Memory/Compact Session API polish

- 背景：P0-P5 已有基础合同，但用户目标是“更好用的 AI Agent SDK”，不是单纯堆 SPI。
- 发现：Session、Memory、Compact 是 Java Agent SDK 易用性的核心，也是 Blueprint、CLI `/compact`、remote runner resume 的共同基础。插件贡献合同也重要，但依赖稳定的 session/memory/compact 接入点。
- 影响：后续优先级建议为：1) Memory/Compact Session API polish；2) Plugin contribution contract expansion；3) docs-site API completeness pass；4) CLI/TUI `/memory` `/compact` UX。
- 后续：新建实现任务时主模块仍为 `agent-runtime`，验证至少覆盖 `ai4j-agent` targeted + broad tests，docs-site 示例必须对齐真实 API。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Backlog 状态表口径 | `merged-on-dev` + `review-confirmation-pending` + `closeout-pending` | 区分代码合并事实和 Harness lifecycle 事实，避免误判 | 继续用 `implementation-verified` 或直接 `done` | accepted |
| 下一步实现切片 | Memory/Compact Session API polish | 最高复用价值，支撑 YAML、CLI、Runner 和长程 Agent 体验 | 先做插件市场/CLI 安装 | accepted |
| 本轮验证深度 | L1 | 规划/状态 reconciliation，不改代码 | 跑全 Maven/docs build | accepted |
| P3/P4 所属模块 | coding-runtime / cli-host | sandbox routing 和 slash command 是 coding/CLI 产品层，不是 agent-runtime 内核 | 继续挂在 agent-runtime backlog | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 这些 review 队列任务是否逐个走 human review-confirm？ | 需要，但本任务不能代替人工确认 | human / coordinator | 进入 closeout 前 |
| Memory/Compact polish 的具体 API 是否保持 Java 8 且最小 public surface？ | 应作为下一实现任务的首要设计问题 | coordinator | 新建实现任务时 |
| docs-site 是否需要单独“真实 API 示例审计”任务？ | 建议需要，晚于 API polish 或与其并行 | coordinator | 下一轮 docs-site planning |
