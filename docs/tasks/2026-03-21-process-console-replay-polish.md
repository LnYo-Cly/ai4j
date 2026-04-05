# 2026-03-21 Process Console And Replay Polish

## Goal
为 `ai4j-cli` + `ai4j-tui` 增加更完整的 process console 与 replay viewer 体验，并把 TUI 交互进一步收紧到更接近 `opencode` / `iflow-cli` 的使用体感。

## Scope
- [completed] 1. 扩展 TUI interaction state，支持 process / replay 子视图状态
- [completed] 2. 增强 `CodingCliSessionRunner` 的 process 命令与快捷键分发
- [completed] 3. 增强 `TuiSessionView` 的 process inspector / replay viewer / 状态条渲染
- [completed] 4. 补充 process status / follow / replay viewer 对应测试
- [completed] 5. 跑验证并更新本任务状态

## Deliverables
- `/process status <id>`
- `/process follow <id> [limit]`
- TUI process inspector
- TUI replay viewer
- 更清晰的 focus / mode / footer 状态表达
- 对应测试

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 保持 Java 8 兼容。
- 继续采用轻量 ANSI 实现，不引入重型 TUI 框架。
- 已验证命令：
  - `mvn -pl ai4j-tui,ai4j-cli,ai4j-coding -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiConfigManagerTest,TuiSessionViewTest,CodeCommandTest,DefaultCodingSessionManagerTest,FileSessionEventStoreTest,FileCodingSessionStoreTest,CodingSessionTest" test`
