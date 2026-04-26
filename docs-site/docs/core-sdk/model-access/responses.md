# Responses

`Responses` 是 AI4J 里更偏事件化、结构化的一条模型调用主线。

## 1. 对应接口

- `IResponsesService`
- `ResponseRequest`
- `Response`
- `ResponseSseListener`

## 2. 适合什么场景

- 需要更细颗粒事件流
- 需要 reasoning / output item / function args
- 在构建新一代 Agent runtime

## 3. 和 Chat 的关系

它不是“另一个名字的 Chat”，而是更强的事件模型。

所以选型重点不是 provider，而是你要不要消费这类结构化事件。
