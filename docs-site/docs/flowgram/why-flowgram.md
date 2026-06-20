# Why Flowgram

`Flowgram` 值得单独存在，不是因为“可视化工作流”这个词听起来更完整，而是因为前端画布产品对后端的要求，和普通 `Agent` 运行时不是同一个问题。

如果只靠 Agent，你可以做出能跑的智能体；如果要做一套能交给前端画布、平台控制面和任务中心消费的后端，你还缺很多结构化能力。

## 1. Agent 解决不了哪些平台问题

自由推理 Agent 擅长的是：

- 根据目标自己决定下一步
- 在工具之间做策略选择
- 通过 memory、handoff、tool use 完成开放任务

但当前端是 `Flowgram.ai` 这样的工作流画布时，平台真正需要的通常是下面这些东西：

- 前后端统一的 workflow schema
- 一个正式的 `validate` 入口，而不是“跑一下看报错”
- 一个任务 ID，可被 report / result / cancel 查询
- 节点级状态，而不是只拿最终文本输出
- 能和任务中心、权限、审计、trace 面板对接的控制面

这些能力如果继续堆在 prompt 或 session 上，产品边界会很模糊。

## 2. Flowgram 真正补上的是什么

从源码看，AI4J Flowgram 补上的不是“图形界面”，而是 4 类后端结构。

### 2.1 明确的工作流契约

前端会先把画布数据归一化，再发给后端执行。

`backend-workflow.ts` 会剥离掉只属于 UI 的节点，并把前端类型映射成后端可执行类型。结果是：

- 画布可以继续有注释、分组、块边界
- 后端只接收可执行图

这让“编辑模型”和“执行模型”不再混在一起。

### 2.2 正式的任务生命周期

`FlowGramTaskController` 暴露的不是单一 `invoke`，而是一整组生命周期 API：

- `run`
- `validate`
- `report`
- `result`
- `cancel`

这意味着前端、测试脚本、平台宿主都能围绕同一任务协议工作。

### 2.3 节点级执行边界

在 Flowgram 里，流程图不是装饰，而是实际控制执行路径的结构。

`FlowGramRuntimeService` 原生支持：

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

而 `HTTP`、`VARIABLE`、`CODE`、`TOOL`、`KNOWLEDGE` 之类能力则通过 `FlowGramNodeExecutor` 注入。这让“流程控制”和“业务能力”天然分层。

### 2.4 面向平台的读侧输出

前端要的不只是“最终结果”，还要知道：

- 当前任务是不是还在跑
- 哪个节点失败了
- 节点耗时是多少
- trace 中发生了哪些事件

`report` / `result` / `trace` 就是为这种平台读侧设计的，而不是为 prompt 设计的。

## 3. 为什么前端画布场景尤其需要它

当系统前面站着的是流程画布，而不是只写代码的后端工程师，后端接口的抽象方式会发生变化。

### 3.1 用户先看到的是图，不是调用栈

在工作流平台里，用户先理解的是：

- 节点
- 边
- 输入输出
- 当前高亮到哪里

这就要求后端也以节点和任务为主语，而不是只返回“模型回答了什么”。

### 3.2 平台需要预检，而不是边跑边试

`WorkflowRuntimeService` 会先调 `/tasks/validate`，通过后再调 `/tasks/run`。

这不是多余步骤，而是画布产品的正常需求：

- 表单要先发现缺失字段
- 非法节点引用要先报出来
- unsupported type 要在提交前阻断

否则前端只能把运行失败当成验证失败，排障体验会很差。

### 3.3 平台需要取消、结果轮询和详情页

普通 Agent demo 很容易忽略“长任务过程中 UI 怎么反馈”。Flowgram 默认就按这类场景设计：

- `run` 立即返回 `taskId`
- 前端轮询 `report`
- 任务结束后再取 `result`
- 需要时可以 `cancel`

这套模型很适合画布、任务中心和控制台。

## 4. 它和 Agent 的差别不是“能不能用模型”

两者都能用模型，但组织模型的方式不同。

| 维度 | Agent | Flowgram |
| --- | --- | --- |
| 主控制逻辑 | 模型循环决定下一步 | 图结构决定下一步 |
| 核心契约 | request / session / tools | workflow schema / task API / node contract |
| 读侧重点 | output / trace / messages | task status / node status / report / result / trace |
| 扩展方式 | 工具、memory、handoff、runtime | node executor、task store、access checker、runtime listener |
| 最适合的任务 | 开放式推理 | 显式流程、画布平台、稳定 schema |

最关键的一点是：Flowgram 没有抛弃 Agent，而是把 Agent 限定在单个 LLM 节点里用。

`Ai4jFlowGramLlmNodeRunner` 默认会构造一个 `maxSteps(1)` 的 Agent，这代表它复用了推理基座，但没有把整张图重新交还给自由推理。

## 5. 它带来的收益是什么

### 5.1 更稳定的前后端契约

前端画布只要围绕 task API 和 schema 对齐，就能和后端长期协作，不需要把某个 prompt 细节写死在前端。

### 5.2 更容易定位问题

当前实现里，失败通常可以归入几类非常明确的边界：

- schema 解析失败
- 节点类型不支持
- 必填输入缺失
- 节点输出引用不存在
- LLM 节点缺模型名或 prompt
- 图里有环或没有走到 `End`

这种错误比“智能体没答对”更可诊断。

### 5.3 更容易接平台治理

`FlowGramRuntimeFacade` 已经把这些治理点抽出来了：

- caller 解析
- access check
- task ownership
- task store
- trace response enrichment

这说明它天然面向“要被平台接走”的后端，而不是只服务一个 demo。

## 6. 它没有解决什么

这一点同样重要。当前实现不是一个完整的分布式工作流引擎。

### 6.1 运行态真相仍然在进程内

`FlowGramRuntimeService` 把 `TaskRecord` 放在进程内 `ConcurrentMap` 里。`FlowGramTaskStore` 会记录 ownership 和结果快照，但 `report` / `result` 读取的第一真相仍然来自 runtime 内存。

这带来的直接后果是：

- 它适合作为平台后端原型和单体服务
- 但不是“重启后还能无缝恢复执行态”的 durable scheduler

### 6.2 默认安全模型是开放的

默认情况下：

- `auth.enabled = false`
- `DefaultFlowGramCallerResolver` 返回匿名 caller
- `DefaultFlowGramAccessChecker` 永远放行

这符合 demo 和内网集成的起点，但不应该被误解成已经完成了多租户权限治理。

### 6.3 默认是轮询模型，不是实时流推送

前端 runtime 当前通过固定间隔轮询 report。`streamProgress` 配置默认也是 `false`。如果你要做高实时、多会话、大规模控制台，需要自己继续演进推送链路。

## 7. 什么时候应该选它

优先考虑 Flowgram 的场景：

- 任务天然就是流程图
- 前端会有真实的可视化画布
- 节点输入输出要长期稳定
- 你希望后端暴露正式任务 API，而不是只有 SDK 调用

不应优先考虑 Flowgram 的场景：

- 需求本质是开放式多步推理
- 没有画布，也不需要任务控制面
- 只是想快速写一个模型调用样例

## 8. 推荐接下来的阅读顺序

1. [Architecture](/docs/flowgram/architecture)
2. [Runtime](/docs/flowgram/runtime)
3. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
4. [Built-in Nodes](/docs/flowgram/built-in-nodes)
5. [Custom Nodes](/docs/flowgram/custom-nodes)

如果你只记一个结论：

`Flowgram` 的价值，不在于把流程画出来，而在于把“画出来的流程”变成一个有正式契约、有控制面、可治理、可扩展的后端执行系统。
