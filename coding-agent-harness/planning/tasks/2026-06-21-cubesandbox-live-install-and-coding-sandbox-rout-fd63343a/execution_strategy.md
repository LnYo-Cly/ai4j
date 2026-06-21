# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | attempted | read-only | harness task policy | 2026-06-21 | docs/harness review | n/a | attempted once |
| worker subagent | not-needed | write only after user approval | coordinator | 2026-06-21 | no disjoint implementation slice needed after code was already localized | n/a | no |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes-attempted | docs/harness completeness review could run in parallel without write conflicts | Spawn attempted; failed with TroveBox 502, so coordinator continued with self-review and deterministic checks |
| Would a worker subagent materially help? | no | write set is narrow and coupled around `CodingCliSessionRunner` runtime switching; splitting would raise merge risk more than it helps | Coordinator implements and verifies directly |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-21 | n/a | `.worktrees/feature/cubesandbox-live-routing` | Reviewer subagent was read-only and failed due upstream 502; no worker needed |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 负责实现、验证、docs/governance 同步和最终收口。 |
| Subagent 模式 | reviewer-attempted/self-review-fallback | 子 agent 审查遇到 TroveBox 502，未产出可用报告；用 self-review + tests/docs build 兜底。 |
| 审查模型 | adversarial self-review + deterministic regression | 重点挑战 no-local-fallback、rollback close、secret leakage 和 docs drift。 |
| Worktree 策略 | dedicated worktree | 所有修改在 `.worktrees/feature/cubesandbox-live-routing` / `feature/cubesandbox-live-routing` 内完成。 |
| 冲突控制 | coordinator owns shared files | 代码、docs-site、Regression/Cadence、task 包均由 coordinator 一次性同步，避免共享表冲突。 |
| 证据深度 | L1 + L2 + L3 skipped evidence | Java targeted tests 为 L1；docs-site build 为 L2；live-provider opt-in test 构建并 skipped 作为 L3 pending-env 证据。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer | worktree path + docs/harness checklist | read-only | report; actual result was 502 notification | coordinator |
| worker | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`; secret scan | `progress.md` / final summary | no whitespace errors; no committed API key/token pattern |
| L1 | `mvn -pl ai4j-cli -am "-Dtest=*Sandbox*Test,DefaultCodingCliAgentFactoryTest,CodingCliSessionRunnerArgumentParsingTest,SlashCommandControllerTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | BUILD SUCCESS, no failures/errors |
| L2 | `npm --prefix docs-site run build` after local dependency restore if needed | `progress.md` | Docusaurus build succeeds |
| L3 | `mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` / `findings.md` | live pass if env exists; otherwise controlled skip + pending-env residual |
| Harness | `npx --yes coding-agent-harness status --json .` | `progress.md` / final summary | no validation failures; dirty warning expected before commit |

## 暂停 / 升级条件

- Need to install Docker/WSL/CubeSandbox with administrator privileges or reboot: stop and record operator-owned residual.
- Need real Cube API key/template/session not present in env: stop and record pending-env.
- Any attach failure risks local fallback: block until fixed.
- Any runtime switch failure leaks live session: block until close/rollback test passes.
- Any docs still claim CubeSandbox is metadata-only after implementation: block until docs build passes with corrected copy.
