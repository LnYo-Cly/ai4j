---
title: Protocol Fields and Assembly Reference
description: Field tables, message assembly rules, role and ordering constraints, and response shapes for the Chat / Responses / Messages protocols — mapped field by field to ai4j entity classes, with links to the official specs.
tags: [reference]
---

# Protocol Fields and Assembly Reference

This page is the **field-level reference**: what each protocol's request entity looks like, how `messages`/`input` are assembled, which roles exist, what must come after what, and how to read the response. Every field maps to a source entity class; protocol semantics follow the official OpenAI / Anthropic specifications.

## 1. At a glance: how the three protocols differ

| Dimension | Chat (`ChatCompletion`) | Responses (`ResponseRequest`) | Messages (`AnthropicChatCompletion`) |
|-----------|--------------------------|-------------------------------|--------------------------------------|
| Conversation carrier | `messages: List<ChatMessage>` | `input: Object` (string or item array) | `messages: List<AnthropicMessage>` |
| Where system lives | a `role=system` entry inside `messages` | top-level `instructions` field | top-level `system` field (not a message) |
| Role set | system / user / assistant / tool | item `role`: user / assistant (+ system/developer via instructions or input items) | user / assistant only |
| Tool result carrier | `role=tool` message + `tool_call_id` | `function_call_output` item + `call_id` | `tool_result` block inside a `user` message + `tool_use_id` |
| Hard requirements | `model`, `messages` | `model`, `input` | `model`, `max_tokens`, `messages` |
| Chained continuation | client resends full `messages` | `previous_response_id` lets the server chain | client resends full `messages` |
| Response body | `choices[].message` | `output[]` item list | `content[]` block list |
| Stop signal | `finish_reason` (stop/length/tool_calls…) | `status` + `incomplete_details` | `stop_reason` (end_turn/tool_use/max_tokens/stop_sequence) |

## 2. Chat protocol (`ChatCompletion`)

Entity: `platform/openai/chat/entity/ChatCompletion.java`. Shared by all 12 chat platforms — the OpenAI-compatible shape is the common denominator across DeepSeek, Moonshot, Doubao, and friends.

### 2.1 Request fields

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `model` | String | — | **required** |
| `messages` | `List<ChatMessage>` | — | **required**, assembly rules in 2.2 |
| `stream` | Boolean | `false` | true switches to SSE; frame shape in [Streaming](./streaming.md) |
| `stream_options` | StreamOptions | — | stream extras (e.g. `include_usage`) |
| `temperature` | Float | `1` | sampling temperature |
| `top_p` | Float | `1` | nucleus sampling |
| `max_tokens` | Integer | — | legacy output cap |
| `max_completion_tokens` | Integer | — | current output cap (o-series/newer models) |
| `reasoning_effort` | String | — | `low`/`medium`/`high`; serialized only when set |
| `stop` | `List<String>` | — | stop sequences |
| `n` | Integer | `1` | number of choices |
| `presence_penalty` / `frequency_penalty` | Float | `0` | presence/frequency penalties |
| `logit_bias` / `logprobs` / `top_logprobs` | Map / Boolean / Integer | — | logprob controls |
| `tools` | `List<Tool>` | — | tool declarations (expanded from `functions`/`mcpServices`) |
| `tool_choice` | String | — | `auto`/`none`/`required`/named function |
| `parallel_tool_calls` | Boolean | `true` | parallel tool calls switch |
| `response_format` | Object | — | structured output constraint, e.g. `{"type":"json_object"}` |
| `user` | String | — | end-user identifier |
| `parameters` | `Map` | — | extra provider parameter bag |
| `extraBody` | `Map` (`@JsonAnyGetter`) | — | flattened into top-level JSON for provider-specific fields |
| `functions` / `mcpServices` / `builtInToolContext` / `passThroughToolCalls` / `streamExecution` | `@JsonIgnore` | — | **SDK-private; never serialized into the provider payload** (see [Request & Response Conventions](./request-and-response-conventions.md)) |

### 2.2 `messages` assembly rules

`ChatMessage.role` is constrained by the `ChatMessageType` enum — four values only:

| role | Purpose | Constructor |
|------|---------|-------------|
| `system` | global instruction/persona | `ChatMessage.withSystem(text)` |
| `user` | user input (may carry images/video) | `withUser(text)` / `withUser(text, images...)` |
| `assistant` | model reply (may carry tool_calls) | `withAssistant(text)` / `withAssistant(toolCalls)` |
| `tool` | one tool execution result | `withTool(result, toolCallId)` |

**Ordering and pairing constraints** (OpenAI spec; compatible providers behave the same):

