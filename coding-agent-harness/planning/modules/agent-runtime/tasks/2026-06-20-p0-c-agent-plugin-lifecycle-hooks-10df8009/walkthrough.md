# 收口记录：P0-C Agent plugin lifecycle hooks

## 摘要

P0-C 已实现可选 Agent lifecycle hook 基础：`ai4j-extension-api` 提供 lifecycle hook 公共合同与 registry/snapshot 支持，`ai4j-agent` 将启用插件贡献的 Hook 接入 AgentBuilder/AgentContext，并在 ReAct/Base runtime、CodeAct runtime 与 `AgentSession.compact(...)` 中触发 observation-first 生命周期事件。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-extension-api`、`ai4j-agent`、`docs-site`、Harness task package |
| 新增文件 | lifecycle API package、agent lifecycle dispatcher/error payload、lifecycle tests、`docs-site/docs/agent/plugin-lifecycle-hooks.md` |
| 删除文件 | 无 |
| 不在范围内 | YAML Blueprint、Sandbox SPI、Remote Runner、CLI/TUI 插件 UI、live-provider 测试、可变 Hook 拦截器 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Extension lifecycle targeted | `mvn -pl ai4j-extension-api "-Dtest=*Lifecycle*" -DskipTests=false test` | passed | `ai4j-extension-api/target/surefire-reports/io.github.lnyocly.ai4j.extension.AgentLifecycleExtensionRegistryTest.txt` |
| Agent lifecycle targeted | `mvn -pl ai4j-agent -am "-Dtest=AgentPluginLifecycleHooksTest" -DskipTests=false -DfailIfNoTests=false test` | passed | `ai4j-agent/target/surefire-reports/io.github.lnyocly.agent.AgentPluginLifecycleHooksTest.txt` |
| Cross-module regression | `mvn -pl ai4j-extension-api,ai4j-agent -am -DskipTests=false test` | passed | extension-api 25, ai4j 103, ai4j-agent 89 tests |
| Docs build | `npm run build` in `docs-site` | passed | first run required local `npm install`; ignored `node_modules` / `build` not committed |
| Harness status | pass with dirty-state warning | failures=0; warning only for uncommitted feature diff before commit | `npx --yes coding-agent-harness status --json .` |
| Regression governance | updated | RG-010/RG-002 notes and SRB-049 cadence row record lifecycle hook surface | `docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 0 | no material finding | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Human Review Confirmation 尚未完成 | human / maintainer | 否 | PR 创建后等待 review / CI |
| 可变 HookResult 不在首版范围 | coordinator | 是 | 后续如需要另开任务 |
| Session start/end 自动触发不在首版范围 | coordinator | 是 | 等显式 session close/end 语义稳定后再接 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | checked-none:p0-c-task-local |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 技术规划 | `references/p0-c-agent-plugin-lifecycle-hooks-plan.md` |
| docs-site 页面 | `docs-site/docs/agent/plugin-lifecycle-hooks.md` |
