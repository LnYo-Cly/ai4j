# RAG token-aware context assembler

## Task ID

`2026-07-06-rag-token-aware-context-assembler-9aba3316`

## 创建日期

2026-07-06

## 一句话结果

为 RAG 增加可选 `TokenAwareRagContextAssembler`，在不改变默认行为的前提下控制注入模型的 context token budget。

## 完成后能得到什么

调用方可以继续使用默认 `DefaultRagContextAssembler`，也可以在企业 RAG 场景显式替换为 `TokenAwareRagContextAssembler("gpt-4o-mini", 3000)`。新 assembler 会按最终命中顺序拼上下文，超过预算时停止追加；第一个命中自身过长时才截断内容，并且 citations 只返回真正进入 context 的来源。

## 交付物

- 可见产物：`TokenAwareRagContextAssembler`、单元测试、RAG/docs-site 使用说明、Regression/Cadence 记录。
- 修改位置：`ai4j/src/main/java/io/github/lnyocly/ai4j/rag/`、`ai4j/src/test/java/io/github/lnyocly/ai4j/rag/`、`docs-site/docs/core-sdk/search-and-rag/`、`docs-site/docs/spring-boot/`、`docs/05-TEST-QA/`。
- 验证证据：见 `progress.md` E-001 到 E-004。

## 第一眼应该看什么

1. `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/TokenAwareRagContextAssembler.java`
2. `ai4j/src/test/java/io/github/lnyocly/ai4j/rag/TokenAwareRagContextAssemblerTest.java`
3. `docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md`

## 边界

- 范围内：可选 token-aware assembler、deterministic tests、RAG/docs-site 说明、回归记录。
- 范围外：修改默认 assembler 行为、给 `RagQuery` 增加策略字段、Spring Boot 新配置项、复杂 context policy 框架。
- 停止条件：如果需要 per-query 动态预算或 provider-specific tokenizer registry，另开任务。

## 完成判断

- 新 assembler 能把 context 控制在 token budget 内。
- 第一个超长 hit 会被截断；后续超预算 hit 不进入 context/citations。
- 默认 `DefaultRagContextAssembler` 不变。
- RG-001、RG-007、RG-008 通过并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交 PR，等待 CI 后合并并清理 worktree。
