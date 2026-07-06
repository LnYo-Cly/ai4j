# 收口记录：ai4j dynamic workflow host runtime

## 摘要

本任务完成了 AI4J 侧可选 dynamic workflow host runtime：独立 `ai4j-plugin-dynamic-workflow` 仍只返回 host-mediated envelope，`ai4j-agent` 在宿主显式 opt-in 后负责解析 envelope、执行受限脚本、调用 host-owned agent bridge，并返回结构化 execution result / trace。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、`docs-site`、`docs/05-TEST-QA`、task-local harness 文件 |
| 新增文件 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/dynamicworkflow/**`；`ai4j-agent/src/test/java/io/github/lnyocly/ai4j/agent/dynamicworkflow/**` |
| 删除文件 | 无 |
| 不在范围内 | 不把插件并入 SDK reactor/BOM；不改 `ai4j-extension-api`；不实现后台 `/workflows`、resume journal、worktree-isolated worker fan-out、model tier routing、live provider E2E |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Agent dynamic workflow runtime regression | `mvn -pl ai4j-agent -am -Dtest=DynamicWorkflow*Test -DfailIfNoTests=false -DskipTests=false test` | 通过：11 tests, 0 failures/errors | `progress.md` / surefire output |
| Docs typecheck | `npm run typecheck` in `docs-site/` | 通过 | terminal evidence |
| Docs build | `npm run build` in `docs-site/` | 通过：Generated static files in `build` | terminal evidence |
| Diff whitespace | `git diff --check` | 通过：exit 0（仅 CRLF working-copy warnings，无 whitespace error） | terminal evidence / `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | F-001：Nashorn 初版 Java interop/raw bridge 暴露风险 | 已修复：默认 `--no-java`、隐藏危险全局、闭包持有 bridge、增加安全回归 | `review.md`；`DynamicWorkflowNashornExecutorTest` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Nashorn MVP 不是完整 Node/modern JS runtime。 | coordinator | yes | 文档已说明；复杂脚本可替换自定义 `DynamicWorkflowExecutor`。 |
| `parallel()` 仅提供确定性 fan-out 语义和 trace，未做物理并发/隔离 worker。 | coordinator | yes | 后续由 `ai4j-coding`/host bridge 设计 worktree-isolated fan-out。 |
| 未执行 live-provider E2E。 | coordinator | yes | 本轮本地 deterministic gate 足够；live-provider 需单独 opt-in 且不提交 secrets。 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，结论为 `checked-none`。 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | 本轮可复用经验已直接固化为 runtime 安全默认、回归测试和 docs-site 边界说明；无需新增共享 lesson。 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| Lesson 判定 | `lesson_candidates.md` |

