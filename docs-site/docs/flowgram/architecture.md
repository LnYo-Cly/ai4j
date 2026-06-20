# Flowgram Architecture

`Flowgram` 的架构重点，不是单讲 `Flowgram.ai` 前端库本身，而是讲“前端画布 + AI4J 后端执行层”如何组成一个真正可运行的工作流平台。

如果你只看 demo，会觉得这是一组 REST API；如果你沿着源码看，会发现它其实拆成了 4 个很明确的层次。

## 1. 先抓住分层

```text
Flowgram.ai canvas / editor
  -> webapp runtime adapter
  -> Spring Boot task API + facade
  -> FlowGramRuntimeService
  -> built-in graph logic + node executors + LLM node runner
  -> task store / trace projection / result snapshot
```

把这条链再展开，可以看到每层责任都不一样。

### 1.1 画布层

- `Flowgram.ai`
- `ai4j-flowgram-webapp-demo/`

职责：

- 提供编辑器和运行时 UI
- 管理节点表单
- 组织 workflow JSON
- 发起 validate / run / report / result / cancel

### 1.2 适配层

关键文件：

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`
- `ai4j-flowgram-webapp-demo/src/plugins/runtime-plugin/runtime-service/index.ts`

职责：

- 把编辑态工作流压缩成后端执行态工作流
- 屏蔽 UI-only 节点
- 负责任务轮询与前端状态同步

### 1.3 平台接入层

- `ai4j-flowgram-spring-boot-starter/`

关键类：

- `FlowGramAutoConfiguration`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`

职责：

- 自动装配 runtime
- 注册节点执行器
- 暴露 REST API
- 接 caller、权限、ownership、task store、trace 输出

### 1.4 执行引擎层

- `ai4j-agent/.../flowgram/FlowGramRuntimeService`
- `Ai4jFlowGramLlmNodeRunner`

职责：

- 解析 schema
- 校验图结构
- 创建和维护 task record
- 调度节点执行
- 组装 report / result

## 2. 编辑态 schema 和执行态 schema 不是一回事

这一点非常关键，也是 Flowgram 体系比“前端直接把 JSON 发给后端”更成熟的地方。

### 2.1 前端会先剥离 UI-only 节点

`backend-workflow.ts` 明确过滤掉：

- `Comment`
- `Group`
- `BlockStart`
- `BlockEnd`

这些元素在画布上有意义，但在后端执行层没有语义。

### 2.2 类型会被映射成后端协议

当前映射至少包括：

- `start -> START`
- `end -> END`
- `llm -> LLM`
- `http -> HTTP`
- `code -> CODE`
- `condition -> CONDITION`
- `loop -> LOOP`
- `variable -> VARIABLE`
- `tool -> TOOL`
- `knowledge -> KNOWLEDGE`

这说明后端根本不依赖前端内部显示名，而是依赖一套独立的执行类型。

### 2.3 后果是什么

后果非常直接：

- 你可以继续演进画布展示层
- 但后端 contract 不应该跟着 UI 文案一起漂移

也因此，自定义节点的真正边界从来不是“前端画出来了没有”，而是“前后端对执行类型和输入输出协议是否对齐”。

## 3. Runtime 内核的真实职责

`FlowGramRuntimeService` 是整套执行链的核心。

### 3.1 `runTask(...)` 做的不是简单转发

它会：

1. 解析和校验 schema
2. 创建 `taskId`
3. 构造 `TaskRecord`
4. 放进进程内 `ConcurrentMap<String, TaskRecord>`
5. 把执行逻辑提交给内部 `ExecutorService`
6. 立即返回 `taskId`

这说明它从设计上就是异步任务模型。

### 3.2 校验并不只是“能不能 parse JSON”

从 `validateGraph(...)`、`validateNodeDefinitions(...)` 可以直接看到它会检查：

- schema 是否存在
- workflow 是否至少有一个节点
- 根图是否恰好一个 `Start`
- 根图是否至少一个 `End`
- 边引用的 source / target 节点是否存在
- 节点 ID 是否重复
- 节点类型是否受支持
- 必填输入绑定是否缺失
- 输出引用的节点是否存在

