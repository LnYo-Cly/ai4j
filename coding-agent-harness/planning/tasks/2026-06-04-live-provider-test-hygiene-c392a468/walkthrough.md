# 收口记录：live provider test hygiene

## 摘要

完成 live provider test hygiene：默认 Maven 本地回归排除 `LiveProviderTest` category；真实 provider 验证通过 `-P live-provider-tests` 显式 opt-in；core/agent/coding 的 live usage tests 改为 env-only credential reads 和 JUnit Assume clean skip。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | root build、core SDK tests、agent runtime tests、coding runtime tests、regression governance docs |
| 新增文件 | `LiveProviderTest.java` marker x3；core/agent/coding test helper x3 |
| 删除文件 | 无 |
| 不在范围内 | 真实 provider 调用、密钥配置、`HandoffPolicyTest` 行为修复、CI branch protection |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| core default local | `mvn -pl ai4j -DskipTests=false test` | pass: 98 tests | `progress.md`, ART-001 |
| coding default local | `mvn -pl ai4j-coding -DskipTests=false test` | pass: 56 tests | `progress.md`, ART-004 |
| compile/package | `mvn -pl ai4j-coding -am -DskipTests package` | pass | `progress.md`, ART-003 |
| core live profile smoke | `mvn -pl ai4j -P live-provider-tests -Dtest=DoubaoTest -DskipTests=false test` | pass with 3 skipped | `progress.md`, ART-005 |
| agent live profile smoke | `mvn -pl ai4j-agent -P live-provider-tests -Dtest=CodeActRuntimeTest -DskipTests=false test` | pass with 1 skipped | `progress.md`, ART-006 |
| coding live profile smoke | `mvn -pl ai4j-coding -P live-provider-tests -Dtest=MinimaxCodingAgentTeamWorkspaceUsageTest -DskipTests=false test` | pass with 1 skipped | `progress.md`, ART-007 |
| agent full local gate | `mvn -pl ai4j-agent -am -DskipTests=false test` | fail in `HandoffPolicyTest` only | R-008, ART-002 |
| credential scan | `rg` over test sources | remaining key hits are local fixture fake keys only | ART-008 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | none for task target | submit for human review | `review.md` |
| regression review | R-008 outside task target | routed to Regression SSoT | `findings.md`, `progress.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| RG-002 fails in `HandoffPolicyTest` | project coordinator | yes for this task | Open/fix an agent-runtime task before claiming RG-002 fully green |
| No real provider call executed | operator/project coordinator | yes | Run LV-001/LV-002 with real env credentials only when explicitly approved |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否需要提升共享 lesson？ | no; 本任务经验已写入 task-local review/walkthrough，不需要更新共享标准 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| 证据索引 | `artifacts/INDEX.md` |
