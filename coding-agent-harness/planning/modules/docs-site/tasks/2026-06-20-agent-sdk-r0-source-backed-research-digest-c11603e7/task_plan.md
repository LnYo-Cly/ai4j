# Agent SDK R0 source backed research digest

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/docs-site/tasks/2026-06-20-agent-sdk-r0-source-backed-research-digest-c11603e7/artifacts/preset/2026-06-20T15-34-41-168Z
Task Package Index: required

## 目标

用公开资料支撑 AI4J Agent SDK 后续方向，避免对 Pi、Codex、Claude Code、OpenCode、Java AI SDK 和 sandbox provider 的设计判断依赖记忆或猜测。

## 范围

- 做什么：写 task-local digest、docs-site 页面、sidebar/roadmap 链接和完整 task package 材料。
- 不做什么：不实现代码、不修改 public API、不执行 provider token、不复制泄露源码、不声称 AI4J 已实现规划能力。
- 主要风险：Pi/CubeSandbox 等公开资料有限；不同产品技术栈差异大，不能把 JS/TS/Rust CLI 的实现方式直接投射到 Java 8/JLine 项目。

## 预算选择

选择预算：complex

选择理由：本任务跨外部公开资料、AI4J 产品定位、docs-site、后续任务队列和 Harness 记录，需要完整 source list、source gap、设计约束和审查材料。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | 仓库模块边界、Harness 流程和 docs-site 验证命令 | coordinator / reviewer |
| C-002 | roadmap | TARGET:docs-site/docs/agent/sdk-roadmap.md | 已有 Agent SDK 路线图，需要链接 R0 digest | coordinator / reviewer |
| C-003 | docs-sidebar | TARGET:docs-site/sidebars.ts | 让新页面进入 docs-site 导航 | coordinator |
| C-004 | task-reference | TARGET:coding-agent-harness/planning/modules/docs-site/tasks/2026-06-20-agent-sdk-r0-source-backed-research-digest-c11603e7/references/agent-sdk-r0-source-backed-research-digest.md | 完整公开资料 digest | coordinator / reviewer / worker |
| C-005 | public-source | URL:https://pt-act-pi-mono.mintlify.app/packages/coding-agent | Pi coding-agent TUI/session/extensions/skills/tools/run modes | coordinator / reviewer |
| C-006 | public-source | URL:https://developers.openai.com/codex/cli | Codex CLI 安装、TUI、模型切换、审批、安全、MCP | coordinator / reviewer |
| C-007 | public-source | URL:https://code.claude.com/docs/en/overview | Claude Code 公开能力边界 | coordinator / reviewer |
| C-008 | public-source | URL:https://opencode.ai/docs/plugins/ | OpenCode 插件 hook/custom tool/permission/session/tool/TUI 事件 | coordinator / reviewer |
| C-009 | public-source | URL:https://docs.spring.io/spring-ai/reference/api/chatclient.html | Spring AI ChatClient/Advisor 生态对比 | coordinator / reviewer |
| C-010 | public-source | URL:https://docs.langchain4j.dev/ | LangChain4j Java AI SDK 对比 | coordinator / reviewer |
| C-011 | public-source | URL:https://java.agentscope.io/v2/en/intro.html | AgentScope Java 对比 | coordinator / reviewer |
| C-012 | public-source | URL:https://e2b.dev/docs | Sandbox provider lifecycle/filesystem/commands/snapshots | coordinator / reviewer |
| C-013 | public-source | URL:https://www.daytona.io/docs/ | Sandbox provider process/filesystem/terminal/snapshot | coordinator / reviewer |
| C-014 | public-source | URL:https://modal.com/docs/guide/sandboxes | 隔离执行环境参考 | coordinator / reviewer |

## 步骤

1. 核对现有 R0 worktree、task package 和 docs-site Agent sidebar。
2. 搜索并读取公开资料，优先官方 docs / GitHub / provider docs。
3. 写 `references/agent-sdk-r0-source-backed-research-digest.md`，明确结论、source gap 和 AI4J 设计约束。
4. 写 `docs-site/docs/agent/source-backed-research-digest.md`，并从 sidebar 与 `sdk-roadmap.md` 链接。
5. 补齐 brief、plan、findings、review、lesson_candidates、walkthrough、references/artifacts 索引。
6. 运行 docs build、diff check、token fragment scan、Harness status。
7. 提交实现 diff，再运行 `task-review` 进入待人工确认。

## 验收标准

- [x] Task-local digest 至少覆盖 Pi、Codex、Claude Code、OpenCode、Spring AI、LangChain4j、AgentScope Java、E2B/Daytona/Modal/CubeSandbox。
- [x] docs-site 页面不写伪 API，不声称规划能力已发布。
- [x] sidebar 与 roadmap 都能导航到新页面。
- [x] source gap 明确，尤其是 Pi 内部实现和 CubeSandbox 细节。
- [x] `npm --prefix docs-site run build` 通过。
- [x] `git diff --check` 通过。
- [x] token fragment scan 无命中。
- [x] Harness status failures=0。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\docs\agent-sdk-r0-research-digest`
- 分支：`docs/agent-sdk-r0-research-digest`
- Worker owner：coordinator
- Worker handoff commit required：yes，PR 前提交本 worktree diff
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用，已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否，本任务是 R0 研究 digest 切片。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续推进整体 program；本切片仍按 task package 收口。
- Stop Condition 摘要：如果需要声明某个外部产品内部实现，必须停下补 source；无法公开验证则标注 source gap。

## 审查判定

- 是否需要对抗性审查：是，self adversarial review。
- 若是，报告文件：`review.md`
- Reviewer：self；PR 后可由 GitHub review/CI 继续检查。
- No-finding 要求：不得存在 P0/P1/P2 open finding；source gap 必须降级为非阻塞且不影响已写结论。

## 关联

- 相关 Regression Gate：docs-site build；无 Java regression。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：Agent SDK roadmap 规划任务；当前 R0 digest 为后续实现门禁。

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：DOCS-R0-AGENT-SDK-RESEARCH
- Module Plan：`coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：docs-site module plan 已由 Harness CLI 同步本任务；PR 后更新状态。
- Harness Ledger update needed：task lifecycle command 负责同步。
- Closeout / Regression update needed：规划/文档任务不新增固定 Regression SSoT；docs build 证据记录在 progress/review。

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | docs-site |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/docs-site/brief.md | Docs-site module purpose and scope. |
| Module plan | coding-agent-harness/planning/modules/docs-site/module_plan.md | Module steps and handoff state. |
| Docs sidebar | docs-site/sidebars.ts | Navigation entry for the new page. |
