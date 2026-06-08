# AI4J extension authoring and validation wave 8 - 执行策略

## Subagent Authorization

本任务由 coordinator 在当前 checkout 中完成；没有授权可写 worker subagent。只读审查由 coordinator 自审材料包完成，最终 Human Review Confirmation 仍由人确认。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | current checkout / main | allowed within this task |
| worker subagent | not used | no write permission | coordinator decision | 2026-06-09 | Wave 8 extension validation task package and touched implementation surfaces | current checkout / main | not used |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 变更范围虽跨 extension API、CLI、docs-site 和治理文件，但实现已经由公共 API 测试、CLI 定向测试、monorepo package、docs-site build 和自审材料覆盖；额外只读 reviewer 不会带来新的独立证据。 | 保留自审报告并等待 Human Review Confirmation。 |
| Would a worker subagent materially help? | no | 本轮改动集中在同一插件生态闭环，公共 validator、CLI 输出和文档措辞需要同一个 owner 保持语义一致；拆 worker 会增加协调成本。 | coordinator 直接实现、验证、提交。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-09 | Wave 8 extension validation task package and touched implementation surfaces | current checkout / main | 未使用可写 worker；用户要求继续一起做完。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责实现、测试、文档和 harness 收口。 |
| Subagent 模式 | none | 不派可写 worker；Human Review Confirmation 由人完成。 |
| 审查模型 | self-check + human review queue | 本轮已写 `review.md` 自审材料，`task-review` 后进入人工确认队列。 |
| Worktree 策略 | same checkout | 当前分支为 `main`，用户要求继续并推送；没有并行 worker 需要独立 worktree。 |
| 冲突控制 | coordinator owns shared files | 共享文件只包括 README、docs-site 插件文档、Regression SSoT、Cadence Ledger 和 Feature SSoT，由 coordinator 统一更新。 |
| 证据深度 | L2 | 公共 API + CLI + docs-site + monorepo package smoke 足以覆盖本轮变更；真实第三方插件市场和安全审计不在本轮。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| coordinator | C-001, C-002, C-003, C-004 | `ai4j-extension-api/`, `ai4j-cli/`, `docs-site/docs/core-sdk/extension/`, `README.md`, regression/governance docs, current task package | feature commit + harness review submission | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` / `review.md` | 无 whitespace error；CRLF warning 可接受。 |
| L1 | `mvn -pl ai4j-extension-api -DskipTests=false test` | `progress.md` / `review.md` | Extension registry 与 validator 测试全部通过。 |
| L1 | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | `progress.md` / `review.md` | CLI extension validate、list、inspect、run、resource 相关测试全部通过。 |
| L2 | `mvn -DskipTests package` | `progress.md` / `review.md` | 10 个 reactor modules package 通过。 |
| L2 | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `npm run build` in `docs-site/` | `progress.md` / `review.md` | docs-site typecheck 和 Docusaurus production build 通过。 |
| L2 | `npx.cmd --yes coding-agent-harness status --json .` | `progress.md` / final summary | 无 harness failure；dirty-state 仅在提交前出现。 |

## 暂停 / 升级条件

- 需要把 validation 扩展成第三方代码安全审计、签名、信誉或远程市场审核。
- 需要运行时 jar 热加载、自动安装插件或绕过 classpath discovery。
- 需要改变插件 enable / exposeTool 门禁，导致 classpath discovery 自动暴露模型工具。
- Java 8 兼容或 CLI 现有 `list / inspect / run / resource` 行为出现回归。
- broad full-suite 的既有 R-008 blocker 被要求在同一任务内修复。
