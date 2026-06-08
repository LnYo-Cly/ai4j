# 收口记录：AI4J Spring Boot extension configuration wave 4

## 摘要

本轮把 AI4J 插件生态接入 Spring Boot starter：用户可以用 `ai.extensions.enabled` 启用 classpath 上发现的插件包，用 `ai.extensions.tools.expose` 显式暴露插件工具，starter 自动提供 `ExtensionRegistry` 和 `ExtensionRuntimeSnapshot` bean。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-spring-boot-starter`、`docs-site`、root `README.md`、harness task package、regression governance docs |
| 新增文件 | `AiExtensionProperties.java`、`ExtensionAutoConfigurationTest.java`、test `META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension` |
| 删除文件 | none |
| 不在范围内 | marketplace、CLI 自动安装、运行时 jar 热加载、provider plugin、Spring 自动创建 Agent/Coding Agent、R-008 修复 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RG-005 targeted | `mvn -pl ai4j-spring-boot-starter -am -Dtest=ExtensionAutoConfigurationTest -DfailIfNoTests=false -DskipTests=false test` | pass, 4 tests | `progress.md` |
| RG-005 full | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | pass, extension API 8 + core 103 + starter 7 tests | `progress.md` |
| RG-007 | `mvn -DskipTests package` | pass, 10 reactor modules | `progress.md` |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` | pass | `progress.md` |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run build` | pass, generated `docs-site/build` | `progress.md` |
| L0 | `git diff --check` | pass | `progress.md` |

Evidence depth reached：L1 tests + L2 local_smoke。

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self adversarial review | 0 open material findings | 可提交 Agent Review Submission；人工确认仍需用户/维护者完成 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Spring Boot starter 不自动创建 Agent/Coding Agent | owner / coordinator | yes | 后续 starter-agent integration 任务单独设计 |
| marketplace / install / hotload 未实现 | owner / coordinator | yes | 当前 docs 明确写为不包含能力 |
| Full `mvn -pl ai4j-agent -am -DskipTests=false test` 仍受既有 R-008 阻塞 | coordinator | yes | 后续单独修复 `HandoffPolicyTest` |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | no-candidate-accepted，本轮没有需要 promotion 的通用流程经验 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Feature SSoT row | F-026 |
| Regression gates | RG-005、RG-007、RG-008 |
| Branch / Worktree | `main`，未使用 dedicated worktree |
| Commit | pending until final git commit |
