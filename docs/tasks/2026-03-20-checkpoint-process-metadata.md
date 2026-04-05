# 2026-03-20 Checkpoint Process Metadata

## Goal
将 ai4j-coding 的 compact 从纯文本摘要提升为结构化 checkpoint，并在 session save/resume 中保存后台进程元数据快照（仅恢复元数据，不恢复活进程控制）。

## Scope
- [completed] 1. 设计并落地 `CodingSessionCheckpoint` / `StoredProcessSnapshot`
- [completed] 2. 更新 compact 结果与 session state 序列化结构
- [completed] 3. 更新 CLI/TUI 的 session/process 可见性
- [completed] 4. 补充测试：compact、save/resume、restored process 只读语义
- [completed] 5. 运行定向验证

## Deliverables
- 结构化 checkpoint 模型与渲染逻辑
- session state 中可持久化的 process metadata snapshots
- restored process 的只读元数据语义
- CLI/TUI 对 checkpoint/process 状态的展示
- 对应测试与验证记录

## Completed
- 新增 `CodingSessionCheckpoint` 与 `StoredProcessSnapshot`
- `CodingSessionCompactor` 已返回结构化 checkpoint，并把渲染后的 summary 写回 memory
- `CodingSessionState` / `CodingSessionSnapshot` 已支持 checkpoint 与 process metadata
- `SessionProcessRegistry` 已支持 restored metadata snapshots，并对 logs/write/stop 返回只读语义错误
- CLI 已支持 `/processes`，TUI 已新增 process 面板与 active/restored 状态展示
- 定向测试 18 项通过
- 真实智谱 `glm-4.7` CLI smoke 再次通过

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- restored process 仅恢复元数据，不支持继续 logs/write/stop 控制。
