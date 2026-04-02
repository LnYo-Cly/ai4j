# 2026-04-02 Coding Agent Loop 实施计划

- 状态：Planned
- 优先级：P0
- 依赖设计：`docs/plans/2026-04-02-coding-agent-loop-design.md`
- 第一阶段目标模块：`ai4j-coding`
- 第二阶段联动模块：`ai4j-cli`
- 保持兼容模块：`ai4j-agent`

## 1. 实施目标

为 coding agent 增加受控的 outer loop，使其具备：

- 有限自动继续
- 统一 stop reason
- compact 后继续
- approval/tool block 后优雅停止
- 不破坏 `BaseAgentRuntime` 与通用 SDK 扩展语义

## 2. Phase 拆分

### Phase 1：在 `ai4j-coding` 落地 controller

目标：

- 在不修改 `BaseAgentRuntime` 默认语义的前提下，为 `CodingSession` 增加产品级 outer loop

主要改动：

- 新增 `coding/loop` 包
- 扩展 `CodingAgentOptions`
- 扩展 `CodingAgentResult`
- 扩展 `SessionEventType`
- 修改 `CodingSession`
- 修改 `CodingAgent`

关键实现点：

- `CodingAgentLoopController` 负责多轮控制
- 使用同一个 session 进行 follow-up continuation
- follow-up 使用隐藏 prompt，不污染用户消息历史
- 读取 `CodingSession` 的 auto compact 结果决定是否继续

验收：

- 普通单轮完成任务，结果与现有行为一致
- 可自动继续至少 1 至 2 轮
- 可在 compact 后继续
- 审批被拒绝时正确停止并返回 stop reason
- 达到上限后停止，不出现无限循环

### Phase 2：接入 CLI / ACP / transcript

目标：

- 让 `coding-agent-cli`、headless runtime、ACP 能正确展示 outer loop 结果

主要改动：

- `DefaultCodingCliAgentFactory`
- `CodingCliSessionRunner`
- `HeadlessCodingSessionRuntime`
- `AcpJsonRpcServer`

关键实现点：

- session 事件桥接新增 `AUTO_CONTINUE / AUTO_STOP / BLOCKED`
- transcript 正确展示自动继续，不把 hidden follow-up 误渲染成用户输入
- ACP 审批拒绝时输出明确 blocked 状态

验收：

- CLI transcript 连续且不混乱
- TUI 不因自动继续破坏 tool phase 展示
- ACP 事件流可反映 blocked / continue / stop

### Phase 3：稳定后抽象下沉

目标：

- 将 coding 层验证成熟的中性抽象下沉到 `ai4j-agent`

主要候选：

- `StopReason`
- `LoopDecision`
- 可选 `OrchestratedAgentRuntime`

验收：

- 下沉不改变通用 `BaseAgentRuntime` 默认行为
- 通用 SDK 文档能清晰区分“底层 runtime”与“产品级 controller”

## 3. 具体文件计划

### 新增文件

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingAgentLoopController.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingLoopPolicy.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingLoopDecision.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingStopReason.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingContinuationPrompt.java`

### 修改文件

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentOptions.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentResult.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingSession.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgent.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/session/SessionEventType.java`

### 第二阶段修改文件

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory/DefaultCodingCliAgentFactory.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/HeadlessCodingSessionRuntime.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpJsonRpcServer.java`

## 4. 配置项建议

在 `CodingAgentOptions` 新增：

- `autoContinueEnabled`
- `maxAutoFollowUps`
- `maxTotalTurns`
- `continueAfterCompact`
- `stopOnApprovalBlock`
- `stopOnExplicitQuestion`

推荐默认值：

- `autoContinueEnabled = true`
- `maxAutoFollowUps = 2`
- `maxTotalTurns = 6`
- `continueAfterCompact = true`
- `stopOnApprovalBlock = true`
- `stopOnExplicitQuestion = true`

## 5. 结果模型建议

在 `CodingAgentResult` 新增：

- `turns`
- `stopReason`
- `autoContinued`
- `autoFollowUpCount`
- `lastCompactApplied`

并保持：

- `outputText`
- `toolCalls`
- `toolResults`
- `steps`

原字段含义不变。

## 6. 事件模型建议

在 `SessionEventType` 新增：

- `AUTO_CONTINUE`
- `AUTO_STOP`
- `BLOCKED`

约束：

- 不删除已有事件
- 不改变已有事件的基本使用方式
- controller 事件通过 session 层桥接给 CLI/TUI/ACP

## 7. 判定逻辑建议

### 自动停止条件

- 模型输出已完成
- 模型显式向用户提问或请求澄清
- 被 approval 拒绝
- 关键工具错误且不可恢复
- 达到自动继续上限
- 达到总 turn 上限

### 自动继续条件

- 上一轮完成了部分工具工作，但任务明显未结束
- compact 已执行且可继续
- 模型未完成但没有阻塞信号

### 第一阶段避免做的事

- 不做二次 judge model
- 不做无限自动继续
- 不做复杂 token-budget continuation
- 不做 workflow graph 编排

## 8. 测试计划

### 单元测试

- `CodingAgentLoopController`：
  - 完成即停
  - 自动继续
  - 达到 `maxAutoFollowUps` 停止
  - 遇到显式问题停止
  - compact 后继续
  - approval block 停止

- `CodingSession`：
  - hidden follow-up 不污染用户输入
  - auto compact 结果可被 controller 消费
  - result 字段映射正确

### CLI 集成测试

- transcript 中 tool call/result 顺序不乱
- auto continue 可见但不过度噪声
- compact 后仍能继续运行
- ACP 审批拒绝时返回 blocked 状态

### 回归测试

- subagent delegation
- team message 展示
- session export/restore 后继续运行
- headless 与 TUI 模式都可工作

## 9. 风险与缓解

### 风险 1：自动继续过度

缓解：

- 默认保守的 follow-up 上限
- 遇到显式问题即停

### 风险 2：CLI 展示混乱

缓解：

- continuation prompt 不当作用户消息渲染
- 通过专门的 session 事件展示 auto continue

### 风险 3：与现有 compact 机制冲突

缓解：

- 不重做 compaction
- 仅消费 `CodingSession` 当前 compact 结果

### 风险 4：未来 agent 层抽象重复

缓解：

- Phase 1 只做 coding 层
- 稳定后再下沉公共抽象

## 10. 实施顺序建议

1. 新增 `coding/loop` 包和 stop/decision 模型
2. 扩 `CodingAgentOptions` 与 `CodingAgentResult`
3. 改 `CodingSession`，让单 session 支持 controller 驱动的多轮继续
4. 改 `CodingAgent` 对外入口，默认使用 controller
5. 扩 `SessionEventType`，打通 session 事件
6. 适配 CLI/headless/ACP 展示
7. 补测试

## 11. 完成标准

满足以下条件即视为该计划完成：

- `coding-agent-cli` 在不改变底层 `BaseAgentRuntime` 默认语义的前提下具备有限自动继续
- compact 后可继续
- approval block 有清晰停止语义
- subagent / teams / ACP 主路径无回归
- CLI transcript 与 TUI 状态展示可接受
