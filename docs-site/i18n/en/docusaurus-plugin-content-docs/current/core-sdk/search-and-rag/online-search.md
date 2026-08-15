---
title: "Online Search"
description: "Clarifies the real positioning of AI4J Online Search: it is not a unified retrieval framework, but rather an online search augmentation layer that wraps IChatService, uses the last message as the query to call SearXNG, splices the result JSON directly into the user prompt, and rewrites the request in place."
tags: [concept]
---

# Online Search

AI4J's current `online search` is not a general-purpose retrieval framework, nor does it automatically feed web crawl results into the `Retriever` system.
Seen from the source, it is actually something more specific:

**A prompt augmentation layer that wraps `IChatService`.**

If this is not spelled out clearly, it is most easily conflated with offline RAG, MCP tools, and function calling.

## 1. Where the source entry point is

The current core entry points are quite explicit:

- `websearch/ChatWithWebSearchEnhance.java`
- `websearch/searxng/SearXNGConfig.java`
- `websearch/searxng/SearXNGRequest.java`
- `websearch/searxng/SearXNGResponse.java`

The main class definition is:

```java
public class ChatWithWebSearchEnhance implements IChatService
```

This signature already states its architectural positioning:

- It is not a `Retriever`
- It is not a `RagService`
- It is not a tool executor
- It is a wrapper around `IChatService`

## 2. How it actually works

The current implementation is very direct.

Whichever path you take:

- `chatCompletion(...)`
- `chatCompletionStream(...)`

it first calls:

```java
addWebSearchResults(chatCompletion)
```

and then hands the modified `ChatCompletion` to the underlying `chatService`.

The logic of `addWebSearchResults(...)` is:

1. Take the text content of the last message
2. Use this text as the query to perform an online search
3. Serialize the search results into JSON text
4. Rewrite the content of the last user message
5. Inject into the new prompt:
   - A fixed Chinese instruction
   - A "web references" block
   - A "user question" block

So the essence of Online Search in current AI4J is not a three-stage "query + retrieval + grounding" pipeline, but rather:

**Search first, then splice the search results directly into the last user prompt.**

## 3. Why it is not traditional RAG

It differs significantly from offline RAG.

Offline RAG typically goes through:

- ingest
- chunk
- embed
- retrieve
- rerank
- assemble context

Whereas `ChatWithWebSearchEnhance` has none of these layers:

- No `RagQuery`
- No `RagHit`
- No `Retriever`
- No `Reranker`
- No `RagTrace`

It only does two things:

- Call an external search API
- Rewrite the last text message in the chat request

So the more accurate positioning of this layer is:

**Freshness augmentation, not knowledge-base RAG.**

## 4. What role SearXNG plays in this chain

The current online search directly depends on `SearXNG`.

`performWebSearch(query)` will:

1. Read `SearXNGConfig` from `Configuration`
2. Validate that `searXNGConfig.getUrl()` is non-null
3. Send a GET request with `OkHttpClient`
4. Parse the JSON into `SearXNGResponse`
5. Truncate the number of results per `searXNGConfig.getNums()`
6. Then serialize the results as a whole via `JSON.toJSONString(...)`

There is an important engineering fact here:

**In the current implementation, search results do not enter the model context in a structured way; instead, they are first serialized wholesale into a JSON string and then spliced into the prompt.**

In other words, what the model ultimately sees is "a text prompt with JSON search results embedded", not a set of strongly-typed document objects.

## 5. Why it is designed as an `IChatService` wrapper layer

Seen from the code, the core advantage of this design is low intrusiveness:

- No need to modify the provider SDK main flow
- No need to introduce a new tool protocol
- Both synchronous and streaming chat can reuse it
- To upstream callers, it is still an `IChatService`

This makes it well suited as an augmentation layer for "quickly wiring online search into a conversation".

But this design also brings natural boundaries:

- It can only augment the chat path
- It does not naturally port to `Responses`-style event streams
- It cannot retain an independent retrieval-hit structure like RAG
- It does not produce intermediate state like `RagTrace`

## 6. Three default behaviors most worth noting in the current implementation

### 6.1 It directly rewrites the original `ChatCompletion`

`addWebSearchResults(...)` does not copy the request; it directly modifies the content of the last message.

This means:

- If the caller later reuses the same `ChatCompletion` object
- What it sees is already the "augmented prompt"

