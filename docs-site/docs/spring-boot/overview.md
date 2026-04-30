# Spring Boot 总览

`Spring Boot` 这一章讲的不是“AI4J 能不能跑在 Spring 里”，而是 **`ai4j-spring-boot-starter` 如何把 `Core SDK` 的能力接入容器、配置、Bean 生命周期和业务分层**。

## 1. 这章在整套体系里的位置

这一层对应的主模块是：

- `ai4j-spring-boot-starter/`

它不是一套新的 AI 实现，而是把 `ai4j/` 的能力基座稳定装进 Spring 容器。

更具体一点：

- `Core SDK` 定义能力本身
- `Spring Boot starter` 定义这些能力如何被配置、装配、覆盖和注入

## 2. 这章最重要的真实入口

如果你要看这章背后的代码，优先看：

- `AiConfigAutoConfiguration`
- `AiConfigProperties`
- `OkHttpConfigProperties`
- `AiService`
- `AiServiceRegistry`
- `FreeAiService`

这几类对象分别对应：

- 自动装配入口
- 配置绑定入口
- 统一服务入口
- 多实例路由入口
- 兼容壳入口

## 3. starter 到底做了什么

这一层不是“帮你少写几行 `new`”，而是把能力组织成一条稳定链路：

1. Spring 读取 `ai.*` 配置
2. `*ConfigProperties` 接住这些字段
3. `AiConfigAutoConfiguration` 在 `@PostConstruct` 里初始化统一 `Configuration`
4. 同时组装统一 `OkHttpClient`
5. 生成 `AiService`、`AiServiceRegistry`、`FreeAiService`
6. 在条件满足时挂上 `VectorStore`、`RagContextAssembler`、`Reranker` 等 Bean

所以这章的核心不是“怎么写一个 starter”，而是“starter 里的对象图是怎么从配置流出来的”。

## 4. 单实例和多实例是两条不同主线

这一层最容易混淆的点，是把所有配置都看成一回事。

### 单实例主线

你直接配置某个 provider，例如：

- `ai.openai.*`
- `ai.doubao.*`
- `ai.ollama.*`

这种路径通常进入统一 `AiService`，由 `PlatformType` 决定平台分发。

### 多实例主线

当你开始使用：

- `ai.platforms[]`

就是在构建 `AiServiceRegistry`。

这条链会给每个实例复制一份 `Configuration`，再把当前 `AiPlatform` 的字段写回对应 provider config。  
所以它不是单例配置的简单别名，而是明确的多实例注册表模型。

## 5. 为什么这一章和 Core SDK 分界要分清

Spring Boot 这一层只负责：

- 容器接入
- 属性绑定
- Bean 组装
- 默认覆盖

它不负责重新定义：

- `Chat` 和 `Responses` 的协议语义
- `Tool`、`MCP`、`RAG` 的底层模型
- provider 的真正请求映射

这些仍然属于 `Core SDK`。

## 6. 阅读顺序

建议按下面顺序读：

1. [Quickstart](/docs/spring-boot/quickstart)
2. [Auto Configuration](/docs/spring-boot/auto-configuration)
3. [Configuration Reference](/docs/spring-boot/configuration-reference)
4. [Bean Extension](/docs/spring-boot/bean-extension)
5. [Common Patterns](/docs/spring-boot/common-patterns)

## 7. 这一章该建立的心智

把 Spring Boot 这一层理解成三句话就够了：

- `ai4j` 提供能力
- starter 把能力装进 Spring
- 业务 Bean 在这个容器里组合这些能力

如果你记住这一点，后面读自动装配、配置参考和 Bean 覆盖时就不会把容器逻辑和 SDK 协议逻辑混成一团。
