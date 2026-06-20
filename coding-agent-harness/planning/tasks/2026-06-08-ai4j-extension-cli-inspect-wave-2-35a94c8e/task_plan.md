# AI4J extension CLI inspect wave 2

Task Contract: harness-task/v1
Task Package Index: required

## 目标

为 `ai4j-cli` 增加 `extension list/inspect`，让用户能审查 classpath 上发现的 AI4J extension，并可显式查看 runtime 贡献清单。

## 范围

- 做什么：在 `ai4j-cli` 增加 top-level `extension` 命令；实现 `list`、`inspect <id>`、`inspect <id> --runtime`；补 CLI deterministic tests；必要时同步回归台账和 harness 任务材料。
- 不做什么：不做 `extension install`、持久化 enable、Spring Boot 配置绑定、Agent/Coding runtime adapter、Marketplace、runtime jar download、provider plugin。
- 主要风险：默认 inspect 如果执行 extension `apply()` 可能触发第三方代码副作用；因此默认只读 manifest，`--runtime` 才临时 apply。

## 预算选择

选择预算：complex

选择理由：任务跨 `ai4j-cli`、`ai4j-extension-api` 消费面、测试、回归治理和 harness 材料；行为面不大，但涉及插件安全边界。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | private-plan | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | 定义 extension CLI 的 list/inspect/enable 分波和安全三道门。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension | Wave 1 公共合同和 registry/snapshot 行为。 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/Ai4jCli.java | top-level CLI command routing。 | coordinator |
| C-004 | code | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java | CLI routing regression pattern。 | coordinator |
| C-005 | public-doc | TARGET:docs/11-REFERENCE/testing-standard.md | CLI regression entrypoint。 | coordinator / reviewer |

## 步骤

1. 诊断 CLI top-level routing、测试模式和 extension API 可消费边界。
2. 增加 `CliExtensionCommand`，接入 `Ai4jCli` 的 `extension` 命令和 help。
3. 补 ServiceLoader fixture tests，覆盖 list、inspect、unknown id、runtime inspect、help。
4. 同步回归/任务材料，运行 targeted CLI tests、package smoke、diff check、harness status。
5. 提交实现并提交 Agent Review Submission。

## 验收标准

- [x] `ai4j-cli extension list` 能列出 classpath 发现的 extension，并显示 id/name/version/capabilities/sourceClass。
- [x] `ai4j-cli extension inspect <id>` 能展示 manifest、permissions、configPrefix，不执行 runtime apply。
- [x] `ai4j-cli extension inspect <id> --runtime` 能临时列出 tools/commands/skills/prompts/guardrails。
- [x] 未知 extension id 返回 exit code 2 并提示错误。
- [x] `ai4j-cli` 依赖 `ai4j-extension-api`，但不改变 code/tui/acp 的既有行为。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：工作树干净，任务切片集中且无并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如果要实现 enable 持久化或 agent runtime adapter，停止并开后续任务。

## 审查判定

- 是否需要对抗性审查：是，采用 self-review + Confidence Challenge；人工确认仍通过 harness workbench。
- 若是，报告文件：`review.md`
- Reviewer：self；human confirmation pending
- No-finding 要求：无 open P0/P1/P2 material finding。

## 关联

- 相关 Regression Gate：RG-010、CLI targeted regression；如新增固定 CLI gate，同步 Regression SSoT。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-08-ai4j-extension-system-wave-1-a924bf99`

## 模块关联（启用模块并行时填写）

- Module：`cli-host`
- Step：CLI-EXT-01
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced-before-review
- Registry update needed：已同步 `cli-host` module plan 的 `CLI-EXT-01`
- Harness Ledger update needed：task-review / task-complete 后由 lifecycle CLI 刷新
- Closeout / Regression update needed：已同步 `docs/05-TEST-QA/*` 和 `coding-agent-harness/governance/regression/*`
