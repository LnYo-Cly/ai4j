# P0-A AgentSession runtime container - Execution Strategy

## 执行模式

- 模式：single coordinator in isolated feature worktree。
- Worktree：TARGET:.worktrees/feature/agent-session-runtime-container。
- Branch：feature/agent-session-runtime-container。
- Commit：完成验证后提交 feature commit，并进入 PR。

## 模块边界

| Surface | Action | Boundary |
| --- | --- | --- |
| `ai4j-agent` | 修改生产代码和测试 | owner module，允许 |
| `docs-site` | 更新技术文档 | 本任务明确要求更新 docs-site，允许 |
| `ai4j-coding` / `ai4j-cli` | 不修改 | sandbox/CLI 属后续 P3/P4 |
| `ai4j-extension-api` | 不修改 | plugin lifecycle 属后续 P0-C |

## 设计取舍

1. 保留 `Agent.run(...)` 原语义，避免一次性运行用户被迫进入 session 模型。
2. `Agent.newSession()` 是长程容器入口；每次调用使用 `memorySupplier` 创建独立 memory。
3. event log 作为 session-local listener 接入 runtime 事件，不重写 runtime 发布逻辑。
4. session store 只保存 snapshot；生产持久化由使用者实现 `AgentSessionStore`。
5. 默认 memory snapshot/restore 放入接口，保证自定义 memory 可以最小接入；已有主实现类继续覆盖 summary 精确保留。

## 验证策略

| Gate | Command | Why |
| --- | --- | --- |
| targeted test | `mvn -pl ai4j-agent "-Dtest=AgentSessionRuntimeContainerTest" -DskipTests=false test` | 快速证明新增合同 |
| owner module broad | `mvn -pl ai4j-agent -am -DskipTests=false test` | RG-002 touched-surface gate |
| docs build | `npm run build` in `docs-site/` | RG-008 docs-site gate |
| harness status | `npx --yes coding-agent-harness status --json .` | task materials and lifecycle gate |

## 风险控制

- 不打印、不落盘用户给出的 live provider token。
- 不引入真实 provider 网络调用；全部测试用 local fake model client。
- 对 docs 新文件使用 `git add -f`，因为 repo `.gitignore` 的 `docs/` 规则会忽略 `docs-site/docs/**` 新文件。
- 如果 broad test 暴露既有 live-provider 或环境问题，按 testing standard 区分 local-required failure 与 opt-in residual。
