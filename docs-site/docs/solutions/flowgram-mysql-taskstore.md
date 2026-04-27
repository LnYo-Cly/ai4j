# Flowgram MySQL Task Store

这个方案解决的是“把 Flowgram 从单进程 demo 提升到可持久化的平台后端”。

## 1. 适合什么场景

- Flowgram 平台后端
- 任务执行需要跨进程可见
- 想保留 task result / report / trace projection

如果你只是本地 demo，内存 task store 就够。
如果你要平台化，通常应尽早进入 JDBC task store。

## 2. 核心模块组合

这条方案的主链是：

- `ai4j-flowgram-spring-boot-starter`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`
- `FlowGramRuntimeService`
- `JdbcFlowGramTaskStore`
- `DataSource / MySQL`

重点已经不是“节点怎么跑”，而是“任务生命周期怎样被平台持久化”。

## 3. 这条方案的价值

- 任务状态不再只留在进程内存
- report / result 更适合平台查询
- 更容易支撑多实例或异步任务面板
- 方便做后续运维治理和排障

## 4. 什么时候可以先不做

- 只是跑本地 demo
- 不关心任务恢复与历史结果
- 还没把前后端联调跑通

先把 runtime 主链跑通，再上 JDBC store，通常更稳。

## 5. 先补哪些主线页

1. [Flowgram / Runtime](/docs/flowgram/runtime)
2. [Flowgram / Frontend Backend Integration](/docs/flowgram/frontend-backend-integration)
3. [Spring Boot / Overview](/docs/spring-boot/overview)

## 6. 继续看实现细节

如果你要看：

- `task-store.type=jdbc`
- 自动装配条件
- 建表与字段
- report / result 的持久化语义

继续看深页：

- [旧路径案例页](/docs/guides/flowgram-mysql-taskstore)
