---
sidebar_position: 4
---

# Tool 暴露语义与安全边界

这页回答一个你非常关心的问题：

> `getAllTools(...)` 到底应该是“传什么用什么”，还是“自动全量注入”？

AI4J 当前语义已经统一为：**传什么，用什么**。

## 1. 关键方法语义

### `ToolUtil.getAllTools(functionList, mcpServerIds)`

- 只合并你显式传入的 function 与 mcp 服务
- 不自动注入全部本地 MCP 工具

### `ToolUtil.getLocalMcpTools()`

- 返回本地 MCP tool 缓存
- 主要用于 MCP Server 对外暴露本地能力

## 2. 为什么这么设计

如果默认全量注入，会出现以下风险：

- Agent 在不知情情况下获得额外高权限工具
- 提示词越狱时攻击面扩大
- 调试时难以判断工具来源

显式传入语义可以把风险收敛到调用点。

## 3. 推荐使用模式

### 业务 Agent

```java
.toolRegistry(Arrays.asList("queryWeather", "queryStock"), Arrays.asList("github-service"))
```

仅暴露本场景需要的工具。

### MCP Server 构建

在 server 构建流程里使用 `getLocalMcpTools()`，暴露你希望对外发布的本地工具。

## 4. 代码审查检查点

- 是否误把 `getLocalMcpTools()` 用在普通 agent 场景
- 是否把高风险工具误加入默认注册列表
- 是否存在“工具名冲突导致的错误调用”

## 5. 安全基线建议

1. 工具白名单优先，不依赖黑名单。
2. 高风险工具单独二次确认。
3. 工具参数做强校验，禁止原样透传外部输入。
4. 工具调用全链路打 trace，便于审计。
