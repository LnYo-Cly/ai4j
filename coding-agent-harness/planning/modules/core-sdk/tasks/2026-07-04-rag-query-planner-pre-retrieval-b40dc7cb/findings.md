# RAG query planner pre retrieval - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### RAG planner 应留在检索前处理层

- 背景：用户明确收敛为 RAG 场景，不希望把 query rewrite / HyDE / step-back 泛化到 Agent/tool 路由。
- 发现：当前 `DefaultRagService` 已有明确链路：retriever -> reranker -> context assembler；`HybridRetriever` 已负责多 retriever 结果融合，query variants 应在 retriever 前执行。
- 影响：新增 `RagQueryPlanner` 而不是通用 `QueryTransformer` 或 Agent planner；调用侧仍使用 `rag.search(RagQuery)`。
- 后续：如未来要把 query planning 下沉到 Spring Boot 自动配置，应另开任务。

### 开箱即用需要模型 planner，但不能默认启用

- 背景：只提供接口会让用户仍需自己写 rewrite / multi-query / HyDE / step-back 生成逻辑。
- 发现：可以通过 `IChatService` 构建 `ModelRagQueryPlanner`，使用 JSON 输出解析为 variants；但默认启用会增加 LLM 调用成本和延迟。
- 影响：提供 `AiService.getModelRagQueryPlanner(...)` 便利入口，但 `getRagService(platform, vectorStore)` 仍保持无 planner。
- 后续：live provider smoke 不是本地 gate；真实模型质量需后续 opt-in 验证。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Planner 命名 | `RagQueryPlanner` | 产物可能是一组 query variants，更像检索计划；且限定 RAG 语义 | `QueryTransformer` / `RetrievalQueryStrategy` | accepted |
| 执行位置 | `DefaultRagService` 内部 | 保持用户调用 `rag.search(RagQuery)`，避免调用侧手动装饰 retriever | 外部 `PlanningRetriever` wrapper | accepted |
| 原 query 处理 | 保留原 query 给 rerank/result/assembler | 避免 rewrite 覆盖用户真实问题 | 用第一条 variant 替换原 query | accepted |
| 模型 planner | 提供 `ModelRagQueryPlanner`，但不默认启用 | 开箱可用，同时避免默认成本/延迟 | 只给接口；或默认开启 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要 Spring Boot 自动配置 planner | 本轮不做，避免配置面过早膨胀 | 用户 / 后续任务 | 有明确 starter 使用需求时 |
| 是否需要 live LLM planner 示例 | 本轮不做，live 调用需凭证且可能产生费用 | 用户 / 后续任务 | 发布前或文档示例需要真实响应时 |
