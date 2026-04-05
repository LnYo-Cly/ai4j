# 2026-03-21 TUI UX Polish Phase 2

## 目标
- 继续提升默认 TUI 的信息密度、状态感知与使用体感。
- 保持自研 ANSI TUI 内核，不引入第三方重量级 TUI 框架。
- 为后续主题替换、自定义 renderer 与更复杂 overlay 打基础。

## 本轮任务
- [x] 盘点现有 TUI 缺口
- [x] 增强 header/status/footer 信息结构
- [x] 优化面板提示、会话/compact/replay 可见性
- [x] 增补定向测试
- [x] 完成后更新文档状态

## 说明
- 该文档为过程沉淀，不纳入 commit。

## 本轮完成
- Header 新增 `tok` / `ckpt` / `compact` badge，状态感更强。
- Status panel 新增 checkpoint goal、checkpoint 时间、compact 状态、compact token 变化、compact summary。
- Footer 直接显示当前 session、tokens、compact mode，减少来回切 panel。
- 为 compact/checkpoint 状态新增 TUI 渲染测试。

## 验证
- `mvn -pl ai4j-tui,ai4j-cli,ai4j-coding -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiSessionViewTest,CodeCommandTest,CodingSessionTest" test`
- 结果：`BUILD SUCCESS`
