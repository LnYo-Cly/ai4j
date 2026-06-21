# P4 CLI sandbox commands and status UX - 发现记录

## F-001 CLI slash dispatch 入口

`CodingCliSessionRunner.dispatchInteractiveInput(...)` 是交互命令分发点。`/provider`、`/model`、`/stream` 等会调用 `switchSessionRuntime(...)` 重建当前 `ManagedCodingSession`，因此 `/sandbox enable|attach|disable` 应复用这一机制，而不是另起一套 runner。

## F-002 ai4j-coding 已有 SandboxSession 消费链

`CodingAgentBuilder.sandbox(SandboxSession)` 会构造 `CodingSandboxRuntime`，`CodingAgent.newSession(...)` 会把 sandbox 绑定到 `AgentSession`，shell `exec` 由 `SandboxShellCommandExecutor` 执行。因此 CLI 侧只需要把 live `SandboxSession` 传入 factory；不应把 Daytona 逻辑放入 `ai4j-coding`。

## F-003 当前任务首批 provider 选择 Daytona

P2-C 已提交 Daytona SandboxProvider，支持 env-only credential、create-or-attach、start/poll、toolbox process execute 和 deleteOnClose。P4 首批只接 Daytona，避免在同一任务里扩展 provider registry、E2B/Daytona/Cube 多 provider UX。

## F-004 明确非 sandbox 化边界

本任务只承诺后续 agent shell `exec` 进入 sandbox。当前 file tools、apply_patch、MCP/browser、后台 process `start/status/logs/write/stop/list` 仍是本地主机或现有 MCP runtime。若要做到“整个 Agent Runner 跑在沙箱”，需要后续远端 workspace/file/process/browser contract。

## F-005 凭证与真实验证

CLI 不接受 raw API key 参数，不把用户提供的 key 写入文档/命令/测试。live-provider smoke 只能在环境变量已配置且不打印值的前提下运行；缺失则记录为 opt-in residual。


## F-006 P4 实现事实

本轮实现只在 CLI host 层创建 sandbox binding：`CliSandboxCommand` 负责 shell-like 参数解析，`CliSandboxSessionResolver` 只支持 Daytona 并从 env/local config 构造 provider，`CodingCliSessionRunner` 在 enable/attach 成功后把 `activeSandboxSession` 传给 `switchSessionRuntime(...)`。如果 rebind 失败，新 session 会被 close 并恢复旧 binding；disable 先清空 active binding 再重建 direct-host runtime，失败则恢复旧 binding。

## F-007 回归事实

Targeted CLI sandbox test set 通过 61 tests，broad `mvn -pl ai4j-cli -am -DskipTests=false test` 通过到 CLI 298 tests。当前 shell 没有 Daytona env credential，因此 live rerun 被记录为 opt-in skip；不影响本地 RG-004 baseline。


## F-008 同一远端 sandbox reattach 边界

Review 时补充了同一 provider/session id 的 reattach 保护：当用户从一个 Daytona handle 切换到同一远端 sandbox 的新 handle 时，CLI 不 close 旧 handle，避免旧 handle 的 `deleteOnClose` 策略误删刚 attach 的远端 sandbox。`CodingCliSessionRunnerSandboxTest.sandboxReattachSameRemoteSessionDoesNotClosePreviousHandle` 覆盖该边界。
