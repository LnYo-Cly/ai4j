# 2026-03-27 FlowGram Spring Boot 集成实施计划

- 状态：Draft
- 优先级：P0
- 依赖设计：`docs/plans/2026-03-27-flowgram-spring-boot-integration-design.md`
- 目标模块：`ai4j-agent`、`ai4j-flowgram-spring-boot-starter`、`ai4j-flowgram-demo`、`ai4j-bom`

## 1. 实施目标

将现有 `ai4j-agent` 中的 FlowGram runtime 封装成可被第三方 Spring Boot 应用直接依赖的 starter，并保证：

- starter 对外暴露稳定 HTTP contract，而不是直接泄漏 kernel DTO
- 默认 LLM 节点可直接复用现有 `AiServiceRegistry`，不要求业务侧手写 `AgentModelClient` Bean
- `ai4j-agent` 当前 FlowGram 行为不回归，JDK 8 兼容性不被破坏
- MVP 先立住 REST + polling，再补 SSE 与持久化

## 2. 实施原则

- 先补 kernel 前置钩子，再做 web adapter，避免 starter 后期返工
- `FlowGramTaskStore` 在 P0/P1 只承担 adapter 元数据存储，不承诺跨进程恢复运行中任务
- 默认 LLM runner 必须有明确的 service 解析规则，不能依赖隐式 Bean 猜测
- starter 作为正式发布物进入 reactor、BOM 和 release profile；demo 仅作为验证应用，不进入 BOM
- 延续现有 Spring Boot 兼容策略，同时提供 `spring.factories` 与 `AutoConfiguration.imports`

## 3. 当前代码基线

当前仓库已经具备下面这些基础能力：

- `FlowGramRuntimeService` 已支持 `runTask`、`validateTask`、`getTaskReport`、`getTaskResult`、`cancelTask` 与自定义节点注册
- `Ai4jFlowGramLlmNodeRunner` 已能通过 `AgentModelClient` 驱动 LLM 节点，但 starter 侧还缺少与 `AiServiceRegistry` 对接的默认解析方案
- 现有 `ai4j-spring-boot-starter` 已验证 Boot 2/3 双注册方式，可以复用相同的自动配置装配策略
- `AiServiceRegistry` 已能解析 `IChatService` 与 `IResponsesService`，可作为默认 FlowGram LLM runner 的后端服务来源

基线验证已完成：

- `cmd /c "mvn -pl ai4j-agent -am -Dtest=FlowGramRuntimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test"`
- 结果：`FlowGramRuntimeServiceTest` 6 个用例全部通过

## 4. 阶段拆分

### Phase 0a：Kernel 前置能力

目标：

- 给 adapter 暴露 workflow 级失败原因，避免 controller/facade 依赖 runtime 私有状态
- 给后续 SSE 引入 task/node 生命周期监听钩子
- 保持现有 FlowGram 测试全绿

