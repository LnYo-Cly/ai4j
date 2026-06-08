# Documentation Site 模块计划

## 模块身份

- 模块 Key：`docs-site`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`docs-site/**`
- 共享面：根 `docs/**`、README、release notes
- 依赖模块：无

## 边界

- 可以编辑：Docusaurus 站点源码、配置、静态资源和 package files。
- 禁止编辑：Java modules 和 harness SSoT，除非任务明确批准。
- 外部依赖：Node/npm、Docusaurus build、站点部署环境。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| DOCS-01 | 维护 docs-site build contract | in_progress | `coding-agent-harness/planning/tasks/2026-06-09-ai4j-spring-boot-extension-configuration-wave-4-cb1cd3f6/task_plan.md` | none |
| DOCS-02 | SSoT 同步检查 | planned | none | DOCS-01 |
| DOCS-03 | 站点构建验证 | planned | none | DOCS-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-09-ai4j-spring-boot-extension-configuration-wave-4-cb1cd3f6` | review-pending | coordinator | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npm run build` | Wave 4 documents Spring Boot plugin configuration path and boundaries. |
| `2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5` | review-pending | coordinator | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npm run build` | Wave 3 adds plugin package docs, sidebar entry, and README links. |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| docs-site build | `npm run build` from `docs-site/` | yes when docs-site changes |
| SSoT drift check | affected root docs reviewed | risk-based |

## 交接

- 分支：`docs/<name>` 或 `.worktrees/docs/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 npm build 或 scoped docs check。
- 变更文件：只列 `docs-site/**` 及批准的 shared docs。
- 残余风险：站点未构建或部署未验证时必须说明。
- 需要 coordinator 同步：影响 root docs SSoT、release docs 或 examples 时同步。
