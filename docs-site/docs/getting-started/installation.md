---
sidebar_position: 1
---

# 安装与环境准备

本页目标：让你在 10 分钟内完成依赖接入，并明确应该从哪条接入路径开始。

> Legacy note: 本页保留为历史长文入口。当前正式入口优先从 [Start Here](/docs/start-here/why-ai4j)、[Quickstart for Java](/docs/start-here/quickstart-java)、[Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) 或 [Coding Agent Overview](/docs/coding-agent/overview) 进入。

如果你想先从当前主线理解真实模块分层、模块职责边界，以及根聚合工程和 demo 模块各自做什么，继续看 [Start Here / Architecture at a Glance](/docs/start-here/architecture-at-a-glance) 和 [Core SDK / Architecture and Module Map](/docs/core-sdk/architecture-and-module-map)。

---

## 1. 先选接入路径

| 场景 | 推荐依赖 | 说明 |
| --- | --- | --- |
| 非 Spring Java 项目 | `ai4j` | 最适合从零验证 Chat / Responses / Embedding 等基础能力 |
| Spring Boot 项目 | `ai4j-spring-boot-starter` | 适合直接注入 `AiService`，快速接进业务系统 |
| 直接使用 Coding Agent CLI / TUI / ACP | `ai4j-cli` 或 Release fat jar | 面向终端、IDE、宿主集成，不是普通业务服务依赖 |
| Flowgram 工作流 | `ai4j-flowgram-spring-boot-starter` | 面向低代码/流程编排，不是基础 LLM 首调入口 |
| 多模块项目统一版本 | `ai4j-bom` | 用 BOM 对齐 `ai4j`、starter、agent、flowgram 等模块版本 |

最短起步建议：

- 非 Spring：先看 [Quickstart for Java](/docs/start-here/quickstart-java)
- Spring Boot：先看 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- Coding Agent：先看 [Coding Agent 快速开始](/docs/coding-agent/quickstart)
- 本地模型：看 [Core SDK / Model Access / Chat](/docs/core-sdk/model-access/chat)

---

## 2. 环境要求

| 项目 | 当前基线 | 说明 |
| --- | --- | --- |
| JDK | `1.8+` | 核心 SDK 兼容 JDK8 |
| Maven | `3.8+` | 推荐使用 3.8 或更高版本 |
| Spring Boot | `2.x` | 当前 starter POM 以 `2.3.12.RELEASE` 为编译基线 |
| Node.js | `18+` | 仅文档站构建需要，业务运行不依赖 |

当前主线里的环境基线，优先看 [Quickstart for Java](/docs/start-here/quickstart-java)、[Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) 和 [Spring Boot / Overview](/docs/spring-boot/overview)。

---

## 3. Maven 依赖坐标

### 3.1 非 Spring 项目

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.1.0</version>
</dependency>
```

### 3.2 Spring Boot 项目

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
```

### 3.3 Flowgram Starter

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-flowgram-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
```

### 3.4 Coding Agent CLI 模块

如果你是想把 AI4J 当成可执行的 Coding Agent 来用，而不是作为普通业务 SDK 依赖，直接看：

- [Coding Agent 快速开始](/docs/coding-agent/quickstart)
- [发布、安装与 GitHub Release](/docs/coding-agent/install-and-release)

作为 Maven 坐标时，它对应：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-cli</artifactId>
  <version>2.1.0</version>
</dependency>
```

但这通常不是你在普通后端服务里最先引入的模块，而更像“现成终端入口”或“宿主集成壳层”。

### 3.5 BOM 对齐版本

如果你的项目会同时使用多个 AI4J 模块，建议引入 BOM：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.lnyo-cly</groupId>
      <artifactId>ai4j-bom</artifactId>
      <version>2.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

之后再按需声明模块，不再单独写版本：

```xml
<dependencies>
  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

当前 BOM 已覆盖这些模块：

- `ai4j`
- `ai4j-agent`
- `ai4j-coding`
- `ai4j-cli`
- `ai4j-spring-boot-starter`
- `ai4j-flowgram-spring-boot-starter`

---

## 4. Gradle 示例

Gradle 项目同样建议用 BOM 对齐版本：

```gradle
dependencies {
    implementation platform('io.github.lnyo-cly:ai4j-bom:2.1.0')
    implementation 'io.github.lnyo-cly:ai4j'
}
```

如果是 Spring Boot：

```gradle
dependencies {
    implementation platform('io.github.lnyo-cly:ai4j-bom:2.1.0')
    implementation 'io.github.lnyo-cly:ai4j-spring-boot-starter'
}
```

---

## 5. 首次健康检查路径

建议按下面顺序验证：

1. 依赖能解析并成功构建
2. API Key 能从环境变量或配置文件读取
3. 先跑一个最小同步请求
4. 再跑一个最小流式请求
5. 最后再接 Tool / Function 或 MCP

如果你一上来就把 Tool、Flowgram、Agent、MCP 全叠在一起，出错时会很难定位是依赖、模型、网络还是业务逻辑的问题。

---

## 6. 常用构建命令

```bash
# 根目录构建（默认跳过测试）
mvn -DskipTests package

# 只构建 ai4j 模块
mvn -pl ai4j -am -DskipTests package

# 构建通用 Agent 模块
mvn -pl ai4j-agent -am -DskipTests package

# 构建 Coding Agent CLI / TUI / ACP
mvn -pl ai4j-cli -am -DskipTests package

# 运行 ai4j 模块测试（显式开启）
mvn -pl ai4j -DskipTests=false test

# 只跑单个测试类
mvn -pl ai4j -Dtest=OpenAiTest -DskipTests=false test
```

---

## 7. 安装注意事项

### 7.1 测试为什么默认被跳过

当前 `ai4j` 模块 POM 里默认 `skipTests=true`，所以你必须显式开启：

```bash
mvn -pl ai4j -DskipTests=false -Dtest=YourTest test
```

### 7.2 代理问题

如果你在国内环境直连外部模型服务，通常需要代理。

Spring Boot 项目可以在配置文件中声明：

```yaml
ai:
  okhttp:
    proxy-url: 127.0.0.1
    proxy-port: 10809
```

非 Spring 项目则在 `OkHttpClient` 里自行配置代理。

### 7.3 编码问题

建议统一使用 UTF-8，并显式设置：

```text
-Dfile.encoding=UTF-8
```

---

## 8. 下一步阅读

1. [Quickstart for Java](/docs/start-here/quickstart-java)
2. [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
3. [Core SDK / Overview](/docs/core-sdk/overview)
4. [Spring Boot / Overview](/docs/spring-boot/overview)
5. [Coding Agent / Overview](/docs/coding-agent/overview)
6. [Start Here / Troubleshooting](/docs/start-here/troubleshooting)

