# AI4J extension system wave 1

Task Contract: harness-task/v1
Task Package Index: required

## 目标

为 AI4J 新增第一版扩展系统公共合同，使第三方扩展包可以通过独立轻量 API 声明、发现、启用并按 allowlist 暴露能力。

## 范围

- 做什么：新增 `ai4j-extension-api` Maven 模块；实现 `Ai4jExtension`、`ExtensionManifest`、`ExtensionContext`、ServiceLoader discovery、显式 enable、tool expose allowlist、tool/command/skill/prompt/guardrail 中立 spec 与 registry snapshot；补 JUnit 4 回归；同步 root POM、BOM、CI、AGENTS、Regression SSoT、Cadence Ledger 和 harness context。
- 不做什么：不接入 `ai4j-agent` / `ai4j-coding` / `ai4j-cli` / Spring Boot starter 的运行时适配；不实现 CLI install、Marketplace、hot reload、runtime jar download、provider extension、OpenAI-compatible 中转平台命名。
- 主要风险：过早把 extension API 绑定到现有 agent/CLI 内部类型会锁死第三方合同；新增模块若不进 CI/BOM/回归台账会形成发布盲区。

## 预算选择

选择预算：complex

选择理由：任务横跨新增 Maven 模块、公共 API、测试、CI、BOM、回归治理和 harness v2 context；虽然 Wave 1 实现小，但它改变 monorepo 模块事实和后续生态边界。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | private-plan | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | 前置架构规划，定义 Package / Manifest / Extension / Resource 分层和 Wave 1 边界。 | coordinator / reviewer |
| C-002 | private-plan | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/pi-extension-ecosystem-research.md | Pi package / extension 调研结论，说明为什么 AI4J 不应只做窄 tool plugin。 | coordinator / reviewer |
| C-003 | code | TARGET:pom.xml | 根 reactor、Java baseline 和模块列表。 | coordinator |
| C-004 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool | 现有 agent tool 接口很窄，不适合作为第三方公共 API 直接暴露。 | coordinator / reviewer |
| C-005 | public-doc | TARGET:docs/11-REFERENCE/testing-standard.md | 定义新增模块的回归入口和证据深度。 | coordinator / reviewer |

## 步骤

1. 诊断 Maven 模块、现有 tool/command/skill 接口和回归规则。
2. 新增 `ai4j-extension-api`，实现中立公共合同与 registry runtime。
3. 补 JUnit 4 deterministic tests，覆盖 discovery / enable / expose / capability validation。
4. 同步 POM、BOM、CI、AGENTS、Regression SSoT、Cadence Ledger、harness context 和 module registry。
5. 运行 RG-010、RG-007、diff check、harness status，并提交 review packet。

## 验收标准

- [x] 第三方可只依赖 `ai4j-extension-api` 实现 `Ai4jExtension`。
- [x] ServiceLoader 能发现 extension，但发现不会自动启用。
- [x] `enable(id)` 只启用已发现 extension；未知 id 报错。
- [x] 启用 extension 不会自动暴露 tool；只有 `exposeTool(name)` allowlist 的 tool 进入 snapshot。
- [x] extension 注册未声明 capability 的资源会失败。
- [x] 重复 extension id 和重复资源 id 不会静默覆盖。
- [x] 新模块进入根 reactor、BOM、CI matrix、Regression SSoT/Cadence 和 harness context。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：当前工作树开始时干净，任务由 coordinator 单独执行，没有并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：若需要运行时接入 agent/CLI/Spring，停止并开后续任务。

## 审查判定

- 是否需要对抗性审查：是，采用 self-review + Confidence Challenge；人工确认仍通过 dashboard/workbench。
- 若是，报告文件：`review.md`
- Reviewer：self；human confirmation pending
- No-finding 要求：无 open P0/P1/P2 material finding。

## 关联

- 相关 Regression Gate：RG-010、RG-007；CI PR matrix 同步 RG-010。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f`

## 模块关联（启用模块并行时填写）

- Module：`extension-api`
- Step：EXT-01
- Module Plan：`coding-agent-harness/planning/modules/extension-api/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：`extension-api` 已加入 `harness.yaml` 和 `Module-Registry.md`
- Harness Ledger update needed：task-review / governance rebuild 刷新
- Closeout / Regression update needed：`walkthrough.md`、`docs/05-TEST-QA/*`、`coding-agent-harness/governance/regression/*` 已更新
