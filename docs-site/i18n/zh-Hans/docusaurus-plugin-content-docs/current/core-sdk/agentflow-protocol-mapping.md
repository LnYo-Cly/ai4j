---
sidebar_position: 4
---

# AgentFlow 协议映射与工作原理

本页聚焦 `agentflow` 内部的协议收敛方式，包括字段映射、stream 处理和错误模型。

## 1. 总体思路

`agentflow` 没有把三家平台做成“伪统一 OpenAI 协议”，而是采用了更保守的策略：

- 顶层统一只抽象公共字段
- provider 特有字段保留在各自 adapter
- 原始返回放进 `raw`
- chat 与 workflow 明确分层

这样做的好处是：

- 不会为了统一而丢掉关键语义
- 业务层拿到的是稳定字段
- 排查问题时还能看到原始 payload

## 2. Dify 映射

### 2.1 Chat

ai4j 直接对接 Dify App API：

- blocking: `POST /v1/chat-messages`
- streaming: `POST /v1/chat-messages`

请求映射：

- `prompt -> query`
- `inputs -> inputs`
- `userId -> user`
- `conversationId -> conversation_id`
- `response_mode -> blocking / streaming`

响应映射：

- `answer -> content`
- `conversation_id -> conversationId`
- `message_id / id -> messageId`
- `task_id -> taskId`
- `metadata.usage -> AgentFlowUsage`

### 2.2 Workflow

workflow 也直接走 Dify 发布端点：

- blocking: `POST /v1/workflows/run`
- streaming: `POST /v1/workflows/run`

请求映射：

- `inputs -> inputs`
- `userId -> user`
- `response_mode -> blocking / streaming`

响应映射：

- `data.status -> status`
- `data.outputs -> outputs`
- `data.outputs.* -> outputText`
- `task_id -> taskId`
- `workflow_run_id -> workflowRunId`

### 2.3 Dify Streaming 处理策略

Dify 的 streaming 事件类型比较多，ai4j 当前只抓公共主干：

- chat:
  - `message`
  - `agent_message`
  - `message_end`
  - `error`
- workflow:
  - `workflow_finished`
  - `message`
  - `text_chunk`
  - `error`

策略是：

- 增量文本进入 `contentDelta` / `outputText`
- 终态事件聚合成最终 response
- `ping` 等保活事件忽略

## 3. Coze 映射

### 3.1 Chat Blocking

Coze 非流式 chat 不是一次请求直接拿最终答案，而是分三步：

1. `POST /v3/chat` 创建 chat
2. `GET /v3/chat/retrieve` 轮询状态
3. `POST /v1/conversation/message/list` 拉取消息列表

ai4j 在 adapter 内部把这三步封装掉了，对业务侧仍然暴露单个：

```java
AgentFlowChatResponse response = agentFlow.chat().chat(request);
```

因此 `AgentFlowConfig` 里需要：

- `botId`
- `pollIntervalMillis`
- `pollTimeoutMillis`

### 3.2 Chat Streaming

Coze streaming 使用原生 SSE 事件流，常见事件包括：

- `conversation.chat.created`
- `conversation.message.delta`
- `conversation.message.completed`
- `conversation.chat.completed`
- `done`
- `error`

ai4j 的处理规则：

- `conversation.message.delta` 作为真正的增量文本
- `conversation.chat.completed` 更新 usage / 状态
- `done` 触发最终完成
- 非 JSON 的 `data`（例如 `[DONE]`）按原始文本保留，不强制解析

### 3.3 Workflow

Coze workflow 使用：

- blocking: `POST /v1/workflow/run`
- streaming: `POST /v1/workflow/stream_run`

blocking 的特点是：

- `data` 本身是一个字符串
- 这个字符串通常是 JSON 序列化结果，但也可能只是纯文本

所以 ai4j 的策略是：

- 先尝试把 `data` 解析成 JSON
- 解析失败就按纯文本处理
- 解析结果放进 `outputs/raw`，同时抽取可读的 `outputText`

streaming 的典型事件：

- `Message`
- `Interrupt`
- `Error`
- `Done`

ai4j 当前把 `Message.content` 聚合为最终 `outputText`。

## 4. n8n 映射

n8n 第一阶段采用 webhook-first 方案：

- `workflow().run() -> POST webhookUrl`

原因很简单：

- n8n 的自然发布对象就是 webhook / workflow endpoint
- 它不是模型 provider，也不天然适合抽象成 chat
- 先把最常见、最稳定的 blocking webhook 接入做好，收益最高

请求策略：

- `inputs` 直接拍平成 webhook body
- `metadata` 作为 `_metadata` 附加字段
- `extraBody` 允许补充自定义字段

响应策略：

- 如果返回 JSON，尽量提取 `result / answer / output / text / content`
- 如果返回纯文本，直接作为 `outputText`
- 完整返回保留在 `raw`

## 5. 为什么 `raw` 很重要

第三方平台的 published endpoint 最大的问题不是“能不能调通”，而是“线上出问题时你怎么知道是平台字段变了，还是你自己的业务参数错了”。

所以所有 response / stream event 都保留 `raw`：

- 调试时可以直接打印 provider 原始响应
- 文档升级或平台字段调整时容易定位
- 不需要为了统一抽象而丢失细节

这和 ai4j 在 MCP、Trace、Responses 上的设计原则是一致的：公共语义统一，底层原始数据不强行抹平。

## 6. 错误处理策略

`agentflow` 的错误来源主要有三类：

### 6.1 HTTP 错误

- 非 2xx 直接抛 `AgentFlowException`
- 异常信息里保留 status code 与部分响应体

### 6.2 Provider 业务错误

例如：

- Dify stream `error`
- Coze `code != 0`
- Coze chat `failed / requires_action`

这些不会静默吞掉，而是直接转成异常。

### 6.3 Streaming 中断

在 stream 模式下：

- `onError` 先回调
- 然后方法抛出异常

这样业务层可以同时获得：

- 监听器感知
- 同步调用栈异常

## 7. 为什么不塞进 `PlatformType`

这一点决定了 `agentflow` 与核心 provider 体系的边界。

如果把 Dify / Coze / n8n 直接塞进 `PlatformType`，会马上出现三个问题：

1. `PlatformType` 从“模型 provider 枚举”变成“什么都往里塞的总开关”
2. `IChatService` 被迫承担 workflow / webhook 语义
3. `AiPlatform` 配置会开始混入 `botId / workflowId / webhookUrl`

这会直接破坏原来核心 SDK 的边界。

所以现在的拆分是：

- 模型 provider 继续走 `PlatformType + IChatService`
- 已发布应用端点走 `AgentFlow`

这个边界清晰之后，后面再扩 Dify / Coze / n8n 的能力，成本就低很多。
