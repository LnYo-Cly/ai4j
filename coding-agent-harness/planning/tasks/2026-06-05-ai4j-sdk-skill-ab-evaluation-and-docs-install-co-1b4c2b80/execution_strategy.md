# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | not used |
| worker subagent | not authorized | write only after user approval | n/a | n/a | n/a | n/a | not used |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 用户未要求并行 agent；评测和 README 改动可由 coordinator 本地完成并用命令验证。 | self-check 后提交人工 review。 |
| Would a worker subagent materially help? | no | 写入范围窄且集中，worker 会增加协调成本。 | coordinator 直接执行。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-05 | docs-site README and task artifacts | current checkout | 无需 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 负责评测、README、验证和收口。 |
| Subagent 模式 | none | 用户未要求并行，且本地评测足够可复查。 |
| 审查模型 | self-check + human review | 命令验证覆盖 README/docs-site 和 Skill 结构。 |
| Worktree 策略 | same checkout | 当前工作树干净，改动范围清晰。 |
| 冲突控制 | coordinator owns shared files | 只修改 `docs-site/README.md` 和当前任务目录。 |
| 证据深度 | L1 | 运行 docs-site build、Skill validation 和内容检索。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git remote -v` | `progress.md` | 安装命令使用真实 remote 坐标。 |
| L0 | `rg` 检索 README 和 A/B 报告关键内容 | `progress.md` | 安装命令、调用示例和评分结果存在。 |
| L1 | `python ... quick_validate.py skills/ai4j-sdk` | `progress.md` | 返回 `Skill is valid!`。 |
| L1 | `cd docs-site && npm run build` | `progress.md` | Docusaurus build 成功。 |

## 暂停 / 升级条件

- 需要推送远程、发布 tag 或验证 GitHub 安装可用性。
- `npx skills add` 的公开安装机制发生变化。
- docs-site build 失败且原因超出 README 修改范围。
