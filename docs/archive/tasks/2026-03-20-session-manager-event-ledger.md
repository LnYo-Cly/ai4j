# 2026-03-20 Session Manager Event Ledger

## Goal
补齐 coding-agent 的 session runtime 核心层：新增 SessionManager、ManagedCodingSession、SessionEvent ledger，并把 CLI/TUI 的 session / events / checkpoint 统一接入该层。

## Scope
- [done] 1. 新增 SessionEvent / SessionDescriptor / ManagedCodingSession 模型
- [done] 2. 新增 file-backed SessionEventStore / SessionManager
- [done] 3. CLI 接入 `/session`、`/events`、`/checkpoint` 与 manager 驱动的 session 生命周期
- [done] 4. TUI 增加 checkpoint 面板并改为 manager 驱动，事件面板改为 ledger 视图
- [done] 5. 补充测试并完成验证

## Deliverables
- SessionEvent 持久化流水
- SessionManager 统一 create/resume/save/list/listEvents
- ManagedCodingSession 一级运行时包装
- CLI 命令：`/session`、`/events`
- TUI checkpoint 面板
- 对应测试与验证记录

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 本阶段不做 replay，只先把 session ledger 和 manager 立起来。

## Verification
- `mvn -pl ai4j-cli -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=CodeCommandTest,Ai4jCliTest,FileCodingSessionStoreTest,FileSessionEventStoreTest,DefaultCodingSessionManagerTest,CodeCommandOptionsParserTest,DefaultCodingCliAgentFactoryTest" test`
- 结果：20 tests, 0 failures, 0 errors
