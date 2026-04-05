# 2026-03-20 AI4J TUI Module Split

## Goal
将当前 coding-agent 的终端 UI 能力从 `ai4j-cli` 中抽离为独立的 `ai4j-tui` 模块，形成 `ai4j-coding + ai4j-tui + ai4j-cli` 三层结构，同时保证默认 CLI 入口继续可用。

## Scope
- [completed] 1. 梳理当前 TUI 相关类、依赖方向与迁移边界
- [completed] 2. 新增 `ai4j-tui` Maven 模块并接入根 POM
- [completed] 3. 迁移 TUI 视图/主题/终端交互相关类到 `ai4j-tui`
- [completed] 4. 调整 `ai4j-cli` 对 `ai4j-tui` 的依赖与启动装配
- [completed] 5. 跑测试并修复回归
- [completed] 6. 更新任务状态与验证记录

## Deliverables
- `ai4j-tui` 独立模块
- `ai4j-cli -> ai4j-tui -> ai4j-coding` 清晰依赖关系
- 默认 CLI/TUI 入口继续可用
- 迁移后的测试通过记录

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 不额外拆分新的 CLI core 模块。
- `ai4j-coding` 保持为 session/process/replay/compact 等领域能力内核。
- 本轮已迁移的主要类：
  - `TerminalIO`
  - `StreamsTerminalIO`
  - `TuiConfig`
  - `TuiTheme`
  - `TuiConfigManager`
  - `TuiSessionView`
  - `TuiInteractionState`
  - `TuiRenderContext`
- 本轮已完成的结构调整：
  - 根 POM 新增 `ai4j-tui`
  - `ai4j-cli` 改为依赖 `ai4j-tui`
  - `ai4j-cli` 删除原有 TUI 类与主题资源，TUI 资源迁移到 `ai4j-tui`
- 验证命令：
  - `mvn -pl ai4j-tui,ai4j-cli -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiConfigManagerTest,CodeCommandTest,Ai4jCliTest,FileCodingSessionStoreTest,FileSessionEventStoreTest,DefaultCodingSessionManagerTest,CodeCommandOptionsParserTest,DefaultCodingCliAgentFactoryTest" test`
