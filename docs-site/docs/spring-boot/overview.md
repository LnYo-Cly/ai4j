# Spring Boot 总览

`Spring Boot` 这一章解决的问题很明确：如何把 AI4J 的基座能力稳定放进容器、配置和 Bean 体系。

## 1. 这章在整套体系里的位置

这里对应的主模块是：

- `ai4j-spring-boot-starter/`

它不是另一套独立 AI 能力，而是把 `Core SDK` 容器化。

更准确地说：

- `Core SDK` 定义能力本身
- `Spring Boot starter` 定义这些能力如何进入容器、配置和 Bean 生命周期

## 2. 什么时候看这一章

适合你在这些场景下阅读：

- 项目本身就是 Spring Boot 服务
- 你想通过 `application.yml` 管理 provider、模型和向量能力
- 你希望直接注入 `AiService`、`AiServiceRegistry`、`VectorStore` 等 Bean

如果你还在做纯 Java 最小接入，先看 `Start Here` 和 `Core SDK`；如果你已经进入容器化项目，这一章就是主入口。

## 3. 这章最值得先建立的心智

可以先记住三层：

- `ai4j`：能力基座
- `ai4j-spring-boot-starter`：自动装配和配置绑定
- 你的业务 Bean：Controller、Service、RAG、Workflow、Tool 组织

理解了这三层，后面读自动装配、配置项和 Bean 扩展就不会混。

## 4. 这章主要解决哪几类问题

这一章真正回答的是：

- starter 默认装了哪些 Bean
- `ai.*` 配置最终会进到哪里
- 单实例配置和多实例注册表怎么分工
- 默认 Bean 不够时应该在哪一层扩展
- 业务代码应该怎么组织，才不会把 AI 能力和 Spring 容器逻辑搅在一起

## 5. 和 Core SDK 的边界

这章不负责重新解释：

- `Chat` 和 `Responses` 的协议语义
- `Function Call`、`Skill`、`MCP` 的基础概念
- RAG 的完整能力链

这些概念仍以 `Core SDK` 为主线。

这章负责的是：

- 它们如何在 Spring 容器里被绑定、注入、覆盖和组合

## 6. 推荐阅读顺序

1. [Quickstart](/docs/spring-boot/quickstart)
2. [Auto Configuration](/docs/spring-boot/auto-configuration)
3. [Configuration Reference](/docs/spring-boot/configuration-reference)
4. [Bean Extension](/docs/spring-boot/bean-extension)
5. [Common Patterns](/docs/spring-boot/common-patterns)

如果你只想先抓住一句话，可以记成：

> `Spring Boot` 这一层不重新发明 AI 能力，它负责把 AI4J 的基座能力稳定接入容器、配置和业务 Bean。

## 7. 关键对象

继续往代码下钻时，建议优先看：

- `AiConfigAutoConfiguration`
- `AiService`
- `AiServiceRegistry`
- Spring 容器中的 `VectorStore`、`Reranker`、`RagContextAssembler`

这组对象分别对应自动装配入口、统一服务工厂、多实例路由和知识增强默认骨架。

## 8. 阅读这一章时要抓住什么

- 这一层讲的是容器接入，不重讲 `Core SDK` 基础语义
- 默认 Bean 出现的原因，要回到自动装配和配置绑定来理解
- 业务扩展优先在 Spring 层替换和组合，而不是绕开基座自行拼装
