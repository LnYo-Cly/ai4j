# Built-in Nodes

这一页是 `Flowgram` 内置节点的 canonical 入口。

它先回答“当前已经内置了哪些节点、各自负责什么”，再决定你是否需要进入更深的实现细节。

## 1. 当前节点大致分成三类

- 结构节点：`Start`、`End`
- 模型与能力节点：`LLM`、`Tool`、`KnowledgeRetrieve`
- 处理与集成节点：`Variable`、`Code`、`HTTP`

## 2. 什么时候先看这页

适合：

- 先判断现成节点能不能满足需求
- 先决定是否真的需要自定义节点
- 先理解平台后端当前有哪些能力面

## 3. 和相邻页面的边界

- [Runtime](/docs/flowgram/runtime)
  解释节点在什么样的任务执行层里运行
- [Custom Nodes](/docs/flowgram/custom-nodes)
  解释当内置节点不够时该怎么扩展
- [Agent / Tool / Knowledge Integration](/docs/flowgram/agent-tool-knowledge-integration)
  解释这些节点如何与更大的 AI4J 能力体系接上

## 4. 继续深入时该看哪里

如果你下一步想判断“现有节点够不够”，建议继续看：

- [Custom Nodes](/docs/flowgram/custom-nodes)
