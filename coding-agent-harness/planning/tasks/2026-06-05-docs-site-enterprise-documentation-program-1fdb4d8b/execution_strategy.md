# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | used | read-only | user approved parallel work | 2026-06-05 | docs-site audit only | n/a | no further action needed |
| worker subagent | not authorized | none | coordinator decision | 2026-06-05 | no worker writes; coordinator owns docs-site shared files | current checkout; no worker worktree | not used for this wave |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes | docs-site 范围大，Core/MCP、Agent/Coding/FlowGram、全站 IA 可以并行只读审计 | 已完成三路只读审计 |
| Would a worker subagent materially help? | no | 本轮写入集中在 sidebar、入口页和 shared docs，多个 worker 同时写会增加冲突；coordinator 串行整合更稳 | 后续深页迁移可再拆 worker |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| parallel read-only subagents | authorized | user | 2026-06-05 | docs-site read-only audits | n/a | 用户确认可以并行 |
| worker subagent | not-needed | coordinator | 2026-06-05 | n/a | n/a | 写入集中在 coordinator |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排、写入、构建验证和提交 |
| Subagent 模式 | reviewer-only | 三个 subagent 只读审计，不直接修改文件 |
| 审查模型 | self-check + build + human review | 本轮为 docs-site 文档和导航，Docusaurus build 是主要回归证据，最终仍需人工确认 |
| Worktree 策略 | same checkout | 只读 subagent 不需要 worktree；写入由 coordinator 串行完成 |
| 冲突控制 | coordinator owns shared files | `sidebars.ts`、`docusaurus.config.ts`、入口页和任务材料由 coordinator 独占 |
| 证据深度 | L1 | docs-site build 覆盖导航、include、链接和 Docusaurus 编译 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| Core/MCP reviewer | docs-site Core SDK、MCP、ai-basics | read-only | final audit summary | subagent 019e95cd-25ef-7610-b044-480d7aa21a2b |
| Agent/Coding/FlowGram reviewer | docs-site Agent、Coding Agent、FlowGram | read-only | final audit summary | subagent 019e95cd-61ed-7b11-ab65-ace9b2b91cad |
| IA reviewer | docs-site sidebar、Start Here、legacy directories | read-only | final audit summary | subagent 019e95cd-9fec-72f0-86f0-be0063f41b12 |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `rg` 检查生硬措辞和旧 MCP 入口 | `progress.md` | 新增/改动范围无目标残留 |
| L1 | `npm run build` | `progress.md`、`review.md` | Docusaurus build 通过 |
| L2 | dashboard / harness status | `review.md` | task lifecycle 可见；无 blocker |
| L3 | Human Review Confirmation | dashboard review workbench | 人工确认后 closeout |

## 暂停 / 升级条件

- 需要删除 legacy 目录或批量移动旧页。
- 文档需要承诺未验证的 provider 能力或发布能力。
- Docusaurus build 出现无法在本轮修复的断链或配置问题。
- 人工 review 要求进一步拆分 deep page migration。
