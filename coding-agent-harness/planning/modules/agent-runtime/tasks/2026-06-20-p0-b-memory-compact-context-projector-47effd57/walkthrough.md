# 收口记录：P0-B Memory Compact Context Projector

Closeout Status: pending-regression-and-pr

## 摘要

本任务为 `ai4j-agent` 增加 Memory / Compact / Context Projector foundation。它让 runtime prompt 构造前可以按预算投影 memory，让 compact 结果结构化保存，并让 `AgentSession` snapshot/store/resume 保留 compact state。

## 变更范围

| 范围 | 详情 |
| --- | --- |
| 生产代码 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/context/**`; `compact/**`; `AgentBuilder`; `AgentContext`; `AgentSession`; `BaseAgentRuntime`; `CodeActRuntime`; session snapshot/store |
| 测试 | `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentMemoryCompactContextProjectorTest.java` |
| 文档 | `docs-site/docs/agent/memory-compact-context.md`; `session-runtime.md`; `sdk-roadmap.md`; `sidebars.ts` |
| Harness | P0-B task package materials |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted P0-B | `mvn -pl ai4j-agent "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false test` | passed | progress.md |
| Broad agent | `mvn -pl ai4j-agent -am -DskipTests=false test` | passed | progress.md |
| Docs-site | `npm run build` in `docs-site/` | passed | progress.md |
| Harness | `npx --yes coding-agent-harness status --json .` | passed with dirty-state warning only | progress.md |
| PR/CI | GitHub checks | pending | PR |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | CodeActRuntime 原先绕过 projector | 已接入 shared projection path | findings.md F-001 |
| self review | MEMORY_COMPRESS 事件原先未传 listener | 已传入 step/listener | findings.md F-002 |
| self review | 内置 compact 可能被误解成智能语义总结 | docs 明确当前为 deterministic foundation | docs-site page |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 模型语义 compact 未实现 | future owner | yes | custom CompactPolicy / plugin lifecycle 后续任务 |
| token 精确预算未实现 | future owner | yes | tokenizer-backed estimator 后续任务 |
| Sandbox state 只是字段，未接真实 sandbox | future owner | yes | P2 Sandbox SPI |

## Lessons Reflection

- 是否完成经验候选检查：yes
- 经验候选详情文件：`lesson_candidates.md`
- 是否提升共享 lesson：no；本任务没有新增跨任务治理经验。

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 发现记录 | `findings.md` |
| P0-B docs | `docs-site/docs/agent/memory-compact-context.md` |
