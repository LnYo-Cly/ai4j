# 任务产物索引

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | diff | `docs-site/docs/intro.md` | 新增 `用多少，取多少` 模块取用表，把首页模块地图转成用户选择路径。 | coordinator |
| ART-002 | diff | `docs-site/docs/start-here/why-ai4j.md` | 新增“不是全家桶，而是可渐进升级的 Java AI SDK”章节，并强化模块取用差异。 | coordinator |
| ART-003 | diff | `docs-site/docs/start-here/feature-map.md` | 新增 `按模块取用` 表，列出最小模块、依赖关系和适合场景。 | coordinator |
| ART-004 | command | repo root | `rg -n "<module>|<artifactId>ai4j|<version>\\$\\{project\\.version\\}" -g "pom.xml"` 用于核对模块依赖关系。 | coordinator |
| ART-005 | command | `docs-site/` | `npm run build` 成功，Docusaurus 生成静态文件到 `docs-site/build`，未报告断链或编译错误。 | coordinator |
| ART-006 | command | repo root | `git diff --check` 成功；仅输出 Windows 工作区 LF/CRLF 转换 warning。 | coordinator |
| ART-007 | command | repo root | `npx --yes coding-agent-harness status --json .` 通过；checkState pass、git dirty false；当前任务 `reviewQueueState=ready-to-confirm`、`materialsReady=True`、`materialIssues=0`。 | coordinator |

## 使用规则

- 长命令输出不粘贴全文；通过 `progress.md` 和 `review.md` 摘要记录结论。
- 本任务不保存外部网页全文，不新增大体量 artifact。
