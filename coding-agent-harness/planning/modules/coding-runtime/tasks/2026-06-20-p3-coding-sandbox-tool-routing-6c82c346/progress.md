# P3 Coding sandbox tool routing - 进度

## 状态：已完成

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

### [2026-06-20 02:55] - task-start

- 做了什么：Start P3 coding sandbox tool routing: first slice will add sandbox-aware execution routing for ai4j-coding without changing direct-host behavior.
- 验证结果：已记录
- 下一步：分析 ai4j-coding built-in tool routing。
- 证据：command:TARGET:.:'npx --yes coding-agent-harness task-start MODULES/coding-runtime/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346 ...' succeeded

### [2026-06-20 11:04] - 边界分析

- 做了什么：检查 `CodingAgentBuilder.createBuiltInToolExecutor(...)`、`BashToolExecutor`、`LocalShellCommandExecutor`、`CodingAgent.newSession(...)`、`AgentSession.bindSandbox(...)`、Sandbox SPI。
- 验证结果：确认首切片应只覆盖 `bash action=exec`，避免一次性改动后台进程和文件/patch/browser 执行面。
- 下一步：实现 sandbox shell executor 和 builder/session wiring。
- 证据：report:TARGET:coding-agent-harness/planning/modules/coding-runtime/tasks/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346/findings.md:boundary recorded

### [2026-06-20 11:08] - targeted implementation test

- 做了什么：新增 `CodingSandboxRuntime`、`SandboxShellCommandExecutor`，扩展 `CodingAgentBuilder.sandbox(SandboxSession)`，新建 coding session 时绑定 `AgentSessionSandboxBinding`，并为 `bash exec` 加 fake sandbox tests。
- 验证结果：`mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` 通过，14 coding tests。
- 下一步：更新 docs-site 和治理记录。
- 证据：command:TARGET:.:'mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test' passed with 14 coding tests

### [2026-06-20 11:11] - broad coding regression

- 做了什么：运行 `ai4j-coding` owning module broad regression。
- 验证结果：`mvn -pl ai4j-coding -am -DskipTests=false test` 通过，extension API 25、core 103、agent 119、coding 61 tests。
- 下一步：docs-site build。
- 证据：command:TARGET:.:'mvn -pl ai4j-coding -am -DskipTests=false test' passed

### [2026-06-20 11:15] - docs build

- 做了什么：新增 `docs-site/docs/coding-agent/sandbox-routing.md`，更新 sidebar、tools-and-approvals、agent/sdk-roadmap。
- 验证结果：第一次 `npm --prefix docs-site run build` 因 worktree 缺少 ignored `docs-site/node_modules` 失败；执行 `npm --prefix docs-site install` 后，`npm --prefix docs-site run build` 通过并生成 `docs-site/build`。
- 下一步：更新 Regression SSoT / Cadence Ledger 和 task package。
- 证据：command:TARGET:docs-site:'npm --prefix docs-site run build' passed after local dependency install

### [2026-06-20 11:20] - task package and governance update

- 做了什么：更新 `brief.md`、`task_plan.md`、`execution_strategy.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`，并同步 RG-003/RG-008/SRB-057。
- 验证结果：待 final diff/harness check。
- 下一步：运行 `git diff --check`、`npx --yes coding-agent-harness status --json .`，然后提交/PR。
- 证据：diff:TARGET:coding-agent-harness/planning/modules/coding-runtime/tasks/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346:task package updated; diff:TARGET:docs/05-TEST-QA:regression governance updated

## 残余

- `read_file`、`write_file`、`apply_patch`、browser、git/project run/test runner 尚未 sandbox routing，后续 P3 切片继续做。
- `bash start/status/logs/write/stop/list` 仍由本地 `SessionProcessRegistry` 管理，后续需要 provider-side process lifecycle 设计。
- CLI `/sandbox` 状态展示和切换不在本任务范围内，后续 P4 处理。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass
- Registry update needed：coding-runtime module plan 在 PR/merge 后同步本任务状态
- Harness Ledger update needed：task review / closeout status 由 lifecycle CLI 更新
- 负责人：coordinator

### [2026-06-20 11:32] - final local hygiene check

- 做了什么：在修复 task packet 后重新运行 targeted coding regression 和 diff hygiene。
- 验证结果：`mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` 通过，14 tests；`git diff --check` 无 whitespace error；`npx --yes coding-agent-harness status --json .` 0 failures，当前仅剩 dirty-state warning，待提交后消失。
- 下一步：提交 feature diff，然后用 lifecycle CLI 推进 VERIFY-01 / Agent Review Submission。
- 证据：command:TARGET:.:'mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test' passed with 14 tests; command:TARGET:.:'git diff --check' passed; command:TARGET:.:'npx --yes coding-agent-harness status --json .' reported 0 failures before clean commit

### [2026-06-20 03:37] - task-review

- 做了什么：P3 coding sandbox routing first slice ready for review: bash exec routes through SandboxSession, local fallback preserved, docs and regression governance updated, targeted coding regression and docs build evidence recorded.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-20 11:44] - PR docs-build repair

- 做了什么：PR #115 的 docs-build CI 失败，原因是 `docs-site/sidebars.ts` 引用了 `coding-agent/sandbox-routing`，但新建页面被仓库根 `.gitignore` 的 `docs/` 规则忽略，未进入上一轮提交。
- 验证结果：确认 `git check-ignore -v docs-site/docs/coding-agent/sandbox-routing.md` 命中 `.gitignore:72:docs/`；将使用 `git add -f` 纳入该页面并重跑 docs build。
- 下一步：提交修复并重新推送 PR #115。
- 证据：command:TARGET:.:'gh run view 27859112205 --job 82451970188 --log-failed' showed missing doc id `coding-agent/sandbox-routing`; command:TARGET:.:'git check-ignore -v docs-site/docs/coding-agent/sandbox-routing.md' showed `.gitignore:72:docs/`

### [2026-06-24 backlog reconciliation] - 任务收口

- 经 backlog 对账：代码已合并到 main（关键能力已在 main 验证存在）；状态由 审查中 推进到 已完成。
- 备注：正式人工 dashboard 确认（GATE-02）未跑；ledger 如实记录为 closed / pending-review，可在本地 Dashboard 补确认。
