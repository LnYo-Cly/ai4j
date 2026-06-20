# AI4J extension ecosystem architecture

Task Contract: harness-task/v1
Task Package Index: required

## 目标

产出 AI4J Extension System 的完整架构方案，明确第三方如何发布插件、使用者如何安装和显式启用、AI4J 各模块如何消费扩展能力，以及首批实现应该从哪些低风险切片开始。

## 范围

- 做什么：完成 Pi 插件生态调研、AI4J 插件分层设计、扩展点优先级、安全/权限模型、模块落点、官方样板插件建议、分波路线和验收标准。
- 不做什么：不改 Java 运行时代码，不新增 Maven 模块，不实现 CLI 命令，不写 docs-site 插件专区正文，不把 OpenAI-compatible 中转平台包装成专属 provider。
- 主要风险：过度对标 Pi 的 npm 动态安装会把 Java SDK 带向高复杂度；过早公开过多扩展点会锁死不成熟 API；自动启用第三方插件会扩大安全面。

## 预算选择

选择预算：complex

选择理由：该规划横跨 `ai4j`、`ai4j-agent`、`ai4j-coding`、`ai4j-cli`、Spring Boot starter、docs-site 和未来第三方生态，既要调研外部 Pi 机制，也要约束 Java 8、Maven、Agent 安全和模块边界。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc | URL:https://pi.dev/docs/latest/packages | Pi package 的安装来源、资源类型、安全提示和 package 结构。 | coordinator / reviewer |
| C-002 | public-doc | URL:https://pi.dev/docs/latest/extensions | Pi extension 的工具、命令、事件、UI、provider 等扩展面。 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent | AI4J agent runtime、tool、memory、trace、workflow 的现有边界。 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding | Coding Agent 的 skill、tool、policy、session、subagent 和 workspace 扩展落点。 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli | CLI slash command、provider profile、MCP、session 和 TUI 扩展落点。 | coordinator / reviewer |
| C-006 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | 保证设计不破坏模块所有权、Java 8 和安全约束。 | coordinator / reviewer |

## 步骤

1. 调研 Pi package / extension 的资源类型、安装模型、安全提示和典型扩展面。
2. 盘点 AI4J 现有模块中可承接扩展的 registry、tool、command、skill、policy、session 和 TUI 边界。
3. 设计 AI4J Package / Manifest / Extension / Resource 四层模型。
4. 定义第一版和后续版本的扩展点优先级，排除不该第一阶段公开的 provider、热加载、marketplace 和完整 RAG API。
5. 写出安全模型、用户体验、第三方开发体验、官方样板插件和分波实施路线。
6. 更新 Feature SSoT、progress、findings、review 和 walkthrough，运行 harness status 与 diff check。

## 验收标准

- [ ] Pi 对标不是停留在“插件很多”，而是拆清 package、extension、skill、prompt、theme 和安全提示。
- [ ] AI4J 方案明确 Package / Manifest / Extension / Resource 分层，并说明 Java/Maven 与 Pi/npm 的差异。
- [ ] 首批扩展点包含 Tool、Command、Skill、Prompt、Guardrail，并说明 Context、AgentMode、SubAgent、MCP、UI、FlowGram 的后续顺序。
- [ ] 明确 OpenAI-compatible 中转平台不是专属 provider plugin，不再随意给平台起 provider 名。
- [ ] 安全模型明确发现、启用、暴露三阶段门禁，且工具暴露默认 allowlist。
- [ ] 分波路线能直接转为后续实现任务。
- [ ] `npx.cmd --yes coding-agent-harness status --json .` 和 `git diff --check` 通过。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本轮只修改 task-local 规划材料和 Feature SSoT，没有运行时代码改动或并行 worker 写入。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果规划被用户要求转入运行时代码实现，必须另开实现任务。

## 审查判定

- 是否需要对抗性审查：是，采用 coordinator self-review 的 architecture/security challenge；本轮不调用 worker。
- 若是，报告文件：`review.md`
- Reviewer：self，后续实现前建议追加 human / reviewer pass
- No-finding 要求：无 P0/P1/P2 阻塞设计发现；残余必须进入后续实现任务。

## 关联

- 相关 Regression Gate：文档/规划材料 L0 gate，不改变固定运行时回归面。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：用户已确认采用 Pi 式第三方可扩展生态方向；本任务承接该方向并固化为架构计划。

## 模块关联（启用模块并行时填写）

- Module：cross-module / base
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 lifecycle CLI 自动同步；本任务进度和 review 由 CLI 推进。
- Closeout / Regression update needed：task-local `walkthrough.md`；Regression SSoT 无新增 gate。
