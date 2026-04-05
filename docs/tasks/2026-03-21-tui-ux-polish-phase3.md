# 2026-03-21 TUI UX Polish Phase 3

## 目标
- 继续贴近 opencode 风格的高信息密度 TUI 体感。
- 强化状态条、当前会话可见性、overlay 上下文信息与快捷提示。

## 本轮任务
- [x] 标记当前会话与面板上下文
- [x] 增强 replay/process overlay 状态信息
- [x] 增强底部状态条与交互提示
- [x] 增补测试
- [x] 更新状态

## 说明
- 该文档为过程沉淀，不纳入 commit。

## 交付
- `TuiSessionView` 会话列表增加当前 session 标记。
- replay viewer 增加 turns/visible/turn 元信息与快捷提示。
- process inspector 增加 selected/restored/log status 提示。
- footer 增加 focus/overlay/process/replay 上下文。
- `TuiSessionViewTest` 补充对应断言。

## 变更
- 修改 `ai4j-tui/src/main/java/io/github/lnyocly/ai4j/tui/TuiSessionView.java`
- 修改 `ai4j-tui/src/test/java/io/github/lnyocly/ai4j/tui/TuiSessionViewTest.java`

## 验证
- `mvn -pl ai4j-tui,ai4j-cli,ai4j-coding -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiSessionViewTest,CodeCommandTest,CodingSessionTest" test`

## 已完成
- phase3 TUI 可见性与底部状态增强已完成。

## 未完成
- 后续还可继续做更强的布局/颜色/快捷操作细化，以进一步贴近 opencode 的默认体验。
