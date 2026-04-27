# Chat

`Chat` 是 AI4J 里最容易打通的模型调用主线。

## 1. 对应接口

- `IChatService`
- `ChatCompletion`
- `ChatCompletionResponse`
- `SseListener`

## 2. 适合什么场景

- 首次接入
- 存量 Chat Completions 迁移
- 稳定文本输出
- 需要 function call 且不想一开始就上更复杂事件模型

## 3. 工程语义

在基础 `IChatService` 场景下，Chat 链路通常自带更直接的工具循环体验。

如果你只是想先把能力打通，优先从它开始最稳。