这就是为什么前端先调 `/validate` 是合理设计，不是多余步骤。

### 3.3 Runtime 原生只内建了“控制结构”

`FlowGramRuntimeService` 内核直接理解的类型只有：

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

这是一种很清晰的设计取舍：

- 控制流语义留在 runtime 内核
- 业务能力通过 executor 扩展

这比把所有节点都塞进一个巨大的 switch 更可维护。

### 3.4 其它业务节点靠注册式 executor

starter 默认会注册：

- `FlowGramHttpNodeExecutor`
- `FlowGramVariableNodeExecutor`
- `FlowGramCodeNodeExecutor`
- `FlowGramToolNodeExecutor`

并且在存在 `AiServiceRegistry` 与单一 `VectorStore` 时，才会注册：

- `FlowGramKnowledgeRetrieveNodeExecutor`

这意味着“内置节点”本身也是有条件能力，不是所有环境下都一模一样。

### 3.5 图执行是递归推进的

`executeFromNode(...)` 会：

- 执行当前节点
- 选出下一批边
- 递归进入后继节点

同时用 `activePath` 检测当前路径上的重复节点，一旦重复会直接抛出：

- `Cycle detected in FlowGram graph at node ...`

这说明当前实现更像显式 DAG / block graph 执行，而不是容忍任意环图的通用流程引擎。

## 4. LLM 节点并不是一个“直接调模型”的特例

`Ai4jFlowGramLlmNodeRunner` 很值得单独看，因为它体现了 Flowgram 和 Agent 的真实关系。

### 4.1 它每次运行都会构造一个 Agent

核心行为是：

- 解析节点输入里的模型名
- 解析 prompt
- 用 `AgentBuilder` 动态创建 Agent
- 执行 `agent.run(...)`
- 把结果和 metrics 写回节点输出

### 4.2 默认执行策略很保守

默认配置是：

- runtime: `ReActRuntime`
- `maxSteps(1)`
- `stream(false)`

这说明 Flowgram 里的 LLM 节点默认不是“可无限思考的小 Agent”，而是“单节点一次完成的智能步骤”。

### 4.3 输入协议也被做了兼容

它会从下面这些字段里取值：

- 模型名：`modelName` / `model` / `modelId`
- prompt：`prompt` / `message` / `input`

这样可以降低前端节点定义和后端执行器之间的耦合度。

### 4.4 metrics 不只是耗时

如果接了 `TracePricingResolver`，LLM 节点还能输出：

- token 使用量
- input / output / total cost
- currency

这对任务详情页和成本面板非常有价值。

## 5. Spring Boot 平台层真正做了什么

很多人会把 starter 想成“只是帮你 new 了几个对象”。实际不是。

### 5.1 `FlowGramAutoConfiguration` 决定系统默认形态

它负责：

- 选择 task store 类型
- 创建 caller resolver
- 创建 access checker
- 创建 ownership strategy
- 创建 protocol adapter
- 创建 LLM node runner
- 创建 runtime service
- 注册默认 node executor 和 runtime listener

也就是说，starter 决定了“这套系统默认怎样运行”。

### 5.2 `FlowGramTaskController` 只暴露控制面

Controller 默认挂在：

- `${ai4j.flowgram.api.base-path:/flowgram}`

并提供 5 个标准入口：

- `POST /tasks/run`
- `POST /tasks/validate`
- `GET /tasks/{taskId}/report`
- `GET /tasks/{taskId}/result`
- `POST /tasks/{taskId}/cancel`

它本身很薄，真正的平台判断都下沉到了 facade。

### 5.3 `FlowGramRuntimeFacade` 是平台治理收口点

Facade 在 `run / validate / report / result / cancel` 里负责：

- 解析 caller
- 执行权限判断
- 创建 ownership
- 读写 task store
- 决定 trace 是否返回
- 把 runtime 输出投影成对前端更友好的响应

