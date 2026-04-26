# SPI HTTP Stack

AI4J 的网络层不是只能被动接受默认 `OkHttp` 设置。

## 1. 这页讲什么

- dispatcher 扩展
- connection pool 扩展
- HTTP 执行栈接入点

## 2. 什么时候需要它

- 并发模型有明确要求
- 要统一连接池策略
- 要把底层 HTTP 行为纳入平台治理
