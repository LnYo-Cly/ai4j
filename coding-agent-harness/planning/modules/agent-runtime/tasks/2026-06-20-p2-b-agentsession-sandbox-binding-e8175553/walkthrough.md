# P2-B AgentSession sandbox binding - Walkthrough

## 摘要

P2-B 把 P2-A Sandbox SPI 的非敏感 session 摘要绑定到 `AgentSession`，让 snapshot/store/restore/event log 能表达当前 sandbox 状态。它不启动真实 sandbox，不保存 secret，不路由 coding tools。

## 范围

| 范围 | 详情 |
| --- | --- |
| Java module | `ai4j-agent` |
| Docs | `docs-site/docs/agent/sandbox-spi.md`, `docs-site/docs/agent/sdk-roadmap.md` |
| Regression | `docs/05-TEST-QA/Regression-SSoT.md`, `docs/05-TEST-QA/Cadence-Ledger.md` |
| 不在范围 | real provider, plugin provider contribution, `ai4j-coding` routing, CLI `/sandbox` |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| targeted | `mvn -pl ai4j-agent -am "-Dtest=AgentSessionSandboxBindingTest" -DskipTests=false -DfailIfNoTests=false test` | pass, 4 tests | `progress.md` |
| broad agent | `mvn -pl ai4j-agent -am -DskipTests=false test` | pending | `progress.md` |
| docs-site | `npm --prefix docs-site run build` | pending | `progress.md` |
| harness | `npx --yes coding-agent-harness status --json .` | pending | `progress.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 真实 provider 仍未实现 | future P2-C/provider owner | yes | provider contribution / demo provider task |
| coding tools 仍不会自动路由到 sandbox | future P3 owner | yes | P3 ai4j-coding sandbox routing |
| CLI/TUI 仍不显示 sandbox 状态 | future P4 owner | yes | P4 CLI/TUI `/sandbox` UX |

## Lessons Reflection

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否需要提升共享 lesson？ | no；需等 P2-C/P3/P4 验证同一敏感信息过滤规则后再沉淀。 |

Closeout Status: pending-review
