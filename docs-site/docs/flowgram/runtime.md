# Flowgram Runtime

`Runtime` 这一层是 Flowgram 的后端执行真相，不是“controller 后面接个 service”那么简单。

如果要用一句话概括：

> `FlowGramRuntimeService` 把 workflow schema 变成异步任务，把节点图变成有状态执行链，把节点输出变成 report / result / trace 可消费的读侧结构。

## 1. 先抓住 Runtime 的职责边界

Runtime 主要回答 5 个问题：

- schema 怎么校验
- task 怎么创建和结束
- 节点怎么被调度执行
- 节点状态和工作流状态怎么流转
- report / result 最终从哪里来

这里讲的不是前端怎么画图，也不是 starter 怎么暴露 HTTP，而是“后端真正怎样跑起来”。

## 2. 一次任务的真实生命周期

从源码看，一次任务的生命周期是很明确的。

### 2.1 `validateTask(...)`: 只做预检，不创建任务

`validateTask(...)` 会走同一套结构校验逻辑，但不会创建 `taskId`，也不会触发执行。

这对前端产品很关键，因为它允许你在“真正提交任务之前”先发现：

- schema 缺失
- 节点类型不支持
- 边引用不存在
- 必填输入没绑
- 输出引用无效

### 2.2 `runTask(...)`: 创建任务并立即异步返回

`runTask(...)` 的核心流程是：

1. `parseAndValidate(input)`
2. 生成 `taskId`
3. 创建 `TaskRecord`
4. 放入进程内 `ConcurrentMap<String, TaskRecord>`
5. `executorService.submit(...)` 真正执行
6. 立即返回 `FlowGramTaskRunOutput`

这意味着 `runTask(...)` 的语义是“提交任务”，不是“同步完成任务”。

### 2.3 `getTaskReport(...)` / `getTaskResult(...)`: 读侧接口

任务提交后，前端或平台侧会通过：

- `getTaskReport(...)`
- `getTaskResult(...)`

读取执行状态与产出。

当前 report 更偏执行视角，result 更偏最终结果视角，两者都依赖 `TaskRecord` 内的运行态数据。

### 2.4 `cancelTask(...)`: 尽力取消

取消的实现会：

- 设置 `cancelRequested`
- 对对应 `Future` 调 `cancel(true)`

所以它是 best-effort cancel，不是事务式撤销。

## 3. 校验到底校验了什么

Flowgram 的校验不是“JSON 能 parse 就行”，这一点很重要。

### 3.1 图结构约束

`validateGraph(...)` 至少会检查：

- schema 存在且至少包含一个节点
- 根图必须恰好有一个 `Start`
- 根图至少有一个 `End`
- 边的 source / target 节点必须存在
- `LOOP` 节点的 block 子图也会递归校验

### 3.2 节点定义约束

`validateNodeDefinitions(...)` 会继续检查：

- 节点 ID 是否为空
- 节点 ID 是否重复
- 节点类型是否受支持
- 必填输入绑定是否缺失
- `REF` 类型输出引用的节点是否真实存在

### 3.3 为什么这个设计重要

这意味着很多错误会在“提交前”暴露，而不是在运行中才以模糊异常出现。这也是 `validate` 成为正式 API 的原因。

## 4. Runtime 内核真正内建了哪些语义

很多节点能力不是一个层次。

### 4.1 Runtime 原生内建的核心类型

`FlowGramRuntimeService` 直接理解的类型只有：

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

这里的重点是：内核只硬编码控制结构和 LLM 节点，不把所有业务节点都塞进内核。

### 4.2 其它节点靠 `FlowGramNodeExecutor`

像这些能力：

- `HTTP`
- `VARIABLE`
- `CODE`
- `TOOL`
- `KNOWLEDGE`

都不是 runtime 内核原生逻辑，而是通过 `FlowGramNodeExecutor` 扩展进来的。

这是一种很健康的分层：

- graph semantics 留在 runtime
- business capability 留在 executor

### 4.3 状态机也很明确

当前工作流和节点状态主要围绕这 5 个值流转：

- `pending`
- `processing`
- `success`
- `failed`
- `canceled`

这组状态对 report、前端高亮和 trace 事件都很重要。

## 5. 节点执行是怎样推进的

