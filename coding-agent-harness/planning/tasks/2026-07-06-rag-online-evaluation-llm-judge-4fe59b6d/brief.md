# rag online evaluation llm judge

## Task ID

`2026-07-06-rag-online-evaluation-llm-judge-4fe59b6d`

## 创建日期

2026-07-06

## 一句话结果

为 core RAG 增加一个可选的在线 LLM judge，用于在回答生成后评估 faithfulness / context relevance / answer relevance，并把结果写回 `RagTrace`。

## 完成后能得到什么

使用者仍然按原方式调用 `RagService.search(...)` 获取检索结果和 context；当上层已经生成最终 answer 后，可以显式调用 `RagOnlineEvaluator.evaluate(ragResult, answer)` 进行线上质量抽样。内置 `ChatRagJudge` 复用现有 `IChatService`，也允许企业替换成自有 judge、策略系统或审计模型。docs-site 已补充使用方式和边界说明，避免把 LLM judge 误认为离线 Recall/MRR 或强审计证明。

## 交付物

- 可见产物：`RagOnlineEvaluator`、`RagJudge`、`ChatRagJudge`、`RagJudgeRequest`、`RagJudgeEvaluation`、`RagTrace.judgeEvaluation`、`AiService#getRagOnlineEvaluator(...)`。
- 修改位置：`ai4j/src/main/java/io/github/lnyocly/ai4j/rag/`、`ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java`、`docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md`。
- 验证证据：RAG 定向测试、core 全量测试、docs-site typecheck/build、monorepo package smoke、`git diff --check`。

## 第一眼应该看什么

1. `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/RagOnlineEvaluator.java`
2. `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/ChatRagJudge.java`
3. `ai4j/src/test/java/io/github/lnyocly/ai4j/rag/RagOnlineEvaluatorTest.java`
4. `docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md`
5. 本任务 `progress.md` / `walkthrough.md` 的验证记录

## 边界

- 范围内：core RAG 回答后在线 judge API、默认 chat-backed judge、trace 字段、用户文档和本地验证。
- 范围外：自动在 `RagService.search(...)` 中调用 judge、provider live 调用、LLM-as-judge 评估平台、批量实验看板、pricing/cost、streaming trace 和 error structure 的后续项。
- 停止条件：如果要引入真实 provider 调用、默认自动评估或新增持久化/可观测平台，需要另开任务确认成本和 API 边界。

## 完成判断

- `RagService.search(...)` 仍保持检索/context-only，不隐式多一次 LLM 调用。
- `RagOnlineEvaluator.evaluate(ragResult, answer)` 能调用 judge 并把结果写入 `ragResult.getTrace().getJudgeEvaluation()`。
- 默认实现只依赖已有 `IChatService`，用户可直接实现 `RagJudge` 替换。
- docs-site 解释在线 judge 的使用形式、三个分数和非强证明边界。
- RG-001、RG-007、RG-008 和 diff hygiene 通过并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中，等待提交/PR/合并后完成
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：验证证据记录到 `progress.md`，PR 合并后清理 worktree/branch

## 当前下一步

提交 feature 分支，推送创建 PR 到 `main`，等待 CI 后合并并清理 worktree。
