# P0-D Agent approval and permission policy - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### ToolExecutor 是本轮最小可治理边界

- 背景：P0-D 要解决 approval / permission policy，但不能扩成真实 sandbox 或 CLI approval UI。
- 发现：现有 docs-site 已多处强调 `ToolExecutor` 才是工具执行面和权限边界；代码中 `BaseAgentRuntime` 和 `CodeActRuntime` 最终也都通过 context 上的 `ToolExecutor` 执行工具。
- 影响：本轮采用 wrapper 而不是改模型请求、tool schema 或 runtime 主循环，能保持小 API 面并覆盖 ReAct / CodeAct 的共同执行点。
- 后续：P3 `ai4j-coding` sandbox routing 仍需在 coding tool executor 内部处理文件/shell/git/browser 的真实环境映射。

### P0-D 不是 sandbox provider

- 背景：用户讨论中的 sandbox 包含本机 permission sandbox 和真实 VM/容器/远端沙箱两类。
- 发现：P0-D 只能表达执行环境元数据（`LOCAL` / `SANDBOX` / `REMOTE_SANDBOX`）和审批决策，不能提供 VM/container 隔离。
- 影响：文档和 API 命名必须避免让用户误以为配置 `executionEnvironment(REMOTE_SANDBOX)` 就创建了远端沙箱；它只是策略输入。
- 后续：P2 单独设计 `SandboxProvider` / `SandboxSession` / artifact / timeout / cancel 合同。

### wrapper 顺序必须靠近最终执行面

- 背景：agent builder 已有 extension guardrail、subagent executor、routing executor 等包装。
- 发现：permission wrapper 放在已有 executor 链外层，可以对扩展工具、subagent tool、普通工具形成统一最终执行 gate。
- 影响：Builder wiring 应在 extension guardrails 后继续包 `AgentPermissionToolExecutor`。如果后续 team runtime 动态替换 executor，需要单独验证是否仍经过 permission wrapper。
- 后续：在 review residual 中记录 AgentTeam 动态 wrapping 路径的后续验证项。

### 当前 main 工作区出现了 P0-D 实现差异

- 背景：Harness 要求非平凡任务使用专用 worktree；当前主工作区 `main` 同时出现了 P0-D 未提交源码差异。
- 发现：专用 worktree `feature/agent-approval-permission-policy` 已存在，但源码差异实际落在 main 工作区；这是需要先修正的执行状态问题。
- 影响：继续实现前应把差异复制/应用到 P0-D worktree，并恢复 main clean，避免 PR 来源错误和 lifecycle auto-commit 被 dirty-state 阻塞。
- 后续：执行时优先做 worktree 归并与 main 清理。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 执行拦截点 | `ToolExecutor` wrapper | 最小、可测试、覆盖 ReAct/CodeAct 共同执行面 | 改 runtime 主循环；改 tool schema；放到 provider 层 | accepted |
| policy 决策 | allow / deny / require-approval | 对应最小审批状态；require-approval 为 CLI/UI 后续接入保留语义 | 直接 boolean；异常回调；同步阻塞等待人 | accepted |
| execution environment | metadata only | 先为 policy 提供上下文，不假装创建 sandbox | P0-D 实现 sandbox；不提供环境字段 | accepted |
| 模块边界 | 只改 `ai4j-agent` + docs/governance | 避免新增模块和跨模块扩散 | 新增 `ai4j-sandbox`；改 `ai4j-coding` | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| AgentTeam 动态 executor wrapping 是否需要同轮覆盖？ | 不进入 P0-D；记录 residual，后续如 team 工具需要统一 approval 再开任务 | coordinator | P0-D review |
| `require-approval` 在 CLI 中如何交互？ | P4 CLI/TUI 任务设计，不在 P0-D 同步阻塞等待用户 | coordinator | P4 |
| Blueprint YAML 字段名是 `approval` 还是 `permission`？ | P1/P4 设计时再定；P0-D 只提供 Java API | coordinator | P1-A/P4-C |
