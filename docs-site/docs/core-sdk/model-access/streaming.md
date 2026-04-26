# Streaming

这一页只讲流式语义，不重复介绍普通同步调用。

## 1. Chat 流式

对应：

- `SseListener`
- 增量读取：`getCurrStr()`

## 2. Responses 流式

对应：

- `ResponseSseListener`
- 增量读取：`getCurrText()`

## 3. 一个常见误区

流式事件不等于 token-by-token。

不同 provider 可能按：

- 字
- 词
- 短句
- 长片段

回传增量。
