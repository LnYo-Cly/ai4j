# 2026-03-21 TUI Slash Command Completion

## 目标
- 对齐 Codex / opencode / pi-tui 的 slash-command 基础交互。
- 在输入 `/` 时自动弹出命令列表，支持方向键选择、Tab 补全、Enter 执行。

## 本轮任务
- [x] 复用现有 palette 状态承载 slash mode
- [x] 接入 slash 自动弹出与实时过滤
- [x] 接入 Tab 补全与 Enter 执行
- [x] 优化输入提示与 commands 面板内容
- [x] 补充 TUI / CLI 回归测试
- [x] 更新状态

## 交付
- `TuiInteractionState` 新增 `PaletteMode`，支持 `GLOBAL` / `SLASH`
- 输入框在 slash 模式下自动弹出 `SLASH COMMANDS`
- `Up/Down` 选择、`Tab` 补全、`Enter` 执行、`Esc` 关闭
- commands 面板同步为完整命令集合
- 新增 slash 交互测试

## 变更
- 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
- 修改 `ai4j-tui/src/main/java/io/github/lnyocly/ai4j/tui/TuiInteractionState.java`
- 修改 `ai4j-tui/src/main/java/io/github/lnyocly/ai4j/tui/TuiSessionView.java`
- 修改 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
- 修改 `ai4j-tui/src/test/java/io/github/lnyocly/ai4j/tui/TuiSessionViewTest.java`

## 说明
- 该文档为过程沉淀，不纳入 commit。

## 验证
- `mvn -pl ai4j-cli -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" -DforkCount=0 "-Dtest=CodeCommandTest#test_tui_slash_commands_can_tab_complete_and_exit" test`
- `mvn -pl ai4j-tui -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" -DforkCount=0 "-Dtest=StreamsTerminalIOTest,TuiSessionViewTest" test`
