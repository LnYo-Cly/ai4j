# Request and Response Conventions

这一页统一解释“请求怎么构造，返回怎么读”。

## 1. 请求对象要点

- `Chat` 看 `ChatCompletion`
- `Responses` 看 `ResponseRequest`
- 其它服务各自有统一请求实体

## 2. 返回对象读取原则

- 先分清同步结果和流式增量
- 先读统一实体，再看 provider 细节
- 日志里建议同时记录 `platform + service + model`

## 3. 为什么这页重要

很多接入问题不是 provider 不可用，而是：

- 读错返回字段
- 混用同步和流式语义
- 没有建立统一读取约定
