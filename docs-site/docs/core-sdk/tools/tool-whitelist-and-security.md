# Tool Whitelist and Security

工具接入里最重要的原则不是“能不能调”，而是“默认暴露哪些”。AI4J 在基座层采用的是显式白名单思路，这一点必须讲透。

## 1. 当前默认语义

Core SDK 不会把所有可发现工具自动暴露给模型。实际暴露面取决于你在请求里显式传入的：

- `functions(...)`
- `mcpServices(...)`

随后 `ToolUtil.getAllTools(...)` 只解析这些名字对应的能力。

这代表 AI4J 现在的默认安全心智是：

- 默认不开放
- 显式选择才开放

## 2. 为什么这比“自动暴露全部工具”更重要

因为真实工具面里经常混着高风险能力：

- 文件读取
- 文件写入
- shell 执行
- 第三方 MCP 服务

如果默认全开，模型第一时间看到的就不是“完成任务的最小工具集”，而是一个过大的攻击面和误用面。

## 3. 源码入口

- `ToolUtil#getAllTools(...)`
- `ToolUtil#getGlobalMcpTools(...)`
- `ToolUtil#getUserMcpTools(...)`
- `Skills#createToolContext(...)`

尤其是 `createToolContext(...)` 这一步很关键，它说明 even `read_file` 这种能力也不是默认任意目录可读，而是和 skill 只读根一起被约束。

## 4. 本地 Tool 和 MCP Tool 的安全面为什么不同

### 本地 Tool 的主要风险

- 本地文件系统
- 本地进程执行
- 宿主环境泄露

### MCP Tool 的主要风险

- 外部账号权限
- 远端副作用操作
- 多服务可见性失控

所以“工具安全”不能只看本地注解函数。MCP 同样必须按服务维度做白名单。

## 5. Core SDK 做到了哪一步

Core SDK 目前已经做到了：

- 工具按白名单暴露
- MCP 服务按白名单暴露
- skill 只读根进入 `BuiltInToolContext`
- provider 请求层支持 `toolChoice` / `parallelToolCalls`

但它没有直接做：

- 人机审批
- 多租户权限判定
- 高风险动作的业务审计

这些必须由更上层 runtime 或业务宿主承担。

## 6. 设计摘要

AI4J 的工具安全第一原则是“显式暴露，不默认全开”。本地 Tool 和 MCP Tool 都要先经过白名单，再进入 provider 请求；审批、权限和审计则保持在模型外完成，因此 Core SDK 负责的是安全边界的第一层，而不是整套治理闭环。

## 7. 继续阅读

- [MCP / Tool Exposure Semantics](/docs/core-sdk/mcp/tool-exposure-semantics)
- [Memory / Memory and Tool Boundaries](/docs/core-sdk/memory/memory-and-tool-boundaries)
