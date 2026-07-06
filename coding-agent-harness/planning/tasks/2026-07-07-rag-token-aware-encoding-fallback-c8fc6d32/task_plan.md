# RAG token aware encoding fallback

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在不替换 jtokkit、不增加大抽象的前提下，让 RAG context token budget 支持模型名优先、显式 EncodingType 覆盖、未知模型名安全降级。

## 范围

- 做什么：补 `TokenAwareRagContextAssembler.withEncoding(...)`，增强 `TikTokensUtil` 模型名 cache miss 解析，更新 docs-site 和 regression/cadence 记录。
- 不做什么：不新增 tokenizer SPI；不替换 jtokkit；不做精确计费 token 计算；不改 Spring 自动配置。
- 主要风险：新增 `EncodingType` 构造器会造成 `new TokenAwareRagContextAssembler(null, 3000)` 源码歧义，因此采用静态工厂避免兼容性破坏。

## 预算选择

选择预算：simple

选择理由：变更集中在一个 RAG assembler、一个 token util、两处 docs-site 示例和治理记录；无需子任务或 worker。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/TokenAwareRagContextAssembler.java | 当前 token-aware context 组装入口 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/token/TikTokensUtil.java | jtokkit wrapper 和模型名映射逻辑 | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md | 用户使用 RAG token budget 的主要文档 | coordinator |
| C-004 | external | URL:https://repo1.maven.org/maven2/com/knuddels/jtokkit/maven-metadata.xml | 核对 jtokkit 当前 Maven Central 最新版本 | coordinator |
| C-005 | external | Context7:/knuddelsgmbh/jtokkit | 核对 jtokkit `EncodingType` / string model registry API | coordinator |

## 步骤

1. 核对现有 jtokkit 版本、`TikTokensUtil` 行为和 `TokenAwareRagContextAssembler` fallback 顺序。
2. 实现静态 `withEncoding(...)`，避免新增构造器造成 `null` 调用歧义。
3. 增强 `TikTokensUtil`：cache miss 时调用 `registry.getEncodingForModel(modelName)` 并缓存成功结果。
4. 更新 docs-site，说明模型名优先、显式 encoding 覆盖、未知模型名估算降级。
5. 运行 core、docs-site、package smoke，并同步 Regression/Cadence。

## 验收标准

- [x] `TokenAwareRagContextAssemblerTest` 覆盖显式 `EncodingType.O200K_BASE` 和 null encoding 拒绝。
- [x] 未知模型名仍 fallback 到默认估算，不阻断 RAG context 组装。
- [x] `mvn -pl ai4j -am -DskipTests=false test` 通过。
- [x] `npm run typecheck` / `npm run build` 在 `docs-site/` 通过。
- [x] `mvn -DskipTests package` 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\fix\rag-token-aware-encoding-type`
- 分支：`fix/rag-token-aware-encoding-type`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用；已使用独立 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：若要求替换 tokenizer 或引入 provider billing 统计，另开任务。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：本地 self-check + deterministic tests + docs build 足够。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：不适用（simple task）
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：SRB-066 RAG token-aware context assembler

## 模块关联（启用模块并行时填写）

- Module：base/core-sdk
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 已同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` 已更新
