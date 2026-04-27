# Why AI4J

如果只用一句话概括，AI4J 是一套面向 `JDK8+` 的 Java AI 基座，它不只解决“怎么调模型”，还把工具、协议扩展、RAG、Agent 和 Coding Agent 的上升路径放进了一套连续的工程体系。

这页不讲细节 API，只回答四个问题：

1. AI4J 到底是什么
2. 它为什么值得选
3. 它和相邻方案的边界是什么
4. 读完之后应该从哪里继续

## 1. 三分钟理解 AI4J

先记住三件事：

- `ai4j` 不是单一 provider 的 Java 包装，而是统一模型接入和 AI 基础能力的底座
- 这套底座往上可以自然升级到 `Spring Boot`、`Agent`、`Coding Agent` 和 `Flowgram`
- `Function Call`、`Skill`、`MCP` 在 AI4J 里是三类并列但分工不同的能力，不会被混成一团

如果你需要的是一条从“先发出第一个模型请求”一直走到“做长期 agentic 系统”的路线，AI4J 的价值就会很明显。

## 2. AI4J 解决的不是单点调用，而是整条能力链

很多 Java AI 项目一开始只想做一件事：把模型调用起来。

但很快就会遇到这些现实问题：

- 不同 provider 的请求格式、字段语义和流式行为不一致
- `Chat`、`Responses`、多模态、`Embedding`、`Rerank`、`Realtime` 分散在不同接法里
- 本地工具、`Skill`、`MCP`、RAG、联网增强各自成岛
- 项目后来要接 `Spring Boot`、Agent runtime、Coding Agent、工作流平台时，前一层抽象又不够用了

AI4J 的核心价值，就是把这些原本会在项目中不断分裂的能力，收敛成一条连续的 Java 工程主线。

## 3. 为什么它值得作为 Java AI 基座

### 3.1 它统一的是整层能力，不只是一个模型 SDK

在 `ai4j` 这一层，你可以看到的是一整组基础能力，而不是一堆互不相干的 demo：

- `Chat`
- `Responses`
- 流式输出与多模态
- `Embedding`、`Rerank`
- `Audio`、`Image`、`Realtime`
- `Function Call`
- `Skill`
- `MCP`
- `VectorStore`、`IngestionPipeline`、检索与重排

这意味着项目不会因为“多接一个能力”就被迫切换另一套心智模型。

### 3.2 它有明确的向上演进路径

AI4J 不是“SDK 用一套，Agent 再换一套，CLI 又换一套”。

当前仓库的主线是：

- `ai4j`：基础能力底座
- `ai4j-spring-boot-starter`：容器化接入
- `ai4j-agent`：通用智能体运行时
- `ai4j-coding`：面向代码仓任务的 coding runtime
- `ai4j-cli`：CLI / TUI / ACP 产品壳层
- `ai4j-flowgram-spring-boot-starter`：可视化工作流平台后端

这条链路的好处是：你前面学到的概念不会在下一层全部作废。

### 3.3 它对 Java 现实环境更友好

这个仓库当前强调的是：

- `JDK8+`
- 普通 Java 应用和 Spring 应用都能走
- 多模块 Maven 结构
- 从 SDK 到 starter、runtime、CLI 的统一发布体系

如果你的项目不在“只做最新 Java + 单一框架”的理想条件里，这一点很重要。

### 3.4 它更像工程底座，而不是一次性示例

AI4J 关心的内容不只是“示例能跑”：

- 服务工厂与注册表
- 配置和 provider/profile 治理
- 工具执行与白名单边界
- 会话 memory 与压缩
- RAG 入库与检索链
- trace、runtime、session、审批等工程问题

这也是它更适合长期维护项目、团队协作和架构说明的原因。

## 4. 最重要的边界：不要把三类能力混为一谈

AI4J 很强调这三个概念的边界：

- `Function Call`：本地工具声明与执行，属于 `Core SDK / Tools`
- `Skill`：给模型按需读取的说明、模板和工作流资产，属于 `Core SDK / Skills`
- `MCP`：协议化外部能力接入体系，属于 `Core SDK / MCP`

这三者会协同工作，但不是同一件事。把它们分开讲清楚，是理解 AI4J 的第一道门槛，也是这个项目的一大优点。

## 5. 什么情况下应该选 AI4J

更适合：

- 你要在 Java 中统一接多个模型平台和多种 AI 服务
- 你希望 `Tool`、`Skill`、`MCP`、RAG、Agent 有一条连续的上升路径
- 你后续很可能要做 `Spring Boot` 集成、Agent runtime 或 Coding Agent
- 你需要一套适合自学、面试复述和长期维护的清晰模块结构

不太适合：

- 你只需要一个极薄的 provider wrapper
- 你完全不关心工具、协议扩展、RAG 或上层运行时
- 你希望所有能力都被隐藏在一个高度封装的黑盒里，而不是保留明确分层

## 6. 建议怎么继续读

如果你是第一次接触 AI4J，建议按这个顺序：

1. [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)
2. [Choose Your Path](/docs/start-here/choose-your-path)
3. [Quickstart for Java](/docs/start-here/quickstart-java) 或 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
4. [Core SDK / Overview](/docs/core-sdk/overview)

如果你是为了面试或架构表达，下一页最该看的是 [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)。
