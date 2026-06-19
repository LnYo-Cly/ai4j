# Findings - AI4J Agent SDK implementation decomposition and docs roadmap

## 发现

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-001 | P2 | 上一规划任务已 ready-to-confirm，但 CLI 不支持命令行 human review confirmation。 | CLI 输出：Human review confirmation is available only through local Dashboard workbench. | 不伪造确认；继续后续拆解，同时提示用户 dashboard 确认。 | accepted-residual |
| F-002 | P2 | docs-site 已有 Agent/Coding Agent 架构页，新路线文档必须避免重复并明确“路线图，不代表已实现”。 | `docs-site/docs/agent/architecture.md`、`docs-site/docs/coding-agent/architecture.md` | 新增 `agent/sdk-roadmap.md`，从 overview/sidebar 链接。 | in-progress |
| F-003 | P2 | P0-P5 不能一次性实现，否则会破坏模块边界和验证范围。 | 架构规划 P0-P5 | 拆成独立 implementation task。 | done |

## 残余问题

- 具体 P0-A/P0-B/P0-C implementation task 尚未创建，本任务只产出拆解和文档。
- 上一规划任务仍需用户通过 Dashboard workbench 做 Human Review Confirmation。
