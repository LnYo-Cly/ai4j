# 2026-03-20 Coding CLI TUI Interaction Layer

## Goal
增强 coding-agent 的 TUI 交互层：session tree/history 面板、TUI 审批区、command palette / 更强状态栏。

## Scope
- [completed] 1. 梳理当前 TUI 渲染与命令刷新链路
- [completed] 2. 实现 HISTORY / TREE 面板
- [completed] 3. 实现 TUI approval 面板与状态同步
- [completed] 4. 实现 command palette / 快捷命令面板 / 强化状态栏
- [completed] 5. 补充并通过测试

## Deliverables
- HISTORY / TREE TUI 面板
- APPROVAL TUI 面板
- COMMANDS / palette TUI 面板
- 更强状态栏
- 对应测试

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 保持当前纯文本 TUI 路线，不引入重型终端 UI 框架。
- 已落地内容：
  - `CliInteractionState` 统一承载 approval / history / tree / commands 交互状态。
  - `CodingCliSessionRunner` 在 TUI 模式下刷新并缓存 session history/tree/commands。
  - `TuiSessionView` 新增 `HISTORY`、`TREE`、`APPROVAL`、`COMMANDS` 面板，并增强头部状态栏。
  - `/palette` 作为 `/commands` 别名纳入 CLI/TUI 交互层。
- 验证命令：
  - `mvn -pl ai4j-cli -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=CodeCommandTest,Ai4jCliTest,FileCodingSessionStoreTest,FileSessionEventStoreTest,DefaultCodingSessionManagerTest,CodeCommandOptionsParserTest,DefaultCodingCliAgentFactoryTest,TuiConfigManagerTest" test`
