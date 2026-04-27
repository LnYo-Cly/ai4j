# Package Map

这一页讲的是基座层的包级心智模型。

## 1. 最值得先看的包

在 `ai4j` 模块里，最关键的包大致可以按下面理解：

- `service`：统一配置、平台枚举、工厂入口
- `platform`：各 provider 适配实现
- `tool`：工具桥接、built-in tools、执行语义
- `skill`：Skill 描述与发现
- `mcp`：MCP client / gateway / server / transport
- `memory`：基础会话上下文
- `rag` / `vector`：RAG 与向量存储链路

## 2. 阅读顺序

如果你要读源码，建议：

1. `service`
2. `tool`
3. `skill`
4. `mcp`
5. `memory`
6. `rag`

这样不容易把“基础能力”和“上层 runtime”混在一起。
