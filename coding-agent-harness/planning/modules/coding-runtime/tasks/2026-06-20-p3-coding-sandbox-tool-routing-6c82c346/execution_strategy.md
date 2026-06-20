# P3 Coding sandbox tool routing - 执行策略

## 执行模式

- 模式：single worktree implementation
- Worktree：`G:/My_Project/java/ai4j-sdk/.worktrees/feature/coding-sandbox-routing`
- Branch：`feature/coding-sandbox-routing`
- Coordinator：当前 agent
- Subagent：未使用；本切片范围可由 coordinator 完成并通过 targeted/broad regression 验证

## 切片边界

本轮只做 P3 的第一实现切片：

```text
CodingAgentBuilder.sandbox(SandboxSession)
  -> CodingSandboxRuntime
  -> CodingAgent.newSession()
  -> AgentSession.bindSandbox(...)
  -> BashToolExecutor(action=exec)
  -> SandboxShellCommandExecutor
  -> SandboxSession.execute(SandboxCommand)
```

不修改 `read_file`、`write_file`、`apply_patch`、后台进程、browser、git/project run/test runner。

## 冲突控制

- 不在 main checkout 修改；若工具误写 main，必须复制到 worktree 并恢复 main。
- 不改 `ai4j-agent` Sandbox SPI 公共合同，避免破坏 P2-A/P2-B。
- 不改 CLI/TUI；P4 再做 `/sandbox`。
- 不使用真实 provider token 或 live provider 测试。

## 验证深度

| Gate | Command | Scope | Status |
| --- | --- | --- | --- |
| targeted coding tests | `mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` | direct executor + agent loop | passed, 14 coding tests |
| broad coding tests | `mvn -pl ai4j-coding -am -DskipTests=false test` | RG-003 | passed, extension API 25 / core 103 / agent 119 / coding 61 |
| docs build | `npm --prefix docs-site run build` | RG-008 | passed after local ignored dependency install |
| diff hygiene | `git diff --check` | whitespace/path hygiene | passed; no whitespace errors |
| harness status | `npx --yes coding-agent-harness status --json .` | task lifecycle/materials | 0 failures; dirty warning until commit |

## Handoff

本任务完成后，PR 应说明：

- 首切片只覆盖 `bash exec` sandbox routing。
- 文件/patch/browser/git/project-run 仍是后续 P3/P4/P5 路线。
- no sandbox 行为保持本地执行。
- docs build 第一次失败是 worktree 缺 ignored `node_modules`，依赖恢复后通过。
