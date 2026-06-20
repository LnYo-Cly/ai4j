# 收口记录：AI4J Extension Permission and Install UX

## 摘要

本任务把 AI4J plugin package 的非 tool 资源从“只能整包信任”扩展为“兼容默认 + 显式授权”双路径。`enable(...)` 继续保持旧行为；需要严格边界时，宿主可以调用 `requireExplicitResourceActivation()`，再用 `allowCommand(...)`、`allowSkill(...)`、`allowPrompt(...)`、`allowGuardrail(...)` 逐项授权。CLI 增加 `extension plan` 用于接入前预览 activation state，Spring Boot starter 增加同等配置映射，docs-site 补齐 Java、CLI、Spring 和 Ask User 插件示例。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-extension-api`, `ai4j-cli`, `ai4j-spring-boot-starter`, `docs-site`, Regression SSoT/Cadence, task package |
| 新增文件 | `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionActivationItem.java`, `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionActivationPlan.java` |
| 删除文件 | 无 |
| 不在范围内 | 远程 marketplace、CLI 自动安装或修改 Maven/Gradle 依赖、运行时热加载 jar、provider 自动注册、Spring 自动创建 Agent |

## 关键决策

| 决策 | 原因 |
| --- | --- |
| 保留 `enable(...)` 默认兼容语义 | 避免破坏已有宿主；严格模式必须由宿主显式选择 |
| `allow*` 自动进入显式资源授权模式 | 使用者一旦声明某类资源 allowlist，就应得到严格过滤和 fail-fast 行为 |
| `exposeTool(...)` 继续只控制模型可见工具 | tool 暴露与 command/Skill/Prompt/Guardrail 资源授权是不同安全边界 |
| CLI `extension plan` 只做预览 | 接入前展示权限、active/inactive 状态和未激活原因，不执行 command、不暴露工具 |
| 不引入 marketplace 或依赖安装语义 | 当前稳定路径仍是 Maven/Gradle classpath + ServiceLoader 发现 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Extension API | `mvn -pl ai4j-extension-api -DskipTests=false test` | pass, 19 tests | `progress.md` |
| CLI targeted | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | pass, 25 tests | `progress.md` |
| Spring starter targeted | `mvn -pl ai4j-spring-boot-starter -am -Dtest=ExtensionAutoConfigurationTest -DfailIfNoTests=false -DskipTests=false test` | pass, 6 tests | `progress.md` |
| Official Ask User plugin | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | pass, extension API 19 tests + plugin 6 tests | `progress.md` |
| monorepo package smoke | `mvn -DskipTests package` | pass, 11 reactor projects | `progress.md` |
| docs-site typecheck | `npm run typecheck` in `docs-site/` | pass | `progress.md` |
| docs-site build | `npm run build` in `docs-site/` | pass, generated `build` | `progress.md` |
| diff whitespace | `git diff --check` | pass with CRLF warnings only | `progress.md` |
| harness status | `npx.cmd --yes coding-agent-harness status --json .` | 0 failures, 1 dirty-state warning | `progress.md` |

## 证据深度

| Gate | Evidence Depth | 说明 |
| --- | --- | --- |
| RG-010 | L1 tests | extension API public contract and strict resource filtering |
| RG-004 | L1 tests | CLI plan/run/resource strict args and scaffold README output |
| RG-005 | L1 tests | Spring explicit resource activation and allow binding |
| RG-011 | L1 tests | official Ask User plugin remains compatible with updated extension API |
| RG-007 | L2 local_smoke | full package across 11 reactor projects |
| RG-008 | L2 local_smoke | docs-site typecheck/build |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Subagent read-only review | P2 governance/task material gap, P3 prompt/guardrail positive coverage gap | 已补 Regression/Cadence/task materials；已补 prompt/guardrail positive tests 并重跑 gates | `review.md` |
| self adversarial review | 0 open material findings after fixes | 提交 Agent Review Submission，等待人工确认 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 未验证真实第三方插件生态包 | maintainer | yes | 后续生态样板或第三方插件接入任务 |
| 人工 Review Confirmation 尚未执行 | human | yes | 用户通过 dashboard/workbench 或 lifecycle CLI 确认或退回 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结果 | no-candidate-accepted；本任务未产生新的 harness 流程规则 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Plugin packages docs | `../../../docs-site/docs/core-sdk/extension/plugin-packages.md` |
| Plugin author cookbook | `../../../docs-site/docs/core-sdk/extension/plugin-author-cookbook.md` |
| Ask User plugin docs | `../../../docs-site/docs/core-sdk/extension/ask-user-plugin.md` |

Closeout Status: closed
