# 任务产物索引

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | diff | `docs-site/docs/intro.md` | 首页入口重写：普通 Java、Spring Boot、Feature Map、能力层级和模块地图。 | coordinator |
| ART-002 | diff | `docs-site/docs/start-here/why-ai4j.md` | Why AI4J 重写：降低 Java AI 接入成本、相邻框架边界、适合/不适合场景。 | coordinator |
| ART-003 | diff | `docs-site/docs/start-here/feature-map.md` | 新增功能地图：能力清单、成熟度标记和深链入口。 | coordinator |
| ART-004 | diff | `docs-site/sidebars.ts` | Start Here sidebar 挂载 `start-here/feature-map`。 | coordinator |
| ART-005 | command | `docs-site/` | `npm run build` 成功，Docusaurus 生成静态文件到 `docs-site/build`，未报告断链或编译错误。 | coordinator |
| ART-006 | command | repo root | `git diff --check` 成功；仅输出 Windows 工作区 LF/CRLF 转换 warning。 | coordinator |
| ART-007 | command | pending | `npx --yes coding-agent-harness status --json .`，提交后验证并补充。 | coordinator |

## 使用规则

- 长命令输出不粘贴全文；通过 `progress.md` 和 `review.md` 摘要记录结论。
- 构建失败时在对应 ART 条目保留失败摘要和后续处理。
