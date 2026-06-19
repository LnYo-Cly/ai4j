# Execution Strategy - P0-B Memory Compact Context Projector

## 执行模式

- 模式：single coordinator in isolated worktree
- Worktree：TARGET:.worktrees/feature/agent-memory-compact-context
- Branch：feature/agent-memory-compact-context
- 提交方式：一个 feature commit + 必要时 lifecycle/governance commit

## 实施策略

1. 先扩展最小 API，不引入外部依赖和 provider 绑定。
2. Runtime 投影只改变 prompt items，不改变 memory 原始写入语义。
3. `ContextReport` 通过 `MEMORY_COMPRESS` 事件暴露给 event publisher、stream listener 和 session event log。
4. Compact policy 以确定性基础策略为主，不伪装成 LLM 总结。
5. Session compact result 通过 defensive copy 保存，避免 snapshot/store 被外部修改污染。
6. Docs 明确当前能力是 foundation，不承诺 P0-C/P1/P2 尚未实现能力。

## 冲突控制

- 不修改 core SDK、extension API、coding runtime、CLI。
- 不改 provider 配置，不写入 token。
- 不改 regression gate 定义；只记录本任务触发 RG-002/RG-008。

## 验证策略

| 层 | 命令 | 目的 |
| --- | --- | --- |
| Targeted | `mvn -pl ai4j-agent "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false test` | 验证 P0-B 新 API 和 session compact 行为 |
| Broad agent | `mvn -pl ai4j-agent -am -DskipTests=false test` | 验证 agent/runtime 兼容性 |
| Docs | `npm run build` in `docs-site/` | 验证 Docusaurus 页面和 sidebar |
| Harness | `npx --yes coding-agent-harness status --json .` | 验证 task package 和队列状态 |

## 停止条件

- 出现需要改 `ai4j-extension-api` lifecycle hook 的需求：转 P0-C。
- 出现需要新增 YAML loader/schema 的需求：转 P1。
- 出现需要真实 sandbox provider 的需求：转 P2/P3。
- 出现 live provider 测试需求：必须显式 opt-in，并且只用 env，不写 token。
