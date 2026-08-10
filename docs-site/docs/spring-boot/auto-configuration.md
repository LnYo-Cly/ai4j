---
title: Spring Boot Auto Configuration
description: 深入解析 ai4j-spring-boot-starter 的真实装配链、初始化顺序与条件装配边界，理解统一 Configuration 与失败传播路径。
tags: [integration]
---

# Spring Boot Auto Configuration

这一页讲的是 starter 的真实装配链，而不是泛泛地说“它会自动配置一些 Bean”。

## 1. 真实入口类

核心入口是：

- `AiConfigAutoConfiguration`

它做的事情不是单点注入，而是一整条装配链：

- `@EnableConfigurationProperties(...)` 绑定一组 `ai.*` 属性类
- `@PostConstruct` 初始化统一 `Configuration`
- 创建 `AiService`
- 创建 `AiServiceFactory`
- 创建 `AiServiceRegistry`
- 创建 `FreeAiService`
- 条件性创建 `VectorStore`、`RagContextAssembler`、`Reranker`

## 2. 初始化顺序

`AiConfigAutoConfiguration` 里最关键的是 `init()`：

1. 初始化 `OkHttpClient`
2. 初始化各类向量数据库配置
3. 初始化 `SearXNG` 配置
4. 初始化各 provider 配置
5. 把这些对象写回统一 `Configuration`

这意味着你在 Spring Boot 下拿到的不是一堆互不相关的配置 Bean，而是已经被组织好的运行时图。

## 3. `initOkHttp()` 的意义

`initOkHttp()` 不是普通工具方法，它决定了整个 starter 的底层网络栈。

它会：

- 构造 `HttpLoggingInterceptor`
- 通过 `ServiceLoaderUtil.load(...)` 加载 `DispatcherProvider`
- 通过 `ServiceLoaderUtil.load(...)` 加载 `ConnectionPoolProvider`
- 组装统一 `OkHttpClient.Builder`
- 按配置加入代理和 SSL 策略
- 最后写回 `Configuration.okHttpClient`

:::warning initOkHttp 失败会全链路波及
这一步一旦失败，后面的 provider、vector、RAG、websearch 相关能力都会一起受影响，因为它们共享同一个底层客户端入口。
:::

## 4. 单实例和多实例在这里怎么分流

### 单实例

如果你只配置一个 provider，主线通常是：

- `AiService`

### 多实例

如果你配置了 `ai.platforms[]`，主线通常是：

- `AiServiceRegistry`
- `AiServiceRegistration`
- `FreeAiService`

这不是同一个东西的不同名字，而是两条不同组织方式。

## 5. 条件装配的边界

starter 里并不是所有东西都无条件创建。

有些 Bean 是：

- `@ConditionalOnMissingBean`
- `@ConditionalOnProperty`
- `@ConditionalOnBean`

这意味着默认 Bean 的存在是“可被接管”的，而不是强制覆盖业务实现。

## 6. 扩展与插件装配：`ai.extensions.*`

`AiConfigAutoConfiguration` 不只装配模型和网络，还把 ai4j 的扩展/插件体系自动接进 Spring。绑定入口是 `AiExtensionProperties`（前缀 `ai.extensions`），产出两个 Bean：

- `ExtensionRegistry`：`@ConditionalOnMissingBean`，先 `ExtensionRegistry.discover()` 做类路径发现，再把 YAML 配置逐项应用上去。
- `ExtensionRuntimeSnapshot`：`registry.snapshot()` 的不可变快照，供运行时读取当前启用的 tools / commands / skills / prompts / guardrails。

装配链把 YAML 的每一组配置映射成 registry 上的一次方法调用：

| YAML 配置 | registry 调用 | 含义 |
| --- | --- | --- |
| `ai.extensions.enabled` | `enableAll(...)` | 显式启用一批扩展 |
| `ai.extensions.explicit-resource-activation` | `requireExplicitResourceActivation()` | 强制资源必须显式激活 |
| `ai.extensions.tools.expose` | `exposeTools(...)` | 暴露哪些工具 |
| `ai.extensions.commands.allow` | `allowCommands(...)` | 允许哪些命令 |
| `ai.extensions.skills.allow` | `allowSkills(...)` | 允许哪些 skill |
| `ai.extensions.prompts.allow` | `allowPrompts(...)` | 允许哪些 prompt |
| `ai.extensions.guardrails.allow` | `allowGuardrails(...)` | 允许哪些 guardrail |

示例：

```yaml
ai:
  extensions:
    enabled: [filesystem, search]
    explicit-resource-activation: true
    tools:
      expose: [read-file, web-search]
    skills:
      allow: [summarize]
```

这样启动后，`ExtensionRuntimeSnapshot` 就是当前生效的扩展视图，业务代码不再需要自己拼装这些清单。

:::tip 两个 Bean 都是 @ConditionalOnMissingBean
如果你想完全接管扩展装配（例如从企业内部的权限系统动态生成清单），直接在自己的 `@Configuration` 里声明同名 `ExtensionRegistry` Bean 即可，starter 的默认装配会自动让位。
:::

## 7. OkHttp SPI 扩展点

`initOkHttp()` 里网络栈的两个关键零件不是写死的，而是通过 SPI 加载：

```text
ServiceLoaderUtil.load(DispatcherProvider.class)    // 并发调度策略
ServiceLoaderUtil.load(ConnectionPoolProvider.class) // 连接池策略
```

接口定义在 `io.github.lnyocly.ai4j.network`：

```java
// ai4j 2.4.2, groupId io.github.lnyo-cly
public interface DispatcherProvider {
    okhttp3.Dispatcher getDispatcher();
}

public interface ConnectionPoolProvider {
    okhttp3.ConnectionPool getConnectionPool();
}
```

默认实现是 `DefaultDispatcherProvider` / `DefaultConnectionPoolProvider`（各返回一个 `new Dispatcher()` / `new ConnectionPool()`）。要替换它们，实现对应接口，并通过 Java SPI（`META-INF/services/...`）注册即可，整个 starter 共享的 `OkHttpClient` 就会走你的实现。常见用途：自定义最大并发请求、连接池存活时长、按租户隔离调度器。

## 8. 你应该怎么看这页

把它看成一个对象图说明页：

- 配置怎么进来
- 统一 `Configuration` 怎么组起来
- 哪些对象是基础入口
- 哪些对象是可选增强

如果你只把它理解成“自动装配示例”，就会看漏真正重要的部分：**装配顺序和失败传播路径**。
