# Why Flowgram

`Flowgram` 解决的是“可视化工作流平台”这条线，不是普通 `Agent runtime` 的另一个壳。

## 1. 先把最容易混淆的点说清楚

- `Flowgram.ai` 是字节开源的前端工作流 / 画布库
- AI4J 这一章讲的是围绕它补起来的后端 runtime、任务 API、节点执行、任务存储和前后端对接方式

也就是说，AI4J 讨论的不是“前端画布本身怎么实现”，而是“画布如何接上稳定的 Java 后端执行层”。

## 2. 为什么这条线值得单独存在

有一类任务，用自由推理 Agent 可以做，但并不稳：

- 流程天然是节点图
- 输入输出 schema 很明确
- 需要前端可视化编排
- 需要任务运行状态、报告、取消、恢复这些平台能力

这时你更需要的是：

- 明确节点图
- 稳定任务 API
- 可解释执行链
- 前后端一致的 schema

而不是把所有逻辑继续堆进一个大 prompt。

## 3. 适合什么场景

- 低代码 / 可视化 AI 工作流平台
- 前端会画流程，后端要稳定执行
- 节点天然比自由推理更可控的业务
- 需要 task store、task report、node detail 的平台后端

## 4. 和 Agent 的边界

`Agent` 更适合：

- 自由推理
- runtime 自主决定下一步
- 工具和 handoff 驱动的任务

`Flowgram` 更适合：

- 节点图
- 明确 schema
- 稳定任务 API
- 平台化前后端协作

一句话区分：

- `Agent` 偏“智能体运行时”
- `Flowgram` 偏“工作流平台后端”

## 5. AI4J 在这条线上补了什么

围绕 `Flowgram.ai`，AI4J 主要补了：

- Java 后端 runtime
- Spring Boot starter
- task controller / facade / task store
- 内置节点执行器
- 与 Agent、Tool、KnowledgeRetrieve 的对接

这也是它对 Java 团队最有价值的地方。

## 6. 推荐阅读顺序

1. [Architecture](/docs/flowgram/architecture)
2. [Quickstart](/docs/flowgram/quickstart)
3. [Runtime](/docs/flowgram/runtime)
4. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
5. [Built-in Nodes](/docs/flowgram/built-in-nodes)
6. [Custom Nodes](/docs/flowgram/custom-nodes)

如果你先想看“前端库、starter、runtime、demo 是怎么拼起来的”，下一页看 [Architecture](/docs/flowgram/architecture)。