主要改动：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/flowgram/FlowGramRuntimeService.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/flowgram/model/FlowGramTaskReportOutput.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/flowgram/model/FlowGramTaskResultOutput.java`
- 推荐新增 runtime listener/event 抽象，放在 `io.github.lnyocly.ai4j.agent.flowgram`

验收：

- facade 可以读取 workflow 级失败原因，而不访问 runtime 私有字段
- runtime 可以按顺序发出 task/node 生命周期回调
- `FlowGramRuntimeServiceTest` 继续通过，并新增 listener/failure 定向测试

### Phase 0b：Starter MVP

目标：

- 创建正式 starter 模块与可运行 demo 模块
- 暴露 REST API 和 polling 主路径
- 自动注册 Spring Bean 形式的自定义节点
- 提供 permissive 默认 auth/access hook

主要改动：

- 根 `pom.xml`
- `ai4j-bom/pom.xml`
- 新增 `ai4j-flowgram-spring-boot-starter/pom.xml`
- 新增 `ai4j-flowgram-demo/pom.xml`
- starter 下的 `autoconfigure`、`config`、`controller`、`dto`、`adapter`、`exception`、`security`、`support`

验收：

- 第三方 Spring Boot 项目仅增加 starter 依赖和最小配置即可启动
- `run/validate/report/result/cancel` 接口可用
- 自定义 `FlowGramNodeExecutor` Spring Bean 会自动注册进 `FlowGramRuntimeService`
- demo 应用可以用 polling 路径完成前后端联通验证

### Phase 0c：默认 LLM 服务解析

目标：

- 让 starter 默认 `FlowGramLlmNodeRunner` 能直接复用现有多服务配置
- 明确 service 选择与协议选择，不把歧义留到联调阶段

推荐决策：

- service id 解析顺序：
  - 节点输入中的 `serviceId`
  - 节点输入中的 `aiServiceId`
  - starter 属性 `ai4j.flowgram.default-service-id`
- P0 默认协议使用 `chat`
- 若后续显式启用 `responses`，再基于同一 `AiServiceRegistry` 构造 `ResponsesModelClient`
- `modelName/model/modelId` 仍保持为节点输入语义，不挪到 starter 全局配置中

主要改动：

- starter 中新增 registry-backed resolver / runner factory
- `FlowGramProperties` 增加默认 service 配置
- 新增 service 解析与错误映射测试

验收：

- 业务方不需要额外声明 `AgentModelClient` Bean
- service id 缺失、未知或协议不匹配时会 fail fast，并返回稳定错误结构

### Phase 1：协议硬化

目标：

- 冻结对外 DTO/status/error 合约
- 增加健康检查与 CORS 支持

主要改动：

- request/response DTO
- `FlowGramProtocolAdapter`
- `@ControllerAdvice` 异常映射
- `/flowgram/health`

验收：

- controller 不再返回 kernel DTO
- 状态值、错误码、错误结构在成功/失败/取消/不存在场景下保持一致
- 前端集成方可以只依赖文档约定，不读取 Java 内部模型

### Phase 2：SSE 与事件桥接

目标：

- 在 Phase 0a 的 runtime listener 基础上增加真正的 task/node 事件推送
- 保持 polling API 不变

主要改动：

- `FlowGramEventPublisher`
- `FlowGramSseController`
- runtime listener 到 SSE publisher 的桥接实现

验收：

- 客户端可订阅任务生命周期事件
- 事件顺序稳定，至少覆盖 task start/node start/node finish/task finish/task fail/task cancel

### Phase 3：生产化

目标：

- 收敛 task metadata retention、cleanup、ownership-aware access control 和持久化选项
- 为后续跨进程任务可见性打基础

主要改动：

- `FlowGramTaskStore` 扩展点
- retention cleanup 任务
- Redis/JDBC store 原型
- observability / metrics 接口

验收：

- 配置持久化 store 后，adapter 元数据可跨进程保留
- cleanup 行为可预测
- ownership-aware access checks 可以在不改 controller 的前提下接入

## 5. 建议文件级改动清单

### Kernel 侧新增或修改

- 推荐新增 `FlowGramRuntimeListener`
- 推荐新增 `FlowGramRuntimeEvent`
- 修改 `FlowGramRuntimeService`
- 修改 `FlowGramTaskReportOutput`
- 修改 `FlowGramTaskResultOutput`

### Starter 侧新增

- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/config/FlowGramProperties.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/autoconfigure/FlowGramAutoConfiguration.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/support/FlowGramRuntimeFacade.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/adapter/FlowGramProtocolAdapter.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/controller/FlowGramTaskController.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/controller/FlowGramSseController.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/exception/FlowGramExceptionHandler.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/security/FlowGramCallerResolver.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/security/FlowGramAccessChecker.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/security/FlowGramTaskOwnershipStrategy.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/support/FlowGramTaskStore.java`
- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot/support/InMemoryFlowGramTaskStore.java`

### 构建与文档

- 修改根 `pom.xml`
- 修改 `ai4j-bom/pom.xml`
- 为新 starter 模块添加 release profile
- 新增 demo 应用 README / sample `application.yml`
- 更新 `README.md` 与 docs-site FlowGram 集成文档

## 6. 测试计划

### 单元测试

- runtime listener 生命周期回调
- workflow failure reason 暴露
- service id 解析优先级
- protocol adapter 状态映射与错误映射
- permissive auth 默认实现
- custom node auto-registration

### 集成测试

- starter context startup
- REST happy path
- validation failure / task not found / cancel path
- `FlowGramProperties` 绑定
- demo 应用启动与基础接口连通

### 端到端测试

- demo + `ZHIPU_API_KEY` 环境变量下的 Start -> LLM -> End 工作流
- demo + condition/loop 工作流
- 使用 `agent-browser` 对运行中的 FlowGram 前端进行真实浏览器验证

## 7. 风险与缓解

### 风险 1：前端 contract 演进快于 adapter

缓解：

- 所有前端映射逻辑集中在 `FlowGramProtocolAdapter`
- P1 前重新核对当前前端 DTO/status 期望

### 风险 2：默认 LLM runner service 语义不清晰

缓解：

- 在 P0c 先冻结 service id 解析顺序
- service 解析失败时返回稳定错误，而不是 NPE 或模糊异常

### 风险 3：误把 task store 当成跨进程恢复方案

缓解：

- 文档明确 P0/P1 store 只存 adapter 元数据
- 进程边界存活问题留到 Phase 3 单独处理

### 风险 4：SSE 设计先行但 runtime 无事件钩子

缓解：

- Phase 0a 先补 runtime listener
- Phase 2 再落具体 `SseEmitter`/publisher 实现

### 风险 5：Spring Boot 兼容面扩大

缓解：

- 复用现有 starter 的双注册方式
- controller/autoconfig 对 MVC 类做条件化装配

## 8. 验证命令

内核基线：

```powershell
cmd /c "mvn -pl ai4j-agent -am -Dtest=FlowGramRuntimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test"
```

starter 模块联调完成后：

```powershell
cmd /c "mvn -pl ai4j-flowgram-spring-boot-starter -am -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test"
cmd /c "mvn -pl ai4j-flowgram-demo -am -DskipTests package"
```

demo 运行验证时，在当前 shell 注入环境变量，不落仓库：

```powershell
$env:ZHIPU_API_KEY="<current-shell-only>"
cmd /c "mvn -pl ai4j-flowgram-demo -am spring-boot:run"
```

浏览器验收：

- 启动 demo 后，用 `agent-browser` 访问本地前端与后端联调地址
- 验证 run/report/result/cancel/polling 或 SSE 行为是否符合预期

## 9. 当前下一步

1. 冻结默认 LLM runner 的 service 解析契约
2. 在 `ai4j-agent` 中补 runtime listener 与 workflow failure reason 暴露
3. 新建 starter/demo 模块并补 reactor/BOM/release wiring
4. 先完成 REST + polling 的自动化测试，再进入浏览器联调
