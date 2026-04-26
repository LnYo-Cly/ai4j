# Provider Extension

当你要新增 provider，首先要想清楚的是“接入边界”。

## 1. 这一层解决什么

- 平台配置
- 统一服务接口映射
- provider 特定实现放在哪

## 2. 什么时候进入这一页

当现有 `PlatformType` 和对应实现已经不够用时。
