# Chat Memory

`ChatMemory` 是基础多轮会话的入口。

## 1. 它适合什么

- 普通聊天
- 基础问答
- 轻量业务会话

## 2. 它不适合什么

- 完整 Agent step loop
- 工具治理状态源
- Coding Agent 长任务会话状态机

## 3. 什么时候升级

如果你已经需要：

- runtime 自己推进下一步
- 工具输出写回状态
- checkpoint / compact / resume

就应该进入 `AgentMemory` 或 `CodingSession` 那一层。
