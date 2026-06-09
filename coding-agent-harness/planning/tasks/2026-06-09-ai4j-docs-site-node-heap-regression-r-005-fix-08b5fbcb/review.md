# AI4J docs site Node heap regression R-005 fix - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | docs-site scripts, docs workflows, RG-008/R-005 governance updates, task materials |

## 审查范围

- 审查类型：regression
- 范围内：`docs-site/package.json`、`.github/workflows/docs-build.yml`、`.github/workflows/docs-pages.yml`、`docs-site/README.md`、Regression SSoT / Cadence Ledger、docs-site module plan、本任务材料。
- 范围外：docs 内容信息架构、Docusaurus 升级、R-004 Windows `EPERM` 文件锁修复、branch protection required check 调整。
- 来源材料：task plan、diff、`progress.md`、本地 RG-008 命令输出、workflow YAML lint、Regression SSoT / Cadence Ledger。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | [由 task-review 生成] |
| Submitted At | [timestamp] |
| Submitted By | [agent 或 coordinator 身份] |
| Task Key | 2026-06-09-ai4j-docs-site-node-heap-regression-r-005-fix-08b5fbcb |
| Materials Checklist Hash | [由 task-review 生成；只作信息记录，不作为手工门禁] |
| Evidence Summary | `npm run typecheck` and `npm run build` pass without external `NODE_OPTIONS`; workflow YAML lint passes; R-005 closed in legacy/v2 Regression SSoT while R-004 stays open. |
| Open Findings Count | 0 |
| Scanner Version | [生成时的 scanner 版本] |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` uses `checked-none:repo-specific-docs-script-regression` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes for R-005
- 如果不是 100%，剩余漏洞或证据缺口：
  - R-004 remains out of scope and open; it is not evidence against the R-005 fix.
- Fix loop count：1
- 当前结论：可以提交审查；standard docs-site commands now carry the required Node heap and local RG-008 passed.

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- R-004 remains open for a later Windows Docusaurus output/cache cleanup lock investigation.
- Push to `main` should trigger docs workflows; remote run evidence can be appended if needed after push.

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:docs-site | `npm run typecheck` passed without external `NODE_OPTIONS`. |
| E-002 | command | TARGET:docs-site | `npm run build` passed and generated `docs-site/build`. |
| E-003 | command | TARGET:.github/workflows | `npx.cmd --yes yaml-lint .github/workflows/docs-build.yml .github/workflows/docs-pages.yml` passed. |
| E-004 | diff | TARGET:docs-site/package.json | `typecheck` and `build` scripts call local CLI through `node --max-old-space-size=8192`. |
| E-005 | report | TARGET:docs/05-TEST-QA/Regression-SSoT.md | R-005 closed; RG-008 last verified updated; R-004 remains open. |
| E-006 | report | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | v2 projection matches legacy R-005/R-004 state. |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞 R-005 目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| R-004 Windows Docusaurus cleanup/file-lock risk remains open. | project coordinator | yes | Future R-004-specific task. |
| Remote docs workflow evidence is not available until after push. | project coordinator | yes | Observe docs-build/docs-pages after push and append evidence only if needed. |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | Agent materials are complete after `task-review`; human confirmation remains separate. | Human confirmation or return. |
| Missing Materials | no | Brief, task plan, progress, visual map, lesson decision, review, and walkthrough are present. | n/a |
| Blocked | no | No open blocking finding for R-005. | n/a |
| Lessons | no | No reusable lesson candidate beyond updated standards and ledgers. | n/a |
| Confirmed / Finalized | no | Human review confirmation has not been performed by agent. | Human confirmation and closeout. |
| Soft-deleted / Superseded | no | Task remains active. | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：见 `progress.md` 2026-06-10 00:22 记录
- 发现记录：已更新 `findings.md`
- Regression SSoT：legacy/v2 均关闭 R-005；R-004 保持开放
- Lessons：checked-none:repo-specific-docs-script-regression
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自标准 `npm run typecheck` / `npm run build` 在无外部 `NODE_OPTIONS` 下通过、workflow YAML lint 通过、R-005 与 R-004 在两套 Regression SSoT 中被明确区分，以及 docs workflows 复用同一 package script 入口。
