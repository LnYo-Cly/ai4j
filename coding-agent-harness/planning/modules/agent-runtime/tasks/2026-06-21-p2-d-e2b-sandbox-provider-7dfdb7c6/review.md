# P2-D E2B sandbox provider - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | E2B provider 源码、Connect 协议正确性、SPI 对齐、测试覆盖、回归 |
| reviewer subagent | subagent | 待 PR 评审时调用（只读） |

## 审查范围

- 审查类型：adversarial / regression / architecture
- 范围内：`ai4j-agent/.../sandbox/e2b/**`、E2B Connect 协议编解码、live 烟测证据、ai4j-agent 回归
- 范围外：core/CLI/starter（未改动）；cancel/filesystem（v1 范围外，见 findings）
- 来源材料：task plan、diff、live 实测帧、测试输出、ai4j-agent 全模块回归

## Agent Review Submission（Agent 提交审查）

| Field | Value |
| --- | --- |
| Submission ID | self-review-1（待 task-review 生成） |
| Submitted At | 2026-06-23 |
| Submitted By | coordinator |
| Task Key | 2026-06-21-p2-d-e2b-sandbox-provider-7dfdb7c6 |
| Materials Checklist Hash | 待生成 |
| Evidence Summary | 15 离线 + 1 live 测试全绿；ai4j-agent 148 测试 0 失败；live 实测 create/execute(exit 0 + 7)/delete |
| Open Findings Count | 0 |
| Scanner Version | n/a（手工 self-review） |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | brief.md |
| Task plan | yes | present | task_plan.md |
| Progress and evidence | yes | present | progress.md |
| Visual map | yes | present | visual_map.md |
| Lesson candidate decision | yes | present | lesson_candidates.md |
| Walkthrough or closeout link | yes | present | walkthrough.md |

## 信心挑战（Confidence Challenge）

- Verdict：yes
- 100% 依据：Connect 协议逐字节 live 实测确认（请求帧、响应帧、exitCode 陷阱）；纯单测覆盖编解码；本地 HTTP 集成覆盖请求结构；live 烟测覆盖端到端（含非零退出）。
- Fix loop count：1（实现后 live 烟测发现 exit=0 省略 exitCode → 加 status 解析 + 回归单测 → 复测通过）
- 当前结论：实现与验证完成，可提交 PR。

## 重要发现（Material Findings）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 非阻塞备注（Non-Material Notes）

- v1 范围外：cancel（SendSignal）、listArtifacts（filesystem）、create labels/metadata —— 见 findings.md 决策表。
- live 用的 E2B key 出现在会话历史，合入后建议轮换。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:ai4j-agent | `mvn -pl ai4j-agent -am test` → 148 tests 0 failures |
| E-002 | command | TARGET:ai4j-agent | `E2B_API_KEY=... -Plive-provider-tests -Dtest=E2BSandboxLiveSmokeTest` → live exit 0 + exit 7 全绿 |
| E-003 | diff | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/e2b/ | 7 源文件，Connect 编解码 + SPI 实现 |
| E-004 | review | PRIVATE:findings.md | Connect 协议逐项 live 实测确认 |
| E-005 | command | TARGET:. | `git diff --check` 无 whitespace error |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| cancel/listArtifacts/create-labels 未实现 | coordinator | yes | 后续任务接 SendSignal/filesystem |
| E2B key 泄露在会话历史 | user | no | 合入后轮换 key |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | self-review 完成，提交 PR 待评审 | PR 评审通过 / 退回 |
| Missing Materials | no | 必需文件齐全 | — |
| Blocked | no | 无 open blocking finding | — |
| Lessons | no | 候见 lesson_candidates.md | — |
| Confirmed / Finalized | no | 待 PR 合并后收口 | — |
| Soft-deleted / Superseded | no | — | — |

## 后续路由（Follow-Up Routing）

- 任务计划：已填，无需更新
- Progress：progress.md 末条
- 发现记录：findings.md F-001..F-007
- Regression SSoT：新增 `mvn -pl ai4j-agent -am -Plive-provider-tests -Dtest=E2BSandboxLiveSmokeTest`
- Lessons：checked-candidate: LC-20260623-001（Connect 协议 live 实测优先于预研笔记）
- 收口记录：PR 合并后写入

## 最终信心依据（Final Confidence Basis）

信心来自：(1) Connect 协议逐字节 live 实测；(2) 纯单测覆盖编解码与 exitCode 陷阱；(3) 本地 HTTP
集成覆盖请求结构；(4) live 烟测端到端验证 exit 0 与非零 7；(5) ai4j-agent 全模块 148 测试无回归。
发布前最终审查待 PR 评审（非 self-only）。
