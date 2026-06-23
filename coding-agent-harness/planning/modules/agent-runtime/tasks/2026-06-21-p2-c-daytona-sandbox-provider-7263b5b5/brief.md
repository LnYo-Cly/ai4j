# P2-C Daytona sandbox provider

## Task ID

`2026-06-21-p2-c-daytona-sandbox-provider-7263b5b5`

## 创建日期

2026-06-21

## 一句话结果

为 `ai4j-agent` 提供一个可直接对接 Daytona 的 `SandboxProvider`，支持创建/附加沙箱、执行命令、关闭清理，并用真实 Daytona live smoke 验证可用性。

## 完成后能得到什么

项目得到一个真实可用的 Daytona 沙箱接入点：宿主可以基于 `SandboxSpec` 和环境变量直接创建 `DaytonaSandboxProvider`，在 Daytona 沙箱内执行 `SandboxCommand`，并把 `cwd`、`stdin`、`env`、`timeout` 和退出码/输出结果带回 `SandboxResult`。这个结果可以继续喂给 `AgentSession` / coding runtime，作为后续 sandbox 路由和 CLI/TUI 体验的基础。

## 交付物

- 可见产物：`DaytonaSandboxProvider` / `DaytonaSandboxSession` / `DaytonaSandboxClient` / DTO 类，及对应单测与 live smoke。
- 修改位置：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/daytona/**`、`ai4j-agent/src/test/java/io/github/lnyocly/agent/daytona/**`。
- 验证证据：本任务的 `progress.md`、`review.md`、`walkthrough.md` 和 `artifacts/INDEX.md`。

## 第一眼应该看什么

先读 `task_plan.md`、`progress.md`、`review.md`，再看 `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/daytona/` 下的实现与 `ai4j-agent/src/test/java/io/github/lnyocly/agent/daytona/` 下的本地/真实验证。

## 边界

- 范围内：Daytona provider、session、client、config、request/response DTO、Daytona 相关测试、任务包收口。
- 范围外：`ai4j-cli`、`ai4j-coding` 进一步路由、ServiceLoader/provider registry、其它 sandbox provider、docs-site 大改（仅同步 Sandbox SPI/Roadmap 小节）。
- 停止条件：Daytona API 字段/行为与本地验证冲突，或真实 live smoke 无法继续复现时，必须停下记录残余。

## 完成判断

- `DaytonaSandboxProvider` 可以按 `SandboxSpec` 创建或附加 sandbox，并在需要时启动它。
- `SandboxCommand` 的 `command`、`cwd`、`stdin`、`env`、`timeout` 能被正确下发到 Daytona 执行面。
- `deleteOnClose=false` 时，创建出来的 sandbox 不会在 close 时被删除。
- 本地 deterministic 测试与真实 Daytona live smoke 都通过。
- `ai4j-agent` 广播回归通过，且任务包里有 review / walkthrough 收口。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

整理 review / walkthrough / ledger 收口并保留真实验证证据。
