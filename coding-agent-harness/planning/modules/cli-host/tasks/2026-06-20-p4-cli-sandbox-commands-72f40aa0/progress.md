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

### [2026-06-20 12:57] - targeted CLI regression

- 做了什么：新增 CLI sandbox binding / metadata-only session handle，接入 `CodingCliSessionRunner` `/sandbox` 命令族、`CodingCliAgentFactory` sandbox overload、slash completion、palette/help/status，并补充 targeted tests。
- 验证结果：`mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest,DefaultCodingCliAgentFactoryTest,CliAttachedSandboxSessionTest" -DskipTests=false -DfailIfNoTests=false test` 通过，60 tests。
- 下一步：运行 broad CLI regression。
- 证据：command:TARGET:.:'mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest,DefaultCodingCliAgentFactoryTest,CliAttachedSandboxSessionTest" -DskipTests=false -DfailIfNoTests=false test' passed with 60 tests

### [2026-06-20 12:58] - broad CLI regression

- 做了什么：运行 CLI owning module broad regression，覆盖 upstream modules 和完整 CLI suite。
- 验证结果：`mvn -pl ai4j-cli -am -DskipTests=false test` 通过，extension API 25、core 103、agent 119、coding 61、CLI 289 tests。
- 下一步：运行 docs-site build。
- 证据：command:TARGET:.:'mvn -pl ai4j-cli -am -DskipTests=false test' passed with extension API 25, core 103, agent 119, coding 61, CLI 289 tests

### [2026-06-20 13:01] - docs-site build

- 做了什么：更新 `docs-site/docs/coding-agent/sandbox-routing.md`、`docs-site/docs/coding-agent/command-reference.md` 和 `docs-site/docs/agent/sdk-roadmap.md`，说明 `/sandbox` P4 命令、metadata-only attach 和 no-local-fallback 边界。
- 验证结果：第一次 `npm --prefix docs-site run build` 因 worktree 缺少 ignored `docs-site/node_modules` 失败；执行 `npm --prefix docs-site install` 后，`npm --prefix docs-site run build` 通过并生成 `docs-site/build`。
- 下一步：更新 Regression SSoT / Cadence Ledger 和 task package。
- 证据：command:TARGET:docs-site:'npm --prefix docs-site install' restored ignored local dependencies; command:TARGET:docs-site:'npm --prefix docs-site run build' passed

### [2026-06-20 13:03] - governance and final hygiene

- 做了什么：同步 `docs/05-TEST-QA/Regression-SSoT.md` 的 RG-004/RG-008 证据，追加 `docs/05-TEST-QA/Cadence-Ledger.md` SRB-058，并运行 diff hygiene / Harness status。
- 验证结果：`git diff --check` 通过（仅 CRLF warning）；`npx --yes coding-agent-harness status --json .` 返回 0 failures，当前仅剩提交前 dirty-state warning。
- 下一步：提交 feature diff；工作树干净后执行 `task-review` 进入 Agent Review Submission。
- 证据：diff:TARGET:docs/05-TEST-QA:RG-004/RG-008 and SRB-058 updated; command:TARGET:.:'git diff --check' passed with CRLF warnings only; command:TARGET:.:'npx --yes coding-agent-harness status --json .' reported 0 failures and 1 dirty-state warning before commit

## 残余

- 真实 sandbox provider discovery / attach / remote runner 不在本任务范围内，需要后续任务。
- `/sandbox create/list/destroy/logs` 不在本任务范围内。
- metadata-only attach 已选择显式 unsupported `SandboxSession`，后续在 provider bridge 落地前不会静默回退到宿主机执行。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-review-submit-after-clean-commit
- Registry update needed：cli-host task `T-P4-CLI-SANDBOX-COMMANDS-72F40AA0` 已实现并本地验证；feature commit 后由 lifecycle CLI 推进 review 状态。
- Harness Ledger update needed：task plan / review / closeout 由 lifecycle CLI 或 governance rebuild 同步。
- 负责人：coordinator
