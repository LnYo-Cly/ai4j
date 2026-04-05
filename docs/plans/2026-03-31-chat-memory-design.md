# 2026-03-31 ChatMemory 设计

- 状态：Approved
- 目标模块：`ai4j`
- 关联模块：`ai4j-agent`

## 1. 背景

`ai4j` 已经提供统一的 Chat / Responses / Tool / MCP 能力，但基础 LLM 调用仍然需要调用方自行维护上下文。当前仓库里的 memory 实现位于 `ai4j-agent`，其语义已经包含：

- 用户输入写入
- assistant 输出 item 写入
- tool output 回填
- summary 注入
- snapshot / restore

这套能力更偏 agent runtime，不适合直接下沉到 `ai4j` 核心层。

## 2. 目标

在 `ai4j` 中新增一个轻量 `ChatMemory` 层，用于基础 LLM 会话上下文管理，满足：

- 简化直接调用 Chat / Responses 时的上下文维护
- 默认提供内存版实现
- 默认不做裁剪或压缩
- 支持可插拔淘汰策略
- 同时输出 Chat 请求消息和 Responses 请求输入

## 3. 非目标

- 不复用 `ai4j-agent` 的 `AgentMemory`
- 不引入持久化抽象
- 不实现 agent loop / planning / MCP routing / runtime state
- 不在首版实现 token 窗口和自动摘要

## 4. 设计决策

### 4.1 分层边界

- `ai4j`
  - 提供基础 `ChatMemory`
  - 只管理对话上下文
- `ai4j-agent`
  - 保留 `AgentMemory`
  - 继续承载智能体运行时语义

### 4.2 核心类型

- `ChatMemory`
  - 轻量接口
- `InMemoryChatMemory`
  - 默认内存实现
- `ChatMemoryItem`
  - 中立会话项，承载 role / text / imageUrls / toolCallId / toolCalls
- `ChatMemorySnapshot`
  - 快照导出与恢复
- `ChatMemoryPolicy`
  - 淘汰策略接口
- `UnboundedChatMemoryPolicy`
  - 默认策略，不做处理
- `MessageWindowChatMemoryPolicy`
  - 按消息窗口保留最近上下文

### 4.3 默认行为

默认策略为 `Unbounded`：

- 不裁剪
- 不压缩
- 不偷偷修改上下文

如果使用者需要控制上下文窗口，可显式配置 `MessageWindowChatMemoryPolicy`。

### 4.4 Message Window 规则

首版窗口策略规则如下：

- 永远保留 `system` 消息
- 非 `system` 消息仅保留最近 N 条
- 保持原始顺序

后续若需要更精细的 tool-call 成组保留、token 预算和摘要压缩，再在此基础上扩展。

## 5. 接口摘要

`ChatMemory` 首版提供：

- `addSystem`
- `addUser`
- `addAssistant`
- `addAssistantToolCalls`
- `addToolOutput`
- `add`
- `addAll`
- `getItems`
- `toChatMessages`
- `toResponsesInput`
- `snapshot`
- `restore`
- `clear`

## 6. 兼容性

- 不修改现有 `IChatService` / `IResponsesService`
- 不修改 `ai4j-agent` 当前 memory 实现
- 基础用户可先手动将 `ChatMemory` 输出塞入现有 `ChatCompletion` / `ResponseRequest`

## 7. 后续扩展

后续可以继续增加：

- `TokenWindowChatMemoryPolicy`
- `SummarizingChatMemoryPolicy`
- 持久化 `Store` / `Manager`

这些能力不纳入首版，以保持 `ai4j` 核心层轻量和清晰。
