# MCP and ACP

`MCP` 和 `ACP` 经常一起出现，但它们解决的是两条完全不同的接入边界。

## 1. `MCP` 解决什么

`MCP` 是模型接外部能力的协议层。

它关注的是：

- 外部工具或服务怎么暴露给模型
- tool schema 怎么传给 runtime
- 调用结果怎么返回

所以它属于“能力来源”这一侧。

## 2. `ACP` 解决什么

`ACP` 是 `Coding Agent` 和宿主之间的集成协议。

它关注的是：

- 会话如何创建、加载、取消
- 事件如何流给 IDE、桌面端或自定义宿主
- 审批如何和宿主交互

所以它属于“产品壳和宿主”这一侧。

## 3. 两者怎么在 Coding Agent 里汇合

可以把它理解成一条链：

```text
MCP server
  -> Coding Agent tool surface
  -> session runtime
  -> ACP host / CLI / TUI
```

也就是说：

- `MCP` 决定模型能接入哪些外部能力
- `ACP` 决定宿主怎样消费 `Coding Agent` 的会话与事件

两者会在同一个系统里出现，但不是同一协议层。

## 4. 推荐下一步

1. [Coding Agent Architecture](/docs/coding-agent/architecture)
2. [CLI / TUI](/docs/coding-agent/cli-and-tui)
3. [Configuration](/docs/coding-agent/configuration)
4. [Command Reference](/docs/coding-agent/command-reference)
