# 2026-03-20 Session Replay TUI Refresh

## Goal
补齐 coding-agent session 体系的下一批体验能力：session replay/restore replay 能力，以及 TUI 的更连续刷新体验，使 session inspect/恢复/追溯接近 pi-sdk / opencode 的使用方式。

## Scope
- [in_progress] 1. 梳理当前 session / ledger / tui 现状与缺口
- [pending] 2. 增加 replay 相关 runtime / CLI 能力
- [pending] 3. 增强 restore 后的 replay/inspect 展示
- [pending] 4. 增强 TUI live refresh / recent activity 展示
- [pending] 5. 补充测试并完成验证

## Deliverables
- session replay / inspect 命令
- restore 后可见的 replay 视图
- TUI 更连续的 session/activity 展示
- 对应测试与验证记录

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 优先做 inspect/replay，不做复杂的交互式 event replay 执行引擎。
