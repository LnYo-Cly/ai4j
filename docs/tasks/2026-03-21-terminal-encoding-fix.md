# 2026-03-21 Terminal Encoding Fix

## 目标
- 修复 Windows/中文终端下 CLI/TUI 输入输出乱码。
- 保证 `StreamsTerminalIO` 的输入与输出统一使用同一编码。

## 本轮任务
- [x] 分析编码链路不一致问题
- [x] 统一 `StreamsTerminalIO` 的读写编码
- [x] 增加显式终端编码覆盖入口
- [x] 补充中文回归测试
- [x] 更新状态

## 交付
- `StreamsTerminalIO` 改为统一 charset 读写。
- 支持 `-Dai4j.terminal.encoding=<charset>` 与 `AI4J_TERMINAL_ENCODING=<charset>`。
- 现代终端场景优先 UTF-8，其他情况回退到 JVM/default charset。
- 新增 `StreamsTerminalIOTest` 验证中文输入输出。

## 变更
- 修改 `ai4j-tui/src/main/java/io/github/lnyocly/ai4j/tui/StreamsTerminalIO.java`
- 新增 `ai4j-tui/src/test/java/io/github/lnyocly/ai4j/tui/StreamsTerminalIOTest.java`

## 说明
- 该文档为过程沉淀，不纳入 commit。
