# docs-site 信息架构重构设计

## 定位

AI4J 文档站应服务两个目标：

1. **低门槛接入**：让第一次来的 Java 开发者尽快跑通模型、工具、RAG、MCP。
2. **完整能力呈现**：把 Core SDK、Spring Boot、Agent、Coding Agent、FlowGram、AgentFlow integrations 等特色功能都讲清楚。

这两个目标不冲突，但必须分层。入口页负责让用户成功，Feature Map 负责不漏能力，功能页负责讲清楚，Reference 负责讲完整。

推荐对外主张：

> AI4J 是面向 Java 8+ 和普通 Java / Spring 项目的低门槛 AI SDK，让开发者用更少配置接入模型、工具调用、RAG、MCP，并逐步升级到 Agent 能力。

## 设计原则

| 原则 | 说明 |
| --- | --- |
| 入口不百科 | `intro` 和 `Start Here` 不承担完整功能解释，只负责定位、路径选择和快速跑通。 |
| 功能不漏项 | 用 `Feature Map` 统一列出所有能力、模块、成熟度和入口链接。 |
| 成熟度透明 | 基础能力、进阶能力、预览能力和实验能力分开标注，避免“昨晚新增能力”被误读为成熟主线。 |
| 示例先于架构 | Quickstart 先给可复制运行代码，再解释模型、配置、扩展点。 |
| Canonical 优先 | 每个主题只保留一个 canonical page；旧页先映射和合并，不直接删除。 |
| 普通 Java 与 Spring 并列 | 不把 Spring 作为唯一入口；Java 8 普通项目是核心差异化。 |

## 目标读者路径

| 读者 | 第一入口 | 第二入口 | 备注 |
| --- | --- | --- | --- |
| 只想跑通模型调用 | `start-here/quickstart-java` | `start-here/first-chat` | Plain Java 是默认低门槛路径。 |
| Spring Boot 项目 | `start-here/quickstart-spring-boot` | `spring-boot/overview` | 兼顾 Spring Boot 2.x / Java 8 现实约束。 |
| 国内模型用户 | `start-here/provider-quickstart` | `core-sdk/provider-profile` | 实施阶段新增，突出 DeepSeek / Doubao / Zhipu / OpenAI-compatible。 |
| 想接工具 | `start-here/first-tool-call` | `core-sdk/tools/overview` | 用最小注解/函数示例开路。 |
| 想做 RAG | `rag/first-rag` | `rag/overview` | 实施阶段建议从 `core-sdk/search-and-rag` 拆出顶层 RAG。 |
| 想接 MCP | `mcp/overview` | `mcp/client-integration` | MCP 是特色能力，但不放进首调路径。 |
| 想做 Agent | `agent/overview` | `agent/quickstart` | 标成 advanced，和 Core SDK 分层。 |
| 想用 Coding Agent | `coding-agent/overview` | `coding-agent/quickstart` | 标成 preview/advanced，避免压住 SDK 主线。 |
| 想接工作流平台 | `integrations/flowgram` 或 `flowgram/overview` | `solutions/*` | FlowGram / Dify / Coze / n8n 作为 integration 特色。 |

## 目标信息架构

```text
docs-site/docs/
  intro.md
  start-here/
    why-ai4j.md
    choose-your-path.md
    feature-map.md
    quickstart-java.md
    quickstart-spring-boot.md
    provider-quickstart.md
    first-chat.md
    first-streaming.md
    first-tool-call.md
    first-rag.md
    first-mcp.md
    troubleshooting.md

  core-sdk/
    overview.md
    provider-profile.md
    model-access/
    tools/
    skills/
    memory/
    extension/

  rag/
    overview.md
    first-rag.md
    ingestion-pipeline.md
    vector-store.md
    retriever.md
    rerank.md
    evaluator.md

  mcp/
    overview.md
    client-integration.md
    server-and-gateway.md
    dynamic-datasource.md
    tool-exposure-semantics.md

  spring-boot/
    overview.md
    quickstart.md
    auto-configuration.md
    configuration-reference.md
    bean-extension.md

  agent/
    overview.md
    quickstart.md
    react.md
    memory-trace.md
    workflow.md
    subagent.md
    agent-team.md

  coding-agent/
    overview.md
    quickstart.md
    cli-tui.md
    workspace-tools.md
    session-runtime.md
    skills.md
    acp.md

  integrations/
    overview.md
    flowgram.md
    dify.md
    coze.md
    n8n.md

  solutions/
    overview.md
    springboot-mysql-chat-memory.md
    rag-ingestion-vector-store.md
    deepseek-stream-search-rag.md
    legal-assistant.md

  reference/
    module-map.md
    provider-matrix.md
    feature-status.md
    configuration-reference.md
    comparison.md
```

