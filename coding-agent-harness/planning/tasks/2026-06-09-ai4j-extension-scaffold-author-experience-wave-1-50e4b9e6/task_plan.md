# AI4J Extension Scaffold Author Experience Wave 11

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让第三方插件作者通过 `ai4j-cli extension init` 生成的骨架直接获得可发布、可验证、边界清楚的 authoring 起点。

## 范围

- 做什么：增强 scaffold README；补 docs-site 插件作者 cookbook；更新 CLI scaffold 测试和 harness/SSoT 记录。
- 不做什么：不新增远程 marketplace、不自动安装 Maven 依赖、不热加载 jar、不改 `ai4j-extension-api` 公共合同、不改变 Agent/Coding Agent runtime 执行语义。
- 主要风险：文档误导用户以为 AI4J 会安装/信任/自动暴露第三方插件；测试只覆盖文件存在而没有锁住 README 合同。

## 预算选择

选择预算：complex

选择理由：本轮跨 CLI scaffold、docs-site 和 harness 治理记录，且需要避免插件生态语义漂移。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/ExtensionScaffoldGenerator.java | 当前 scaffold 生成器实现和 README 模板来源 | coordinator / reviewer |
| C-002 | test | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java | CLI scaffold、validate、resource、run 行为的现有回归入口 | coordinator / reviewer |
| C-003 | public-doc | TARGET:docs-site/docs/core-sdk/extension/plugin-packages.md | 插件包现有使用者/作者说明和当前边界 | coordinator / reviewer |
| C-004 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | 模块边界、Java 8 和安全约束 | coordinator / reviewer |
| C-005 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | CLI/docs 变更验证入口 | coordinator / reviewer |

## 步骤

1. 更新 F-033 与任务边界，确认不扩大到远程插件分发。
2. 改进 scaffold README 模板，补齐作者发布清单、使用者集成路径、CLI 验证路径和权限/副作用说明。
3. 新增或更新 docs-site author cookbook，并从 Plugin Packages 页面链接。
4. 补 CLI scaffold 测试，锁住 README 合同。
5. 运行 targeted Java/docs/harness 验证，记录 evidence，提交 review packet。

## 验收标准

- [x] 生成的 README 明确 plugin package 是普通 Maven jar，不会被 AI4J 远程安装、自动启用或自动暴露给模型。
- [x] README 包含 manifest/resource 清单、Maven 使用方式、Spring Boot 配置方式、CLI validate/inspect/resource/run 验证路径。
- [x] docs-site author cookbook 能独立指导第三方作者完成 scaffold -> 替换业务逻辑 -> 校验 -> 发布说明。
- [x] CLI 测试覆盖新增 README 关键文本。
- [x] targeted Maven、docs-site typecheck/build、diff check、harness status 均通过或记录受控 residual。

## 工作树（Worktree）

- 路径：n/a
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：当前 checkout 干净，改动范围集中且由 coordinator 串行执行；无并行写入 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：一旦需要公共 API 扩容、远程安装语义或 runtime hotload 语义，停止并重新确认。

## 审查判定

- 是否需要对抗性审查：是，采用 coordinator self-review，重点挑战文档是否夸大插件生态能力。
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：无 P0/P1/P2 material finding；残余风险写入 review 和 walkthrough。

## 关联

- 相关 Regression Gate：RG-007 CLI regression；RG-011 docs-site build/typecheck；RG-010 harness governance status
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-extension-scaffold-author-experience-wave-1-50e4b9e6/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：Wave 8 validation、Wave 9 scaffold、Wave 10 official ask-user plugin

## 模块关联（启用模块并行时填写）

- Module：cli-host / docs-site / extension-api
- Step：CLI scaffold author experience / docs author cookbook
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`、`coding-agent-harness/planning/modules/docs-site/module_plan.md`、`coding-agent-harness/planning/modules/extension-api/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-review
- Registry update needed：按实际改动更新 module plan / registry 如有必要
- Harness Ledger update needed：task lifecycle CLI 自动更新
- Closeout / Regression update needed：`progress.md`、`review.md`、`walkthrough.md`、Regression/Cadence 若新增固定门禁
