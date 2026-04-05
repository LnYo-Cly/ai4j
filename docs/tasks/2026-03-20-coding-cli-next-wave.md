# 2026-03-20 Coding CLI Next Wave

## Goal
依次补齐 coding-agent CLI/TUI 的下一轮核心能力：TUI 配置/主题、session history/tree/fork/no-session、custom commands、permission/approval，以及更强的 TUI 交互体验，使其逐步靠近 pi / opencode 的使用层次。

## Scope
- [completed] 1. 梳理现有 CLI/TUI/session 现状与设计缺口
- [completed] 2. 实现 TUI 配置与主题体系（tui.json、themes、/theme）
- [completed] 3. 实现 session history/tree/fork/no-session 基础能力
- [completed] 4. 实现 custom commands 与 permission/approval 基础层
- [completed] 5. 增强 TUI 组件与交互表现
- [completed] 6. 补充测试并完成验证

## Deliverables
- TUI 主题配置与内置主题
- session tree/history/fork 基础能力
- custom command registry
- permission / approval strategy
- 更完整的 TUI 布局与状态栏
- 对应测试与验证记录

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 优先先把基础层立住，交互复杂度逐步增加。
- 当前已验证 TUI 主题、session lineage、custom commands、approval 相关测试。
- 本轮补齐后，已额外覆盖：
  - TUI `HISTORY` / `TREE` 独立面板
  - TUI `APPROVAL` 面板与审批状态同步
  - `COMMANDS` / `/palette` 与增强状态栏
  - 对应 CLI 测试断言更新
