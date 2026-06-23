# P4 CLI sandbox commands and status UX

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/cli-host/tasks/2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6/artifacts/preset/2026-06-21T15-53-44-258Z
Task Package Index: required

## 目标

交付 `ai4j-cli code` 的 `/sandbox` 交互命令，使用户可以在当前 session 中查看、启用、attach 和关闭 Daytona sandbox，并让后续 agent shell exec 使用 live `SandboxSession`。

## 范围

- 做什么：新增 CLI 侧 sandbox command/state；补齐 slash palette、completion、help、status；将 active sandbox session 传给 `CodingCliAgentFactory.prepare(..., SandboxSession)` 并重建当前 `ManagedCodingSession`；写 CLI tests、task closeout 和回归台账。
- 不做什么：不新增公共 provider registry；不把 API key 作为 slash 参数；不改 Daytona provider 已验证 HTTP contract；不承诺 file/MCP/browser/background process 全部进入 sandbox。
- 主要风险：远端执行是安全敏感面；CLI 必须避免 silent local fallback、避免记录密钥，并在 attach/enable 失败时保持原 runtime。

## 预算选择

选择预算：complex

选择理由：该任务跨 CLI command、TUI/main-buffer rendering、agent runtime rebind、真实 sandbox provider 与回归治理材料，且需要明确安全边界和后续 residual。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | slash dispatch、status rendering、runtime switching 插入点 | coordinator/reviewer |
| C-002 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java | slash palette 与 completion 入口 | coordinator/reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory/CodingCliAgentFactory.java | CLI runtime factory contract，需要支持可选 SandboxSession | coordinator/reviewer |
| C-004 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/daytona/DaytonaSandboxProvider.java | 首批真实 provider，env-only credential | coordinator/reviewer |
| C-005 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | CLI/remote execution 边界与 Java 8 约束 | coordinator/reviewer |
| C-006 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | CLI/TUI 与 live-provider 测试策略 | coordinator/reviewer |

## 步骤

1. 任务包修复：替换模板占位，记录设计、代码事实、范围边界和验证计划。
2. CLI state/controller：新增 sandbox binding/session state，解析 `/sandbox status|enable|attach|disable`，并安全 close/restore。
3. Runtime wiring：扩展 `CodingCliAgentFactory` 可选 `SandboxSession` overload，Default factory 调用 `CodingAgentBuilder.sandbox(...)`。
4. UX surface：更新 help、status、main-buffer palette、SlashCommandController built-ins/completion。
5. Regression：新增/更新 targeted tests；运行 CLI targeted、CLI module、必要 docs build；更新 Regression SSoT/Cadence Ledger。
6. Review/closeout：写 review、walkthrough、lesson decision，提交并推送。

## 验收标准

- [ ] `/sandbox` 命令在 help、slash palette 和 completion 可见。
- [ ] `/sandbox status` 无 sandbox 时显示 direct-host/local；启用后显示 provider/session/status/spec/deleteOnClose。
- [ ] `/sandbox enable daytona` 与 `/sandbox attach daytona <id-or-name>` 通过 env-only `DaytonaSandboxProvider` 创建 live session，不接受 raw API key 参数。
- [ ] 启用/关闭 sandbox 会通过 `switchSessionRuntime` 重建当前 session，且失败时不破坏原 runtime。
- [ ] Targeted tests、`mvn -pl ai4j-cli -am -DskipTests=false test`、Harness status 通过；live-provider 结果或 skip/residual 被记录。

## 工作树（Worktree）

- 路径：主工作树 `G:\My_Project\java\ai4j-sdk`
- 分支：`docs/agent-final-roadmap-record`
- Worker owner：coordinator；后续可用 subagent 做 review，不使用写入型 worker，避免 CLI shared file 冲突。
- Worker handoff commit required：不适用
- Coordinator integration branch：当前分支
- 未使用 worktree 的原因：本轮延续已启动的主分支任务，且修改集中在 CLI shared files，拆 worktree 会增加 merge 冲突。

## 长程任务判定

- 是否属于长程任务：是，用户已连续授权“继续，一起来做完”。
- 若是，合同文件：沿用当前 thread 授权与本 task package，不另建 legacy docs plan。
- 连续执行权限：已授权
- Stop Condition 摘要：触及公共 SPI 大改、需要提交密钥、或 live sandbox 需要用户重新配置凭证时停止并记录 residual。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + subagent review（若 agent slot 可用）
- No-finding 要求：没有 P1/P0 correctness/security finding；否则修复后再提交。

## 关联

- 相关 Regression Gate：RG-004 CLI/TUI host；RG-003 coding runtime shell sandbox consumption；RG-008 docs-site（仅当 docs-site 变化）
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P2-C Daytona SandboxProvider（已提交 `f038efb`）

## 模块关联（启用模块并行时填写）

- Module：cli-host
- Step：T-P4-CLI-SANDBOX-COMMANDS-AND-STATUS-UX-4E7E51C6
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced-for-review
- Registry update needed：cli-host step evidence/status after tests
- Harness Ledger update needed：task review path present; final complete after human confirmation if required
- Closeout / Regression update needed：`walkthrough.md`、`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | cli-host |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/cli-host/brief.md | CLI host module purpose and boundaries. |
| Module plan | coding-agent-harness/planning/modules/cli-host/module_plan.md | Active cli-host step and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/cli-host/visual_map.md | Check sequencing/dependencies when needed. |