This is a very typical wrapper side effect, and must be spelled out clearly when documenting.

### 6.2 It only looks at the last message

The code directly takes:

```java
chatCompletion.getMessages().get(chatLen - 1)
```

So the current semantics are:

- Only the last message text is used as the search query
- It does not synthesize the whole conversation to do search query rewrite

If your multi-turn conversation needs to "search with context", this default layer does not do that for you.

### 6.3 By default it injects a fixed Chinese instruction

This instruction explicitly requires the model to:

- Answer based on the web references and the user question
- Use Markdown
- List references at the end of the answer
- When references are insufficient, supplement with its own knowledge or state uncertainty

This shows that online search is currently not only data augmentation, but also **prompt policy injection**.

## 7. What the current failure path looks like

Failures in `performWebSearch(...)` are distinguished by type:

- If no `SearXNG url` is configured, it throws `CommonException` directly
- If the upstream returns non-2xx, it throws the typed exception decoded by `HttpErrorDecoder` (`AiAuthException` / `AiRateLimitException` / `AiServerErrorException` / `AiClientException`), **carrying the original error message returned by the upstream**, with the status code readable via `getStatusCode()`
- On parsing or network exceptions, it throws `CommonException`, with the message including the underlying exception's `getMessage()`

Therefore, when troubleshooting, you can distinguish directly:

```java
try {
    String results = enhance.performWebSearch(query);
} catch (AiRateLimitException e) {
    // Upstream rate-limited; can back off and retry
} catch (AiAuthException e) {
    // Credential issue
} catch (AiHttpException e) {
    // Other upstream HTTP errors; e.getStatusCode() + e.getMessage() already contains the upstream original
} catch (CommonException e) {
    // Missing config, or network/parsing failure
}
```

:::note Version differences
In v2.4.2 and earlier, all of the above cases are flattened into the same `CommonException("SearXNG request failed")` and cannot be distinguished. See [issue #228](https://github.com/LnYo-Cly/ai4j/issues/228).
:::

## 8. The most real safety and quality boundaries of this layer

:::warning Safety and quality boundaries
Because it is essentially injecting web search results into the prompt as-is, you must be very clear about the differences between it and an offline knowledge base:

- Results are fresh, but stability is weak
- Data is open, but noise and prompt injection risk are higher
- No chunk-level structure control
- No independent rerank/trace mechanism
:::

In other words, Online Search is better suited for:

- "What is today's news"
- "What changed in the latest version of some library"
- "Supplement with the latest public online material"

It is not suited to directly replace:

- Enterprise internal knowledge bases
- Strictly auditable citation systems
- Production RAG that needs stable replay

## 9. What is the most stable way to collaborate with offline RAG

If this layer is placed into a more complete system, the most stable division of roles is usually:

- Offline RAG handles stable, structured, traceable internal knowledge
- Online Search handles time-sensitive open-web supplementation

Do not invert this and let online search serve as the primary knowledge source with the offline library as a garnish.
Because, seen from the current implementation, the structured governance capability of online search is clearly weaker than that of offline RAG.

## 10. Five easiest pitfalls

### 10.1 Describing it as "AI4J's general-purpose search subsystem"

The current implementation is only an `IChatService` wrapper, not a unified search framework.

### 10.2 Assuming it automatically enters the `Retriever`/`Reranker` chain

It does not. It is not wired into the `RagService` main line.

### 10.3 Ignoring that the request is rewritten in place

If the same `ChatCompletion` object is reused, this side effect propagates directly.

### 10.4 Treating the last message as the full conversation intent

The current search query comes only from the last message text; multi-turn semantics may be lost.

### 10.5 Assuming "listing references" equals a hard citation constraint

Currently this is only a prompt-level requirement, not a hard-constraint citation system.

## 11. The conclusion most worth remembering from this page

AI4J's current Online Search is essentially an `IChatService`-level online search augmentation wrapper:

- It uses the last message as the query
- Calls SearXNG to pull public web results
- Splices the result JSON directly into the user prompt
- Then hands it to the underlying chat service

What it solves is "giving a conversation online-supplementation capability", not "replacing the structured retrieval chain of offline RAG".

## 12. Further reading

- [Search and RAG Overview](/docs/core-sdk/search-and-rag/overview)
- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