实施时不要求一次性改成这个树。第一波只需要补 `feature-map`、明确 canonical 路径，并在 sidebar 上降低旧目录的存在感。

## 功能状态标签

| 标签 | 含义 | 示例 |
| --- | --- | --- |
| `stable` | 推荐作为常规接入主线 | Chat、Streaming、Tool Call、Spring Boot starter、基础 RAG |
| `advanced` | 能力可用，但适合有经验用户 | MCP gateway、Hybrid Retriever、Rerank、Agent runtime |
| `preview` | 已存在，需要继续验证 API / 文档 / 行为稳定性 | Coding Agent、FlowGram starter、Agent Teams |
| `experimental` | 探索性能力，不作为主路径承诺 | 新 provider 特性、尚未充分验证的 runtime 能力 |

首页只展示能力类别和入口，不承诺每项都是 stable。`feature-map.md` 明确写状态、模块、适合人群和下一步链接。

## Feature Map 结构

Feature Map 是“不漏特色功能”的唯一入口，建议表格字段：

| 字段 | 说明 |
| --- | --- |
| Capability | 能力名，如 Chat、Responses、Tool Call、MCP Gateway、Hybrid RAG。 |
| Status | `stable` / `advanced` / `preview` / `experimental`。 |
| Module | `ai4j` / `ai4j-agent` / `ai4j-coding` / starter。 |
| Best for | 该能力解决什么用户问题。 |
| Start here | 最小教程入口。 |
| Deep dive | 完整说明入口。 |

## 首屏叙事建议

`intro.md` 应从“低门槛 Java AI SDK”进入，而不是“完整 Agentic 开发套件”进入。

建议结构：

1. 一句话定位。
2. 三个适合人群：Java 8/普通 Java、Spring Boot、国内模型/Agentic 扩展。
3. 三条最短路径：Plain Java、Spring Boot、Feature Map。
4. 五个能力类目：Model、Tool、RAG、MCP、Agentic Extensions。
5. 明确“完整能力见 Feature Map”。

## 迁移波次

### Wave 1: 入口修正

- 重写 `intro.md`。
- 改写 `start-here/why-ai4j.md`，弱化“工程体系宏大叙事”，强化“为什么更简单”。
- 新增 `start-here/feature-map.md`。
- 新增或改写 provider quickstart。
- 不删除旧目录。

### Wave 2: 快速成功路径

- 统一 `quickstart-java`、`quickstart-spring-boot`、`first-chat`、`first-tool-call`、`first-rag`、`first-mcp`。
- 每页都要求复制即跑，先代码后解释。
- 将 `getting-started` 中仍有价值的内容合并到新 canonical 页。

### Wave 3: Core/RAG/MCP 分层

- `core-sdk` 保留模型、工具、Skill、Memory、Extension。
- RAG 从 `core-sdk/search-and-rag` 抽成顶层 `rag`，但实施时先保留旧 URL 或写 redirect。
- MCP 顶层保留，Core SDK 中只放“能力关系”入口，避免双主线。

### Wave 4: Advanced modules

- Agent、Coding Agent、FlowGram 均保留，但加 `advanced` / `preview` 标签。
- 清理 `agent/coding-agent-*` 旧页，把 Coding Agent canonical 内容归到 `coding-agent`。
- FlowGram 去重 canonical slug。

### Wave 5: Legacy cleanup

- 处理 `ai-basics`、`getting-started`、`guides` 的迁移映射。
- Docusaurus include 中移除已迁移旧目录，或保留为 archive/redirect。
- 跑 `npm run build`，修 broken links。

## 验收标准

实施阶段每一波都应满足：

- `npm run build` 通过。
- 新用户入口不超过 3 次点击能到可运行示例。
- 所有特色能力在 Feature Map 中可见。
- 每个 stable 能力至少有一个最小可运行示例。
- preview/experimental 能力有明确标签，不和 stable 混在同层承诺。

## 非目标

- 不追求把文档写得像大厂百科。
- 不用攻击 Spring AI、LangChain4j、AgentScope。
- 不在首页堆全部 API。
- 不把昨晚新增能力包装成完全成熟能力。
