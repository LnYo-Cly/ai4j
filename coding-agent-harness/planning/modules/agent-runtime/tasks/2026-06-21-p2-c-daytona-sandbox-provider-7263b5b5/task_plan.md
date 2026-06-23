# P2-C Daytona sandbox provider

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-21-p2-c-daytona-sandbox-provider-7263b5b5/artifacts/preset/2026-06-21T11-29-33-892Z
Task Package Index: required

## 目标

在 `ai4j-agent` 内交付首个真实可用的 `SandboxProvider` 实现：`DaytonaSandboxProvider`，让宿主可以通过 env-only Daytona 凭证创建/附加 sandbox、执行 `SandboxCommand`、返回 `SandboxResult`，并在文档和回归台账中明确 deterministic 与 live-provider 验证边界。

## 范围

- 做什么：
  - 新增 `io.github.lnyocly.ai4j.agent.sandbox.daytona` provider、client、config、DTO、session 实现。
  - 支持 Daytona create-or-attach、start/poll、toolbox `/process/execute`、`deleteOnClose` 清理。
  - 覆盖 command、cwd、stdin、env、timeout、stdout/stderr/exitCode 映射。
  - 增加本地 deterministic HTTP fake 测试与 opt-in live smoke 测试。
  - 更新 `docs-site/docs/agent/sandbox-spi.md`、`docs-site/docs/agent/sdk-roadmap.md`、Regression SSoT、Cadence Ledger 和本任务包。
- 不做什么：
  - 不新增 `ai4j-cli /sandbox` 命令。
  - 不新增 ServiceLoader/provider registry 自动发现。
  - 不实现 E2B/CubeSandbox/Docker/K8s provider。
  - 不把 file/git/browser/project runner 全量路由到 sandbox；P3 现有 `bash exec` 路由保持不变。
  - 不提交 API key、租户私密信息或本机路径。
- 主要风险：
  - Daytona live API/Toolbox 字段可能变化，必须用 deterministic test 固化 SDK 映射，并把真实 smoke 作为 opt-in 证据。
  - `SandboxSpec.config` 可能携带敏感字段，因此 docs 和 session binding 不能保存或打印密钥。
  - `deleteOnClose=false` 与 attach/create 行为必须测试清楚，避免误删用户 sandbox。

## 预算选择

选择预算：complex

选择理由：该任务同时触及 agent runtime 代码、真实第三方 sandbox、live-provider 验证、docs-site 和回归治理，需要完整任务包、发现记录、审查和 walkthrough 收口。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox | 现有 Sandbox SPI 合同与 AgentSession binding 边界 | coordinator |
| C-002 | code | TARGET:ai4j-agent/src/test/java/io/github/lnyocly/agent | agent runtime JUnit4 测试风格与 live-provider category 用法 | coordinator |
| C-003 | public-doc | TARGET:docs/11-REFERENCE/testing-standard.md | live-provider opt-in、env-only secret、docs-site build 要求 | coordinator/reviewer |
| C-004 | public-doc | TARGET:docs-site/docs/agent/sandbox-spi.md | 用户面 Sandbox SPI 文档，需要同步 Daytona provider | coordinator |
| C-005 | public-doc | TARGET:docs/05-TEST-QA/Regression-SSoT.md; TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 新 provider 改变固定回归面，需要记录 RG/LV/SRB 证据 | coordinator |

## 步骤

1. 诊断现有 Sandbox SPI、AgentSession binding、live-provider test profile 与 Daytona API/toolbox 执行路径。
2. 实现 Daytona provider/config/client/session/DTO，并保持 Java 8 与 env-only secret 边界。
3. 增加 deterministic local HTTP server 测试，覆盖 attach、create、start、execute、deleteOnClose、config merge/default URL。
4. 增加 opt-in live smoke 测试，使用 `DAYTONA_API_KEY` 环境变量创建 disposable sandbox、执行命令、关闭。
5. 更新 docs-site、Regression SSoT、Cadence Ledger、本任务 progress/findings/review/walkthrough。
6. 执行 targeted/broad/docs/hygiene/harness 验证并提交。

## 验收标准

- [x] `DaytonaSandboxProvider` 支持 `providerId=daytona`，可 create 或 attach Daytona sandbox。
- [x] attach 到 stopped/paused/null state 时会 start 并 poll 到 started；创建态会 poll 到 started。
- [x] `SandboxCommand` 的 command/cwd/stdin/env/timeout 能映射到 toolbox execute 请求。
- [x] `deleteOnClose=true` 时 close 删除 sandbox，`false` 时不删除。
- [x] `createIfMissing=false` 不会被普通 `SandboxSpec` 默认值覆盖。
- [x] 默认 API URL 可用，live smoke 不要求必须显式设置 `DAYTONA_API_URL`。
- [x] 本地 deterministic、agent broad、docs-site build 通过。
- [x] 已记录 Daytona live smoke opt-in 证据，且没有提交密钥。

## 工作树（Worktree）

- 路径：主工作树 `G:/My_Project/java/ai4j-sdk`
- 分支：`docs/agent-final-roadmap-record`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：当前分支
- 未使用 worktree 的原因：本轮是在已有连续 agent-runtime 路线图分支上收口单一 provider 切片；没有与其他 worker 的独立写集并行冲突。

## 长程任务判定

- 是否属于长程任务：是
- 若是，合同文件：本任务包 `task_plan.md` / `progress.md` / `review.md` / `walkthrough.md`
- 连续执行权限：已授权
- Stop Condition 摘要：若 Daytona API 字段与 deterministic/live 行为冲突且无法用非密钥证据确认，停止并记录 blocker。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self（尝试创建子 agent review 时当前并发额度已满，未获得独立 subagent 输出）
- No-finding 要求：无 P0/P1/P2 open material finding；live-provider residual 必须记录为 opt-in 边界。

## 关联

- 相关 Regression Gate：RG-002、RG-008、LV-004、SRB-058
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P2-A Sandbox SPI、P2-B AgentSession sandbox binding、P3 Coding sandbox routing

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：P2-C Daytona sandbox provider
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime P2-C status/review evidence after commit
- Harness Ledger update needed：task plan path, review path, closeout status after lifecycle command
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` 已更新；lifecycle generated ledger 可后续 rebuild

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
