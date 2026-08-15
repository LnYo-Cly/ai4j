---
title: "Chat Memory"
description: "A deep dive into the ChatMemory contract: ChatMemoryItem carries multimodal and tool facts, InMemory and Jdbc storage backends, windowing and summary policies, snapshot recovery, and the shared foundation that projects onto both Chat and Responses."
tags: [concept]
---

# Chat Memory

`ChatMemory` is a very central, yet easily underestimated object in the AI4J foundation.
What it actually does is not "store a copy of messages for you", but rather **organize multi-turn conversation facts into a unified context abstraction that is projectable, trimmable, snapshot-able, and recoverable**.

:::tip All code on this page is runnable
The examples below come from
[`MemoryDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/MemoryDocExamplesTest.java),
require no API keys, and run in an ordinary CI environment.
:::

## 1. Start with the real contract

Minimal usage — record conversation facts and project them into request messages:

```java
ChatMemory memory = new InMemoryChatMemory();

memory.addSystem("You are a helpful assistant.");
memory.addUser("Remember the number 42");
memory.addAssistant("Okay, got it.");

// Project to Chat: a list of ChatMessage
List<ChatMessage> messages = memory.toChatMessages();   // [system, user, assistant]
```

The core capabilities currently defined by `ChatMemory.java` include:

- `addSystem(...)`
- `addUser(...)`
- `addAssistant(...)`
- `addAssistantToolCalls(...)`
- `addToolOutput(...)`
- `add(...)`
- `addAll(...)`
- `getItems()`
- `toChatMessages()`
- `toResponsesInput()`
- `snapshot()`
- `restore(...)`
- `clear()`

Just looking at this set of methods tells you it is not an auxiliary tool of some provider, but the formal abstraction layer for conversation state.

## 2. It records facts, not text

`ChatMemoryItem` determines what this layer actually stores.

It can currently carry:

- role
- text
- image URL
- assistant tool calls
- the `toolCallId` corresponding to the tool output
- whether it is a summary entry

This means memory can simultaneously cover:

- ordinary text conversations
- multimodal user input
- tool calls initiated by the assistant
- results written back after a tool executes

So what this layer stores is "model context facts", not "the text of the last Q&A turn".

## 3. Why it is the shared foundation of `Chat` and `Responses`

`ChatMemoryItem` has two key methods:

- `toChatMessage()`
- `toResponsesInput()`

They correspond to two different protocol projections:

- The `Chat` path projects into `ChatMessage`
- The `Responses` path projects into input items such as `message` / `function_call_output`

The same history does not need to be maintained as two separate structures by the business layer — this is exactly the value of `ChatMemory`.

```java
ChatMemory memory = new InMemoryChatMemory();
memory.addUser("What color is this picture?", dataUrl);

// Chat projection: text + image_url
memory.toChatMessages();
// Responses projection: input_text + input_image
memory.toResponsesInput();
```

## 4. The real behavior of `InMemoryChatMemory`

The default implementation is:

- `InMemoryChatMemory`

Several of its key behaviors are straightforward:

- It holds a `List<ChatMemoryItem>` internally
- On `add(...)` it copies entries, avoiding direct sharing of external objects
- After each addition it immediately applies the current policy
- `getItems()`, `snapshot()`, and `toChatMessages()` all return freshly reorganized results

This means it does not "stack the list as-is"; instead, it actually runs the current retention policy after every write.

## 5. The real behavior of `JdbcChatMemory`

`JdbcChatMemory` is the official persistence implementation, not a demo class.

At construction it requires:

- `sessionId`
- `dataSource` or `jdbcUrl`

Default behaviors include:

- `tableName = "ai4j_chat_memory"`
- `initializeSchema = true`
- the policy still defaults to `UnboundedChatMemoryPolicy`

Its write strategy is also worth spelling out:

- read all entries for the current session
- merge the new entries
- apply the policy
- within a transaction, delete the old records first, then re-insert the entire conversation ordered by `item_index`

The benefit of this design is simple recovery and stable ordering; the cost is that it favors session consistency over high-frequency incremental update performance.

## 6. How the policy actually takes effect

### `UnboundedChatMemoryPolicy`

The simplest — essentially copies the current session, with no trimming.

### `MessageWindowChatMemoryPolicy`

This is not "simply keep the last N elements of the array".

It will:

- count the most recent non-system messages starting from the tail
- try to keep `system` entries
- drop earlier non-system entries that exceed the window

```java
// Keep the most recent 2 non-system messages, but system is always preserved
ChatMemory memory = new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(2));

