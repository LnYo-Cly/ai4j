# 收口记录：RAG token aware encoding fallback

## 摘要

本任务补齐 RAG token-aware context assembler 在新模型 / 未知模型场景下的使用体验：普通用户继续传模型名或只传 budget；知道 tokenizer 的高级用户使用 `withEncoding(EncodingType, maxContextTokens)`；未知模型名会降级到显式或默认 encoding 的估算，不阻断 RAG。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core RAG/token utility；docs-site RAG/Spring 文档；Regression/Cadence 记录 |
| 新增文件 | 无生产新增文件；复用已创建 task package |
| 删除文件 | 无 |
| 不在范围内 | 替换 jtokkit、Tokenizer SPI、provider billing 级精确 token 计费 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted RAG | `mvn -pl ai4j "-Dtest=TokenAwareRagContextAssemblerTest,TikTokensUtilTest,DefaultRagServiceTest" -DskipTests=false test` | PASS，9 tests | `progress.md` final-validation |
| RG-001 core | `mvn -pl ai4j -am -DskipTests=false test` | PASS，149 tests | `progress.md` final-validation |
| RG-008 typecheck | `npm run typecheck` in `docs-site/` | PASS | `progress.md` final-validation |
| RG-008 build | `npm run build` in `docs-site/` | PASS，generated `docs-site/build` | `progress.md` final-validation |
| RG-007 package | `mvn -DskipTests package` | PASS，11 reactor projects | `progress.md` final-validation |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-check | 不应新增 `EncodingType` 构造器，否则 `null` 调用会在 Java overload 中变歧义 | 改用静态工厂 `withEncoding(...)` | diff |
| self-check | `TikTokensUtil` 只依赖预填 `ModelType.values()` 会错过 jtokkit string registry 可识别的模型名 | cache miss 时调用 `registry.getEncodingForModel(modelName)` 并缓存 | diff |
| self-check | token 估算容易被误解为精确计费 | docs-site 明确写成 context budget guard | docs diff |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| jtokkit 仍可能晚于最新 provider 模型命名 | SDK | 接受 | 文档建议未知时保守 budget，知道 tokenizer 时用 `withEncoding` |
| token count 不等于 provider billing 精确值 | SDK | 接受 | 文档已声明 budget guard；精确计费另开 provider-specific 任务 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | checked-none |
| 经验候选详情文件 | 不创建；这是局部 API 兼容性修正，不需要沉淀全局 lesson |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 进度记录 | `progress.md` |
| RAG docs | `docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md` |
| Spring docs | `docs-site/docs/spring-boot/bean-extension.md` |
| Regression | `docs/05-TEST-QA/Regression-SSoT.md` RG-001 / RG-007 / RG-008 |
| Cadence | `docs/05-TEST-QA/Cadence-Ledger.md` SRB-067 |

Closeout Status: closed