1. `system` may only appear at the head (0 or 1 entries), before any user/assistant.
2. After that, user/assistant entries alternate; the first business message must be `user`.
3. An `assistant` message carrying `tool_calls` **must be followed immediately** by one `tool` message per `tool_calls[i].id` (`tool_call_id` pairing) before the next `user` message.
4. A `tool` message may not appear detached from a preceding `assistant.tool_calls`.

A legal tool round trip:

```text
system → user → assistant(tool_calls:[call_A, call_B])
        → tool(tool_call_id=call_A) → tool(tool_call_id=call_B)
        → assistant(final answer)
```

This is exactly the shape ai4j writes back inside the automatic tool loop: `SseListener` aggregates streaming `toolCalls`, then the service appends `assistant(tool_calls)` plus one `tool` result message per call before the next request.

### 2.3 Two `content` shapes

`ChatMessage.content` is a `Content` object — one of:

- **Plain text**: `Content.text` — serializes to `content: "..."`.
- **Multimodal**: `Content.multiModals` — serializes to a parts array `content: [{type:"text",text:...},{type:"image_url",image_url:{url}},...]`; `MultiModal` supports `text` / `image_url` / `video_url` parts.

`withUser(text, images...)` picks the multimodal shape automatically; hand-build `Content.ofMultiModals(...)` to interleave text and media parts.

### 2.4 Response shape (`ChatCompletionResponse`)

```text
id / object / created / model / system_fingerprint
choices[]:
  index
  message | delta          # sync returns message; streaming yields delta frames
  finish_reason            # stop | length | tool_calls | content_filter
usage: prompt_tokens / completion_tokens / total_tokens
```

When streaming, the same fields arrive incrementally on `delta`: `reasoning_content` (reasoning chain) and fragmented `tool_calls` name/arguments must be aggregated by index (see [Streaming](./streaming.md)).

## 3. Responses protocol (`ResponseRequest`)

Entity: `platform/openai/response/entity/ResponseRequest.java`. Implemented by OpenAI, Doubao, and DashScope.

### 3.1 Request fields

