# P4 CLI sandbox commands - 进度

## 状态：进行中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

### [2026-06-20 03:52] - task-start

- 做了什么：Start P4 CLI sandbox commands: expose CLI/TUI sandbox status and local attach/disable commands on top of existing ai4j-coding sandbox routing without adding real provider or bypassing approval.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-20 12:02] - planning recorded

- 做了什么：复用现有 `MODULES/cli-host/2026-06-20-p4-cli-sandbox-commands-72f40aa0` task package，补齐 brief、task plan、execution strategy、visual map、findings、references 和 artifacts；明确 P4 是 CLI 可见 sandbox binding + runtime rebind，不实现真实 provider/runner。
- 验证结果：已完成代码接缝诊断：`CodingAgentBuilder.sandbox(SandboxSession)` 已存在；`SandboxProvider` 无通用 attach SPI；`CodingCliSessionRunner` 是 slash dispatch/runtime rebind 主接点；`SlashCommandController` 需要补 root/action/palette。
- 下一步：按 `references/cli-sandbox-command-plan.md` 执行窄切片实现，并在实现后运行 CLI targeted tests、docs build、diff check 和 Harness status。
- 证据：report:TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-p4-cli-sandbox-commands-72f40aa0/references/cli-sandbox-command-plan.md:P4 command contract and implementation plan; command:TARGET:.:'npx --yes coding-agent-harness status --json .' passed before planning edits; diff:TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-p4-cli-sandbox-commands-72f40aa0:planning files updated

## 残余

- 实现阶段仍需决定 attach 后是否使用 metadata-only `SandboxSession` 触发显式 unsupported execution，或先做 status-only binding；原则是不允许静默 direct-host fallback 或伪造 sandbox 成功。
- 真实 sandbox provider discovery / attach / remote runner 不在本任务范围内，需要后续任务。
- 实现完成前尚未运行 Maven/docs 回归。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass
- Registry update needed：cli-host task `T-P4-CLI-SANDBOX-COMMANDS-72F40AA0` 已 active；实现验证后同步证据和状态。
- Harness Ledger update needed：task plan / review / closeout 由 lifecycle CLI 或 governance rebuild 同步。
- 负责人：coordinator
