# CLI launcher distribution package - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | Maven distribution, launcher boundaries, docs-site, regression evidence |

## 审查范围

- 审查类型：implementation / release-packaging / docs / regression
- 范围内：`ai4j-cli` distribution assembly、launcher、example config、package smoke、docs-site install/quickstart、RG/SRB 更新。
- 范围外：GitHub Release 上传、checksum、真实 provider、系统 PATH installer 重构。
- 来源材料：`ai4j-cli/pom.xml`、`ai4j-cli/src/assembly/dist.xml`、`ai4j-cli/src/main/dist/**`、`CliDistributionLayoutTest`、docs-site 页面和 Regression SSoT / Cadence Ledger。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | coordinator |
| Task Key | MODULES/cli-host/2026-06-20-cli-launcher-distribution-package-85f1c718 |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | CLI distribution implementation prepared; targeted layout test and package smoke passed; docs build / harness status pending final run. |
| Open Findings Count | 0 |
| Scanner Version | pending-task-review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - Unix launcher 目前通过 archive/layout 和 script 内容检查，尚未在真实 Unix shell 下运行。
  - GitHub Release asset 上传、checksum 和 release notes 未实现。
- Fix loop count：1
- 当前结论：这些缺口不阻塞本任务，因为本任务目标是源码 package 可产出 distribution 包；后续发布自动化需另开任务。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- `.gitignore` 需要精确反忽略 `ai4j-cli/src/main/dist/**`，否则发行包源模板不会进入 git。
- 发行包示例配置只能包含 placeholder/env var，不能包含用户或开发者真实 key。
- 在线 install 脚本和源码 dist 包是两条互补路径，不应互相覆盖。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:. | `mvn -pl ai4j-cli -am "-Dtest=CliDistributionLayoutTest" -DskipTests=false -DfailIfNoTests=false test` passed with 3 tests. |
| E-002 | command | TARGET:. | `mvn -pl ai4j-cli -am -DskipTests package` generated fat jar, dist zip and dist tar.gz. |
| E-003 | fixture | TARGET:ai4j-cli/target/ai4j-cli-2.3.0-dist.zip | Archive contains bin launchers, fat jar, example config, and README. |
| E-004 | command | TARGET:. | `cmd /c ai4j-cli\src\main\dist\bin\ai4j.cmd --help` with `AI4J_CLI_JAR` override printed CLI help. |
| E-005 | diff | TARGET:docs-site/docs/coding-agent/install-and-release.md | Docs updated to describe current dist package, not missing launcher state. |

## 无重要发现声明

当前未发现阻塞本任务目标的重要发现。剩余风险均属于后续 release automation 或跨平台 smoke 深化范围。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| checksum / GitHub Release asset 上传未实现 | coordinator | yes | Release automation task |
| Unix launcher 未在真实 Linux/macOS shell 下运行 | coordinator | yes | CI/Linux package smoke |
| 在线 install 脚本仍下载 fat jar 而不是 dist asset | coordinator | yes | 安装脚本统一策略 task |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 本地验证完成后提交待人工确认。 | `task-review` 后人工确认。 |
| Missing Materials | no | 必需 task package 文件已补齐；最终以 harness status 为准。 | n/a |
| Blocked | no | 当前无 open blocking finding。 | n/a |
| Lessons | no | 本任务无 lesson candidate。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认和 closeout。 | 人工确认后 closeout。 |
| Soft-deleted / Superseded | no | 当前任务有效。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：`task_plan.md`
- Progress：`progress.md`
- 发现记录：`findings.md`
- Regression SSoT：RG-004 已更新
- Cadence Ledger：SRB-062 已新增
- Lessons：checked-none: cli-distribution-bounded-slice
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

本地 targeted test、package smoke、archive inspection、launcher help smoke 和 docs diff 支撑当前实现。最终提交前仍需 docs-site build、diff check、Harness status 和 PR CI。
