# live provider test hygiene - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001 - root POM 不能覆盖核心模块

- 背景：需要统一 Surefire category/profile，但模块继承关系不一致。
- 发现：`ai4j-agent`、`ai4j-coding` 等继承 root `pom.xml`；`ai4j/pom.xml` 是独立 POM，不继承 root。
- 影响：必须在 root POM 和 `ai4j/pom.xml` 同时定义 `live-provider-tests` profile 与 `LiveProviderTest` category。
- 后续：若未来把 `ai4j` 改为继承 root POM，可消除这份重复配置。

### F-002 - live provider tests 与本地 fixture tests 混在同一 test tree

- 背景：默认 local gate 需要 deterministic，不能隐式读真实 provider 凭据。
- 发现：core/agent/coding 中存在真实 provider usage tests；同一目录下也有 `config-api-key`、`sk-test` 等本地 fixture tests。
- 影响：不能用简单字符串扫描删除所有 fake key；需要只处理 live provider classes，并把本地 fake key 作为允许残余。
- 后续：后续可把 live usage tests 迁到更明确的 package 或命名空间，降低扫描误报。

### F-003 - agent local gate 有非本任务失败

- 背景：运行 `mvn -pl ai4j-agent -am -DskipTests=false test` 验证默认排除是否生效。
- 发现：live provider tests 未进入默认集，但 `HandoffPolicyTest` 中 `testAllowedToolsPolicyDeniesUnexpectedSubagent` 和 `testNestedHandoffBlockedByMaxDepth` 失败。
- 影响：RG-002 不能记为 green；RG-003 的 `-am` 全证据也受上游 agent gate 阻塞。
- 后续：已新增 R-008，建议独立修复 agent handoff policy/test contract。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| live test isolation | JUnit4 `@Category(LiveProviderTest.class)` + Surefire `excludedGroups` | 保持 JUnit4/Java8，不引入新测试框架 | Maven profile + naming patterns | accepted |
| opt-in command | `-P live-provider-tests -Dtest=<LiveTest>` | 明确选择 live surface，避免全量真实调用 | 默认运行并依赖 Assume | accepted |
| API key 来源 | env-only | 避免 system property 和代码默认值泄漏/误用 | env + system property fallback | accepted |
| 本地文件输入 | env var + Assume | 避免桌面绝对路径和开发者本机耦合 | 临时文件或固定 repo fixture | accepted |
| R-008 处置 | route residual, not fix in this task | 与 live provider hygiene 无关，需单独诊断 agent runtime contract | 顺手修改 HandoffPolicy 行为 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否立即修复 R-008 | 不纳入本任务，建议作为下一任务 | project coordinator | 下一轮 agent-runtime 回归前 |
