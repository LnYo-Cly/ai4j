# CLI permissions command UX - Walkthrough

## Summary

本任务已完成实现后收口准备：`/permissions` 与 `/permissions status` 已接入 CLI/TUI/ACP/docs/test，用于只读展示当前 approval mode、配置来源提示、tool gate 行为、ACP `session/request_permission` 关系、sandbox 边界和 no-raw-output 安全说明。

## Changed Files

| Area | Summary |
| --- | --- |
| Production code | `ai4j-cli` 新增 `/permissions` root/completion、runtime dispatch、help、ACP support 和 `permissions:` info block 识别。 |
| Tests | 覆盖 root completion、status alias、unknown option、scripted CLI output、ACP command list / execution、formatter。 |
| Docs-site | `command-reference.md` 与 `tools-and-approvals.md` 新增 `/permissions` 定位、字段说明和与 `/status` / `/sandbox` / ACP 的区别。 |
| Regression governance | `Regression-SSoT.md` 与 `Cadence-Ledger.md` 补入 RG-004 / RG-008 evidence。 |
| Harness task package | 记录任务计划、发现、执行证据、review routing、lesson decision 和 closeout 摘要。 |

## Verification

| Command | Result | Evidence |
| --- | --- | --- |
| targeted CLI tests | pass | `mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodeCommandTest,AcpSlashCommandSupportTest,CodexStyleBlockFormatterTest" -DskipTests=false -DfailIfNoTests=false test` passed with 120 tests |
| broad CLI tests | pass | `mvn -pl ai4j-cli -am -DskipTests=false test` passed through CLI with 304 CLI tests plus upstream module tests |
| docs-site build | pass | `npm --prefix docs-site run build` generated static files successfully |
| final targeted rerun | pass | targeted CLI tests rerun after ACP/CLI wording alignment passed with 120 tests |
| `git diff --check` | pending | final static check before commit |
| `npx --yes coding-agent-harness status --json .` | pending | final Harness status before review gate |

## Review Disposition

| Review | Result | Notes |
| --- | --- | --- |
| self implementation review | pass-for-local-review-readiness | No P0/P1/P2 material finding found in CLI/ACP/docs/tests diff. |
| release review | pending | PR CI / human confirmation / merge still required. |

## Residual Risks

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| PR CI / merge 尚未完成 | coordinator | no | Push branch and open PR to `dev`. |
| Human Review Confirmation 尚未完成 | human | no | Agent Review Submission 后由 dashboard/workbench 确认。 |
| `/permissions` 只展示启动时 approval mode，不审计每个未来 tool 的动态 policy 决策 | coordinator | yes | 如需要动态 permission audit，另开任务。 |

## Lessons Reflection

| Question | Answer |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| Lesson candidate token | checked-none:straightforward-permission-command-surface-extension |
| Reason | 本任务沿用既有 slash command parity 规则，无新增通用治理规则。 |

## Closeout Status

- 当前状态：implementation-ready-for-final-verification
- 完成 closeout 的前提：final targeted rerun、`git diff --check`、Harness status、task-review、PR/CI、human review confirmation、merge。
