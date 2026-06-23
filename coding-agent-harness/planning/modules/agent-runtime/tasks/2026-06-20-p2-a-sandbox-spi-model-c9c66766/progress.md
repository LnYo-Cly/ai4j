# P2-A Sandbox SPI model - 进度

## 状态：已完成

证据使用 `type:path:summary` 格式。允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

### [2026-06-20 08:52] - worktree and task created

- 做了什么：创建 `.wt/p2a` / `feature/agent-sandbox-spi-model`，新建 P2-A Harness module task。
- 验证结果：`new-task` 已生成任务包；随后将 Harness task commit cherry-pick 到 feature worktree，并恢复主仓 main 干净状态。
- 下一步：精确执行 `task-start`。
- 证据：command:TARGET:.:'git worktree add -b feature/agent-sandbox-spi-model .wt\\p2a main'; command:TARGET:.:'npx --yes coding-agent-harness new-task --budget complex --locale zh-CN --title "P2-A Sandbox SPI model" --module agent-runtime --preset module .'

### [2026-06-20 08:56] - task-start

- 做了什么：启动 P2-A Sandbox SPI model。
- 验证结果：Harness 自动提交 `254febf8b0d378660a9f615358c57e48f2d5665c`，任务进入 active。
- 下一步：实现 SPI/model 和 fake provider tests。
- 证据：command:TARGET:.:'npx --yes coding-agent-harness task-start MODULES/agent-runtime/2026-06-20-p2-a-sandbox-spi-model-c9c66766 ...'

### [2026-06-20 09:00] - Sandbox SPI model implemented

- 做了什么：新增 `io.github.lnyocly.ai4j.agent.sandbox` 包，包含 provider/session/spec/command/result/artifact/event/status/exception 合同；新增 `AgentSandboxSpiModelTest`。
- 验证结果：targeted test 通过，4 tests。
- 下一步：补 docs-site 与 broad regression。
- 证据：diff:TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox:Sandbox SPI model; command:TARGET:.:'mvn -pl ai4j-agent -am "-Dtest=AgentSandboxSpiModelTest" -DskipTests=false -DfailIfNoTests=false test' -> BUILD SUCCESS, 4 tests

### [2026-06-20 09:03] - docs and broad regression

- 做了什么：新增 docs-site `agent/sandbox-spi` 页面，更新 sidebar / Agent SDK Roadmap / Regression SSoT / Cadence Ledger。
- 验证结果：broad agent regression 通过；docs build 首次因 worktree 缺 `docs-site/node_modules` 失败，执行 `npm --prefix docs-site install` 后 build 通过。npm install 报告 50 个既有依赖漏洞，未在本任务调整依赖。
- 下一步：运行 `git diff --check`、Harness status，提交并 PR。
- 证据：command:TARGET:.:'mvn -pl ai4j-agent -am -DskipTests=false test' -> BUILD SUCCESS, extension API 25, core 103, agent 115 tests; command:TARGET:docs-site:'npm --prefix docs-site run build' -> first MODULE_NOT_FOUND, then success after local dependency install; diff:TARGET:docs-site/docs/agent/sandbox-spi.md:Sandbox SPI technical page; diff:TARGET:docs/05-TEST-QA/Cadence-Ledger.md:SRB-055

## 残余

- P2-A 不实现真实 sandbox provider。
- P2-A 不把 sandbox 绑定到 AgentSession snapshot/event log；该能力留给 P2-B。
- P2-A 不让插件贡献 provider；该能力留给 P2-C。
- P2-A 不接 `ai4j-coding` file/shell/git/browser routing；该能力留给 P3。

## Log

| Time | Actor | Action | Evidence | Next |
| --- | --- | --- | --- | --- |
| 2026-06-20 01:12 | coordinator | task-review: P2-A Sandbox SPI model ready for review: Java 8 provider/session/spec/command/result/artifact/event contracts added, fake provider tests passed, broad ai4j-agent regression passed, docs-site Sandbox SPI page and regression evidence updated. | n/a | continue |

### [2026-06-20 09:18] - review materials repaired

- 做了什么：根据 Harness missing-materials 结果修复 P2-A task-local review 材料：将 `execution_strategy.md` 从模板占位改为任务专属执行策略；将 `lesson_candidates.md` 改为 `lesson-candidate-v1` schema，并明确 `accepted-no-candidate` / `checked-none:p2-a-task-local`。
- 验证结果：`git diff --check` exit 0；`npx --yes coding-agent-harness status --json .` exit 0，failure 0，P2-A `materialsReady=true`、`reviewQueueState=ready-to-confirm`、`lessonCandidateDecisionComplete=true`；当前仅剩 dirty-state warning，原因是本轮材料修复尚未提交。
- 下一步：提交材料修复，重新运行 `task-review`，然后推送 PR。
- 证据：command:TARGET:.:'git diff --check' -> exit 0; command:TARGET:.:'npx --yes coding-agent-harness status --json .' -> failures 0, P2-A ready-to-confirm

### [2026-06-24 backlog reconciliation] - 任务收口

- 经 backlog 对账：代码已合并到 main（关键能力已在 main 验证存在）；状态由 审查中 推进到 已完成。
- 备注：正式人工 dashboard 确认（GATE-02）未跑；ledger 如实记录为 closed / pending-review，可在本地 Dashboard 补确认。
