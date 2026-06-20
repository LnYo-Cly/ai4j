# AI4J Spring Boot extension configuration wave 4

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让 Spring Boot 用户可以通过 `ai.extensions.*` 配置显式启用 classpath 上的 AI4J 插件包，并显式 allowlist 暴露插件工具，形成普通 Java、Agent/Coding Agent 之外的第三个低成本接入口。

## 范围

- 做什么：在 `ai4j-spring-boot-starter` 中新增 extension 配置属性、自动装配 `ExtensionRegistry` / `ExtensionRuntimeSnapshot`，补 starter 回归测试，更新 docs-site 插件包文档、Feature SSoT、Regression SSoT 和 Cadence Ledger。
- 不做什么：不做 marketplace、插件自动安装、运行时 jar 热加载、provider plugin、Spring 自动创建 Agent/Coding Agent、第三方源码审查系统。
- 主要风险：starter 新增依赖和配置面会影响 Spring Boot 用户；必须保持 Java 8 / Spring Boot 2.3 兼容，并坚持 discover / enable / expose 三段式安全门禁。

## 预算选择

选择预算：complex

选择理由：本轮横跨 starter 公开配置、extension API 消费、docs-site、回归治理和 harness closeout，虽然代码改动不大，但属于用户可见集成面。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | starter 只能做配置装配，不能复制 Agent/Coding Agent 运行时逻辑。 | coordinator / reviewer |
| C-002 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | 确定 starter 回归命令和证据深度。 | coordinator / reviewer |
| C-003 | design | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | 承接插件生态的 discover / enable / expose 安全模型。 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension | 复用现有 registry、snapshot 和 ServiceLoader API。 | coordinator |
| C-005 | code | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j | starter 自动配置和属性绑定现状。 | coordinator |

## 步骤

1. 读取 extension API、starter 自动配置、既有测试和插件生态设计。
2. 新增 `AiExtensionProperties`，在 auto-configuration 中绑定 `ai.extensions.enabled` 和 `ai.extensions.tools.expose`。
3. 装配 `ExtensionRegistry` 与 `ExtensionRuntimeSnapshot`，不引入 Agent/Coding Agent 传递依赖。
4. 编写 Spring Boot starter 测试，覆盖“发现但未启用不暴露”和“显式启用+expose 后可用”。
5. 更新 docs-site、README/索引必要链接、Feature SSoT、Regression SSoT、Cadence Ledger、task-local progress/review/walkthrough。
6. 运行 targeted Maven、package smoke、docs-site build/typecheck 和 harness status。

## 验收标准

- [ ] Spring Boot 用户可以配置 `ai.extensions.enabled` 启用已在 classpath 上发现的插件包。
- [ ] Spring Boot 用户可以配置 `ai.extensions.tools.expose` 暴露插件工具，未暴露的工具不会进入 runtime snapshot。
- [ ] starter 自动装配不自动创建 Agent/Coding Agent，不把 `ai4j-agent` 作为 starter 新传递依赖。
- [ ] 配置错误（启用未发现插件）保持显式失败，不静默忽略。
- [ ] starter 测试覆盖配置绑定和 runtime snapshot。
- [ ] docs-site 说明 Spring Boot 插件配置边界，不声称支持 marketplace、install、hotload 或 provider plugin。
- [ ] `mvn -pl ai4j-spring-boot-starter -am -Dtest=ExtensionAutoConfigurationTest -DfailIfNoTests=false -DskipTests=false test` 通过。
- [ ] `mvn -DskipTests package`、docs-site typecheck/build、harness status 通过或记录明确 residual。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：用户要求继续并一起做完；本轮是单线小范围 starter/docs/governance 改动，未启用并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果需要 marketplace、install/hotload、provider plugin 或 Agent 自动创建，必须另开任务。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self，最终仍进入 harness review queue 等待人工确认
- No-finding 要求：无 open material finding；残余必须写入 review/walkthrough。

## 关联

- 相关 Regression Gate：RG-005 Spring Boot starter、RG-007 monorepo package smoke、RG-008 docs-site、harness status
- 审查报告：[路径 / 不适用]
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f`、`2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5`

## 模块关联（启用模块并行时填写）

- Module：spring-starter / extension-api / docs-site
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/spring-starter/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：spring-starter / docs-site module plans may need note after implementation
- Harness Ledger update needed：由 lifecycle CLI 自动同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、task-local `walkthrough.md`
