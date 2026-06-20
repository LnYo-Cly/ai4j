# 收口记录：P4 CLI sandbox commands

## 摘要

P4 CLI sandbox commands 已完成本地实现和验证：`ai4j-cli` 现在提供 `/sandbox`、`/sandbox status`、`/sandbox attach <providerId> <sessionId> [workspaceId]`、`/sandbox disable`，并把当前 CLI session 的 sandbox binding 传入 `CodingAgentBuilder.sandbox(...)`。当前 attach 是 metadata-only；没有真实 provider bridge 时，后续 `bash action=exec` 会明确失败并声明 `Command was not executed locally`，不会静默回退到宿主机。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-cli`、`docs-site`、`docs/05-TEST-QA`、本 Harness task package |
| 新增文件 | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/sandbox/CliSandboxBinding.java`; `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/sandbox/CliAttachedSandboxSession.java`; `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/sandbox/CliAttachedSandboxSessionTest.java` |
| 删除文件 | 无 |
| 不在范围内 | 真实 sandbox provider bridge、CubeSandbox/Docker/E2B/K8s provider、Remote Agent Runner、`/sandbox create/list/destroy/logs`、provider auth/secret storage |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| targeted CLI | `mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest,DefaultCodingCliAgentFactoryTest,CliAttachedSandboxSessionTest" -DskipTests=false -DfailIfNoTests=false test` | pass, 60 tests | `progress.md` 2026-06-20 12:57 |
| broad CLI | `mvn -pl ai4j-cli -am -DskipTests=false test` | pass, extension API 25, core 103, agent 119, coding 61, CLI 289 tests | `progress.md` 2026-06-20 12:58 |
| docs-site | `npm --prefix docs-site run build` after local ignored dependency install | pass | `progress.md` 2026-06-20 13:01 |
| diff hygiene | `git diff --check` | pass, CRLF warnings only | `progress.md` 2026-06-20 13:03 |
| harness status | `npx --yes coding-agent-harness status --json .` | 0 failures, 1 pre-commit dirty-state warning | `progress.md` 2026-06-20 13:03 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 0 material findings | ready for Agent Review Submission after feature commit | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| metadata-only attach 无真实 provider bridge | coordinator | yes | 当前显式失败且不执行本地命令；后续 provider bridge / Remote Agent Runner task |
| `/sandbox create/list/destroy/logs` 未实现 | coordinator | yes | 后续 CLI sandbox provider task |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，checked-none:p4-cli-sandbox-slice-task-local |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
