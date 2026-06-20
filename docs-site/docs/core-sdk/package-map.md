# Package Map

这一页讲的是 `ai4j/` 模块的包级心智模型。

它的目标不是列出每一个类，而是先回答三个问题：

- `Core SDK` 的代码在源码里大致怎么分层
- 读源码时应该先看哪些包
- 哪些包是主能力面，哪些更像支撑层

## 1. 先记住源码根路径

`Core SDK` 对应模块是：

- `ai4j/`

主要源码根路径是：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/`

所以这一章讨论的所有包，默认都在这个根路径下面。

## 2. 最值得先看的包簇

在 `ai4j` 模块里，最关键的包更适合按“包簇”理解，而不是只看单个目录名：

- `service` + `service.factory`：统一入口、配置对象、平台枚举、服务工厂与注册表
- `platform`：各 provider 的具体适配实现
- `tool` + `tools`：本地工具声明、桥接、内置工具与执行语义
- `skill`：Skill 描述、发现与加载
- `mcp`：MCP client / gateway / server / transport
- `memory`：基础会话上下文能力
- `rag` + `vector` + `rerank` + `websearch` + `document`：知识增强链

## 3. 支撑层包大致负责什么

除了主能力面，`ai4j` 里还有一批更偏支撑性质的包：

- `config`：平台配置对象
- `network`：HTTP、连接和底层网络能力
- `auth`：鉴权相关能力
- `interceptor`：请求/响应拦截扩展点
- `annotation`：注解式能力暴露
- `listener`：流式或事件监听辅助
- `convert`：对象转换与适配辅助
- `exception`：统一异常定义
- `constant`、`token`：常量与分词/token 相关辅助

这些包很重要，但它们通常不是第一次读源码时的第一落点。

## 4. 每个主包簇应该怎么理解

### 4.1 `service` + `service.factory`

这是 `Core SDK` 的统一入口层。

如果你想先看：

- `Configuration`
- `PlatformType`
- `AiService`
- `AiServiceRegistry`

就先从这里下去。

### 4.2 `platform`

这是 provider 落地层。

它回答的是：

- OpenAI、DashScope、Doubao、Ollama 等平台各自怎么适配
- 哪些能力接口已经有具体实现

### 4.3 `tool` + `tools`

这是本地可调用能力面。

它不只是一个工具列表，而是包含：

- 工具声明
- schema 暴露
- 执行语义
- 工具安全边界

### 4.4 `skill`

这是说明资产层。

它的重点不是执行，而是：

- 发现什么 skill
- 何时加载
- 如何把 `SKILL.md` 这一类资源纳入模型上下文

### 4.5 `mcp`

这是协议化外部能力层。

它和 `tool` 平级，不是 `tool` 的子目录，因为它还涉及：

- transport
- client
- gateway
- server
- tool/resource/prompt exposure

### 4.6 `memory`

这是基座层的基础会话上下文，不是上层 runtime 的完整状态机。

### 4.7 `rag` + `vector` + `rerank` + `websearch` + `document`

这是知识增强主线。

如果你要追整条链，通常会看到：

- 入库与切块
- embedding
- vector store
- rerank
- online search
- citations / trace 相关语义

## 5. 读源码的推荐顺序

如果你要读源码，建议：

1. `service` / `service.factory`
2. `platform`
3. `tool` / `tools`
4. `skill`
5. `mcp`
6. `memory`
7. `rag` / `vector` / `rerank` / `websearch`

这样能先建立“入口层 -> 能力面 -> 知识增强”的主线，不容易把 `Core SDK` 和上层 runtime 混在一起。

## 6. 和上层模块的边界

如果你读着读着开始看到：

- runtime step loop
- subagent / team orchestration
- workspace-aware tools
- CLI / TUI / ACP
- Flowgram 节点图运行

那通常已经不是 `Core SDK` 本层了，而是：

- `ai4j-agent`
- `ai4j-coding`
- `ai4j-cli`
- `ai4j-flowgram-spring-boot-starter`
