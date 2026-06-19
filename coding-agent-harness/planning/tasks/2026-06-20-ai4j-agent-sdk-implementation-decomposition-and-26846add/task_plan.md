# AI4J Agent SDK implementation decomposition and docs roadmap

Task Contract: harness-task/v1
Task Package Index: required

## 目标

承接已认可的 `ai4j-agent` 架构增强规划，完成可执行任务拆解，并把关键技术路线投影到 docs-site，供开发者理解 AI4J Agent SDK 的演进边界。

## 范围

- 做什么：P0-P5 任务拆解、docs-site Agent SDK roadmap 页面、sidebar/overview 入口、自测和 PR。
- 不做什么：不改 Java 生产代码，不提交任何 provider token，不实现 sandbox/blueprint/runtime API。
- 主要风险：文档先行可能和后续实现细节漂移；需在每个 implementation task 中回写 docs-site。

## 预算选择

选择预算：complex

选择理由：本任务跨 Harness 任务治理、docs-site 文档、PR/验证和后续任务队列拆解，需要完整 references/artifacts/review/walkthrough。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-enhancement-plan.md | 上一轮已认可架构规划 | coordinator / reviewer |
| C-002 | docs | TARGET:docs-site/docs/agent/architecture.md | 当前 Agent 架构说明 | coordinator / reviewer |
| C-003 | docs | TARGET:docs-site/docs/coding-agent/architecture.md | Coding Agent 边界说明 | coordinator / reviewer |
| C-004 | docs | TARGET:docs-site/sidebars.ts | docs-site 导航入口 | coordinator / reviewer |
| C-005 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-implementation-decomposition-and-26846add/references/ai4j-agent-implementation-roadmap.md | 本任务拆解路线图 | future workers |

## 步骤

1. 创建独立 worktree：`.worktrees/docs/ai4j-agent-architecture-roadmap`。
2. 创建并启动 Harness 任务。
3. 编写 P0-P5 实施拆解路线图。
4. 更新 docs-site Agent 技术路线页面和导航入口。
5. 运行 docs-site build 与 Harness status。
6. 提交、推送并创建 PR。

## 验收标准

- [ ] `references/ai4j-agent-implementation-roadmap.md` 覆盖 P0-P5。
- [ ] `docs-site/docs/agent/sdk-roadmap.md` 存在并能被 sidebar 访问。
- [ ] docs-site build 通过。
- [ ] Harness status 通过或无阻塞 failure。
- [ ] PR 已创建，说明验证命令和残余风险。

## 工作树（Worktree）

- 路径：TARGET:.worktrees/docs/ai4j-agent-architecture-roadmap
- 分支：docs/ai4j-agent-architecture-roadmap
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：main
- 未使用 worktree 的原因：不适用；本任务已使用 worktree。

## 长程任务判定

- 是否属于长程任务：否
- Stop Condition 摘要：如果开始修改 Java API 或实现代码，停止并另开 implementation task。

## 审查判定

- 是否需要对抗性审查：否
- Reviewer：self + PR review
- No-finding 要求：文档不应承诺已实现能力；必须明确路线图和当前边界。

## 模块关联

- Module：agent-runtime / docs-site
- Step：implementation-decomposition-and-docs-roadmap

## 协调者交接

- Global sync owner：coordinator
- Global sync status：pending until PR
- Registry update needed：不适用
- Closeout / Regression update needed：docs-site build 证据写入 progress/walkthrough
