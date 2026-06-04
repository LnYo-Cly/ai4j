# live provider test hygiene - 进度

## 状态：审查中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

## 进度记录

证据使用 `type:path:summary` 格式。

### [2026-06-04 10:52] - task-start

- 做了什么：Start live provider test hygiene. Scope: JUnit4 live-provider category/profile, env-only credential handling, targeted test verification, and regression SSoT/Cadence residual updates.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-04 11:02] - diagnosis

- 做了什么：扫描 provider/live tests、`setApiKey`、API key env/property fallback、本机路径、POM Surefire 配置。
- 验证结果：确认 `ai4j` 不继承 root POM；core/agent/coding 均有 live usage tests；agent/coding 有 API key property fallback；core 有默认 key 和本机路径/代理引用。
- 下一步：实现 Surefire category/profile 和 env-only credential helper。
- 证据：command:TARGET:.:`rg` scan over `ai4j/src/test/java`, `ai4j-agent/src/test/java`, `ai4j-coding/src/test/java`

### [2026-06-04 11:20] - implementation

- 做了什么：添加 `io.github.lnyocly.ai4j.test.LiveProviderTest` marker；root POM 与 `ai4j/pom.xml` 增加默认 `excludedGroups` 和 `live-provider-tests` profile；core/agent/coding 添加 test helper；标记 live tests；移除默认/嵌入 key、API key system property 回退、本机路径和本机代理。
- 验证结果：代码变更完成，credential scan 后仅剩 local unit fixture fake key。
- 下一步：运行 default/local 和 live profile smoke。
- 证据：diff:TARGET:.:POM/test changes; command:TARGET:.:ART-008

### [2026-06-04 11:36] - core default regression

- 做了什么：运行 core 默认本地测试。
- 验证结果：`mvn -pl ai4j -DskipTests=false test` 通过；98 tests, 0 failures, 0 errors, 0 skipped；live provider tests 未进入默认集。
- 下一步：验证 agent/coding 和 live profile。
- 证据：command:TARGET:.:ART-001

### [2026-06-04 11:44] - agent gate residual

- 做了什么：运行 `mvn -pl ai4j-agent -am -DskipTests=false test`。
- 验证结果：core 部分通过；agent 默认测试中 live provider tests 未进入默认集；`HandoffPolicyTest` 两个非 live 测试失败：`testAllowedToolsPolicyDeniesUnexpectedSubagent`、`testNestedHandoffBlockedByMaxDepth`。
- 下一步：不在本任务修复 runtime policy，新增 R-008 回归残余。
- 证据：command:TARGET:.:ART-002

### [2026-06-04 11:49] - compile and live profile smoke

- 做了什么：运行 coding/agent/coding compile/package、core/agent/coding targeted live profile smoke。
- 验证结果：
  - `mvn -pl ai4j-coding -am -DskipTests package` 通过，core/agent/coding 测试源码均可编译。
  - `mvn -pl ai4j -P live-provider-tests -Dtest=DoubaoTest -DskipTests=false test` 通过，3 skipped。
  - `mvn -pl ai4j-agent -P live-provider-tests -Dtest=CodeActRuntimeTest -DskipTests=false test` 通过，1 skipped。
  - `mvn -pl ai4j-coding -P live-provider-tests -Dtest=MinimaxCodingAgentTeamWorkspaceUsageTest -DskipTests=false test` 通过，1 skipped。
- 下一步：运行 coding 默认本地测试。
- 证据：command:TARGET:.:ART-003; command:TARGET:.:ART-005; command:TARGET:.:ART-006; command:TARGET:.:ART-007

### [2026-06-04 11:55] - coding default regression

- 做了什么：运行 coding 默认本地测试。
- 验证结果：`mvn -pl ai4j-coding -DskipTests=false test` 通过；56 tests, 0 failures, 0 errors, 0 skipped。
- 下一步：更新 Regression SSoT / Cadence / testing standard 和任务材料。
- 证据：command:TARGET:.:ART-004

### [2026-06-04 12:05] - governance sync

- 做了什么：同步更新 v2 和 legacy projection 的 Regression SSoT / Cadence Ledger / testing standard。
- 验证结果：R-002/R-006 标记已解决；R-008 新增为 agent local gate 残余；LV-001/LV-002 主入口改为 `-P live-provider-tests`。
- 下一步：提交 Agent Review。
- 证据：diff:TARGET:coding-agent-harness/governance/regression; diff:TARGET:docs/05-TEST-QA; diff:TARGET:docs/11-REFERENCE/testing-standard.md

## 残余

- R-008：`mvn -pl ai4j-agent -am -DskipTests=false test` 仍失败在 `HandoffPolicyTest` 两个断言；这不是 live-provider hygiene 改动范围，已路由到 Regression SSoT。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：review/closeout 后由 lifecycle CLI 记录
- 负责人：coordinator

### [2026-06-04 11:16] - task-review

- 做了什么：Live provider test hygiene ready for human review: default local tests exclude LiveProviderTest, live-provider profile smoke skips cleanly without credentials, env-only credential handling is in place, regression docs synced, and R-008 routes the unrelated HandoffPolicyTest gate failure.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