如果你后面要接权限、审计、租户、任务中心，这里是第一入口。

## 6. 存储模型的真实边界

这是最容易被误判的一部分。

### 6.1 运行态真相在 runtime 内存里

`FlowGramRuntimeService` 内部有：

```java
private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<String, TaskRecord>();
```

`report(...)` 和 `result(...)` 首先读的就是这份进程内状态。

### 6.2 `FlowGramTaskStore` 不是完整的 durable execution store

当前 `FlowGramTaskStore` 只定义了：

- `save`
- `find`
- `updateState`

默认实现有：

- `InMemoryFlowGramTaskStore`
- `JdbcFlowGramTaskStore`

它们负责的是：

- ownership 元数据
- 任务状态快照
- result snapshot

但当前 `report` / `result` 的第一真相仍然来自 runtime。换句话说，JDBC store 更像“平台记录层”，不是“完整可恢复的执行内核”。

### 6.3 这带来的架构含义

这套设计非常适合：

- 单体后端
- demo / staging
- 平台原型
- 需要查询任务状态但不要求分布式恢复的场景

但如果你的目标是“重启后无缝恢复执行态”，还需要继续演进。

## 7. 安全和租户边界的默认值

默认安全姿态必须说清楚，否则很容易高估这套 starter 的完成度。

### 7.1 Caller 默认可以是匿名

`DefaultFlowGramCallerResolver` 在 `auth.enabled = false` 时会直接返回匿名 caller。

启用 auth 后，它默认从：

- `Authorization`
- `X-Tenant-Id`

读取 caller 和 tenant 信息。

### 7.2 Access checker 默认永远放行

`DefaultFlowGramAccessChecker.isAllowed(...)` 直接返回 `true`。

这说明当前 starter 的默认定位是：

- 先把平台接起来
- 安全策略通过替换 bean 自行加固

### 7.3 Ownership 是有抽象层的

`FlowGramTaskOwnershipStrategy` 负责创建 ownership。默认实现会写入：

- `creatorId`
- `tenantId`
- `createdAt`
- `expiresAt`

因此权限治理虽然默认很轻，但扩展点已经留出来了。

## 8. 失败路径和调试意义

这一层如果不写清楚，文档就会只剩流程图。

### 8.1 提交前失败

常见于：

- schema 缺失
- JSON 解析失败
- 多个 `Start`
- 没有 `End`
- 节点引用不存在
- 必填字段没绑
- 节点类型没注册

这类问题应主要通过 `/validate` 暴露。

### 8.2 执行中失败

常见于：

- LLM 节点缺少模型名或 prompt
- 自定义 executor 抛异常
- 图执行未走到 `End`
- 运行期触发 cycle 检测

这类问题会体现在 node status、workflow status、error 字段和 trace 事件里。

### 8.3 取消是 best-effort

`cancelTask(...)` 的实现会：

- 标记 `cancelRequested`
- 调 `future.cancel(true)`

这意味着取消语义是尽力而为，而不是事务式回滚。

## 9. 关键扩展点

如果你要把这套架构继续做成平台，最重要的扩展点是这些：

- `FlowGramNodeExecutor`
- `FlowGramLlmNodeRunner`
- `FlowGramRuntimeListener`
- `FlowGramTaskStore`
- `FlowGramCallerResolver`
- `FlowGramAccessChecker`
- `FlowGramTaskOwnershipStrategy`

它们分别对应：

- 节点能力
- LLM 节点策略
- 事件监听
- 状态落库
- 身份解析
- 权限判定
- 任务归属与保留策略

## 10. 这一章之后怎么继续

建议按这个顺序往下走：

1. [Runtime](/docs/flowgram/runtime)
2. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
3. [Built-in Nodes](/docs/flowgram/built-in-nodes)
4. [Custom Nodes](/docs/flowgram/custom-nodes)

如果你只记住一个架构结论：

Flowgram 的核心不是“前端画布”，而是“把画布产出的图，落成一个有正式任务生命周期、有节点执行 contract、可接平台治理的后端执行系统”。
