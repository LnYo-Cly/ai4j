# 收口记录：AI4J extension system wave 1

## 摘要

本任务新增 `ai4j-extension-api` 轻量模块，把 AI4J 插件生态的第一版底座落成公共合同：第三方扩展包实现 `Ai4jExtension`，声明 `ExtensionManifest`，通过 ServiceLoader 发现；宿主必须显式 `enable(id)`，并且 tool 还必须 `exposeTool(name)` 才会出现在 runtime snapshot 中。Wave 1 同步了 Maven reactor、BOM、CI matrix、回归台账和 harness context，但不接入 CLI/Spring/Agent runtime adapter。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | 新增 `ai4j-extension-api`；共享构建/治理面：root POM、BOM、CI、AGENTS、Regression/Cadence、harness context |
| 新增文件 | `ai4j-extension-api/**`; `coding-agent-harness/planning/modules/extension-api/brief.md`; `coding-agent-harness/planning/modules/extension-api/module_plan.md` |
| 删除文件 | 无 |
| 不在范围内 | CLI extension 命令、Spring Boot 配置绑定、Agent/Coding runtime adapter、Marketplace、hot reload、runtime jar download、provider extension、真实第三方包发布验证 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RG-010 extension API | `mvn -pl ai4j-extension-api -DskipTests=false test` | pass, 7 tests | `progress.md` |
| RG-007 package smoke | `mvn -DskipTests package` | pass, root POM + 9 Java/BOM modules | `progress.md` |
| Harness module list | `npx.cmd --yes coding-agent-harness module list --json .` | pass, includes `extension-api` | `progress.md` |
| Module view refresh attempt | `harness module scaffold --all`; `harness module register extension-api ...` | no generated view refresh; register rejected existing module | `progress.md`; `findings.md` |
| Diff hygiene | `git diff --check` | pass, CRLF warnings only | `progress.md` |
| Harness status | `npx.cmd --yes coding-agent-harness status --json .` | warn, failures 0; dirty-state only before commit | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | 可提交人工确认；runtime adapter 残余已明确路由 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| runtime adapter 尚未接入 agent/coding/CLI/Spring | coordinator | yes | Wave 2/3 单独任务 |
| 真实第三方扩展 jar 未从外部项目验证 | coordinator | yes | 样板插件或示例项目任务 |
| `Module-Registry.md` 为手动同步 | coordinator | yes | 后续如需要，增强 harness CLI generated view refresh |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | checked-none: extension-api-wave1-local-contract-no-new-governance-lesson |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| 回归 SSoT | `docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `docs/05-TEST-QA/Cadence-Ledger.md` |

Closeout Status: pending-human-confirmation
