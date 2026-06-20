# P3 Coding sandbox tool routing - Brief

## 任务摘要

在 `ai4j-coding` 中落地 P3 sandbox routing 的第一个可验证切片：宿主可以通过 `CodingAgentBuilder.sandbox(SandboxSession)` 给 Coding Agent 绑定 live sandbox；新建 `CodingSession` 时底层 `AgentSession` 保存非敏感 sandbox binding；内置 `bash action=exec` 通过 `SandboxShellCommandExecutor` 调用 `SandboxSession.execute(SandboxCommand)`，未绑定 sandbox 时继续本地执行。

## 背景

P2-A/P2-B 已经在 `ai4j-agent` 中提供 Sandbox SPI 和 `AgentSessionSandboxBinding`。本任务承接 Agent SDK roadmap 的 P3：让 `ai4j-coding` 的执行型工具开始感知 sandbox，但首切片必须保持边界可控，不能误称所有工具都已远端化。

## 目标

- `bash action=exec` 支持 sandbox routing。
- 无 sandbox 时本地 shell 行为不变。
- Coding session 创建时自动把 sandbox 摘要绑定到 delegate `AgentSession`。
- docs-site 写清楚当前已实现能力与后续未实现边界。
- 固定回归治理记录更新到 RG-003 / RG-008 / SRB-057。

## 非目标

- 不实现真实 Docker / CubeSandbox / E2B / K8s provider。
- 不把 `read_file` / `write_file` / `apply_patch` 路由到 sandbox。
- 不把 `bash start/status/logs/write/stop/list` 远端化。
- 不实现 CLI `/sandbox` 命令或 TUI 展示；那是 P4。
- 不使用、保存或打印任何 provider token。

## 验证摘要

- Targeted coding regression：`mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` passed，14 tests。
- Broad coding regression：`mvn -pl ai4j-coding -am -DskipTests=false test` passed，extension API 25、core 103、agent 119、coding 61 tests。
- Docs build：第一次因 worktree 缺少 ignored `docs-site/node_modules` 失败；`npm --prefix docs-site install` 后 `npm --prefix docs-site run build` passed。

## 当前状态

实现和本地验证已完成，等待 final diff check、Harness status、提交、PR、CI 和合并。