看 `executeTask(...)` 和 `executeFromNode(...)` 就能知道当前执行模型。

### 5.1 从 `Start` 开始递归推进

任务进入执行后，runtime 会：

1. 把 workflow 状态改成 `processing`
2. 发布 `TASK_STARTED`
3. 从解析出来的 `Start` 节点进入 `executeFromNode(...)`
4. 节点执行完成后，根据边选择后继节点继续递归

### 5.2 运行时会做环检测

`executeFromNode(...)` 维护了一个 `activePath`。当前路径里如果再次遇到同一个节点，会直接报：

- `Cycle detected in FlowGram graph at node ...`

这说明当前执行模型默认不接受任意环图。

### 5.3 没有走到 `End` 会被当成失败

如果整条执行链结束后仍然没有产出最终结果，runtime 会抛：

- `FlowGram workflow finished without reaching an End node`

这不是小细节，而是它把 `End` 当成正式结束语义的一部分。

## 6. LLM 节点的真实执行语义

`Ai4jFlowGramLlmNodeRunner` 决定了 Flowgram 如何复用 Agent 基座。

### 6.1 它会临时构造一个 Agent

每次执行 LLM 节点，runner 都会：

- 解析模型 client
- 解析 model 名称
- 解析 prompt
- 用 `AgentBuilder` 构造 Agent
- 运行一次 `agent.run(...)`

### 6.2 默认不是“自由多步思考”

默认选项是：

- runtime: `ReActRuntime`
- `maxSteps(1)`
- `stream(false)`

所以这里更像“单节点智能步骤”，而不是一个无边界小 Agent。

### 6.3 输入字段有兼容别名

模型字段支持：

- `modelName`
- `model`
- `modelId`

prompt 字段支持：

- `prompt`
- `message`
- `input`

这种兼容让前端节点表单和后端执行器之间的耦合更低。

### 6.4 输出不只是文本

LLM 节点最终会返回：

- `result`
- `outputText`
- `rawResponse`
- `metrics`

如果配置了 `TracePricingResolver`，`metrics` 还会带 token 和 cost 信息。

## 7. report / result / task store 之间的关系

这是最容易被误解的地方。

### 7.1 运行态真相在 `TaskRecord`

`FlowGramRuntimeService` 内部用 `ConcurrentMap<String, TaskRecord>` 保存任务运行态。当前 report 和 result 都优先读这里。

### 7.2 starter 的 `FlowGramTaskStore` 是补充层

`FlowGramRuntimeFacade` 会把状态和结果快照写到 `FlowGramTaskStore`，但这不等于 runtime 已经变成可恢复执行引擎。

更准确的理解是：

- runtime 保存活的执行态
- task store 保存平台需要的元数据和快照

### 7.3 这对平台的意义

这套结构已经足够支持：

- 任务中心
- 详情页
- trace 面板
- 基础持久化

但如果你要的是“跨进程恢复原任务执行态”，当前实现还没有走到那一步。

## 8. 默认线程模型和它的含义

Runtime 默认用内部 `ExecutorService` 异步执行任务。

### 8.1 默认是 cached thread pool

内部线程名形如：

- `ai4j-flowgram-1`
- `ai4j-flowgram-2`

这很适合 demo 和轻量平台，但也意味着线程治理、限流、资源隔离仍需要你在平台层继续建设。

### 8.2 取消依赖线程中断

由于取消通过 `future.cancel(true)` 和中断传播完成，长时间阻塞的自定义 executor 是否能及时响应，取决于 executor 自己的实现质量。

## 9. 扩展 Runtime 最关键的入口

如果你要扩展这层，不要先改 controller，先看这些扩展点：

- `FlowGramNodeExecutor`
- `FlowGramLlmNodeRunner`
- `FlowGramRuntimeListener`

分别对应：

- 新节点能力
- LLM 节点策略
- 运行事件观测

这也是为什么 Runtime 是整套子系统真正的内核。

## 10. 当前边界

Runtime 当前非常清晰，但也有明确边界：

- 不默认提供 durable distributed scheduler
- 不默认提供实时推送式进度通道
- 不默认提供强权限模型
- 不鼓励把复杂业务逻辑继续塞回 LLM 节点

如果你理解了这些边界，就能更准确地判断什么时候该继续做平台层，什么时候该去写节点 executor。
