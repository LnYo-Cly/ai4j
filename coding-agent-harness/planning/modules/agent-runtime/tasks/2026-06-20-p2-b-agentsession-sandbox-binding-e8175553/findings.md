# P2-B AgentSession sandbox binding - 发现

## Findings

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-001 | P1 | Sandbox binding 只能保存非敏感摘要，不能保存 `SandboxSpec.config`。 | `AgentSessionSandboxBinding` class comment and tests | 过滤 config；敏感 label key 不进入 snapshot。 | done |
| F-002 | P2 | P2-B 只解决 session/snapshot/store/event log 可见性，不路由 coding tools。 | task_plan scope / docs-site boundary | P3 再实现 file/shell/git/browser routing。 | open-follow-up |
| F-003 | P2 | sandbox 状态事件需要进入 session event log，供后续 CLI/TUI 展示。 | `SANDBOX_BOUND` / `SANDBOX_UPDATED` / `SANDBOX_CLEARED` | P4 可读取 session event log 呈现 `/sandbox status`。 | open-follow-up |

## Residuals

- 不实现真实 sandbox provider。
- 不接插件 provider contribution。
- 不改 `ai4j-coding` 或 `ai4j-cli`。
