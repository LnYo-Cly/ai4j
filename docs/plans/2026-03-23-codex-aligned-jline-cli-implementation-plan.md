# 2026-03-23 Codex 对齐 JLine CLI 实施计划

- 状态：Draft
- 优先级：P0
- 依赖设计：`docs/plans/2026-03-23-codex-aligned-jline-cli-design.md`
- 目标模块：`ai4j-cli`

## 1. 实施目标

将 `ai4j-cli --ui tui` 从 `旧实验 TUI` 主路径迁移到 `JLine 3` shell-first 架构，并在 `Windows Terminal + PowerShell` 下把输入、状态、块输出、slash palette、approval、compact、session 体验压到接近 Codex。

## 2. 实施原则

- 只替换 UI 壳，不重写 `agent / session / tool / patch` 业务层
- 先立住输入与输出纪律，再补功能块
- 先做参考终端体验，再做降级兼容
- 每一阶段都自验证，并记录“做了什么、效果、是否达标、残留缺陷”

## 3. 当前代码基线

当前 `ai4j-cli` 已具备：

- `CodeCommand` 作为入口和模式分流
- `CodingSessionManager`、`SessionEventStore`、`CustomCommandRegistry`
- `旧实验 runner` 作为当前交互式 TUI runner
- `ai4j-coding` 内完整的 session / tool / process / patch 基础能力

这意味着本次迁移重点在 `ai4j-cli` 的交互层，而不是底层业务。

## 4. 阶段拆分

### Phase 1：建立 JLine 主壳骨架

目标：

- 引入 `JLine 3` 所需依赖
- 创建新 runner 和 shell context
- 让 `--ui tui` 可以切到新主壳启动并退出

主要改动：

- `ai4j-cli/pom.xml`
- 新增 `JlineCodeCommandRunner`
- 新增 `JlineShellContext`
- 新增 `TerminalCapabilityDetector`
- 在 `CodeCommand` 中增加新 runner 分流

验收：

- 可以启动新 TUI 主壳
- 可以稳定退出
- 不再依赖 `旧实验 TUI` 才能进入交互路径

### Phase 2：打通 composer 与 transcript 主路径

目标：

- 用 `LineReader` 作为 composer
- transcript 改成 `printAbove`
- 底部状态栏与 spinner 原位更新

主要改动：

- 新增 `ComposerController`
- 新增 `TranscriptPrinter`
- 新增 `CliStatusController`
- 建立 UI 线程事件循环

验收：

- 英文、数字、中文、删除、移动、粘贴稳定
- spinner 不污染 transcript
- 不出现重复 `Thinking / Responding` 噪音块

### Phase 3：完成 Codex 风格基础块格式

目标：

- 用户块、回复块、error 块、info 块格式统一
- 块间空白和换行纪律固定

主要改动：

- 新增 `CodexStyleBlockFormatter`
- 梳理所有基础 block 类型
- 收口长文本裁剪、换行、宽度控制

验收：

- 不再出现大量空白换行
- 不显示 `assistant` / `user`
- transcript 基本风格稳定

### Phase 4：接入 slash palette 与命令语义

目标：

- slash 触发、补全、命令执行都走新输入链
- 命令集合和文案尽量向 Codex 靠拢

主要改动：

- 新增 `SlashCommandController`
- 对接 `/help`、`/session`、`/status`、`/compact`、`/theme`、`/exit`
- 统一 palette 行为和光标恢复

验收：

- `/` 只出现一份 palette
- `Up/Down/Tab/Enter/Esc` 稳定
- 补全后光标在末尾

### Phase 5：接入 tool / patch / approval

目标：

- tool call/result 输出改成 Codex 风格块
- `apply_patch` 输出文件摘要
- approval 走 inline 流程

主要改动：

- 新增 `AgentEventBridge`
- 新增 `ApprovalController`
- 收口 tool result、patch summary、审批交互

验收：

- tool 执行可观察但不刷屏
- patch 展示以文件摘要为主
- 审批结束后 composer 可正常恢复

### Phase 6：接入 session / compact / replay / process

目标：

- 把高阶会话功能接到新 UI 壳
- 维持高级能力可用，但不破坏主路径节奏

主要改动：

- session opened/saved/resumed/forked block
- compact block
- `/history`、`/tree`、`/events`、`/processes` 命令输出收口

验收：

- session/compact 元信息可用
- 默认主界面不被高级状态噪音淹没

### Phase 7：体验压平与主路径切换

目标：

- 细化留白、节奏、spinner 速度、文案
- 将 `--ui tui` 默认主路径正式切到 `JLine`

主要改动：

- 调整 formatter、status 文案、空白和宽度策略
- 让 `旧实验 runner` 退出正式主路径

验收：

- `Windows Terminal + PowerShell` 达到主路径验收标准
- 旧 `旧实验 TUI` runner 不再是默认实现

## 5. 建议文件级改动清单

### 新增

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineCodeCommandRunner.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellContext.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/ComposerController.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliStatusController.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/TranscriptPrinter.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatter.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/ApprovalController.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/AgentEventBridge.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/TerminalCapabilityDetector.java`

### 修改

- `ai4j-cli/pom.xml`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommand.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/DefaultCodingCliTuiFactory.java`（如仍保留 legacy 分流）

### 退场或降级为兼容

- 已移除的旧实验 runner 实现
- 已移除的旧自定义输入组件

## 6. 测试计划

### 单元测试

- `CodexStyleBlockFormatterTest`
- `CliStatusControllerTest`
- `SlashCommandControllerTest`
- `TranscriptPrinterTest`
- `ApprovalControllerTest`

### 集成测试

- `JlineCodeCommandRunnerTest`
- slash palette 单实例
- tool / patch / compact block 输出纪律
- session 恢复、fork、compact 流程

### 人工终验

- `Windows Terminal + PowerShell`
- 中文输入法
- 粘贴
- 光标闪烁
- 连续多轮输入
- slash palette
- approval
- patch / compact / session block

## 7. 风险与缓解

### 风险 1：`LineReader` 与后台输出互相打断

缓解：

- UI 线程单点访问终端
- 后台线程只投递事件
- transcript 统一走 `printAbove`

### 风险 2：Windows 输入法与粘贴边界问题

缓解：

- 真实终端人工终验纳入每阶段验收
- 避免自绘输入框，优先使用终端原生编辑能力

### 风险 3：迁移过程中主路径不可用

缓解：

- 迁移前期保留 `旧实验 TUI` fallback
- 新 runner 成熟后再切默认值

## 8. 每阶段自验证命令

- 编译：`mvn -pl ai4j-cli -am -DskipTests compile`
- 测试：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' test`
- 打包：`mvn -pl ai4j-cli -am -DskipTests package`

对于高风险阶段，额外增加定向测试类和一次人工终验。

## 9. 文档沉淀要求

每完成一个阶段，在 `docs/tasks` 追加记录：

- 做了什么
- 实际效果
- 是否达标
- 剩余缺陷

避免只记录“完成了”，必须记录体验是否真的向 Codex 靠拢。

## 10. 当前下一步

1. 在 `ai4j-cli/pom.xml` 中切换依赖，从 `旧实验 TUI` 主路径转向 `JLine 3`
2. 新建 `JlineCodeCommandRunner` 与 `JlineShellContext`
3. 让 `--ui tui` 先稳定进入新的 shell 骨架
4. 以 composer + transcript + status 为第一批落地点
