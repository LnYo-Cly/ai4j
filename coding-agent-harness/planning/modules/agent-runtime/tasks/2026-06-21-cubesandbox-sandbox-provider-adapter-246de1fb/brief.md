# CubeSandbox sandbox provider adapter

## Task ID

`2026-06-21-cubesandbox-sandbox-provider-adapter-246de1fb`

## 创建日期

2026-06-21

## 一句话结果

在 `ai4j-agent` 中提供一个可选的 CubeSandbox 真实远端 sandbox provider，让宿主能通过现有 Sandbox SPI 创建/连接 CubeSandbox 并执行命令。

## 完成后能得到什么

本任务完成后，AI4J 的 Agent runtime 不再只有抽象 Sandbox SPI：宿主代码可以显式使用 `CubeSandboxProvider` 创建或连接 CubeSandbox session，通过 `SandboxSession.execute(...)` 执行 `/bin/bash -l -c` 命令，并拿到 stdout、stderr、exitCode、duration、artifact 与事件。文档站会说明配置、生命周期、密钥边界、本地协议级验证和 live smoke 运行方式；回归治理会记录本地必跑 gate 与 live-provider opt-in gate。

## 交付物

- 可见产物：CubeSandbox Provider Java adapter、协议级本地测试、live opt-in smoke test、docs-site CubeSandbox Provider 文档、Regression SSoT/Cadence 更新。
- 修改位置：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/cubesandbox/`、`ai4j-agent/src/test/java/io/github/lnyocly/agent/cubesandbox/`、`docs-site/docs/agent/`、`docs/05-TEST-QA/`、本任务包。
- 验证证据：`progress.md` 中记录 Maven targeted/broad、docs build、diff check、Harness status、live env availability。

## 第一眼应该看什么

1. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/cubesandbox/CubeSandboxProvider.java`
2. `ai4j-agent/src/test/java/io/github/lnyocly/agent/cubesandbox/CubeSandboxProviderTest.java`
3. `docs-site/docs/agent/cubesandbox-provider.md`
4. `review.md` 的 subagent review / confidence challenge
5. `walkthrough.md` 的最终验证表

## 边界

- 范围内：CubeSandbox create/connect/delete + envd Connect process execution adapter、命令级 sandbox session、非敏感配置处理、协议级测试、live opt-in test hook、文档与回归治理。
- 范围外：把 `ai4j-coding` 的 file/shell/git/browser 全量路由到 CubeSandbox；Jupyter code API、files API、snapshot/rollback、browser capability、云端 runner 托管平台。
- 停止条件：需要真实 CubeSandbox 部署但环境变量缺失时，记录 `skipped/pending-env`；不得伪造 live 通过，也不得打印或提交 secret。

## 完成判断

- `CubeSandboxProviderTest` 覆盖 create/connect/delete、proxy-node Host 保持、Connect envelope、stdout 解码、错误帧、partial frame 和 close lifecycle。
- `CubeSandboxLiveProviderTest` 以 `LiveProviderTest` category 隔离，只有 `AI4J_CUBESANDBOX_LIVE=true` 且必需 env 存在才运行。
- docs-site 新增 CubeSandbox Provider 页面，并从 Agent Sandbox SPI / sidebar 可达。
- Regression SSoT 与 Cadence Ledger 标明 RG-002、RG-008 与 LV-002 关系。
- `git diff --check`、targeted/broad Maven、docs build、Harness status 证据记录到 `progress.md` 和 `walkthrough.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`
- 完成条件：验证证据必须记录到 `progress.md`，Agent Review Submission 后等待人工确认/PR CI。

## 当前下一步

完成 broad Maven、docs build、diff/harness 检查，更新 walkthrough/review 后提交并推送 PR。
