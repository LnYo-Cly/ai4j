# 收口记录：P4 CLI sandbox commands and status UX

## 摘要

已在 `ai4j-cli code` 交互会话中加入 `/sandbox` 命令：用户可以查看 direct-host/sandbox 状态，使用 Daytona 创建或 attach sandbox，把后续 coding agent shell `exec` 通过 `SandboxSession` 路由到远端，再用 `/sandbox disable` 回到 direct-host。该任务不扩大承诺到 file tools、MCP/browser、apply_patch 或后台 process lifecycle。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-cli` |
| 新增文件 | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/sandbox/CliSandboxBinding.java`; `CliSandboxCommand.java`; `CliSandboxSessionResolver.java`; `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/sandbox/*`; `CodingCliSessionRunnerSandboxTest.java` |
| 删除文件 | 无 |
| 不在范围内 | 公共 SandboxProvider registry、E2B/Cube 多 provider UX、file/MCP/browser/process lifecycle sandbox 化、提交或打印 provider secret |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted CLI sandbox tests | `mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest,CliSandboxCommandTest,CliSandboxSessionResolverTest,CodingCliSessionRunnerSandboxTest" -DskipTests=false -DfailIfNoTests=false test` | pass, 61 tests | `progress.md` / `artifacts/INDEX.md` A-002 |
| Broad CLI `-am` regression | `mvn -pl ai4j-cli -am -DskipTests=false test` | pass, CLI 298 tests; upstream modules also pass | `progress.md` / `artifacts/INDEX.md` A-003 |
| Live provider env check | env presence only, no secret values printed | `DAYTONA_API_KEY=False`; live rerun skipped | `progress.md` A-006; LV-004 retains prior Daytona live smoke |
| Regression governance | Update RG-004 and SRB-059 | done | `docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md` |
| Harness status | `npx --yes coding-agent-harness status --json .` | pass with dirty-state warning; current task ready-to-confirm | `progress.md` A-005 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Self adversarial review | 0 P0/P1/P2 blocking findings | 可提交进入审查；保留 live skip 与 runtime boundary residual | `review.md` |
| Subagent review | 请求已发送给现有 review agent；若返回阻塞发现需修复后再最终完成 | 作为提交前附加审查，不代替人工确认 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 当前 shell 无 Daytona credential，未重复 live smoke | coordinator | yes | 后续 release/provider task 在已配置 env 的机器上重跑；本轮引用 LV-004 既有 pass |
| 只有 shell `exec` 进入 sandbox | product/coordinator | yes | 后续单独规划 remote file/workspace/process/browser/sandbox runner |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，`checked-none` |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 回归治理 | `docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md` |
