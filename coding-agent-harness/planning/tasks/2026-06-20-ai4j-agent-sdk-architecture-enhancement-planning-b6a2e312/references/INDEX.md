# References - AI4J Agent SDK architecture enhancement planning

| ID | Path | Purpose | Status |
| --- | --- | --- | --- |
| R-001 | `ai4j-agent-sdk-enhancement-plan.md` | 初版 `ai4j-agent` 架构增强规划，定义 Session/Memory/Compact/Plugin/Sandbox/Runner/Blueprint 路线。 | current-base |
| R-002 | `ai4j-agent-sdk-complete-planning-refresh.md` | 2026-06-20 完整规划刷新稿，补充插件生态、YAML Blueprint、真实 sandbox、远端 Runner、CLI/TUI、Harness 边界和 P2 下一步。 | current-refresh |
| R-003 | `ai4j-agent-sdk-execution-roadmap-and-research-gates.md` | 执行级路线图与调研门禁，明确 P0-P5 拆分、Pi/Codex/Java SDK/source-backed research gates、docs-site 同步路线和当前实际下一步。 | current-execution |
| R-004 | `ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md` | 集成实施规划，把前序规划、执行路线、P1-C 已合并后的 P2 Sandbox SPI 下一步合并为后续实施入口。 | current-integrated |
| R-005 | `ai4j-agent-sdk-final-roadmap-and-task-plan-2026-06-20.md` | 最终总规划与当前仓库状态校正，记录 P2/P3/P4 已推进后的真实下一步：R0 调研、backlog reconciliation、Remote Runner SPI、one-command install、CLI/TUI polish、docs-site completeness。 | current-final |

## 使用顺序

1. 先读 `ai4j-agent-sdk-final-roadmap-and-task-plan-2026-06-20.md`，获得当前最终入口、root `main` / `dev` 分支状态校正和下一批任务队列。
2. 再读 `ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md`，了解早期集成实施规划和 P1-C 合并后的上下文。
3. 再读 `ai4j-agent-sdk-execution-roadmap-and-research-gates.md`，获得可执行任务队列和调研门禁。
4. 再读 `ai4j-agent-sdk-complete-planning-refresh.md`，获得完整背景结论。
5. 最后读 `ai4j-agent-sdk-enhancement-plan.md`，了解初版路线和审查历史。

## 当前最终入口补充

截至 2026-06-20 13:30 +08:00，优先阅读 `ai4j-agent-sdk-final-roadmap-and-task-plan-2026-06-20.md`。该文件校正了早期“下一步 P2-A”的过期结论：root `main` 已含 P2/P3 基础，`dev` 已含 P4 `/sandbox` metadata-only 命令；后续应按 R0 调研、任务队列收敛、Remote Runner SPI、one-command install、CLI/TUI polish 和 docs-site completeness 分片推进。
