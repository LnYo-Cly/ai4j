---
sidebar_position: 2
---

# 版本与兼容性

> Legacy note: 本页保留为历史兼容性说明。当前正式入口优先从 [Quickstart for Java](/docs/start-here/quickstart-java)、[Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) 和 [Spring Boot / Overview](/docs/spring-boot/overview) 进入。

这一页只回答一个问题：你当前的工程版本条件，适不适合直接接入 AI4J。

---

## 1. 基线结论

当前仓库里的版本基线可以直接归纳成下面几条：

- 核心 SDK 基线：JDK `1.8+`
- 当前根工程版本：`2.1.0`
- Spring Boot starter 编译基线：`2.3.12.RELEASE`
- 文档中的 Maven 坐标和 BOM 坐标均以 `2.1.0` 为当前版本示例

---

## 2. 兼容性矩阵

| 项目 | 当前基线 | 说明 |
| --- | --- | --- |
| `ai4j` | JDK `1.8+` | 最基础的统一 AI SDK 入口 |
| `ai4j-spring-boot-starter` | Spring Boot `2.3.12.RELEASE` 基线 | 文档示例按 Spring Boot 2.x 写法组织 |
| `ai4j-flowgram-spring-boot-starter` | JDK `1.8+` + Spring Boot 场景 | 适合工作流和低代码平台，不是首调入口 |
| `ai4j-bom` | 与根工程 `2.1.0` 对齐 | 用于统一模块版本 |

---

## 3. JDK 相关说明

### 3.1 JDK 8

这是当前文档主路径的最低基线。

适合：

- 基础 Chat / Responses / Embedding / Audio / Image 接入
- 非 Spring 与 Spring Boot 基础场景
- MCP Client / Gateway 的基础接入

### 3.2 JDK 15+

仓库中存在 `nashorn-runtime` profile，会在 `JDK [15,)` 条件下激活。

这意味着：

- 某些脚本执行相关能力会开始依赖 Nashorn runtime profile
- 如果你只做基础 LLM 接入，这一层通常不是首要关注点

### 3.3 JDK 17+

仓库中还存在 `graalpy-runtime` profile，会在 `JDK [17,)` 条件下激活。

这更偏向高级运行时能力，而不是“第一个成功请求”的最低门槛。

如果你只是首次接入：

- 先按 JDK 8 路径打通
- 再按需要评估更高版本运行时

---

## 4. Spring Boot 相关说明

当前 `ai4j-spring-boot-starter` 的 POM 内部使用：

```text
spring-boot.version = 2.3.12.RELEASE
```

因此文档里的 starter 示例默认按 Spring Boot 2.x 思路组织。

推荐理解：

- 你是 Spring Boot 2.x 项目：直接按文档接入
- 你是更高版本项目：先做最小集成验证，再决定是否大面积接入

---

## 5. BOM 什么时候该用

只要你遇到下面任一情况，就建议用 BOM：

- 同时引入 `ai4j` 和 starter
- 计划同时接 `agent`、`coding`、`flowgram`
- 团队里有多个服务同时引用 AI4J 模块

原因很简单：

- BOM 负责统一版本
- 业务 POM 只负责声明“我需要哪些模块”

这样后续升级时不会到处手改版本号。

---

## 6. 一个简单判断法

如果你现在还不确定该怎么选，可以直接按下面判断：

1. 只是先打通模型请求：用 `ai4j`
2. 项目本身就是 Spring Boot：用 `ai4j-spring-boot-starter`
3. 会用多个 AI4J 模块：加上 `ai4j-bom`
4. 要做工作流和节点编排：再引入 `ai4j-flowgram-spring-boot-starter`

---

## 7. 继续阅读

1. [Start Here / Quickstart for Java](/docs/start-here/quickstart-java)
2. [Start Here / Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
3. [Spring Boot / Overview](/docs/spring-boot/overview)

