# 2026-03-21 TUI Runtime Renderer Abstraction

## 目标
- 将 CLI 与默认 ANSI TUI 解耦，形成可替换的 `TuiRuntime` / `TuiRenderer` 抽象。
- 保持现有默认 TUI 能力不退化，包括会话面板、事件流、process inspector、replay overlay。
- 补齐 process follow 的自动刷新基础能力，为后续自定义 TUI/主题/渲染器提供稳定接入点。

## 背景
- 目前 `ai4j-cli` 仍直接依赖 `TuiSessionView` 的具体实现。
- 用户预期默认官方 TUI 持续增强，同时开发者可以替换为自己的 TUI 框架或渲染器。

## 设计要点
1. `TuiRuntime` 负责终端生命周期、raw input、带超时按键轮询。
2. `TuiRenderer` 负责基于 `TuiScreenModel` 的纯渲染。
3. `TuiScreenModel` 作为 CLI -> TUI 的稳定视图模型。
4. `CodingCliSessionRunner` 只依赖抽象，不依赖默认 ANSI 视图内部状态。
5. process inspector 在 follow 模式下支持自动刷新。

## 任务拆分
- [x] 新增 TUI 抽象接口与 screen model
- [x] 完成 `CodingCliSessionRunner` 抽象迁移
- [x] 补齐 `/process follow` 与 TUI 自动刷新联动
- [x] 跑定向测试并修正回归
- [x] 代码整理与注释补强

## 交付
- `ai4j-tui`：新增抽象层与默认 ANSI runtime 实现
- `ai4j-cli`：迁移到抽象层，保留现有默认交互能力
- 测试：TUI、CLI、coding session 相关测试通过

## 风险与说明
- 当前仓库存在其它未提交改动，本任务只处理 TUI/CLI/coding 相关文件。
- 该文档为过程沉淀，不纳入 commit。

## 变更结果
- CLI 新增 `CodingCliTuiFactory` / `CodingCliTuiSupport` / `DefaultCodingCliTuiFactory`，默认 ANSI TUI 构造已从 runner 中抽离。
- `CodingCliSessionRunner` 现在只依赖 `TuiRuntime` / `TuiRenderer` / `TuiScreenModel` 抽象。
- `TuiSessionView` 增加兼容渲染入口，避免老测试与外部调用方式断裂。
- `/process follow` 在普通 CLI 下改为增量 follow，支持 running 进程短轮询与无输出自动暂停提示。
- `StreamsTerminalIO` 修复了基于 `available()` 的轮询缺陷，改为同时检测 reader buffer，避免 TUI 按键丢失与空转。

## 验证
- `mvn -pl ai4j-tui,ai4j-cli,ai4j-coding -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=TuiConfigManagerTest,TuiSessionViewTest,CodeCommandTest,DefaultCodingSessionManagerTest,FileSessionEventStoreTest,FileCodingSessionStoreTest,CodingSessionTest" test`
- 结果：`BUILD SUCCESS`
