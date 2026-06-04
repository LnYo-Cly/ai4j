# 外部资料包索引 / External Source Packs

Context Doc Type: external-source-pack-registry
Owner: project coordinator
Last Verified: 2026-06-04
Confidence: high

## Purpose

这个目录只承载外部资料的摄取、索引和摘要。稳定事实必须投影到 `context/architecture`、`coding-agent-harness/context/development/external-context` 或 `context/integrations` 后才算进入 Harness 执行上下文。

先读 `coding-agent-harness/governance/standards/external-source-intake-standard.md`，再新增资料包。

## Source Packs

| Source Key | External Project / Service | Raw Storage Mode | Source Count | Digest Status | Projected To context/{architecture,development,integrations} | Owner | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| none-currently-supplied | N/A | N/A | 0 | N/A | `Architecture-SSoT.md` records absence of external docs | user / project coordinator | 2026-06-04 | high |

## Placement Rule

- 资料量小于 5 份时，优先在 `context/{architecture,development,integrations}` 的 `Source Evidence` 中引用，不必建资料包。
- 资料很多、跨多个主题或会持续增长时，创建 `<source-key>/README.md` 和 `digests/`。
- `raw/` 只能放允许入仓、无密钥、无隐私、无客户数据的原始材料。
- 不能入仓的资料只记录外部路径、owner、访问条件和 digest。
