---
title: 三协议字段与拼装参考
description: Chat / Responses / Messages 三套协议的请求字段表、消息拼装规则、角色与顺序约束、返回结构——逐字段对应 ai4j 实体类，并给出官方规范出处。
tags: [reference]
---

# 三协议字段与拼装参考

这一页是**字段级参考**：三套协议的请求实体长什么样、messages/input 怎么拼、role 有哪些、什么必须排在什么后面、返回怎么读。所有字段都能在源码实体类里找到；协议语义以 OpenAI / Anthropic 官方规范为准。

## 1. 一页速览：三套协议的形状差异

| 维度 | Chat（`ChatCompletion`） | Responses（`ResponseRequest`） | Messages（`AnthropicChatCompletion`） |
|------|--------------------------|-------------------------------|--------------------------------------|
| 对话载体 | `messages: List<ChatMessage>` | `input: Object`（字符串或 item 数组） | `messages: List<AnthropicMessage>` |
| system 位置 | `messages` 里 `role=system` 的一条 | 顶层 `instructions` 字段 | 顶层 `system` 字段（不进 messages） |
| role 集合 | system / user / assistant / tool | item 内 `role`：user / assistant（+system/developer 经 instructions 或 input item） | 仅 user / assistant |
| 工具回传 | `role=tool` 消息 + `tool_call_id` | `function_call_output` item + `call_id` | `user` 消息内 `tool_result` block + `tool_use_id` |
| 必填硬约束 | `model`、`messages` | `model`、`input` | `model`、`max_tokens`、`messages` |
| 链式续聊 | 客户端自行回传完整 messages | `previous_response_id` 让服务端接龙 | 客户端自行回传完整 messages |
| 返回主体 | `choices[].message` | `output[]` item 列表 | `content[]` block 列表 |
| 结束原因 | `finish_reason`（stop/length/tool_calls…） | `status` + `incomplete_details` | `stop_reason`（end_turn/tool_use/max_tokens/stop_sequence） |

## 2. Chat 协议（`ChatCompletion`）

对应实体：`platform/openai/chat/entity/ChatCompletion.java`。被 12 家 chat 平台共用（OpenAI 兼容面最广，DeepSeek/Moonshot/Doubao 等共用同一形状）。

### 2.1 请求字段表

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `model` | String | — | **必填**，模型名 |
| `messages` | `List<ChatMessage>` | — | **必填**，拼装规则见 2.2 |
| `stream` | Boolean | `false` | true 走 SSE，帧格式见[流式调用](./streaming.md) |
| `stream_options` | StreamOptions | — | 流式附加项（如 `include_usage`） |
| `temperature` | Float | `1` | 采样温度 |
| `top_p` | Float | `1` | nucleus 采样 |
| `max_tokens` | Integer | — | 旧版输出上限字段 |
| `max_completion_tokens` | Integer | — | 新版输出上限（o 系列/新模型用这个） |
| `reasoning_effort` | String | — | 推理强度（`low`/`medium`/`high`，仅序列化非空） |
| `stop` | `List<String>` | — | 停止序列 |
| `n` | Integer | `1` | 返回 choice 数 |
| `presence_penalty` / `frequency_penalty` | Float | `0` | 存在/频率惩罚 |
| `logit_bias` / `logprobs` / `top_logprobs` | Map / Boolean / Integer | — | logprob 控制 |
| `tools` | `List<Tool>` | — | 工具声明（由 `functions`/`mcpServices` 展开而来） |
| `tool_choice` | String | — | `auto`/`none`/`required`/指定函数 |
| `parallel_tool_calls` | Boolean | `true` | 是否允许并行工具调用 |
| `response_format` | Object | — | `{"type":"json_object"}` 等结构化输出约束 |
| `user` | String | — | 终端用户标识 |
| `parameters` | `Map` | — | 附加 provider 参数袋 |
| `extraBody` | `Map`（`@JsonAnyGetter`） | — | 展开进 JSON 顶层，塞 provider 特有扩展字段 |
| `functions` / `mcpServices` / `builtInToolContext` / `passThroughToolCalls` / `streamExecution` | `@JsonIgnore` | — | **SDK 私有字段，不进 provider payload**（见[请求与返回约定](./request-and-response-conventions.md)） |

### 2.2 messages 拼装规则

`ChatMessage` 的 `role` 由 `ChatMessageType` 枚举约束，只有四个值：

| role | 用途 | 构造器 |
|------|------|--------|
| `system` | 全局指令/人设 | `ChatMessage.withSystem(text)` |
| `user` | 用户输入（可含图/视频） | `withUser(text)` / `withUser(text, images...)` |
| `assistant` | 模型答复（可携带 tool_calls） | `withAssistant(text)` / `withAssistant(toolCalls)` |
| `tool` | 单条工具执行结果 | `withTool(result, toolCallId)` |

**顺序与配对约束**（OpenAI 规范，各兼容平台基本一致）：

