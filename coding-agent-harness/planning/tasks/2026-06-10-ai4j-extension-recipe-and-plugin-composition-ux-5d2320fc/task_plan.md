# AI4J Extension Recipe and Plugin Composition UX

Task Contract: harness-task/v1
Task Package Index: required

## 目标

docs-site Extension 章节新增面向使用者的插件 recipe 页面，把现有插件 API、Spring Boot 配置、CLI 检查和多插件组合串成可复制路径。

## 范围

- 做什么：新增 `plugin-recipes.md`，更新 sidebar 和相关 extension 页面交叉链接，补任务材料、Feature SSoT、验证和 walkthrough。
- 不做什么：不改 Java 运行时，不做 marketplace，不做 CLI 自动安装插件依赖，不做 jar 热加载，不做 provider 自动注册。
- 主要风险：文档可能越界承诺尚不存在的能力；需要严格以 `ExtensionRegistry`、CLI `extension plan/run/resource` 和 Spring Boot `ai.extensions.*` 已有实现为边界。

## 预算选择

选择预算：standard

选择理由：任务涉及 docs-site 多页面入口、harness 任务材料、Feature SSoT 和构建验证，但不涉及 Java 行为变更，适合 standard。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc | TARGET:docs-site/docs/core-sdk/extension/plugin-packages.md | 插件包当前权威说明，recipe 必须复用其 discover / enable / allow / expose 边界 | coordinator / reviewer |
| C-002 | public-doc | TARGET:docs-site/docs/core-sdk/extension/ask-user-plugin.md | 官方样板插件资源名和接入方式 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionRegistry.java | 确认 Java API 的 enable / exposeTool / allow* 语义 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | 确认 CLI `extension plan/run/resource` 参数和边界 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiExtensionProperties.java | 确认 Spring Boot 配置键名 | coordinator / reviewer |

## 步骤

1. 审查现有 extension 文档、API、CLI 和 Spring Boot 配置语义。
2. 设计 recipe 页面结构，明确使用者、第三方作者和多插件组合路径。
3. 新增 docs-site 页面并接入 sidebar / 相关链接。
4. 运行 docs-site typecheck/build、harness status 和 diff check。
5. 补齐 progress、findings、review、walkthrough 和 Feature SSoT 收口。

## 验收标准

- [x] `plugin-recipes.md` 覆盖 Java、Spring Boot、CLI 和多插件组合接入。
- [x] 示例全部使用现有资源名和现有配置键，不发明未实现 API。
- [x] 文档明确不包含 marketplace、自动依赖安装、jar 热加载和 provider 自动注册。
- [x] docs-site typecheck/build 通过。
- [ ] harness status 通过，任务包材料完整。

## 工作树（Worktree）

- 路径：不适用
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：本任务为 docs-site 小范围文档与任务材料更新，当前 main 干净且无并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：发现文档需要承诺未实现能力时停止并回到范围决策。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self + human confirmation gate
- No-finding 要求：`review.md` 无 open material finding。

## 关联

- 相关 Regression Gate：docs-site typecheck/build
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：F-039 AI4J Extension Permission and Install UX

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle closeout 时同步
- Closeout / Regression update needed：task-local walkthrough
