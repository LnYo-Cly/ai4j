# AI4J Agent SDK architecture enhancement roadmap

## Task ID

`2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81`

## 创建日期

2026-06-20

## 一句话结果

沉淀 AI4J Agent SDK 下一阶段架构增强路线图，明确 `ai4j-agent` 继续作为 Agent SDK 核心承载层，并按 Session/Memory、Blueprint、插件生态、Sandbox/Remote Runner、Coding CLI/TUI 分阶段推进。

## 完成后能得到什么

本任务完成后，下一轮 agent 或开发者可以直接按任务包里的路线图继续执行：先收口 P2-B 与已有 P0/P1/P2 队列，再逐步完善 Memory/Compact、YAML Agent、插件贡献契约、Sandbox 路由、Remote Agent Runner 与 `ai4j` CLI/TUI 体验。规划明确了模块边界、命名边界、外部 sandbox 接入方式、插件生态分层、安装体验候选路线，以及 docs-site 后续应如何写真实 API 文档。

## 交付物

- 可见产物：`references/agent-sdk-architecture-enhancement-plan.md` 中的完整架构增强规划。
- 修改位置：本 task package、`coding-agent-harness/planning/modules/agent-runtime/module_plan.md` 由 Harness CLI 同步。
- 验证证据：`npx --yes coding-agent-harness status --json .`、`git diff --check`。

## 第一眼应该看什么

1. `references/agent-sdk-architecture-enhancement-plan.md`：完整路线图和阶段拆分。
2. `task_plan.md`：执行范围、上下文包、验收标准和工作树策略。
3. `findings.md`：关键技术判断和不采用方案。
4. `visual_map.md`：路线图依赖关系和任务生命周期。

## 边界

- 范围内：规划 AI4J Agent SDK 的增强方向，记录模块边界、任务顺序、验证策略和后续拆分方式。
- 范围外：本任务不改 Java 实现、不新增 Maven 模块、不实现 sandbox provider、不改 CLI/TUI、不修改 docs-site 用户页面。
- 停止条件：如果后续要改变 Maven 模块边界、引入真实云 sandbox、改变 public API 命名，必须单独开任务并重新确认。

## 完成判断

- [x] 已在 Harness task package 中记录完整规划。
- [x] 已明确 `ai4j-agent` 继续作为 Agent SDK 核心，不新增额外核心 Maven 拆分。
- [x] 已覆盖 memory/compact、YAML Agent、插件生态、sandbox/remote runner、coding CLI/TUI、harness 关系。
- [x] 已记录不做事项：不写不存在 API 示例、不把 harness 内化进核心 SDK、不默认自研 TUI renderer。
- [ ] Harness status 与 diff 检查通过后提交本规划。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行 `git diff --check` 与 `npx --yes coding-agent-harness status --json .`，确认规划材料无模板残留，再提交本 task package。