memory.addSystem("system prompt");
memory.addUser("u1");
memory.addAssistant("a1");
memory.addUser("u2");

memory.getItems();
// → [system, assistant("a1"), user("u2")]   // u1 is trimmed, system is kept
```

The tests already verify this: even when window trimming occurs, the `system` message is still preserved.

### `SummaryChatMemoryPolicy`

This is a heavier policy chain.

It will:

- skip non-summary `system` entries and not include them in the summary target
- trigger a summary when the number of summarizable messages exceeds `summaryTriggerMessages`
- keep the most recent `maxRecentMessages`
- hand earlier messages to `ChatMemorySummarizer`
- generate a new entry with `summary=true` and insert it back into the conversation

It is not simply "concatenate the preceding messages into one string"; instead it explicitly introduces a summarizer interface and a summary request object:

- `ChatMemorySummarizer`
- `ChatMemorySummaryRequest`

This allows the summarization logic to be decided by the business itself, rather than being hard-coded by the SDK.

## 7. What snapshot and recovery mean

The presence of `snapshot()` / `restore(...)` makes this layer more than just "the current session container"; it also supports:

- session replay
- temporary branching experiments
- manual checkpoints
- persistence recovery

Here `ChatMemorySnapshot` simply copies the list of `ChatMemoryItem` and carries no additional semantics.
Its value lies in stably freezing "the current session state".

```java
InMemoryChatMemory memory = new InMemoryChatMemory();
memory.addUser("first turn");
memory.addAssistant("reply one");

ChatMemorySnapshot snapshot = memory.snapshot();   // freeze the current state

memory.addUser("second turn");                     // keep advancing
// ... try an experimental branch ...

memory.restore(snapshot);                          // return to the snapshot point, discard the experiment
```

## 8. How multimodal input and tool results enter memory

### Multimodal user input

`addUser(String text, String... imageUrls)` records the text and image URLs into the same `ChatMemoryItem`.
When projected to `Chat` it becomes `Content.MultiModal`; when projected to `Responses` it becomes the `input_text` / `input_image` structure.

### Tool results

The assistant's tool calls and tool outputs are recorded separately:

- `addAssistant(..., toolCalls)`
- `addToolOutput(toolCallId, output)`

This lets the model see a complete chain on the next turn:

- what tool the assistant previously requested
- what that tool returned

```java
ChatMemory memory = new InMemoryChatMemory();

ToolCall toolCall = new ToolCall(
        "call_1", "function",
        new ToolCall.Function("queryWeather", "{\"city\":\"Beijing\"}"));

memory.addAssistant("Let me look that up", Collections.singletonList(toolCall));
memory.addToolOutput("call_1", "{\"weather\":\"sunny\"}");

// On the next request, the model can see the full [assistant tool_call → tool output] chain
memory.toChatMessages();   // Chat: assistant(toolCalls) + role:"tool"
memory.toResponsesInput(); // Responses: message(tool_calls) + function_call_output
```

## 9. The most common misconceptions when using this layer

### Treating `InMemoryChatMemory` as a cross-process persistence layer

:::warning
It is only suitable for sessions within the lifecycle of a single JVM, not for recovery after a service restart.
:::

### The business layer hand-crafting `subList` itself

:::warning
A safer approach is to explicitly use a `ChatMemoryPolicy`. Hand-truncating the list in the business layer can easily break the structure among system, summary, and tool output.
:::

### Assuming memory automatically governs tools

It can record tool calls and tool outputs, but it does not decide whether a tool can be executed, nor does it handle approval or side-effect compensation.

## 10. A more accurate definition

> `ChatMemory` is the unified conversation-fact layer in AI4J. It folds multi-turn dialogue, multimodal input, tool calls, tool outputs, window trimming, summary compaction, and snapshot recovery into a single contract, and allows the same history to project simultaneously onto both the `Chat` and `Responses` access main lines.