| Field | Type | Notes |
|-------|------|-------|
| `model` | String | **required** |
| `input` | Object | **required**, three shapes in 3.2 |
| `instructions` | String | system-level instruction (Chat's system equivalent, but separate from input) |
| `previous_response_id` | String | chains the previous response id; the server carries context (requires server-side storage per `store`/policy) |
| `include` | `List<String>` | extra items to return (e.g. `reasoning.encrypted_content`) |
| `max_output_tokens` | Integer | output cap (**includes reasoning spend**, not just visible output) |
| `reasoning` | Object | e.g. `{"effort":"low / medium / high"}` |
| `tools` | `List<Object>` | tool declarations (function/web_search/mcp items) |
| `tool_choice` | Object | tool selection strategy |
| `parallel_tool_calls` | Boolean | parallel tool switch |
| `text` | Object | output format constraint (`format:{type:"json_schema",...}`) |
| `temperature` / `top_p` | Double | sampling |
| `truncation` | String | `auto`/`disabled` context truncation policy |
| `store` | Boolean | server-side retention (pairs with previous_response_id) |
| `metadata` | Map | echoed business metadata |
| `stream` / `stream_options` | — | SSE streaming |
| `user` | String | end-user identifier |
| `extraBody` | `@JsonAnyGetter` | provider extension pass-through |
| `functions` / `mcpServices` / `streamExecution` | `@JsonIgnore` | SDK-private |

### 3.2 Three `input` shapes

1. **Plain string**: `input: "ask something"` — the simplest form.
2. **Item array**: each entry is a `ResponseItem` (`type` + `role` + `content[]`). Common item types:
   - `message`: `role=user/assistant/system`, with `input_text`/`input_image`/`output_text` parts inside `content[]`
   - `function_call`: a call issued by the model (`call_id`/`name`/`arguments`)
   - `function_call_output`: the tool result going back (`call_id` pairing + `output`)
   - `reasoning`: reasoning summary item (`summary[]`)
3. **Chained**: `previous_response_id` + only this turn's new input — the server stitches context by response id, no full resend needed.

A legal tool round trip (item shape):

```text
input: [user message]
→ returns output: [reasoning?, function_call(call_id=X)]
→ next input: [function_call_output(call_id=X, output=...)]
→ returns output: [message(final answer)]
```

### 3.3 Response shape (`Response`)

```text
id / object / created_at / model / status        # completed|failed|incomplete|cancelled
output[]: ResponseItem                           # mixed reasoning / message / function_call list
error / incomplete_details                       # reason when status=incomplete (e.g. max_output_tokens)
instructions / previous_response_id              # echoes
usage: input_tokens / output_tokens / details    # ResponseUsageDetails splits reasoning etc.
metadata / context_management                    # metadata echo + context-edit record
```

The biggest difference from Chat: **no `choices` array**; the answer text lives inside `output[]` items of `type=message`, in `content[]` parts of `type=output_text`.

## 4. Messages protocol (`AnthropicChatCompletion`)

Entity: `platform/anthropic/chat/entity/AnthropicChatCompletion.java`, implemented by Anthropic only.

### 4.1 Request fields

| Field | Type | Notes |
|-------|------|-------|
| `model` | String | **required** |
| `messages` | `List<AnthropicMessage>` | **required**, constraints in 4.2 |
| `max_tokens` | Integer | **required** (Anthropic hard requirement; no default) |
| `system` | Object | top-level: string or content-block array (supports `cache_control` breakpoints); **not a message** |
| `temperature` / `top_p` | Double | sampling |
| `stop_sequences` | `List<String>` | stop sequences |
| `tools` | `List<AnthropicTool>` | tool declarations |
| `tool_choice` | Object | `auto`/`any`/`tool`/`none` |
| `stream` | Boolean | SSE streaming |
| `extraBody` | `@JsonAnyGetter` | extension pass-through |

### 4.2 `messages` assembly rules

`AnthropicMessage` has just `role` + `content`; role is **user / assistant only** (system is a top-level field, never a message).

Hard constraints (Anthropic spec):

1. **The first message must be `user`**; after that user/assistant **strictly alternate** — no consecutive same-role entries.
2. When the model calls tools, its `assistant` message `content[]` contains `tool_use` blocks (`id`/`name`/`input`).
3. Tool results go inside the **next `user` message**'s `content[]` as `tool_result` blocks, with `tool_use_id` paired to the `tool_use.id`. Multiple results can share one user message.
4. In the SDK this assembly is `AnthropicMessagesService.appendToolResults(messages, response)`: it appends the raw assistant reply (including tool_use blocks), then one user message holding all tool_result blocks.

A legal sequence:

```text
system(top-level) → user → assistant[text+tool_use(id=T)]
                   → user[tool_result(tool_use_id=T)] → assistant(final answer)
```

### 4.3 Content block types (`AnthropicContentBlock`)

| type | Fields | Direction |
|------|--------|-----------|
| `text` | `text` | both ways |
| `thinking` | `thinking` | response (extended-thinking chain) |
| `tool_use` | `id`/`name`/`input` | response (model-issued call) |
| `tool_result` | `tool_use_id`/`content` | request (result return; content is a string or block array) |
| `image` etc. | — | request multimodal parts |

`cacheControl` can be attached to the system field or individual blocks for prompt-caching breakpoints.

### 4.4 Response shape (`AnthropicChatCompletionResponse`)

```text
id / type=message / role=assistant / model
content[]: AnthropicContentBlock               # mixed text / thinking / tool_use
stop_reason                                    # end_turn | tool_use | max_tokens | stop_sequence
stop_sequence                                  # the stop sequence that fired
usage: input_tokens / output_tokens            # plus cache_read/cache_creation_input_tokens, serverToolUse, etc.
```

`stop_reason=tool_use` means the model is waiting for tool results — append `tool_result` per 4.2 before the next call.

## 5. Assembly constraints side by side

| Constraint | Chat | Responses | Messages |
|------------|------|-----------|----------|
| system location | messages[0] role=system | top-level instructions | top-level system field |
| First message | user (after system) | input item role=user | **must be user, strictly alternating** |
| Tool call carrier | assistant.tool_calls[] | function_call item in output | tool_use block in assistant content |
| Tool result carrier | role=tool message (tool_call_id) | function_call_output item (call_id) | tool_result block in user message (tool_use_id) |
| Context continuation | full client resend | previous_response_id or full resend | full client resend |
| Multimodal | content parts array | content parts inside message item | content block array |

## 6. Official specifications

- OpenAI Chat Completions API: `platform.openai.com/docs/api-reference/chat` (message assembly, tool_calls pairing, finish_reason values)
- OpenAI Responses API: `platform.openai.com/docs/api-reference/responses` (input item shapes, output list, previous_response_id)
- Anthropic Messages API: `docs.anthropic.com/en/api/messages` (alternation constraint, tool_use/tool_result blocks, stop_reason, cache_control)

ai4j entities map to these specs field by field; spec fields not yet modeled by the SDK can pass through `extraBody` meanwhile.
