# Spring Boot 总览

`ai4j-spring-boot-starter` 用来把 Core SDK 接入 Spring Boot 的配置、Bean 生命周期和业务分层。它不是一套新的 AI 实现，也不是必须先使用的入口；如果你不是 Spring 项目，直接从 [Core SDK](/docs/core-sdk/overview) 开始即可。

## 一句话定位

Spring Boot starter 解决的是：

> 在 Spring Boot 应用里，用配置和 Bean 管理 AI4J 的模型服务、服务注册表、HTTP client、RAG 组件和扩展点。

它负责“接入 Spring 容器”，不负责重新定义 Chat、Responses、Tool、MCP 或 RAG 的底层语义。

## 什么时候使用 starter

| 场景 | 是否适合 |
| --- | --- |
| 普通 Java main 方法先验证模型调用 | 不需要 starter |
| 已有 Spring Boot 项目，需要配置化接入模型 | 适合 |
| 需要用 Bean 注入 `AiService` 或 `AiServiceRegistry` | 适合 |
| 需要多 provider / 多实例配置 | 适合 |
| 需要业务侧覆盖 HTTP client、service 或 RAG Bean | 适合 |
| 需要 Agent、Coding Agent 或 FlowGram | 先理解 Core SDK，再接对应上层模块 |

## 最小路径

第一次接入建议按这个顺序：

1. [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
2. [Spring Boot Quickstart](/docs/spring-boot/quickstart)
3. [Auto Configuration](/docs/spring-boot/auto-configuration)
4. [Configuration Reference](/docs/spring-boot/configuration-reference)
5. [Bean Extension](/docs/spring-boot/bean-extension)

先跑通单 provider，再考虑 `ai.platforms[]` 多实例注册表。

## starter 帮你装配什么

| 能力 | 说明 |
| --- | --- |
| 配置绑定 | 读取 `ai.*` 相关配置，映射到配置属性对象 |
| 统一服务入口 | 创建或暴露 `AiService` |
| 多实例注册表 | 通过 `AiServiceRegistry` 管理多个 provider 实例 |
| HTTP client | 统一 OkHttp 配置、超时和连接能力 |
| 兼容入口 | 保留旧入口或兼容壳，帮助旧示例迁移 |
| RAG 相关 Bean | 在条件满足时装配 vector、assembler、reranker 等能力 |

真正的模型协议、Tool schema、MCP transport、RAG ingestion 仍属于 Core SDK。

## 单实例和多实例

### 单实例

适合先跑通一个 provider：

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    base-url: https://api.openai.com
```

### 多实例

适合同时管理多个 provider、多个模型配置或多个租户级入口：

```yaml
ai:
  platforms:
    - id: primary-openai
      type: OPENAI
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
```

多实例不是单实例配置的别名。它会进入 `AiServiceRegistry` 心智，后续业务代码应按 id 取用服务。

## 扩展点

| 你想扩展什么 | 从这里开始 |
| --- | --- |
| 覆盖默认 Bean | [Bean Extension](/docs/spring-boot/bean-extension) |
| 查看配置项 | [Configuration Reference](/docs/spring-boot/configuration-reference) |
| 组织常见业务写法 | [Common Patterns](/docs/spring-boot/common-patterns) |
| 回到模型和 Tool 语义 | [Core SDK](/docs/core-sdk/overview) |
| 做 Spring 场景方案 | [Solutions](/docs/solutions/overview) |

## 上线前检查

- key、baseUrl、model 不写死在代码里。
- dev/test/prod 配置来源可区分。
- 单实例和多实例不会混用成不清晰的路由。
- HTTP 超时、代理、日志和错误处理符合项目要求。
- 自定义 Bean 的覆盖顺序可解释。
- RAG、MCP、Tool 的安全边界仍回到对应主线确认。

相关页面：

- [Version Compatibility](/docs/reference/version-compatibility)
- [Security Overview](/docs/security/overview)
- [Production Checklist](/docs/operations/production-checklist)
- [Troubleshooting](/docs/troubleshooting/overview)

## 继续阅读

1. [Quickstart](/docs/spring-boot/quickstart)
2. [Auto Configuration](/docs/spring-boot/auto-configuration)
3. [Configuration Reference](/docs/spring-boot/configuration-reference)
4. [Bean Extension](/docs/spring-boot/bean-extension)
5. [Common Patterns](/docs/spring-boot/common-patterns)
