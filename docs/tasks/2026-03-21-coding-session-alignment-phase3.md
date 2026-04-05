# 2026-03-21 Coding Session Alignment Phase 3

## 目标
- 增强 coding session 生命周期可见性。
- 提供 compact 历史查询入口，并让 CLI/TUI 可以感知相关状态。

## 本轮任务
- [x] 设计 compact 历史查询接口
- [x] 接入 CLI 命令与 palette
- [x] 补充测试
- [x] 更新状态

## 说明
- 该文档为过程沉淀，不纳入 commit。

## 交付
- 新增 `/compacts [n]`，从 session event ledger 查询 compact 历史。
- `/help` 与 palette 已接入 compact history 入口。
- CLI 输出 compact mode/tokens/items/splitTurn/checkpoint goal/summary。
- `CodeCommandTest` 增加 `/compacts` 的回归覆盖。

## 变更
- 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
- 修改 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`

## 验证
- `mvn -pl ai4j-tui,ai4j-cli,ai4j-coding -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiSessionViewTest,CodeCommandTest,CodingSessionTest" test`

## 已完成
- compact history 查询入口已打通，CLI/TUI 均可感知最近 compact 结果。

## 未完成
- 后续可考虑增加专门的 compact overlay/ledger viewer，而不仅是 assistant panel 文本输出。
