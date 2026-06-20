# P4 CLI sandbox commands

## Task ID

`2026-06-20-p4-cli-sandbox-commands-72f40aa0`

## 创建日期

2026-06-20

## 一句话结果

在 `ai4j-cli` 里提供可见、可切换、可回退的 `/sandbox` 交互入口，让用户能看到当前 CLI session 是否直连宿主机，或已绑定到一个外部 sandbox 会话。

## 完成后能得到什么

完成后，终端用户在 `ai4j` 交互会话里可以输入 `/sandbox`、`/sandbox status`、`/sandbox attach <providerId> <sessionId> [workspaceId]` 和 `/sandbox disable` 来管理当前 CLI session 的 sandbox 绑定状态。该切片不创建真实 VM/container，也不承诺远端执行后端；它只把 P3 已完成的 `CodingAgentBuilder.sandbox(SandboxSession)` 能力接到 CLI/TUI 命令面，明确显示 direct-host 与 attached-sandbox 的边界，并在没有真实 provider bridge 时避免静默把命令落回宿主机。

## 交付物

- 可见产物：CLI/TUI slash command、命令补全、命令面板项、状态输出和文档说明。
- 修改位置：`TARGET:ai4j-cli/**`，必要时更新 `TARGET:docs-site/**`、`TARGET:docs/05-TEST-QA/**` 和本 task package。
- 验证证据：CLI targeted tests、必要的 `ai4j-cli` broad tests、docs-site build、`git diff --check`、Harness status。

## 第一眼应该看什么

1. `task_plan.md`：范围、验收标准和不做真实 provider 的边界。
2. `references/cli-sandbox-command-plan.md`：具体实现方案、命令语义和测试矩阵。
3. `findings.md`：已诊断的代码接缝和被排除的方案。
4. `progress.md`：当前已完成的计划记录和后续执行证据。

## 边界

- 范围内：`/sandbox` 命令族、slash completion、palette/help/status 展示、CLI runtime rebind 接线、文档和回归记录。
- 范围外：创建真实 sandbox、连接 CubeSandbox/容器/VM/浏览器后端、实现远端 Agent Runner、引入新的 Maven 模块、绕过 approval/permission、把 sandbox 当默认执行环境。
- 停止条件：如果要真正连接外部 sandbox provider、改变 `SandboxProvider` SPI、或需要凭据/网络后端，必须暂停并新开 provider/runner 任务。

## 完成判断

- `/sandbox` 和 `/sandbox status` 能稳定展示 direct-host / attached 状态。
- `/sandbox attach <providerId> <sessionId> [workspaceId]` 能记录当前 CLI session 的非敏感绑定，并触发 runtime rebind。
- `/sandbox disable` 能清除绑定并回到 direct-host。
- command completion、palette/help/status 都能发现 sandbox 入口。
- 测试和文档不声称已经拥有真实 sandbox provider 或云端 runner。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并在 closeout 时写入 `walkthrough.md`

## 当前下一步

按 `references/cli-sandbox-command-plan.md` 开始窄切片实现：先加 CLI sandbox 状态模型和 `/sandbox` dispatch，再接 factory runtime rebind，最后补 completion、docs 和 regression。
