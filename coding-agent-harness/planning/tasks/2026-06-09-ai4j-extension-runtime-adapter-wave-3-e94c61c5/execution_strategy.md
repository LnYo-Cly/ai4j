# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 当前变更可由 targeted unit tests、docs build 和 self adversarial review 覆盖；没有独立 worker commit 需要审查。 | 使用 `review.md` 完成 self review，并等待人工确认。 |
| Would a worker subagent materially help? | no | 代码、docs、SSoT 都在同一条插件生态交付链上，拆 worker 会增加共享文件冲突；用户要求继续一起做完。 | coordinator 单线程完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-09 | n/a | n/a | 本轮不启用写入型 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责实现、验证、docs 和治理收口。 |
| Subagent 模式 | none | 单线程执行，避免跨共享 docs / ledger 冲突。 |
| 审查模型 | self adversarial review + human confirmation pending | 本轮有跨模块 API 入口，但可通过确定性本地回归覆盖；人工确认仍由 harness review 队列承接。 |
| Worktree 策略 | same checkout | 当前分支已有 Wave 3 harness lifecycle 提交，用户要求继续完成并推送。 |
| 冲突控制 | coordinator owns shared files | Feature SSoT、Regression SSoT、Cadence Ledger、module plans 均由 coordinator 更新。 |
| 证据深度 | L1 + L2 | Java runtime 使用 targeted tests；docs-site 使用 typecheck/build；共享构建使用 package smoke。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace error |
| L1 | `mvn -pl ai4j-extension-api -DskipTests=false test` | `progress.md` | extension API contract tests pass |
| L1 | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | Agent extension adapter tests pass |
| L1 | `mvn -pl ai4j-coding -am "-Dtest=CodingAgentBuilderTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | Coding Agent builder + extension adapter tests pass |
| L1 | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | CLI inspect surface still passes |
| L2 | `mvn -DskipTests package` | `progress.md` | monorepo package smoke pass |
| L2 | `npm run typecheck` and `npm run build` in `docs-site/` | `progress.md` | docs-site route/build pass |
| L0 | `npx --yes coding-agent-harness status --json .` | `progress.md` | no unexpected missing-material blocker for this task after review submission |

## 暂停 / 升级条件

- 需要把插件包变成远程 marketplace、CLI 安装器、运行时热加载或 provider 自动注册。
- 需要新增 Spring Boot 配置化装配。
- Targeted tests 暴露 agent/coding tool routing 冲突。
- docs-site build 报出 route id、sidebar 或 MDX 错误。
- harness lifecycle CLI 报告任务材料缺失或状态非法。
