# Add Anthropic Messages adapter - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self-review | `platform/anthropic/` 协议映射、流式转译、回归、live 烟测、任务材料一致性 |

## 审查范围

- 审查类型：implementation / regression
- 范围内：`ai4j` 新增 `platform/anthropic/`、`PlatformType.ANTHROPIC`、`Configuration`/工厂注册、13 个单测、2 个 live 烟测
- 范围外：docs-site、agent/coding/cli 层接入（后续任务）
- 来源材料：代码 diff、真实 Maven 回归（116 tests）、live 烟测（coding-plan key × api/anthropic）

## Agent Review Submission（Agent 提交审查）

| Field | Value |
| --- | --- |
| Submission ID | 2026-06-22-add-anthropic-messages-adapter-3876ee40 |
| Submitted At | 2026-06-22 21:40 +08:00 |
| Submitted By | coordinator |
| Task Key | 2026-06-22-add-anthropic-messages-adapter-3876ee40 |
| Materials Checklist Hash | n/a |
| Evidence Summary | 13 映射单测 + 2 live 烟测通过 + ai4j 116 回归 + 下游编译 + diff hygiene |
| Open Findings Count | 0 |
| Scanner Version | local manual review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Findings | yes | present | `findings.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

## 信心挑战（Confidence Challenge）

- Verdict：yes
- 信心来源：请求/响应/流映射均有离线单测；live 烟测用真实 coding-plan key 经 Anthropic 端点返回真实内容，且 finishReason/usage 正确；下游编译证明纯增量改动无回归。
- 剩余漏洞：首轮未接入 agent/coding/cli 层（已知范围外）；多 tool 并行流式按单 tool 链路聚合。

## 重要发现（Material Findings）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

| ID | Note | Evidence | Follow-up |
| --- | --- | --- | --- |
| N-001 | 首轮 provider-only，未接入 agent/coding/cli | task_plan 范围外 | 后续任务 |
| N-002 | 多 tool 并行流式按单 tool 链路（content_block_stop 逐个 emit） | `AnthropicChatService` convertEventSource | 如需 token 级 tool arg 流式再增强 |

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | `G:\My_Project\java\ai4j-sdk` | 实测：同一 coding-plan key 打 `api/paas/v4` 报余额不足、打 `api/anthropic/v1/messages` 秒回（见 findings） |
| E-002 | research | `platform/` + grep | 确认 SDK 此前零 Anthropic 支持 |
| E-003 | command | `G:\My_Project\java\ai4j-sdk` | `mvn -pl ai4j -am "-Dtest=AnthropicChatServiceTest" test` 通过，13 tests 0 failures |
| E-004 | command | `G:\My_Project\java\ai4j-sdk` | `mvn -pl ai4j -DskipTests=false test` 通过，116 tests |
| E-005 | command | `G:\My_Project\java\ai4j-sdk` | 下游 `ai4j-agent,ai4j-coding,ai4j-cli -am compile` BUILD SUCCESS |
| E-006 | command | `G:\My_Project\java\ai4j-sdk` | live `AnthropicTest`（coding-plan key × open.bigmodel.cn/api/anthropic，glm-5.1）common+stream 2 tests 通过 |
| E-007 | command | `G:\My_Project\java\ai4j-sdk` | `git diff --check` 无 whitespace error |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 未接入 agent/coding/cli 层 | coordinator | yes | 后续任务 |
| 多 tool 并行流式为单 tool 链路实现 | coordinator | yes | 需要时增强 |
| 未做 docs-site | coordinator | yes | 单独文档任务 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 已提交审查材料包，可等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 任务材料已补齐。 | 无 |
| Blocked | no | 无阻塞发现。 | 无 |
| Lessons | no | 前置发现已沉淀为记忆；本轮无新共享候选。 | 无 |
| Confirmed / Finalized | no | 尚未人工确认。 | 继续 closeout。 |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新
- Progress：已更新 `progress.md`
- 发现记录：已更新 `findings.md`
- Regression SSoT：本轮新增 live provider 烟测（`AnthropicTest`，属 `LiveProviderTest` 类别，默认排除），不改变固定门禁；不更新
- Lessons：checked-none（前置已沉淀）
- 收口记录：已更新 `walkthrough.md`

## 最终信心依据（Final Confidence Basis）

信心来自：13 个映射单测 + 真实 live 烟测（coding-plan key 经 Anthropic 端点返回真实内容、finishReason/usage 正确）+ ai4j 116 回归通过 + 下游编译 SUCCESS + diff hygiene 通过 + 任务材料与代码证据一致。
