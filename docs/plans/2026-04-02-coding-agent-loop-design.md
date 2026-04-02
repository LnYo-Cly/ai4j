# 2026-04-02 Coding Agent Loop 设计

## 背景

当前 `ai4j-agent` 与 `ai4j-coding` 已经具备较完整的 agent 基础能力：

- `BaseAgentRuntime` 已经实现稳定的内层 `tool-loop`
- `ReActRuntime` 已作为默认通用 runtime 使用
- `CodingSession` 已具备 session、compact、checkpoint、process state 等能力
- `ai4j-cli`、ACP、subagent、teams 已围绕既有事件与 session 模型形成上层集成

现阶段的主要缺口，不是“有没有 loop”，而是“缺少产品级 outer agent-loop”：

- 缺少明确的 `continue / stop / blocked / compact-after-continue` 决策层
- 缺少任务级 stop reason 语义
- 缺少 coding agent 风格的有限自动继续机制
- 缺少对 compact、approval block、follow-up 的统一控制

同时，仓库中存在一个重要约束：

- `BaseAgentRuntime` 已经是通用扩展基类
- 外部用户可能基于该类实现自定义 runtime
- 直接修改其默认终止语义，会对兼容性造成不可控影响

因此，这次设计的目标不是重写 `BaseAgentRuntime`，而是在不破坏通用 SDK 的前提下，为 coding agent 增加成熟的外层 orchestration。

## 目标

- 为 `ai4j-coding` 增加接近 `Codex / Claude Code / OpenCode` 风格的 outer agent-loop
- 保持 `BaseAgentRuntime` 的默认行为和扩展模式稳定
- 优先复用 `CodingSession` 已有的 compact / checkpoint / session state 能力
- 让 `coding-agent-cli`、ACP、subagent、teams 在兼容前提下获得更成熟的自动继续能力
- 为后续 hooks、approval policy、loop telemetry 预留明确边界

## 非目标

- 本轮不把整个系统改成 `CodeAct` 主架构
- 本轮不把整个系统改成 workflow graph-first
- 本轮不直接修改 `BaseAgentRuntime` 的默认“无 tool call 即结束”语义
- 本轮不重做 MCP、tool executor、subagent、teams 底层机制
- 本轮不实现无限自治式 continuation，也不引入激进 token-budget 驱动策略

## 设计结论

主路线采用：

`tool-first runtime + coding-layer loop controller + compatibility-first rollout`

也就是：

- `BaseAgentRuntime` 保持底层单轮 `tool-loop`
- 新增 `CodingAgentLoopController` 作为 `ai4j-coding` 层的任务级循环控制器
- `coding-agent-cli` 默认接入该 controller
- `CodeActRuntime` 保留为可选实验模式，不再作为主设计中心

## 方案备选

### 方案 A：直接增强 `BaseAgentRuntime`

优点：

- 看起来结构更统一
- 通用 agent 可以直接获得新能力

缺点：

- 会改变现有扩展基类语义
- 外部用户若覆写 `runInternal`，兼容性风险高
- CLI/ACP/TUI 现有事件消费链对行为变化敏感

结论：不采用作为第一阶段主线。

### 方案 B：在 `ai4j-coding` 增加外层 controller

优点：

- 兼容性最好
- 可以直接复用 `CodingSession` 的 compact/checkpoint/session state
- 与 `coding-agent-cli` 的产品需求最对齐
- 后续验证范围清晰

缺点：

- 通用 agent 层短期内不会直接获得同一套 outer loop
- 后续若要下沉抽象，需要再做一次收敛

结论：本轮采用。

### 方案 C：直接上 workflow/state graph 主架构

优点：

- 理论上编排能力最强
- 对复杂流程表达更显式

缺点：

- 与 `Codex / Claude Code / OpenCode` 的 coding agent 主干路线不一致
- 会引入过重的抽象负担
- 当前仓库已有 runtime/session/tool/event 结构，不适合第一步就切换主架构

结论：不采用。

## 参考实现取舍

本设计不照抄某一个项目，而是有选择地吸收三类成熟模式：

### 借鉴 `Codex`

- 明确的单轮契约
- turn 内 `assistant message` 与 `tool call` 的边界清晰
- 内层 loop 保持干净，不把太多产品策略塞进底层 runtime

### 借鉴 `OpenCode`

- 显式 outer loop
- `continue / stop / compact` 这类控制结果很适合作为 controller 输出
- compaction 与 loop continuation 之间的衔接较清晰

### 借鉴 `Claude Code`

- 产品级 hook / approval / compaction 思路
- coding session 应具备“继续工作而不是每轮都问用户”的能力
- 但不在本轮引入激进的强自治 token-budget continuation

## 总体架构

### 1. 底层执行层

由 `BaseAgentRuntime` 与其子类承担，职责不变：

- 构造 prompt
- 调用模型
- 执行 tool
- 写回 memory
- 返回当前回合结果

默认语义保持：

- 这一层仍然是“无 tool call 即结束当前 runtime.run”

### 2. coding 层控制器

新增 `CodingAgentLoopController`，作为 `ai4j-coding` 的产品级 outer loop：

- 调用 `CodingSession` 进行一轮执行
- 读取当前回合结果
- 读取本轮 auto-compact 结果
- 判断本轮是否应该继续
- 必要时向同一个 session 注入隐藏 follow-up prompt 后继续下一轮

该层不直接执行工具，不直接处理 MCP，不直接实现 UI，仅负责任务级控制。

### 3. 产品入口层

`coding-agent-cli`、ACP 以及后续高级 coding agent 入口使用 controller：

