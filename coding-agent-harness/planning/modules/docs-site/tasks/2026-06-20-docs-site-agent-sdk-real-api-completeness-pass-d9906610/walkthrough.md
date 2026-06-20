# 收口记录：docs site agent sdk real api completeness pass

## 摘要

本任务为 docs-site Agent 章节新增 `Agent SDK 真实 API 能力矩阵`，并把它接入 Agent sidebar、overview、quickstart。矩阵按当前 `dev` 源码已存在的类、接口和 CLI 命令组织，明确区分可直接使用、SPI/合同已存在、Host/CLI 绑定中、规划中，避免把未实现 fluent API 或真实 provider 写成已可用能力。同时修正 `reference-core-classes.md` 里 `AgentSession` 过期的“只切换 memory”描述。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `docs-site` + task-local Harness 材料 |
| 新增文件 | `docs-site/docs/agent/real-api-matrix.md` |
| 删除文件 | 无 |
| 不在范围内 | Java API、真实 provider/sandbox/runner、全站 docs IA、npm dependency upgrades |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Typecheck | `npm run typecheck` in `docs-site` | passed | `progress.md` |
| Build | `npm run build` in `docs-site` | passed | `progress.md` |
| Whitespace | `git diff --check` | passed | `progress.md` |
| Fake API / secret scan | `rg fake-api-patterns and provider-token-patterns` | no secret; anti-pattern references intentional | `progress.md` |
| Harness | `npx --yes coding-agent-harness status --json .` | failures=0; dirty warning before commit | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 无 P0/P1/P2 finding | 可提交 PR，CI 后合并 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 全站其他章节仍可能需要类似真实 API 矩阵 | coordinator | yes | 后续 docs-site completeness pass |
| AGENTS.md 引用的部分 numbered reference 文件在 `origin/dev` 缺失 | coordinator | yes | 后续 harness/reference repair task |
| PR CI 未运行 | coordinator | no | PR 后 watch checks |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 新增文档 | `docs-site/docs/agent/real-api-matrix.md` |
