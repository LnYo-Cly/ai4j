# 2026-03-20 CLI Session Compact TUI Smoke

## Goal
按用户指定顺序完成 ai4j-cli / ai4j-coding 的 1、2、4、5：session 持久化与恢复、真实 compact、增强 TUI、智谱真实 smoke。

## Scope
- [completed] 1. Session store / save / list / resume / load
- [completed] 2. Threshold + model-based compact
- [completed] 4. Richer TUI shell
- [completed] 5. Zhipu real smoke for coding-agent-cli

## Deliverables
- coding session 可序列化、可恢复
- CLI/TUI 支持 session 管理命令与 resume 参数
- compact 支持阈值与模型摘要
- TUI 展示状态区 / 事件区 / 会话区
- 智谱真实连通性 smoke 记录

## Completed
- 新增 `CodingSessionState`，支持 session memory 的导出与恢复
- 新增 `FileCodingSessionStore` / `StoredCodingSession`
- CLI 已支持 `--session-id`、`--resume`、`--session-dir`、`--auto-save-session`
- CLI/TUI 已支持 `/save`、`/sessions`、`/resume`、`/load`
- `CodingSessionCompactor` 已支持阈值触发、结构化摘要、保留最近上下文、split-turn 处理与激进压缩回退
- TUI 已支持状态区 / 会话区 / 事件区 / assistant 区 / commands 区
- 智谱 `glm-4.7` + Coding Plan 基于 `chat` 协议的真实 CLI smoke 已通过
- 智谱 `glm-4.7` + Coding Plan 基于 `chat` 协议的真实 TUI smoke 已通过
- 定向测试已通过

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 每完成一项后同步更新状态，完成后归档到 docs/archive/tasks/。
