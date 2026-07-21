<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" />
</p>

<p align="center">
  <a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j">
    <img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" />
  </a>
  <a href="https://lnyo-cly.github.io/ai4j/">
    <img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" />
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">
    <img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" />
  </a>
  <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" />
  <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" />
  <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" />
  <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" />
  <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" />
</p>

# ai4j
A Java AI Agentic development toolkit for JDK 8+, combining foundational AI capabilities with higher-level agent development capabilities.  
It covers multi-provider model access, unified I/O, Tool Calling, MCP, RAG, unified `VectorStore`, ChatMemory, agent runtime, coding agent, CLI / TUI / ACP, FlowGram integration, and integration with published AgentFlow endpoints such as Dify, Coze, and n8n, helping Java applications grow from basic model integration to more complete agentic application development.

This repository has evolved into a multi-module SDK. In addition to the core `ai4j` module, it now provides `ai4j-agent`, `ai4j-coding`, `ai4j-cli`, `ai4j-spring-boot-starter`, `ai4j-flowgram-spring-boot-starter`, and `ai4j-bom`. If you only need the basic LLM integration layer, start with `ai4j`. If you need agent runtime, coding agent, CLI / ACP, Spring Boot, or FlowGram integration, add the corresponding modules.

## README navigation

- [Quick choice](#quick-choice)
- [Install snippets](#install-snippets)
- [Project overview](#positioning-compared-with-common-java-ai-options)
- [Detailed docs](#detailed-docs)
- [中文 README](README.md)

## Quick choice

| Goal | Entry |
| --- | --- |
| Run the first request | [Quick start](docs/readme/en/quick-start.md) |
| Use Chat / streaming / vision / function call / memory | [Chat service](docs/readme/en/chat.md) |
| Use Embedding, RAG, and retrieval | [Embedding / RAG](docs/readme/en/rag-and-retrieval.md) |
| Use Coding Agent CLI / TUI / ACP | [Coding Agent CLI / TUI](docs/readme/en/coding-agent-cli.md) |
| Contribute or find support links | [Contributing and support](docs/readme/en/contributing-support.md) |

## Install snippets

Gradle:

```gradle
implementation 'io.github.lnyo-cly:ai4j:2.4.1'
```

Maven:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.4.1</version>
</dependency>
```

For module selection, Spring Boot configuration, and usage examples, see [Quick start](docs/readme/en/quick-start.md).

## Positioning Compared with Common Java AI Options

| Option | Java baseline | Application style | Primary focus |
| --- | --- | --- | --- |
| `ai4j` | `JDK 8+` | Plain Java / Spring | Unified model access, Tool / MCP / RAG, agent runtime, coding agent, CLI / TUI / ACP |
| `Spring AI` | `Java 17+` | `Spring Boot 3.x` | Spring-native AI integration, model access, Tool Calling, MCP, and RAG |
| `Spring AI Alibaba` | `Java 17+` | `Spring Boot 3.x` | Spring and Alibaba Cloud AI ecosystem integration |
| `LangChain4j` | `Java 17+` | Plain Java / Spring / Quarkus and more | General Java abstractions for LLM, agent, and RAG integration, plus AI Services |

## Supported platforms
+ OpenAi
+ Jina (Rerank / Jina-compatible Rerank)
+ Zhipu
+ DeepSeek
+ Moonshot
+ Tencent Hunyuan
+ Lingyi AI
+ Ollama
+ MiniMax
+ Baichuan

## Supported services
+ Chat Completions（streaming and non-streaming）
+ Responses
+ Embedding
+ Rerank
+ Audio
+ Image
+ Realtime

## Supported AgentFlow / hosted workflow platforms
+ Dify (chat / workflow)
+ Coze (chat / workflow)
+ n8n (webhook workflow)

## Features
+ Supports Spring and ordinary Java applications. Supports applications above Java 8.
+ Multi-platform and multi-service.
+ Provides `AgentFlow` support for integrating published Agent / Workflow endpoints from Dify, Coze, and n8n.
+ Provides `ai4j-agent` as the general agent runtime, with ReAct, subagents, agent teams, memory, tracing, and tool loop support.
+ Built-in Coding Agent CLI / TUI with interactive repository sessions, provider profiles, workspace model override, and session/process management.
+ Provides `ai4j-coding` as the coding agent runtime, with workspace-aware tools, outer loop, checkpoint compaction, subagent, and team collaboration support.
+ Provides `ai4j-flowgram-spring-boot-starter` for integrating FlowGram workflows and trace in Spring Boot applications.
+ Provides `ai4j-bom` for version alignment across multiple ai4j modules.
+ Unified input and output.
+ Unified error handling.
+ Supports streaming output. Supports streaming output of function call parameters.
+ Easily use Tool Calls.
+ Supports simultaneous calls of multiple functions (Zhipu does not support this).
+ Supports stream_options, and directly obtains statistical token usage through streaming output.
+ Supports RAG. Built-in vector database support: Pinecone.
+ Uses Tika to read files.
+ Token statistics`TikTokensUtil.java`

## Detailed docs

- [Coding Agent CLI / TUI](docs/readme/en/coding-agent-cli.md)
- [Quick start](docs/readme/en/quick-start.md)
- [Chat service](docs/readme/en/chat.md)
- [Embedding / RAG](docs/readme/en/rag-and-retrieval.md)
- [Contributing and support](docs/readme/en/contributing-support.md)

## Tutorial documents
+ [Quick access to Spring Boot, access to streaming and non-streaming and function calls.](http://t.csdnimg.cn/iuIAW)
+ [Quick access to open source large models such as qwen2.5 and llama3.1 on the Ollama platform in Java.](https://blog.csdn.net/qq_35650513/article/details/142408092?spm=1001.2014.3001.5501)
+ [Build a legal AI assistant in Java and quickly implement RAG applications.](https://blog.csdn.net/qq_35650513/article/details/142568177?fromshare=blogdetail&sharetype=blogdetail&sharerId=142568177&sharerefer=PC&sharesource=qq_35650513&sharefrom=from_link)
