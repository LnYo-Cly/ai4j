# Spring Boot 总览

`Spring Boot` 这一章解决的问题很明确：如何把 AI4J 的基座能力稳定放进容器、配置和 Bean 体系。

## 1. 什么时候看这一章

适合你在这些场景下阅读：

- 项目本身就是 Spring Boot 服务
- 你想通过 `application.yml` 管理 provider、模型和向量能力
- 你希望直接注入 `AiService`、`AiServiceRegistry`、`VectorStore` 等 Bean

如果你还在做纯 Java 最小接入，先看 `Start Here` 和 `Core SDK`；如果你已经进入容器化项目，这一章就是主入口。

## 2. 它和 Core SDK 的关系

`Core SDK` 定义能力本身。

`Spring Boot` 定义的是：

- 如何把这些能力放进容器
- 如何通过配置组织这些能力
- 如何覆盖默认 Bean

所以它不是另一套独立能力模型，而是 `Core SDK` 的容器化入口。

## 3. 这章最值得先建立的心智

可以先记住三层：

- `ai4j`：能力基座
- `ai4j-spring-boot-starter`：自动装配和配置绑定
- 你的业务 Bean：Controller、Service、RAG、Workflow、Tool 组织

理解了这三层，后面读自动装配、配置项和 Bean 扩展就不会混。

## 4. 推荐阅读顺序

1. [Quickstart](/docs/spring-boot/quickstart)
2. [Auto Configuration](/docs/spring-boot/auto-configuration)
3. [Configuration Reference](/docs/spring-boot/configuration-reference)
4. [Bean Extension](/docs/spring-boot/bean-extension)
5. [Common Patterns](/docs/spring-boot/common-patterns)