1. `system` 只能出现在开头（0 或 1 条），先于一切 user/assistant。
2. 其后 user/assistant 交替排列；第一条业务消息必须是 `user`。
3. 带 `tool_calls` 的 `assistant` 消息之后，**必须紧跟**与每个 `tool_calls[i].id` 一一对应的 `tool` 消息（`tool_call_id` 配对），然后才能放下一条 `user`。
4. `tool` 消息不能脱离 `assistant.tool_calls` 单独出现。

一次工具往返的合法序列：

```text
system → user → assistant(tool_calls:[call_A, call_B])
        → tool(tool_call_id=call_A) → tool(tool_call_id=call_B)
        → assistant(最终答复)
```

`AnthropicMessagesService` 之外的 chat 平台在自动 tool loop 里正是按这个形状回写的——`SseListener` 流式聚合出 `toolCalls` 后，service 层追加 `assistant(tool_calls)` + 每条 `tool` 结果消息再发下一轮。

### 2.3 content 的两种形态

`ChatMessage.content` 是 `Content` 对象，二选一：

- **纯文本**：`Content.text` —— 序列化为 `content: "..."` 字符串。
- **多模态**：`Content.multiModals` —— 序列化为 `content: [{type:"text",text:...},{type:"image_url",image_url:{url}},...]` 部件数组；`MultiModal` 支持 `text` / `image_url` / `video_url` 部件。

`withUser(text, images...)` 自动走多模态形态；手写 `Content.ofMultiModals(...)` 可混排文本与媒体部件。

### 2.4 返回结构（`ChatCompletionResponse`）

```text
id / object / created / model / system_fingerprint
choices[]:
  index
  message | delta          # 同步返回 message；流式逐帧给 delta
  finish_reason            # stop | length | tool_calls | content_filter
usage: prompt_tokens / completion_tokens / total_tokens
```

流式时同一字段逐帧出现在 `delta` 上：`reasoning_content`（推理链）、`tool_calls` 的 name/arguments 碎片需要按 index 聚合（见[流式调用](./streaming.md)）。

## 3. Responses 协议（`ResponseRequest`）

对应实体：`platform/openai/response/entity/ResponseRequest.java`。OpenAI/Doubao/DashScope 三家实现。

### 3.1 请求字段表

| 字段 | 类型 | 说明 |
|------|------|------|
| `model` | String | **必填** |
| `input` | Object | **必填**，三种形态见 3.2 |
| `instructions` | String | 系统级指令（相当于 Chat 的 system，但独立于 input） |
| `previous_response_id` | String | 接龙上一轮的 response id，服务端自动携带上下文（需 `store=true` 或默认服务端存储策略允许） |
| `include` | `List<String>` | 要求返回的附加项（如 `reasoning.encrypted_content`） |
| `max_output_tokens` | Integer | 输出上限（**含 reasoning 消耗**，不是纯可见输出） |
| `reasoning` | Object | `{"effort":"low / medium / high"}` 等推理配置 |
| `tools` | `List<Object>` | 工具声明（function/web_search/mcp 等 item） |
| `tool_choice` | Object | 工具选择策略 |
| `parallel_tool_calls` | Boolean | 并行工具开关 |
| `text` | Object | 输出格式约束（`format:{type:"json_schema",...}`） |
| `temperature` / `top_p` | Double | 采样参数 |
| `truncation` | String | `auto`/`disabled`，超长上下文截断策略 |
| `store` | Boolean | 是否服务端留存（配合 previous_response_id） |
| `metadata` | Map | 业务元数据回显 |
| `stream` / `stream_options` | — | SSE 流式 |
| `user` | String | 终端用户标识 |
| `extraBody` | `@JsonAnyGetter` | provider 扩展字段直通 |
| `functions` / `mcpServices` / `streamExecution` | `@JsonIgnore` | SDK 私有 |

### 3.2 input 的三种形态

1. **字符串**：`input: "问一句话"` —— 最简单形态。
2. **item 数组**：每项是 `ResponseItem`（`type` + `role` + `content[]`）。常见 item 类型：
   - `message`：`role=user/assistant/system`，`content[]` 内是 `input_text`/`input_image`/`output_text` 部件
   - `function_call`：模型发起的调用（`call_id`/`name`/`arguments`）
   - `function_call_output`：工具结果回传（`call_id` 配对 + `output`）
   - `reasoning`：推理摘要项（`summary[]`）
3. **链式**：`previous_response_id` + 只传本轮新增 input —— 上下文由服务端按 response id 拼接，不必整段回传。

工具往返合法序列（item 形态）：

```text
input: [user message]
→ 返回 output: [reasoning?, function_call(call_id=X)]
→ 下一轮 input: [function_call_output(call_id=X, output=...)]
→ 返回 output: [message(最终答复)]
```

### 3.3 返回结构（`Response`）

```text
id / object / created_at / model / status        # completed|failed|incomplete|cancelled
output[]: ResponseItem                           # reasoning / message / function_call 混合列表
error / incomplete_details                       # status=incomplete 时给原因（如 max_output_tokens）
instructions / previous_response_id              # 回显
usage: input_tokens / output_tokens / details    # ResponseUsageDetails 细分 reasoning 等
metadata / context_management                    # 元数据回显 + 上下文编辑记录
```

