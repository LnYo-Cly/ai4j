# 2026-03-21 Live Process Tail Auto Refresh

## Goal
为 `ai4j-cli` 与 `ai4j-tui` 增加真正可用的 live process tail / auto-refresh 能力，让 process inspector 与 `/process follow` 更接近 coding agent 场景下的实时进程观察体验。

## Scope
- [in_progress] 1. 扩展 `TerminalIO` 支持带超时的按键轮询
- [pending] 2. 改造 TUI loop，在空闲时自动刷新 live process 视图
- [pending] 3. 增强 `/process follow` 为增量日志跟随
- [pending] 4. 补充对应测试
- [pending] 5. 跑验证并更新任务状态

## Deliverables
- `TerminalIO.readKeyStroke(long timeoutMs)`
- TUI live process auto-refresh
- CLI incremental `/process follow`
- 对应测试

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 保持 Java 8 兼容。
- 继续采用轻量 ANSI 路线，不引入额外 TUI 框架。
