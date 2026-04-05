# 2026-03-21 Coding Session Alignment Phase 2

## 目标
- 补齐 coding-agent 的 session / compact / replay 关键体验。
- 让 CLI/TUI 能更直接感知 compact 状态、checkpoint 概要、session 元数据。
- 保持现有 Java 8 兼容与最小依赖。

## 本轮任务
- [x] 盘点现有 session/compact/replay 缺口
- [x] 增强 session 状态模型与 CLI/TUI 可视化输出
- [x] 增补测试覆盖
- [x] 完成后更新文档状态

## 说明
- 该文档为过程沉淀，不纳入 commit。

## 本轮完成
- `CodingSessionSnapshot` 新增 checkpoint 与最近 compact 的关键信息字段。
- `CodingSession` 新增 `latestCompactResult`，让 snapshot 能稳定反映最近一次成功 compact，而不是只靠临时事件输出。
- CLI `/status` 与 `/session` 已展示 checkpoint goal 与 compact mode/token 变化。
- 新增 snapshot/compact 相关断言，保证后续 refactor 不回退。

## 验证
- `mvn -pl ai4j-tui,ai4j-cli,ai4j-coding -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiSessionViewTest,CodeCommandTest,CodingSessionTest" test`
- 结果：`BUILD SUCCESS`
