# Transport Types

这一页只讲 transport 选型。MCP 的 transport 不决定 capability 语义，但决定了**部署方式、连接模型、生命周期和故障形态**。

## 1. AI4J 当前显式支持什么

`McpTypeSupport` 会把 transport 统一归一化为三类：

- `stdio`
- `sse`
- `streamable_http`

并兼容旧写法，例如：

- `process` / `local` -> `stdio`
- `event-stream` -> `sse`
- `http` / `streamable-http` -> `streamable_http`

这说明 transport 在 AI4J 里不是随便传个字符串，而是正式进入类型规范化体系。

## 2. 三类 transport 的真实差异

### `stdio`

更像：

- 本地子进程服务
- 同机部署
- 宿主负责启动和关闭

适合本地工具进程、桌面工具链、仓库内辅助服务。

### `sse`

更像：

- 已服务化的远端事件流服务
- 长连接
- 数据持续推送

适合需要持续事件回传的远端 MCP 场景。

### `streamable_http`

更像：

- 正式 HTTP 服务发布
- 更贴近常规服务基础设施
- 但仍保留流式能力

适合云端服务化和标准网关环境。

## 3. 为什么 transport 不是小事

transport 会直接影响：

- 服务谁来启动
- 谁来重连
- 调用是在本地进程内还是远端网络
- 日志和故障应该按什么心智理解

所以 transport 不是 capability 本身，但在工程上是一级决策。

## 4. 设计摘要

> AI4J 把 MCP transport 统一成 `stdio / sse / streamable_http` 三类，并通过 `McpTypeSupport` 做归一化。这样 transport 从“随便填个字符串”升级成正式的部署与连接模型选择，而不是 capability 的附属细节。

## 5. 选型时应该先看什么

实际选 transport 时，建议优先判断：

1. 服务是在本机进程里，还是远端网络里
2. 宿主是否负责启动和关闭服务
3. 连接失败后的重试、重连和观察方式是什么
4. 部署环境更适合子进程模式，还是标准 HTTP 服务模式

先把这四件事定下来，再谈 capability 暴露，架构会稳定很多。

## 6. 关键对象

这页最核心的实现锚点其实很少：

- `mcp/util/McpTypeSupport.java`
- `McpConfig` 中的 transport 相关配置
- `McpServerReference` 里的服务定义

它们共同决定 transport 在 AI4J 中如何被规范化和消费。

## 7. 继续阅读

- [MCP / Client Integration](/docs/core-sdk/mcp/client-integration)
- [MCP / Third-party MCP Integration](/docs/core-sdk/mcp/third-party-mcp-integration)
