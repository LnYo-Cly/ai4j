# 2026-03-20 TUI Interactive Kernel Phase 1

## Goal
为 `ai4j-tui` 实现第一阶段可交互内核：raw key 输入、alternate screen、输入缓冲、command palette、面板焦点切换，并为后续 process/replay/compact 控制台打基础。

## Scope
- [completed] 1. 设计并实现 TUI key/interaction 基础模型
- [completed] 2. 扩展 `TerminalIO` 支持 raw key 与 alternate screen
- [completed] 3. 改造 `CodingCliSessionRunner` 为 TUI raw event loop
- [completed] 4. 实现 palette / 输入栏 / 面板 focus 渲染
- [completed] 5. 补充 replay 与 process 控制基础命令
- [completed] 6. 跑测试并对本轮代码做 simplifier 收敛

## Deliverables
- raw TUI event loop
- `Ctrl+P` command palette
- `Tab` panel focus
- TUI input bar
- `/replay` 与 process 控制基础命令
- 对应测试

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 继续保持 Java 8 兼容。
- 本轮以可维护的轻量 ANSI 路线为主，不引入重型终端 UI 框架。
- 已验证命令：
  - `mvn -pl ai4j-tui,ai4j-cli,ai4j-coding -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiConfigManagerTest,CodeCommandTest,DefaultCodingSessionManagerTest,FileSessionEventStoreTest,FileCodingSessionStoreTest" test`
