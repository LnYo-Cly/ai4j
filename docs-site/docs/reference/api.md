---
sidebar_position: 1
---

# API Reference

这一页只负责把你带到发布版本的 API 文档，不在文档站手工复制类、字段或方法清单。

## 先选模块

| 你要使用什么 | Maven artifact | 发布版 Javadoc |
| --- | --- | --- |
| 模型、Tool、Skill、MCP、RAG | `ai4j` | [ai4j-2.4.2-javadoc.jar](https://repo1.maven.org/maven2/io/github/lnyo-cly/ai4j/2.4.2/ai4j-2.4.2-javadoc.jar) |
| 通用 Agent runtime | `ai4j-agent` | [ai4j-agent-2.4.2-javadoc.jar](https://repo1.maven.org/maven2/io/github/lnyo-cly/ai4j-agent/2.4.2/ai4j-agent-2.4.2-javadoc.jar) |
| Coding Agent runtime | `ai4j-coding` | [ai4j-coding-2.4.2-javadoc.jar](https://repo1.maven.org/maven2/io/github/lnyo-cly/ai4j-coding/2.4.2/ai4j-coding-2.4.2-javadoc.jar) |
| CLI、TUI、ACP host | `ai4j-cli` | [ai4j-cli-2.4.2-javadoc.jar](https://repo1.maven.org/maven2/io/github/lnyo-cly/ai4j-cli/2.4.2/ai4j-cli-2.4.2-javadoc.jar) |
| 扩展与插件契约 | `ai4j-extension-api` | [ai4j-extension-api-2.4.2-javadoc.jar](https://repo1.maven.org/maven2/io/github/lnyo-cly/ai4j-extension-api/2.4.2/ai4j-extension-api-2.4.2-javadoc.jar) |

下载 Javadoc JAR 后解压并打开其中的 `index.html`。它和 Maven artifact 同版本发布，避免文档站手工维护 API 表而与源码漂移。

## 阅读顺序

先使用概念和教程页面确定入口与边界，再查 Javadoc 的类型签名：

1. 普通模型调用从 [Core SDK](/docs/core-sdk/overview) 开始。
2. Spring 应用从 [Spring Boot](/docs/spring-boot/overview) 开始。
3. 多步工具调用从 [Agent Runtime](/docs/agent/overview) 开始。
4. 命令行或 ACP 宿主从 [Coding Agent](/docs/coding-agent/overview) 开始。

Javadoc 解释“类型有什么”，而概念页解释“何时使用、与什么组合、有什么安全边界”。两者不应互相替代。

## 版本边界

这里链接的是当前已发布的 `2.4.2`。根工程的 `2.4.3-SNAPSHOT` 是下一开发版本，不能作为用户安装或稳定 API 依据。升级前查看 [Version Compatibility](/docs/reference/version-compatibility)；多模块项目使用 [BOM](/docs/reference/release-and-artifacts) 对齐版本。
