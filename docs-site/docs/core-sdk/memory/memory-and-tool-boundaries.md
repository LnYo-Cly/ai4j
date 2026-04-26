# Memory and Tool Boundaries

这一页的重点不是 API，而是边界。

## 1. Memory 边界

- `ChatMemory`：基础会话容器
- `AgentMemory`：runtime 状态源
- `CodingSession`：长期任务会话状态机

## 2. Tool 边界

- `ToolUtil`：基础桥接层
- `AgentToolRegistry / ToolExecutor`：Agent 治理层
- coding built-in tools：workspace-aware 工具层

## 3. MCP 在三层里的角色

- 在基座层：工具来源或协议接入
- 在 Agent 层：registry / executor 的输入之一
- 在 Coding Agent 层：宿主会话里的动态外部能力
