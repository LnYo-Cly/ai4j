# CubeSandbox live install and coding sandbox routing

## Task ID

`2026-06-21-cubesandbox-live-install-and-coding-sandbox-rout-fd63343a`

## 创建日期

2026-06-21

## 一句话结果

把 `ai4j-cli` 的 `/sandbox attach cubesandbox|cube <sessionId> [workspaceId]` 从旧的 metadata-only 摘要升级为连接已有 CubeSandbox live session，并补齐文档、回归和真实环境 blocker 记录。

## 完成后能得到什么

开发者可以在 CLI/TUI 当前会话中 attach 到一个已经由外部系统创建好的 CubeSandbox session；attach 成功后，coding agent 的 foreground `bash action=exec` 会通过 live `SandboxSession.execute(...)` 进入 CubeSandbox，而不是只保存摘要。非 Cube provider 仍保持 metadata-only 且失败时不回退本地执行。文档同步说明 CLI 不创建、不认证 CubeSandbox，真实部署依赖外部 CubeAPI/template/session。当前 Windows 环境无法完成本地 CubeSandbox 部署和 live smoke，已作为 `pending-env` 残余记录。

## 交付物

- 可见产物：CLI live attach resolver、CLI runtime lifecycle/rollback/close 处理、docs-site CubeSandbox/live attach 文档、Harness closeout 记录。
- 修改位置：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/**`、`ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/**`、`docs-site/docs/**`、`docs/05-TEST-QA/**`、本任务包。
- 验证证据：Maven targeted tests、docs-site build、live provider opt-in skipped evidence、环境探测、diff hygiene。

## 第一眼应该看什么

1. `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/sandbox/DefaultCliSandboxSessionResolver.java`
2. `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java`
3. `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunnerSandboxTest.java`
4. `docs-site/docs/coding-agent/sandbox-routing.md`
5. `docs-site/docs/agent/cubesandbox-provider.md`

## 边界

- 范围内：已有 CubeSandbox session 的 CLI live attach；runtime switch rollback；session close lifecycle；非 Cube provider metadata-only no-local-fallback；docs/governance 更新；本机安装可行性探测。
- 范围外：CLI 创建/销毁 CubeSandbox、Docker/K8s/E2B provider、把 file/git/browser/长进程全量路由到 sandbox、远端 Agent Runner 产品化、写入任何真实 key。
- 停止条件：缺少 Docker/WSL Linux/KVM/Cube env vars 时停止 live smoke，不伪造通过；若 runtime switch 失败必须回滚 binding/session 并关闭新 session。

## 完成判断

- [x] `/sandbox attach cubesandbox|cube ...` 经 resolver 连接 live `CubeSandboxProvider.connect(...)`。
- [x] attach 失败或 runtime switch 失败不会留下错误 binding，也不会回退成本地执行。
- [x] `/sandbox disable` 和 `run()` 退出会关闭 CLI 持有的 live session handle。
- [x] 非 Cube provider 仍 metadata-only，执行时明确失败。
- [x] 目标 Maven 回归、docs-site build、live opt-in skip、Harness/治理记录完成。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`
- 完成条件：验证证据记录到 `progress.md`，review 无开放 material finding，walkthrough 引用残余。

## 当前下一步

等待人工确认或 PR/CI；如要完成真实 CubeSandbox smoke，需要先在 Linux/KVM/Docker 环境部署 CubeSandbox 并设置 `AI4J_CUBESANDBOX_LIVE=true`、`CUBE_API_URL`、`CUBE_TEMPLATE_ID` 和可选 key。
