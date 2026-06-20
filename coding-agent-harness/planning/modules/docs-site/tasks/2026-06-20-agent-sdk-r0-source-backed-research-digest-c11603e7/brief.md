# Agent SDK R0 source backed research digest

## Task ID

`2026-06-20-agent-sdk-r0-source-backed-research-digest-c11603e7`

## 创建日期

2026-06-20

## 一句话结果

沉淀一份公开资料支撑的 Agent SDK R0 调研 digest，约束 AI4J 后续插件生态、CLI/TUI、Memory/Compact、Sandbox/Remote Runner 和 docs-site 改进不再凭印象设计。

## 完成后能得到什么

完成后，后续实现任务可以直接读取 task-local digest 和 docs-site 页面，知道 Pi、Codex、Claude Code、OpenCode、Spring AI、LangChain4j、AgentScope Java、E2B/Daytona/Modal 等公开资料对 AI4J 的真实启发与边界。它会明确哪些模式值得借鉴，哪些来源不足不能臆造，以及每个后续任务应如何落到 AI4J 现有模块边界。

## 交付物

- 可见产物：`docs-site/docs/agent/source-backed-research-digest.md`
- 修改位置：docs-site Agent sidebar、`docs-site/docs/agent/sdk-roadmap.md`、本 task package references/progress/review/walkthrough。
- 验证证据：`npm --prefix docs-site run build`、`git diff --check`、token fragment scan、`npx --yes coding-agent-harness status --json .`。

## 第一眼应该看什么

1. `references/agent-sdk-r0-source-backed-research-digest.md`：完整公开资料 digest。
2. `docs-site/docs/agent/source-backed-research-digest.md`：用户可读的 docs-site 页面。
3. `findings.md`：关键判断和 source gap。
4. `review.md`：资料边界和无重要发现声明。

## 边界

- 范围内：公开资料 digest、docs-site 技术页面、sidebar/roadmap 链接、task-local review/progress/walkthrough。
- 范围外：不改 Java 实现；不实现插件、sandbox、runner 或 CLI 命令；不复制泄露源码；不使用用户提供的 provider token 测试。
- 停止条件：如果来源无法公开验证，只记录 source gap，不把它写成已确认事实。

## 完成判断

- [x] Digest 覆盖 Pi、Codex、Claude Code、OpenCode、Spring AI、LangChain4j、AgentScope Java、sandbox providers。
- [x] docs-site 新增可读页面并从 Agent sidebar/roadmap 可达。
- [x] 对 source gap 和不做事项明确标注。
- [x] 验证命令通过并记录在 `progress.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行 docs build、diff check、token fragment scan 和 Harness status；通过后提交并进入 `task-review`。
