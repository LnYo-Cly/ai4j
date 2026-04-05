# 2026-03-20 Coding CLI Session Lineage

## Goal
为 coding-agent CLI/TUI 补齐 session lineage 基础能力：history、tree、fork、no-session。

## Scope
- [completed] 1. 为 session 增加 root/parent lineage 元数据
- [completed] 2. 增加 `--fork` / `--no-session` 参数
- [completed] 3. 增加 `/history` / `/tree` / `/fork` 命令
- [completed] 4. 补充 TUI/CLI 展示与帮助文本
- [completed] 5. 补充并通过测试

## Deliverables
- session lineage 元数据持久化
- fork session 创建能力
- history/tree CLI 命令
- no-session 内存模式
- 对应测试

## Verification
- `mvn -pl ai4j-cli -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=CodeCommandTest,Ai4jCliTest,FileCodingSessionStoreTest,FileSessionEventStoreTest,DefaultCodingSessionManagerTest,CodeCommandOptionsParserTest,DefaultCodingCliAgentFactoryTest,TuiConfigManagerTest" test`

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 下一步继续补 custom commands、permission/approval 与更强的 TUI 交互。
