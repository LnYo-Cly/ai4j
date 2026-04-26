# Service Entry and Registry

这一页负责回答 `Core SDK` 最核心的工程问题之一：当你真正开始接 provider、切模型、加能力时，代码应该从哪里进入。

## 1. 先记住两层入口

可以先把这一层理解成两层：

- `AiService`：单实例统一入口
- `AiServiceRegistry`：多实例、多 provider、多租户的注册与路由层

如果你只是先把一个模型能力跑起来，通常从 `AiService` 入手；如果你已经进入多环境、多账号或多 provider 调度，再进入 `AiServiceRegistry`。

## 2. 为什么这页很重要

AI4J 的基座不是一组分散的 API，而是试图把模型能力收束到统一服务入口下。

这意味着你不需要分别为：

- `Chat`
- `Responses`
- `Embedding`
- `Rerank`
- `Audio`
- `Image`
- `Realtime`

各写一套完全不同的接入心智。

## 3. 和相邻页面的边界

- `service-entry-and-registry` 讲“从哪里接入能力”
- `model-access` 讲“请求语义和协议族怎么选”
- `tools` / `skills` / `mcp` 讲“模型之外还能接什么能力”
- `extension` 讲“默认入口不够时该沿哪条线扩展”

## 4. 推荐阅读顺序

1. [Model Access](/docs/core-sdk/model-access/overview)
2. [Tools](/docs/core-sdk/tools/overview)
3. [Skills](/docs/core-sdk/skills/overview)
4. [MCP](/docs/core-sdk/mcp/overview)
5. [Extension](/docs/core-sdk/extension/overview)
