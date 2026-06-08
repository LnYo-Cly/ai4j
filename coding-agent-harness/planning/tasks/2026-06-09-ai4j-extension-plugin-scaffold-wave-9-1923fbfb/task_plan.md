# AI4J extension plugin scaffold wave 9

Task Contract: harness-task/v1
Task Package Index: required

## 目标

新增本地插件项目脚手架命令，让第三方开发者能快速生成可编译、可校验、可按需替换业务逻辑的 AI4J extension plugin package。

## 范围

- 做什么：在 `ai4j-cli` 增加 `extension init`；生成 Maven Java 8 插件骨架；补 CLI tests、README、docs-site plugin package 文档；更新 Feature / Regression / Cadence 治理记录和 walkthrough。
- 不做什么：不做远程插件市场，不写入使用者项目依赖，不做运行时 jar 热加载，不改变 extension public API，不把 provider extension 混入 plugin package。
- 主要风险：生成代码必须严格匹配当前 extension API；CLI 不能覆盖用户非空目录；文档不能暗示 AI4J 会自动安装或信任第三方插件。

## 预算选择

选择预算：complex

选择理由：该任务同时触及 CLI 行为、生成代码、docs-site 文档和治理表，并需要对第三方开发者 onboarding contract 做明确边界。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | extension CLI 子命令入口和帮助输出 | coordinator |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension | 生成项目必须使用当前公共 API | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java | CLI targeted regression 入口 | coordinator |
| C-004 | docs | TARGET:docs-site/docs/core-sdk/extension/plugin-packages.md | 插件生态使用者与开发者路径的主要文档 | coordinator / reviewer |
| C-005 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md; TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 变更 CLI/docs 后需要记录回归证据 | coordinator |

## 步骤

1. 更新任务包与 Feature SSoT，锁定本轮只做本地脚手架。
2. 在 `CliExtensionCommand` 增加 `init` 子命令、参数解析、目录安全检查和文件生成。
3. 更新顶层 CLI help 与 extension help。
4. 补充 CLI tests：生成结构、内容关键字段、非空目录拒绝、帮助文案。
5. 更新 README 与 docs-site plugin package 文档。
6. 更新 Regression SSoT / Cadence Ledger，执行目标回归和 docs-site build。
7. 填写 review / walkthrough / lesson candidates，提交 `task-review`，提交并推送。

## 验收标准

- [ ] `ai4j-cli extension init <directory> --id weather-pack --package com.example.ai4j.weather --name "Weather Pack"` 在空目录生成预期文件。
- [ ] 生成项目包含 `pom.xml`、`Ai4jExtension` 实现、`META-INF/services`、示例 skill/prompt、validator test 和 README。
- [ ] 非空目录默认返回 argument error，不覆盖已有内容。
- [ ] CLI tests 覆盖新命令；临时脚手架 Maven smoke 可执行或记录明确环境残余。
- [ ] 文档准确说明命令、生成结构、验证方式和当前边界。

## 工作树（Worktree）

- 路径：n/a
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：当前任务由 coordinator 在同一 checkout 串行完成；没有授权写入 worker subagent。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：用户已要求“继续，一起做完”
- Stop Condition 摘要：需要改变公共 API、覆盖已有用户文件、或引入远程安装语义时暂停。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self + harness material check
- No-finding 要求：无 open P0/P1/P2 material finding，目标回归通过。

## 关联

- 相关 Regression Gate：RG-004、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：Wave 8 `2026-06-09-ai4j-extension-authoring-and-validation-wave-8-e4b994a7`

## 模块关联（启用模块并行时填写）

- Module：ai4j-cli
- Step：extension-plugin-scaffold
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：Feature SSoT F-031、Regression SSoT、Cadence Ledger
- Harness Ledger update needed：task lifecycle CLI 负责
- Closeout / Regression update needed：`walkthrough.md`、`docs/05-TEST-QA/*`
