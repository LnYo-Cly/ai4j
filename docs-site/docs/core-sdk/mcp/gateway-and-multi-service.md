# Gateway and Multi-service

一旦你接的不止一个 MCP 服务，就不该停在单 client 心智。

## 1. Gateway 解决什么

- 多服务聚合
- 统一调度
- 配置化管理
- 服务可见性控制

## 2. 什么时候必须上 gateway

- 同时接 GitHub、浏览器、数据库、内部 API
- 按租户或工作区启停服务
- 想统一处理连接和路由
