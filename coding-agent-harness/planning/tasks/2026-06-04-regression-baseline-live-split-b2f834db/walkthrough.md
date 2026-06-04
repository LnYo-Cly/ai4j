# 收口记录：regression baseline live split

## 摘要

本任务将 ai4j-sdk 回归治理拆成 deterministic local-required baseline、live-provider opt-in gate 和 credential-release opt-in gate。默认 closeout 只要求本地可重复证据；真实 provider、发布签名、hosted demo 或浏览器真人代理证据必须由任务/发布明确 opt-in，并记录 env var 名称和脱敏证据。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | harness governance / regression |
| 新增文件 | none |
| 删除文件 | none |
| 不在范围内 | 业务测试代码、Maven live-test profile、真实 provider、发布签名、CI required checks |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| harness status | `npx --yes coding-agent-harness status --json .` | pending final run | `progress.md` |
| key scan | scan for local/live gate ids and template placeholders | pending final run | `progress.md` |
| executable regression | not run | waived for governance-only docs change | `review.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| coordinator self-review | none blocking | live/profile/CI follow-ups routed to residuals | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| live-provider tests still need normalized profile/category | project coordinator | yes | R-002 / R-006 |
| FlowGram webapp demo baseline not yet in dedicated CI | project coordinator | yes | R-007 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes; no candidate accepted |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
