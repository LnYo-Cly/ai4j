# 任务产物索引

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | command | TARGET:. | `mvn -pl ai4j -DskipTests=false test` passed: 98 tests, 0 failures, 0 errors, live provider tests excluded by default. | coordinator |
| ART-002 | command | TARGET:. | `mvn -pl ai4j-agent -am -DskipTests=false test` failed only in `HandoffPolicyTest` with 2 failures; live provider tests were not run by default. | coordinator |
| ART-003 | command | TARGET:. | `mvn -pl ai4j-coding -am -DskipTests package` passed and compiled core/agent/coding test sources with tests skipped. | coordinator |
| ART-004 | command | TARGET:. | `mvn -pl ai4j-coding -DskipTests=false test` passed: 56 tests, 0 failures, 0 errors. | coordinator |
| ART-005 | command | TARGET:. | `mvn -pl ai4j -P live-provider-tests -Dtest=DoubaoTest -DskipTests=false test` passed with 3 skipped tests because provider env vars were absent. | coordinator |
| ART-006 | command | TARGET:. | `mvn -pl ai4j-agent -P live-provider-tests -Dtest=CodeActRuntimeTest -DskipTests=false test` passed with 1 skipped test because provider env vars were absent. | coordinator |
| ART-007 | command | TARGET:. | `mvn -pl ai4j-coding -P live-provider-tests -Dtest=MinimaxCodingAgentTeamWorkspaceUsageTest -DskipTests=false test` passed with 1 skipped test because provider env vars were absent. | coordinator |
| ART-008 | command | TARGET:. | `rg` credential scan shows remaining `setApiKey("...")` hits only in deterministic local unit fixtures (`config-api-key`, `sk-test`, `jina-key`); no live provider default key/property fallback/local path remains. | coordinator |

## 使用规则

- 原始终端输出不提交；本索引记录可复跑命令和摘要。
- 敏感信息不记录，live profile smoke 只记录 env var 缺失导致的 JUnit Assume skip。
