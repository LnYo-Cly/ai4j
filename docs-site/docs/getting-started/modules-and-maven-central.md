---
sidebar_position: 2
---

# 模块选择与 Maven Central 发布

这页解决三个常见问题：

1. 我到底该引哪个模块
2. 如果只想要某一类能力，最小依赖应该是什么
3. 哪些模块会发布到 Maven Central，发布时应该怎么理解模块边界

## 1. 一张表先看清模块职责

| 模块 | 适用场景 | 你会得到什么 |
| --- | --- | --- |
| `ai4j` | 想最快接入统一 AI SDK，少选模块 | 统一 provider 接入、Chat / Responses / Embedding / Audio / Image、多模态、Function Tool、MCP Client/Server/Gateway、`ChatMemory`、`IngestionPipeline`、基础请求响应与监听器能力 |
| `ai4j-agent` | 想实现 ReAct、CodeAct、工作流 Agent | Agent runtime、memory、tool loop、trace 等 |
| `ai4j-coding` | 想实现工作区型 Coding Agent，但不一定要默认 UI | coding runtime、session、compact、skills、commands |
| `ai4j-cli` | 想直接使用现成 Coding Agent CLI / TUI / ACP | 可执行入口、命令分发、终端 UI、ACP 接入 |
| `ai4j-spring-boot-starter` | Spring Boot 项目 | 自动装配、配置绑定、直接注入 `AiService` |
| `ai4j-flowgram-spring-boot-starter` | 想做 Agentic 工作流平台后端 | Flowgram runtime、节点执行、画布到后端运行桥接 |
| `ai4j-bom` | 多模块项目统一版本 | 所有发布模块的版本对齐 |

## 2. 如果你只想做某件事，该引什么

### 2.1 只想调用统一 AI 服务

直接引：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.1.0</version>
</dependency>
```

适合：

- 统一接 OpenAI / DeepSeek / Ollama / Doubao 等 provider
- 直接做 Chat、Responses、Embedding、Image、Audio
- 使用内置 `ChatMemory` 维护基础多轮上下文
- 使用 `IngestionPipeline + VectorStore` 做基础知识库入库
- 不想一开始就拆太细模块

### 2.2 只想做统一模型层与工具生态扩展

最小建议：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.1.0</version>
</dependency>
```

适合：

- 想只用统一模型接口，不需要 Agent runtime
- 想自己封装服务层
- 想新增 provider、模型或网络栈扩展
- 想扩展 Tool、MCP、基础 ChatMemory 对接时的能力层

### 2.3 只想做通用 Agent

直接引：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-agent</artifactId>
  <version>2.1.0</version>
</dependency>
```

它依赖 `ai4j`，也就是站在统一 AI SDK 之上继续补齐 agent runtime。

### 2.4 只想做 Coding Agent runtime

直接引：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-coding</artifactId>
  <version>2.1.0</version>
</dependency>
```

适合：

- 自己做 coding runtime
- 自定义 session / memory / compact / skill 加载
- 想要 Coding Agent 能力，但不一定需要默认终端 UI

### 2.5 想要默认终端体验

有两条路：

- 直接复用成品入口：引 `ai4j-cli` 或使用 release 二进制/打包 jar
- 如果你只是做自己的 Coding Agent 产品，也可以把 `ai4j-cli` 当成现成终端壳层来二次定制

### 2.6 Spring Boot 项目

直接引：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
```

### 2.7 想做 Agentic 工作流平台

直接引：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-flowgram-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
```

## 3. 如果全都想要，怎么引最省事

### 3.1 非 Spring 项目

最省事的入口仍然是：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.1.0</version>
</dependency>
```

然后再按需追加：

- 要 Agent：`ai4j-agent`
- 要 Coding runtime：`ai4j-coding`
- 要 CLI：`ai4j-cli`

### 3.2 多模块项目

建议统一先引 BOM：

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

再按需声明模块，不再单独写版本。

## 4. 哪些模块会发布到 Maven Central

当前建议发布的模块：

- `ai4j`
- `ai4j-agent`
- `ai4j-coding`
- `ai4j-spring-boot-starter`
- `ai4j-flowgram-spring-boot-starter`
- `ai4j-bom`
- `ai4j-cli`

当前不应发布到 Central 的模块：

- `ai4j-flowgram-demo`

根工程 `ai4j-sdk` 是聚合工程，不作为业务依赖坐标发布使用。

## 5. Maven Central 发布应该怎么理解

这里不展开具体 `pom.xml` 签名和 Central 插件配置细节，因为这些配置已经放进各发布模块的 `pom` 中了。你在工程层面只需要理解下面几点：

### 5.1 发布单位是模块，不是整个仓库的所有内容

也就是说：

- SDK 能力模块单独发布
- starter 单独发布
- BOM 单独发布
- demo 不发布

### 5.2 发布顺序建议先从基础模块到上层模块

建议顺序：

1. `ai4j`
2. `ai4j-agent`
3. `ai4j-coding`
4. `ai4j-cli`
5. `ai4j-spring-boot-starter`
6. `ai4j-flowgram-spring-boot-starter`
7. `ai4j-bom`

### 5.3 发布时使用 `release` profile

例如：

```bash
mvn -pl ai4j -Prelease -DskipTests package
```

真正发 Central 时再执行对应的 `deploy`。

## 6. 模块边界上的几个判断规则

- 如果你只想“用 SDK”，优先引 `ai4j`
- 如果你想“做平台/框架扩展”，优先沿着 `ai4j + ai4j-agent` 这条线扩展
- 如果你想“做 Coding Agent 产品”，重点看 `ai4j-coding + ai4j-cli`
- 如果你想“做工作流平台”，重点看 `ai4j-flowgram-spring-boot-starter`
- 如果你项目里会同时引多个 AI4J 模块，优先上 `ai4j-bom`

## 7. 继续阅读

1. [安装与环境准备](/docs/getting-started/installation)
2. [平台与服务能力矩阵](/docs/getting-started/platforms-and-service-matrix)
3. [Chat 与 Responses 实战指南](/docs/getting-started/chat-and-responses-guide)
4. [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)
5. [Memory 记忆管理与压缩策略](/docs/agent/memory-management)
6. [Coding Agent 快速开始](/docs/coding-agent/quickstart)

