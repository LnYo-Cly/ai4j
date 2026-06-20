# Spring Boot Bean Extension

默认自动装配只是起点。真正的业务系统通常会覆盖一部分 Bean，但应该沿着 AI4J 的抽象层去接管。

## 1. 什么时候该覆盖

常见场景：

- 自定义 `OkHttpClient`
- 指定自己的 `VectorStore`
- 自定义 `RagContextAssembler`
- 自定义 `Reranker`
- 对接企业内部的装配或路由策略

## 2. 应该覆盖哪一层

优先顺序是：

1. 先替换容器层 Bean
2. 再考虑业务层组合
3. 最后才考虑改底层 SDK 实现

也就是说，Bean extension 的目标不是绕开 AI4J，而是把你的自定义逻辑放回它的容器模型里。

## 3. 最容易做错的地方

最常见的问题不是不会写 Bean，而是位置选错：

- 本该在容器层替换，却去改底层 provider 实现
- 本该用统一抽象，却在业务代码里写平台私货
- 本该交给注册表或服务层处理，却硬塞进 Controller

这会让项目很快失去 AI4J 原本的分层价值。

## 4. 典型覆盖点

starter 默认给出的基础 Bean，通常可以按需接管：

- `AiService`
- `AiServiceRegistry`
- `FreeAiService`
- `VectorStore`
- `RagContextAssembler`
- `Reranker`

## 5. 工程原则

- 优先替换统一抽象后的 Bean，而不是修改底层 provider 私有实现
- 多个同类型 Bean 并存时，显式声明选择策略
- 让业务路由逻辑留在 service 层，不要回流到 controller 或 util 类

## 6. 这一页的结论

Bean extension 的核心不是“能不能重写一个 Bean”，而是 **你是否还留在 AI4J 的容器和抽象边界里**。  
只要覆盖动作仍然发生在 Spring 层，通常就还能保持 starter 的统一模型；一旦开始绕开这些抽象，后续治理成本会迅速上升。
