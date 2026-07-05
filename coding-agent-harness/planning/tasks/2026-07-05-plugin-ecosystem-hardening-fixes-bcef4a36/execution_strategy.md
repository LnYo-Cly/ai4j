# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | not used | read-only if needed | task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | no write access | n/a | n/a | n/a | n/a | not needed |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 修复点明确且已由 owning tests、consumer tests、package smoke、docs build/typecheck 覆盖；self-review 足够进入 PR 前状态。 | 记录 self-review 到 `review.md`。 |
| Would a worker subagent materially help? | no | 修改面虽跨模块，但每个切片都很小，串行修复能避免共享 docs/governance 冲突。 | coordinator 直接实现和验证。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-07-05 | plugin ecosystem hardening fixes | `.worktrees/fix/plugin-ecosystem-hardening` / `fix/plugin-ecosystem-hardening` | 串行修复即可。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 范围明确且共享文件较多，避免并行冲突。 |
| 审查模型 | self-check + predefined verifier | 本轮是 review 后窄修复，使用明确 Maven/docs gates 验证。 |
| Worktree 策略 | dedicated worktree | 使用 `.worktrees/fix/plugin-ecosystem-hardening` 隔离 `main`。 |
| 冲突控制 | coordinator owns shared files | Regression SSoT、Cadence、docs-site 和任务包由 coordinator 统一编辑。 |
| 证据深度 | L1 + L2 | owning module/consumer targeted tests + monorepo package smoke + docs-site build/typecheck。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git fetch --all --prune`; `git rev-list --left-right --count main...origin/main`; `git diff --check`; `rg "2\.3\.0" ...` | `progress.md` / walkthrough | main 与 origin/main 一致、无 whitespace error、相关 surfaces 无旧版本残留。 |
| L1 | `mvn -pl ai4j-extension-api -DskipTests=false test`; `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test`; `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test`; `mvn -pl ai4j-coding -am -Dtest=CodingSkillSupportTest -DfailIfNoTests=false -DskipTests=false test` | `progress.md`; `review.md`; Cadence SRB-060 | owning module and first consumer tests pass. |
| L2 | `mvn -DskipTests package`; `npm run build`; `npm run typecheck` in `docs-site/` after `npm ci` if dependencies are absent | Cadence SRB-060; walkthrough | package smoke passes across 11 reactor projects and docs-site build/typecheck pass. |
| L3 | PR CI / remote checks | PR evidence after push | not required before local branch delivery; follow up after PR. |

## 暂停 / 升级条件

- 所需工作超出插件生态 review 修复范围。
- strict resource read 造成旧测试或 consumer behavior 失败，需要重新评估兼容边界。
- docs-site build/typecheck 暴露路由/Markdown 语法问题。
- 需要真实发布、Central 凭据、远端插件安装或人工 Human Review Confirmation。
