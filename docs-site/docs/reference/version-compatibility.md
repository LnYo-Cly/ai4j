---
sidebar_position: 1
---

# Version Compatibility

这页用于版本评估和升级前检查。它不承诺所有 provider 的所有能力完全对称，而是把当前文档站推荐的兼容性边界讲清楚。

## 基线

| 项 | 当前边界 |
| --- | --- |
| AI4J 版本 | `2.4.2` |
| Maven groupId | `io.github.lnyo-cly` |
| Java baseline | Java 8 source / target |
| 构建工具 | Maven |
| 测试主线 | JUnit 4，live provider tests 默认排除 |
| 文档站 Node baseline | `docs-site` 需要 Node.js `>=20.0` |

## 模块兼容矩阵

| Artifact | 对应源码模块 | 最小使用场景 | 内部依赖 |
| --- | --- | --- | --- |
| `ai4j` | `ai4j/` | 模型、Tool、Skill、MCP、RAG、Memory 等 Core SDK 能力 | 无 AI4J 内部依赖 |
| `ai4j-spring-boot-starter` | `ai4j-spring-boot-starter/` | Spring Boot 应用配置化接入 | `ai4j` |
| `ai4j-agent` | `ai4j-agent/` | 通用 Agent runtime、workflow、trace、team | `ai4j` |
| `ai4j-coding` | `ai4j-coding/` | 本地代码仓任务 runtime、workspace tools、compaction | `ai4j`、`ai4j-agent` |
| `ai4j-cli` | `ai4j-cli/` | CLI、TUI、ACP host、session 入口 | `ai4j-coding` 及其基础依赖 |
| `ai4j-flowgram-spring-boot-starter` | `ai4j-flowgram-spring-boot-starter/` | FlowGram.ai 画布后端执行层 | `ai4j-agent`、`ai4j-spring-boot-starter` |
| `ai4j-bom` | `ai4j-bom/` | 多模块版本对齐 | 管理发布 artifact 版本 |

## Java 8 说明

AI4J 的 Java 模块仍按 Java 8 兼容设计。项目可以运行在更高版本 JDK 上，但代码和公共 API 不应主动依赖 Java 9+ 语言特性，除非某个任务明确升级基线。

需要注意两点：

- `ai4j` 中存在面向高版本 JDK 的运行时 profile，例如 Nashorn / GraalPy 相关 profile；它们是可选运行时路径，不改变主代码基线。
- `docs-site` 是 Docusaurus 站点，Node.js 基线独立于 Java SDK，不代表 Java 模块要求 Node.js。

## Provider 能力不是完全对称

AI4J 统一的是入口、请求模型和工程心智，不是把每个 provider 包装成完全一样的能力面。实际使用前应按 [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix) 确认：

- Chat / Responses 支持范围。
- Embedding / Rerank 支持范围。
- Image / Audio / Realtime 是否只在特定 provider 路径可用。
- Tool calling、streaming、多模态是否有 provider 差异。

项目接入时建议把 provider 能力矩阵写进自己的接入说明，不要只依赖默认配置。

## Spring Boot 兼容性

Spring Boot starter 的职责是配置绑定、自动装配和 Bean 生命周期接入。接入前先确认：

- 项目是否能引入 `ai4j-spring-boot-starter` 当前版本。
- 配置项是否落在 `ai.*` 命名空间。
- 单实例配置和 `ai.platforms[]` 多实例注册表是否需要同时存在。
- 是否需要自定义 `OkHttpClient`、`AiService`、`AiServiceRegistry` 或业务侧 Bean。

推荐从 [Spring Boot Overview](/docs/spring-boot/overview) 和 [Configuration Reference](/docs/spring-boot/configuration-reference) 开始。

## 升级顺序

同时使用多个 AI4J 模块时，推荐：

1. 用 `ai4j-bom` 固定同一版本。
2. 先升级 `ai4j` 和最小 quickstart。
3. 再升级 starter、Agent、Coding Agent 或 FlowGram。
4. 对照 [生产检查清单](/docs/operations/production-checklist) 复核密钥、超时、日志、工具白名单、MCP 配置和回归命令。

## 回归建议

| 改动 | 最小检查 |
| --- | --- |
| 只改 docs-site 内容 | `npm run build` |
| 改 Java API 或 provider 支持 | 对应模块 `mvn -pl <module> -DskipTests=false test` |
| 改 starter 配置 | `mvn -pl ai4j-spring-boot-starter -DskipTests=false test` |
| 改 Agent / Coding Agent | 对应模块测试 + CLI 或 session 层 smoke |
| 改 FlowGram starter | starter 测试 + demo 或 task API smoke |

如果 live provider 测试需要真实密钥，应显式走 live profile，不要把密钥写入仓库。