与 Chat 的最大区别：**没有 choices 数组**；答复文本在 `output[]` 里 `type=message` 的 item 内，`content[]` 里 `type=output_text` 的部件中。

## 4. Messages 协议（`AnthropicChatCompletion`）

对应实体：`platform/anthropic/chat/entity/AnthropicChatCompletion.java`，仅 Anthropic 实现。

### 4.1 请求字段表

| 字段 | 类型 | 说明 |
|------|------|------|
| `model` | String | **必填** |
| `messages` | `List<AnthropicMessage>` | **必填**，约束见 4.2 |
| `max_tokens` | Integer | **必填**（Anthropic 硬性要求，不设默认值） |
| `system` | Object | 顶层字段：字符串或 content block 数组（支持 `cache_control` 缓存断点），**不是一条消息** |
| `temperature` / `top_p` | Double | 采样参数 |
| `stop_sequences` | `List<String>` | 停止序列 |
| `tools` | `List<AnthropicTool>` | 工具声明；支持 `defer_loading` 延迟加载标记、`type`（server tool 如 `tool_search_tool_*`）、`cache_control` 断点 |
| `tool_choice` | Object | `auto`/`any`/`tool`/`none` |
| `stream` | Boolean | SSE 流式 |
| `extraBody` | `@JsonAnyGetter` | 扩展字段直通 |

### 4.2 messages 拼装规则

`AnthropicMessage` 只有 `role` + `content` 两个字段，role 只有 **user / assistant** 两种（system 是顶层字段，不进 messages）。

硬性约束（Anthropic 官方规范）：

1. **第一条必须是 `user`**；其后 user/assistant **严格交替**——不允许连续两条同 role。
2. 模型发起工具调用时，其 `assistant` 消息 `content[]` 内含 `tool_use` block（带 `id`/`name`/`input`）。
3. 工具结果必须放在**下一条 `user` 消息**的 `content[]` 里，作为 `tool_result` block，`tool_use_id` 与 `tool_use.id` 配对。多个工具结果可放进同一条 user 消息。
4. SDK 里这一拼装由 `AnthropicMessagesService.appendToolResults(messages, response)` 完成：原样回填 assistant 回复（含 tool_use blocks），再追加一条 user 消息装所有 tool_result。

合法序列：

```text
system(顶层) → user → assistant[text+tool_use(id=T)]
              → user[tool_result(tool_use_id=T)] → assistant(最终答复)
```

### 4.3 content block 类型（`AnthropicContentBlock`）

| type | 字段 | 方向 |
|------|------|------|
| `text` | `text` | 双向 |
| `thinking` | `thinking` | 返回（extended thinking 推理链） |
| `tool_use` | `id`/`name`/`input` | 返回（模型发起调用） |
| `tool_result` | `tool_use_id`/`content` | 请求（回传结果，content 可为字符串或 block 数组） |
| `tool_reference` | `tool_name` | tool search 命中的工具引用，历史往返需保留 |
| `image` 等 | — | 请求多模态部件 |

`cacheControl` 字段可打在 system 或消息 block 上做 prompt caching 断点。

### 4.4 返回结构（`AnthropicChatCompletionResponse`）

```text
id / type=message / role=assistant / model
content[]: AnthropicContentBlock               # text / thinking / tool_use 混合
stop_reason                                    # end_turn | tool_use | max_tokens | stop_sequence
stop_sequence                                  # 命中的停止序列
usage: input_tokens / output_tokens            # 另有 cache_read/cache_creation_input_tokens、serverToolUse 等细分
```

`stop_reason=tool_use` 表示模型在等工具结果——此时必须按 4.2 的规则回填 `tool_result` 再发下一轮。

## 5. 三协议拼装约束对照

| 约束 | Chat | Responses | Messages |
|------|------|-----------|----------|
| system 位置 | messages[0] role=system | 顶层 instructions | 顶层 system 字段 |
| 首条消息 | user（system 之后） | input item role=user | **必须 user，且严格交替** |
| 工具调用载体 | assistant.tool_calls[] | output 中 function_call item | assistant content 中 tool_use block |
| 工具结果载体 | role=tool 消息（tool_call_id） | function_call_output item（call_id） | user 消息中 tool_result block（tool_use_id） |
| 上下文续接 | 客户端全量回传 | previous_response_id 或全量 | 客户端全量回传 |
| 多模态 | content 部件数组 | message item 的 content 部件 | content block 数组 |

## 6. 官方规范出处

- OpenAI Chat Completions API：`platform.openai.com/docs/api-reference/chat`（messages 拼装、tool_calls 配对、finish_reason 枚举）
- OpenAI Responses API：`platform.openai.com/docs/api-reference/responses`（input item 形态、output 列表、previous_response_id）
- Anthropic Messages API：`docs.anthropic.com/en/api/messages`（messages 交替约束、tool_use/tool_result block、stop_reason、cache_control）

ai4j 的实体类与这三份规范逐字段对应；规范里的新字段在 SDK 建模之前可以先用 `extraBody` 透传。
