# Trace

`Trace` 是 `Agent` 章节里把“任务到底怎么跑起来的”解释清楚的关键页面。

## 1. 为什么这一层重要

一旦任务变长、工具变多、编排变复杂，你迟早会遇到这些问题：

- 模型到底什么时候发起了工具调用
- 为什么某一步停止了
- 哪个节点或子任务最慢
- 失败发生在 runtime、tool 还是 orchestration

没有 trace，这些问题只能靠猜。

## 2. Trace 主要回答什么

- 关键事件按什么顺序发生
- runtime 决策怎么被记录
- tool call / tool result 如何被审计
- workflow / subagent / team 过程怎么回放

## 3. 推荐下一步

1. [Agent Architecture](/docs/agent/architecture)
2. [Memory and State](/docs/agent/memory-and-state)
3. [Reference Core Classes](/docs/agent/reference-core-classes)
