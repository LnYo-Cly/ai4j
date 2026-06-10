# AI4J extension runtime adapter wave 3 - 进度

## 状态：已完成

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

### [2026-06-08 18:32] - task-start

- 做了什么：开始 Wave 3：补齐 extension runtime 到 agent tool registry/executor 的最小运行时适配，并同步 docs-site 插件生态入口。
- 验证结果：任务已通过 harness lifecycle 创建并进入进行中。
- 下一步：实现 Agent / Coding Agent adapter。
- 证据：report:coding-agent-harness/planning/tasks/2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5/INDEX.md:harness new-task/task-start 已创建任务包

### [2026-06-09 02:51] - targeted agent/coding compile check

- 做了什么：在 `ai4j-agent` 新增 `ExtensionAgentTools` adapter、extension tool registry/executor/schema mapper 和 `RoutingToolExecutor`；在 `AgentBuilder` 与 `CodingAgentBuilder` 增加 `.extensions(...)` 入口。
- 验证结果：
  - `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` 通过，4 tests。
  - `mvn -pl ai4j-coding -am "-Dtest=CodingAgentBuilderTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` 通过，`ExtensionAgentToolsTest` 4 tests、`CodingAgentBuilderTest` 6 tests。
- 下一步：补 Coding Agent 的插件工具 session 回归。
- 证据：command:terminal:mvn targeted agent/coding tests passed before coding extension session test

### [2026-06-09 02:53] - coding extension session test

- 做了什么：在 `CodingAgentBuilderTest` 中增加 `shouldAllowModelToInvokeExposedExtensionToolWithinCodingSession`，覆盖 `ExtensionRegistry.enable(...).exposeTool(...)` 后进入 coding session 的完整 tool call。
- 验证结果：`mvn -pl ai4j-coding -am "-Dtest=CodingAgentBuilderTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` 通过，`ExtensionAgentToolsTest` 4 tests、`CodingAgentBuilderTest` 7 tests。
- 下一步：补 docs-site 插件包页和 README 入口。
- 证据：command:terminal:mvn coding targeted tests passed with new coding extension session coverage

### [2026-06-09 03:03] - docs and task materials

- 做了什么：
  - 新增 `docs-site/docs/core-sdk/extension/plugin-packages.md`，说明插件包使用者路径、开发者路径、安全门禁、发布建议和当前边界。
  - 更新 `docs-site/docs/core-sdk/extension/overview.md`，区分 plugin package、provider extension、model/service extension 和 HTTP SPI。
  - 更新 `docs-site/sidebars.ts`、根 `README.md`、`docs-site/README.md`，加入插件包文档入口。
  - 更新本任务 `brief.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`。
- 验证结果：文档路由和构建待跑；`plugin-packages.md` 被仓库 `.gitignore` 的 `docs/` 规则忽略，提交时需 `git add -f`。
- 下一步：更新 SSoT / 回归台账并执行最终验证。
- 证据：diff:docs-site/docs/core-sdk/extension/plugin-packages.md:新增插件包生态文档；diff:README.md:新增插件生态入口；diff:docs-site/sidebars.ts:新增 sidebar item

### [2026-06-09 03:09] - final verification batch

- 做了什么：执行 extension API、agent runtime adapter、coding runtime adapter、CLI inspect、monorepo package smoke 和 docs-site 构建验证。
- 验证结果：
  - `mvn -pl ai4j-extension-api -DskipTests=false test` 通过，8 tests。
  - `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` 通过，4 tests。
  - `mvn -pl ai4j-coding -am "-Dtest=CodingAgentBuilderTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` 通过，`ExtensionAgentToolsTest` 4 tests、`CodingAgentBuilderTest` 7 tests。
  - `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` 通过，8 tests。
  - `mvn -DskipTests package` 通过，10 reactor modules。
  - `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` 通过。
  - `NODE_OPTIONS=--max-old-space-size=8192 npm run build` 通过，生成 `docs-site/build`。
- 下一步：更新治理记录并提交 Agent Review Submission。
- 证据：command:terminal:mvn extension-api/agent/coding/cli/package and docs-site typecheck/build passed

## 残余

- full `mvn -pl ai4j-agent -am -DskipTests=false test` 已知受 R-008 `HandoffPolicyTest` 阻塞；本轮使用 targeted adapter tests、CLI targeted tests 和 package smoke 作为 touched-surface 证据。
- `docs-site/docs/core-sdk/extension/plugin-packages.md` 受 `.gitignore` 的 `docs/` 规则影响，需要在提交时强制 add。
- Human Review Confirmation 不能由 agent 代办；本轮完成后提交 Agent Review Submission。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：Feature SSoT、module plans、Regression SSoT、Cadence Ledger 待提交前更新
- Harness Ledger update needed：task review lifecycle command 待最终验证后执行
- 负责人：coordinator

### [2026-06-08 19:24] - task-review

- 做了什么：Wave 3 runtime adapter, docs, and verification are ready for human review
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-10 12:35] - task-complete

- 做了什么：Human review confirmed; closeout finalized after user confirmation.
- 验证结果：已记录
- 下一步：完成
- 证据：n/a
