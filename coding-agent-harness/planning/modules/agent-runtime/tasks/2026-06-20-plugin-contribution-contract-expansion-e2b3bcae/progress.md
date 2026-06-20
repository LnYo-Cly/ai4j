# Plugin contribution contract expansion - 进度

## 状态：审查中

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

### [2026-06-20 08:15] - task-start

- 做了什么：Start Plugin contribution contract expansion: diagnose existing extension-api manifest/resources and ai4j-agent runtime contribution points before implementation.
- 验证结果：已记录。
- 下一步：诊断现有 extension surfaces。
- 证据：command:.:`npx --yes coding-agent-harness task-start MODULES/agent-runtime/2026-06-20-plugin-contribution-contract-expansion-e2b3bcae ...` succeeded

### [2026-06-20 16:36] - 诊断和范围确认

- 做了什么：读取 AGENTS、testing standard、任务包和现有 `ai4j-extension-api` / `ai4j-agent` / `ai4j-plugin-ask-user` 插件代码；确认 worktree 中缺少 `engineering-standard.md`，但 `testing-standard.md` 存在。
- 验证结果：确认现有 capability/inspection/activation/validator 只覆盖已注册 runtime surface，缺少 provider-style contribution metadata。
- 下一步：实现 manifest-level contribution contract。
- 证据：report:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionManifest.java:existing manifest diagnosed

### [2026-06-20 16:44] - Extension contribution contract 实现

- 做了什么：新增 `ExtensionContribution`、`ExtensionContributionType`；扩展 `ExtensionCapability`、`ExtensionManifest`、`ExtensionInspectionSnapshot`、`ExtensionActivationPlan`、`ExtensionRegistry`、`ExtensionValidator`；更新 ask-user 示例插件和测试。
- 验证结果：`mvn -pl ai4j-extension-api -DskipTests=false test` 通过；`mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` 通过。
- 下一步：补 docs-site 并跑 agent bridge / docs build。
- 证据：command:.:`mvn -pl ai4j-extension-api -DskipTests=false test` passed; command:.:`mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` passed

### [2026-06-20 16:49] - docs-site 和跨模块验证

- 做了什么：新增 `docs-site/docs/agent/plugin-contribution-contract.md`，更新 sidebar、sdk roadmap、plugin lifecycle hooks、sandbox SPI、remote runner SPI 链接。
- 验证结果：`mvn -pl ai4j-agent -am "-Dtest=ExtensionAgentToolsTest,AgentPluginLifecycleHooksTest" -DskipTests=false -DfailIfNoTests=false test` 通过；首次 `npm --prefix docs-site run build` 因 worktree 缺少 `docs-site/node_modules` 失败，运行 `npm --prefix docs-site install` 后重跑 build 通过；`git diff --check` 通过。
- 下一步：运行 Harness status、提交实现、task-review、推送 PR。
- 证据：command:.:`mvn -pl ai4j-agent -am "-Dtest=ExtensionAgentToolsTest,AgentPluginLifecycleHooksTest" -DskipTests=false -DfailIfNoTests=false test` passed; command:docs-site:`npm --prefix docs-site run build` passed after local dependency install; command:.:`git diff --check` passed

### [2026-06-20 17:02] - 贡献契约收紧

- 做了什么：补充禁止同一插件重复声明相同 `type:name` contribution，避免后续 CLI/宿主展示歧义。
- 验证结果：`mvn -pl ai4j-extension-api -DskipTests=false test` 通过，31 tests。
- 下一步：提交实现并执行 task-review。
- 证据：command:.:`mvn -pl ai4j-extension-api -DskipTests=false test` passed with 31 tests

## 残余

- `docs/11-REFERENCE/engineering-standard.md` 在当前 worktree 缺失；本任务没有补标准文件，后续如需标准同步另开任务。
- `npm --prefix docs-site install` 报 50 个依赖 audit issue，是既有依赖树审计提示，不在本任务范围内。
- Provider-style contribution 仍只是 manifest metadata；真实 sandbox/runner/memory 绑定由后续任务完成。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass
- Registry update needed：module plan 应在 task-review / merge 后更新状态。
- Harness Ledger update needed：由 lifecycle CLI 同步。
- 负责人：coordinator

### [2026-06-20 09:07] - task-review

- 做了什么：Plugin contribution contract expansion ready for review: manifest-level contributions, inspection/activation/validator projection, ask-user sample metadata, docs-site page, and targeted regressions completed.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-20 17:14] - visual_map 材料修复

- 做了什么：修复 `visual_map.md` 中 EXEC-01/GATE-01 的模板占位内容，将实现输出、证据、review exit command、risk 和 owner 改为本任务真实材料。
- 验证结果：`npx --yes coding-agent-harness status --json .` 显示本任务 `reviewQueueState=ready-to-confirm`、`materialsReady=true`、`taskQueues=[review]`，missing-materials 队列已清空。
- 下一步：提交本次材料修复；等待人工 review-confirm 或继续 PR/合并流程。
- 证据：diff:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-plugin-contribution-contract-expansion-e2b3bcae/visual_map.md:removed visual-map-execution-phase template placeholders
