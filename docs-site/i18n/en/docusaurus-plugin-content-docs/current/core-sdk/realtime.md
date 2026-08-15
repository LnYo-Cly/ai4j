---
sidebar_position: 33
title: "Realtime Interface (WebSocket)"
description: "Describes the IRealtimeService long-lived-connection capability surface: currently only supports OpenAI, with a unified entry point, default auth headers, the WebSocket connection flow, and callback caveats."
tags: [concept]
---

# Realtime Interface (WebSocket)

Realtime in AI4J is currently a **thin but officially supported long-lived-connection capability surface**.
Its focus is not on how completely the event protocol is modeled, but rather on the fact that the SDK already provides a unified entry point, default auth headers, and a WebSocket connection flow.

:::tip All code on this page is runnable
The connection examples below come from
[`AudioAndRealtimeDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/AudioAndRealtimeDocExamplesLiveTest.java).
(Realtime endpoints are not supported by every gateway; the test is skipped in that scenario.)
:::

## 1. Current support matrix

Looking at the dispatch in `AiService.createRealtimeService(...)`, realtime currently supports only:

- `OPENAI`

This means it is not currently a multi-provider abstraction surface that has been fully validated; rather, the OpenAI realtime path has been formally brought into the Core SDK first.

## 2. What the unified contract looks like

The unified entry point is:

- `IRealtimeService`

It provides two connection methods:

- `createRealtimeClient(String baseUrl, String apiKey, String model, RealtimeListener listener)`
- `createRealtimeClient(String model, RealtimeListener listener)`

This shows that the realtime layer's abstraction is very clear:

- It is only responsible for establishing the connection
- It does not define event semantics for you
- Nor does it drive a state machine

Connection example:

```java
IRealtimeService realtime = new AiService(configuration).getRealtimeService(PlatformType.OPENAI);

WebSocket ws = realtime.createRealtimeClient("gpt-4o-realtime-preview", new RealtimeListener() {
    @Override
    protected void onOpen(WebSocket webSocket) {
        System.out.println("realtime connected");
    }

    @Override
    protected void onMessage(ByteString bytes) {
        // binary audio frame
    }

    @Override
    protected void onMessage(String text) {
        // JSON event; parse the event type yourself, then dispatch
        System.out.println("event: " + text);
    }

    @Override
    protected void onFailure() { /* note: see implementation details below */ }
});
```

## 3. Actual behavior of `OpenAiRealtimeService`

This implementation is thin, but a few default behaviors must be spelled out.

### Configuration fallback

If `baseUrl` or `apiKey` is empty at call time, it falls back to:

- `OpenAiConfig.apiHost`
- `OpenAiConfig.apiKey`

### URL assembly

It uses:

- `openAiConfig.getRealtimeUrl()`
- `?model=<model>`

to assemble the final WebSocket URL.

### Default request headers

Currently it automatically adds:

- `Authorization: Bearer <apiKey>`
- `OpenAI-Beta: realtime=v1`

This means the SDK has baked the most basic protocol headers required by OpenAI realtime into the implementation, so callers do not need to add them by hand each time.

## 4. What `RealtimeListener` actually encapsulates

`RealtimeListener` is located at:

- `io.github.lnyocly.ai4j.listener.RealtimeListener`

It extends `WebSocketListener` and abstracts out four callbacks you must care about:

- `onOpen(WebSocket)`
- `onMessage(ByteString)`
- `onMessage(String)`
- `onFailure()`

:::warning
Note one implementation detail in particular here:

- `onFailure(WebSocket, Throwable, Response)` currently only logs and does NOT invoke the abstract method `onFailure()`

In other words, the interface appears to expose a unified failure callback, but the current implementation does not actually forward the underlying OkHttp failure event to your abstract `onFailure()`.

This is well worth documenting explicitly; otherwise, callers will mistakenly assume that overriding `onFailure()` is guaranteed to receive disconnect/failure notifications.
:::

## 5. What this layer does not do for you currently

The realtime service currently only handles "correct connection establishment" for you; it does not do:

- Event object modeling
- Message-type dispatch
- Automatic reconnection
- Heartbeat governance
- Backpressure
- Session state recovery

So this capability surface currently reads more as a "formal connection entry point" than a complete realtime runtime.

## 6. Why this page cannot just show a connection example

If the docs only showed a single `createRealtimeClient(...)` example, they would miss three key facts:

### It is the thinnest layer of abstraction

The realtime layer currently does almost no event-semantic encapsulation; the business side has to consume text or binary messages itself.

### It depends on the shared `OkHttpClient`

Like other HTTP capabilities, Realtime shares `Configuration.okHttpClient`.
This means proxy, timeout, connection-pool, and dispatcher policies also affect this long-lived-connection path.

### It currently only formalizes the OpenAI connection convention

The unified interface already exists, but provider coverage is still narrow, which shows this layer currently reads more as "establish a formal capability surface first" than as a cross-platform protocol layer that has already been abstracted to a highly stable level.

## 7. What matters most when wiring it into your application

### Do not do CPU-heavy work in callbacks

:::tip
The `RealtimeListener` callbacks are wired directly onto the OkHttp WebSocket listener.
If you do heavy processing here, it is easy to tightly couple message consumption with connection handling to the point of breakage.
:::

### Define your own event routing layer

The SDK currently does not break string messages down into event objects for you, so the more robust approach is to add an event-dispatch layer of your own in the business layer.

### Connection governance still belongs to the application layer

Automatic reconnection, exponential backoff, session identifiers, monitoring instrumentation, and disconnect compensation are still not responsibilities of the SDK's current realtime layer.

## 8. Conclusion of this page

> AI4J's current Realtime capability is a thin, formal connection-establishment abstraction: it unifies the OpenAI realtime URL, auth headers, and WebSocket entry point, but has not yet turned the event protocol, failure forwarding, reconnection governance, and session recovery into a complete runtime. When you use it, you should treat it as a "long-lived-connection entry-point layer," not a "complete realtime session framework."
