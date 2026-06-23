# P4 CLI sandbox commands and status UX

## Task ID

`2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6`

## 创建日期

2026-06-21

## 一句话结果

让 `ai4j-cli code` 在交互会话中提供 `/sandbox` 命令，用 Daytona 创建、绑定、查看和关闭当前 coding agent 的远端 sandbox execution session。

## 完成后能得到什么

用户可以在 CLI/TUI 会话里输入 `/sandbox status` 看到当前执行位置；输入 `/sandbox enable daytona ...` 或 `/sandbox attach daytona ...` 后，后续 coding agent 的 shell `exec` 能通过 `ai4j-coding` 已有的 `CodingAgentBuilder.sandbox(SandboxSession)` 链路进入 Daytona sandbox；输入 `/sandbox disable` 后回到本地主机执行。该任务只做 CLI host 接线和可见 UX，不把文件工具、MCP/browser、后台 process lifecycle 一次性迁移到 sandbox。

## 交付物

- 可见产物：`/sandbox` help、palette/completion、status、enable、attach、disable 输出。
- 修改位置：`ai4j-cli/**`；只在必要时触及 `ai4j-agent`/`ai4j-coding` 的既有 public consumption path。
- 验证证据：CLI targeted tests、CLI module tests、可选 live-provider smoke（env-only，缺凭证时记录 skip/residual）。

## 第一眼应该看什么

1. `task_plan.md` 的命令合同与范围边界。
2. `findings.md` 的代码事实和真实 sandbox 范围说明。
3. `review.md` / `walkthrough.md` 的最终验证证据。

## 边界

- 范围内：CLI slash command、TUI/main-buffer status rendering、agentFactory rebind 到 `SandboxSession`、Daytona provider env-only session creation。
- 范围外：新增公共 SandboxProvider registry、远端 file tools、MCP/browser sandbox 化、后台 process start/status/logs/write/stop 的远端 lifecycle、把 API key 写入命令或文档。
- 停止条件：如果需要修改公共 SPI、提交密钥、或 live provider 需要用户环境外的凭证，必须记录 residual 或回到用户确认。

## 完成判断

- `/sandbox` 出现在 slash palette/help/completion 中。
- `/sandbox status` 在无 sandbox 时明确显示 direct-host/local；在启用后显示 provider/session/status/deleteOnClose/workspace。
- `/sandbox enable|attach daytona` 使用 env/config 创建 live `SandboxSession`，并通过 `switchSessionRuntime` 让后续 agent shell exec 走 sandbox runtime。
- `/sandbox disable` close 当前 sandbox session，重建 direct-host runtime，并不误删未声明 deleteOnClose 的会话。
- Targeted + module regression 通过，Harness task packet 无模板占位。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md` 和 `walkthrough.md`

## 当前下一步

已完成 CLI /sandbox 实现、targeted + broad RG-004 回归和治理记录；下一步是提交推送，并等待需要时的人工确认。
