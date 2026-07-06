# RAG token aware encoding fallback

## Task ID

`2026-07-07-rag-token-aware-encoding-fallback-c8fc6d32`

## 创建日期

2026-07-07

## 一句话结果

让 token-aware RAG context assembler 在用户不知道最新模型 EncodingType 时仍然可用，并为知道 encoding 的高级用户提供显式覆盖入口。

## 完成后能得到什么

`TokenAwareRagContextAssembler` 继续支持最常用的模型名构造方式；未知或较新的模型名不会导致 RAG 组装失败，而是退回到显式或默认 encoding 的 context budget 估算。知道 tokenizer 的用户可以通过 `withEncoding(EncodingType, maxContextTokens)` 显式覆盖。文档明确说明这是上下文预算保护，不是精确计费 token 统计。

## 交付物

- 可见产物：RAG token-aware assembler 的 encoding override / fallback 能力和 docs-site 使用说明。
- 修改位置：`ai4j/src/main/java/io/github/lnyocly/ai4j/rag/TokenAwareRagContextAssembler.java`、`ai4j/src/main/java/io/github/lnyocly/ai4j/token/TikTokensUtil.java`、`docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md`、`docs-site/docs/spring-boot/bean-extension.md`。
- 验证证据：`progress.md` 中的 Maven、docs-site 和 package smoke 记录。

## 第一眼应该看什么

先看 `TokenAwareRagContextAssembler.withEncoding(...)` 和 `countTokens(...)` 的 fallback 顺序，再看 `TikTokensUtil.encodingForModel(...)` 是否仍保持 Java 8 / jtokkit 兼容。

## 边界

- 范围内：显式 encoding override、未知模型名 fallback、jtokkit string model registry cache-miss lookup、docs-site 说明、回归记录。
- 范围外：替换 jtokkit、引入 Tokenizer SPI、接入 Python/Rust tokenizer、精确 provider billing token 统计。
- 停止条件：若需要跨语言 tokenizer 或 provider-specific billing 计算，另开设计任务。

## 完成判断

- `TokenAwareRagContextAssembler.withEncoding(EncodingType, int)` 可用且不引入 `null` 构造器歧义。
- 模型名构造器保持原使用方式，未知模型名仍降级估算。
- `TikTokensUtil` 在静态 cache miss 时会调用 jtokkit string model registry。
- core、docs-site、package smoke 均通过并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交 PR，等待合并后清理 worktree 和本地/远端分支。
