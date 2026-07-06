# RAG token-aware context assembler - 发现记录

## 研究发现

### Default assembler 只有拼接，没有预算控制

- 背景：用户指出 `DefaultRagContextAssembler.assemble` 会把 final hits 直接拼成 context。
- 发现：源码只按 hit 顺序生成 citation 并 append content；`DefaultRagService` 只按 `finalTopK` 控制数量，不控制 token。
- 影响：token budget 应落在 `RagContextAssembler` 扩展点，而不是 retriever/reranker。
- 后续：本任务新增可选 assembler，默认行为不变。

### 已有 token 工具可复用

- 背景：避免新增依赖和 tokenizer 抽象。
- 发现：`ai4j` 已依赖 `jtokkit`，且有 `TikTokensUtil`。
- 影响：新 assembler 直接复用现有工具；未知模型名 fallback 到 `cl100k_base`。
- 后续：如未来需要 provider-specific tokenizer registry，另开任务。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 默认行为 | 不修改 `DefaultRagContextAssembler` | 兼容现有用户和测试 | 给默认 assembler 加预算字段 | accepted |
| 公共 API | 新增一个可选实现类 | 已有 `RagContextAssembler` 扩展点够用 | 新增 `ContextBudgetPolicy`/`RagQuery` 字段/starter 配置 | accepted |
| 截断策略 | 只截断第一个 oversized hit | 最小解决超窗；避免复杂片段重排 | 对所有 hit 做分段截断/重排 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要 per-query 动态预算 | 本轮不需要；可通过创建不同 assembler 或业务层选择实现 | future | 有真实多模型/多窗口场景时 |