- CLI/TUI 看到的是一个“单个用户任务”
- controller 在内部可能执行多轮
- 对外仍保持 session 视角和 transcript 连续性

## 为什么不采用 CodeAct 主路线

`CodeAct` 的强项在于把动作统一为代码执行，但当前项目更需要的是：

- 工具级权限控制
- 工具调用可见性
- MCP/tool schema 一致性
- session 级 compact / checkpoint / replay
- 兼容 CLI/ACP/subagent/teams 的稳定控制层

这些诉求更适合 `tool-calling + controller`，而不是 `code-as-action` 作为主干。

因此：

- `CodeActRuntime` 可以保留
- 但只作为特化模式，不作为默认 coding agent 主路径

## 控制器语义

第一阶段 controller 只实现有限自动继续，不做无限自治。

### 推荐 stop reason

- `COMPLETED`
- `NEEDS_USER_INPUT`
- `BLOCKED_BY_APPROVAL`
- `BLOCKED_BY_TOOL_ERROR`
- `MAX_AUTO_FOLLOWUPS_REACHED`
- `MAX_TOTAL_TURNS_REACHED`
- `INTERRUPTED`
- `ERROR`

### 推荐 continue reason

- `CONTINUE_AFTER_TOOL_WORK`
- `CONTINUE_AFTER_COMPACTION`
- `CONTINUE_AUTONOMOUS_WORK`

### continue 原则

- 只有在模型显然仍有未完成工作时才自动继续
- 遇到显式向用户提问、请求澄清、等待审批、关键工具失败时应停止
- 自动继续必须受预算限制

## follow-up 机制

controller 使用隐藏 follow-up prompt，而不是二次 judge model。

原因：

- 复用现有 session 最直接
- 上下文连续性最好
- 改动最小
- 更符合 coding agent 的单线程工作流

该 follow-up prompt 的职责是：

- 告诉模型这是内部 continuation，不要重复已完成内容
- 若任务已完成或需要用户输入，则明确停止
- 若仍可推进，则继续执行下一步

第一阶段不要求输出显式 XML/JSON 停止标签，可先通过规则化提示与结果判定实现。

## compact 策略

第一阶段不在 `ai4j-agent` 重做 compaction，直接复用 `ai4j-coding` 现有能力：

- `CodingSessionCompactor`
- `CodingSession` 的 auto compact
- `CodingSessionCheckpointFormatter`

controller 只需要消费 compact 结果，并决定：

- compact 已执行且上下文恢复到可控范围，可继续
- compact 失败或仍无法继续，则停止并返回相应 stop reason

## 兼容性策略

### 对 `BaseAgentRuntime` 用户

- 默认行为不变
- 扩展点不变
- 自定义 runtime 不需要立即适配 controller

### 对 `coding-agent-cli`

- controller 作为默认高级路径接入
- 原有 `run/runStream/newSession` 视角保持不变
- transcript 仍按 session 顺序呈现

### 对 ACP

- approval 仍由现有 tool decorator/gateway 处理
- controller 只识别“被审批阻塞”并停止或等待

### 对 subagent / teams

- 仍经由 tool registry / tool executor / handoff 工作
- controller 不修改其底层调用协议

## 推荐文件边界

### 第一阶段新增

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingAgentLoopController.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingLoopDecision.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingStopReason.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingLoopPolicy.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingContinuationPrompt.java`

### 第一阶段重点修改

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingSession.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgent.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentOptions.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentResult.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/session/SessionEventType.java`

## 结果模型建议

第一阶段不修改通用 `AgentResult`。

仅扩展 `CodingAgentResult`，建议新增：

- `turns`
- `stopReason`
- `autoContinued`
- `autoFollowUpCount`
- `lastCompactApplied`

这样：

- 通用 SDK 用户不受影响
- coding 产品层可以直接消费更丰富的任务级结果

## 事件策略

第一阶段尽量不修改 `AgentEventType` 主集合，以降低 CLI/TUI/ACP 风险。

优先扩展 coding session 事件：

- `AUTO_CONTINUE`
- `AUTO_STOP`
- `BLOCKED`

CLI/TUI 只需在 session 事件桥接层处理这些新增事件。

## 风险

- 自动继续判定过松，会让 agent 过度自转
- 自动继续判定过紧，会失去产品价值
- compact 后继续时，若提示设计不好，模型可能重复上轮内容
- CLI/TUI 若直接把每次 follow-up 都渲染成“新一轮用户发言”，体验会变差
- ACP 审批拒绝与 controller 停止语义若不统一，会出现重复提示

## 风险控制

- 第一阶段限制自动继续次数，默认保守
- 不改底层 `BaseAgentRuntime` 语义
- 不改 `AgentRuntime` / `AgentSession` 接口
- 结果字段与事件字段只增不删
- 先在 `ai4j-coding` 验证，再考虑下沉抽象

## 演进路径

### Phase 1

在 `ai4j-coding` 落地 controller 和有限自动继续。

### Phase 2

接入 `ai4j-cli`、ACP、headless transcript、TUI 事件展示。

### Phase 3

等 coding 层跑稳后，再考虑将中性抽象下沉到 `ai4j-agent`：

- `StopReason`
- `LoopDecision`
- 可选 `OrchestratedAgentRuntime`

## 当前决策

- 采用 `tool-first + coding-layer loop controller` 主路线
- 不以 `CodeAct` 作为主架构
- 不以 workflow graph-first 作为主架构
- 保持 `BaseAgentRuntime` 兼容稳定
- 优先在 `ai4j-coding` 实现成熟 outer agent-loop
