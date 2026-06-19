# P0-B Memory Compact Context Projector - Brief

## Task ID

`MODULES/agent-runtime/2026-06-20-p0-b-memory-compact-context-projector-47effd57`

## 一句话结果

在 `ai4j-agent` 中落地 Memory / Compact / ModelContext 的基础分层：新增 `ContextProjector`、`ContextBudget`、`ContextReport`、`CompactPolicy`、`CompactResult`，让 runtime prompt 构造可投影、session compact 可保存和恢复，并补齐 docs-site 技术文档。

## 完成后能得到什么

开发者可以：

- 在 `AgentBuilder` 上配置 `contextProjector(...)` 与 `contextBudget(...)`。
- 让 ReAct / DeepResearch / CodeAct 在构造模型 prompt 前按预算投影 memory items。
- 从 `MEMORY_COMPRESS` 事件中拿到 `ContextReport`，知道本轮上下文是否被裁剪。
- 调用 `AgentSession.compact(CompactPolicy)` 生成结构化 compact 结果。
- 将 `CompactResult` 随 `AgentSessionSnapshot` 保存和 resume。
- 在 docs-site 中阅读 Memory Compact Context Projector 的边界和用法。

## 交付物

- 可见产物：context/compact API、runtime/session 集成、P0-B 定向测试、docs-site 技术页。
- 修改位置：`ai4j-agent/**`、`docs-site/docs/agent/**`、`docs-site/sidebars.ts`、本任务包。
- 验证证据：targeted Maven、broad Maven、docs-site build、harness status、PR CI。

## 第一眼应该看什么

1. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/context/`
2. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/compact/`
3. `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentMemoryCompactContextProjectorTest.java`
4. `docs-site/docs/agent/memory-compact-context.md`
5. `task_plan.md` 与 `review.md`

## 边界

- 范围内：`ai4j-agent` runtime/context/compact/session 基础能力、定向测试、docs-site 技术文档、任务包材料。
- 范围外：不做模型驱动语义总结、不做 token 精确估算、不接真实 sandbox、不做 plugin lifecycle hooks、不做 YAML Blueprint、不做 CLI `/sandbox`。
- 停止条件：如果转入 P0-C/P1/P2/P3/P4/P5 能力，停止并另开任务。

## 完成判断

- P0-B 定向测试覆盖 projector、runtime projection、compact policy、session save/resume。
- Broad agent regression 与 docs-site build 通过。
- Harness task package 无 missing-materials/failure。
- PR 创建并 CI 通过后 merge。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行 broad Maven、docs-site build、Harness status；然后提交、推送、创建 PR 并等待 CI。
