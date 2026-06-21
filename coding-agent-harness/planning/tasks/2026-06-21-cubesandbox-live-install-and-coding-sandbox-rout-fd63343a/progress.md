# CubeSandbox live install and coding sandbox routing - 进度

## 状态：审查中

## 进度记录

证据使用 `type:path:summary` 格式。允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

### [2026-06-21 04:45] - task-start

- 做了什么：Start CubeSandbox live install diagnosis and coding sandbox routing verification。
- 验证结果：任务包创建并进入进行中。
- 下一步：诊断 CubeSandbox provider 与 CLI `/sandbox` 旧 metadata-only 行为。
- 证据：command:TASK:task-start:task package moved to in_progress

### [2026-06-21 15:38] - worktree and current diff diagnosis

- 做了什么：确认当前工作树为 `.worktrees/feature/cubesandbox-live-routing`，分支 `feature/cubesandbox-live-routing`，已有两笔 harness 注册/启动提交；只在 worktree 内继续。
- 验证结果：`git status --short --branch` 显示仅 CLI runtime 与新 sandbox resolver/test 文件未提交。
- 下一步：运行 targeted Maven 回归找真实失败点。
- 证据：command:TASK:git status --short --branch showed feature/cubesandbox-live-routing ahead origin/dev by 2 and pending CLI sandbox files

### [2026-06-21 15:40] - first Maven compile failure

- 做了什么：运行最小 CLI sandbox 回归。
- 验证结果：失败于 `CodingCliSessionRunnerSandboxTest` 导入错误：`CodingCliTuiSupport` 实际在 `io.github.lnyocly.ai4j.cli.runtime`，不是 `factory`。
- 下一步：窄修测试 import、reflection 和 TUI runtime test double。
- 证据：command:TARGET:mvn -pl ai4j-cli -am "-Dtest=DefaultCliSandboxSessionResolverTest,CodingCliSessionRunnerSandboxTest,CliAttachedSandboxSessionTest,DefaultCodingCliAgentFactoryTest" -DskipTests=false -DfailIfNoTests=false test:failed at testCompile missing CodingCliTuiSupport symbol

### [2026-06-21 15:44] - minimal CLI sandbox regression pass

- 做了什么：修复测试导入、类型化 `ManagedCodingSession` / `ToolExecutor` 获取、`CodingCliAgentFactory.prepare(options)` test double、TerminalIO throws/visibility。
- 验证结果：最小 CLI sandbox 回归通过；18 tests，0 failures/errors/skipped。
- 下一步：扩大到 agent sandbox + CLI sandbox + argument parsing。
- 证据：command:TARGET:mvn -pl ai4j-cli -am "-Dtest=DefaultCliSandboxSessionResolverTest,CodingCliSessionRunnerSandboxTest,CliAttachedSandboxSessionTest,DefaultCodingCliAgentFactoryTest" -DskipTests=false -DfailIfNoTests=false test:BUILD SUCCESS, 18 tests

### [2026-06-21 15:45] - expanded sandbox regression pass

- 做了什么：运行 `*Sandbox*Test`、`DefaultCodingCliAgentFactoryTest`、`CodingCliSessionRunnerArgumentParsingTest`。
- 验证结果：agent sandbox 16 tests + CLI 21 tests 通过；覆盖 CubeSandbox provider protocol、AgentSession binding、CLI resolver/runtime。
- 下一步：探测本机 live CubeSandbox 环境并更新文档。
- 证据：command:TARGET:mvn -pl ai4j-cli -am "-Dtest=*Sandbox*Test,DefaultCodingCliAgentFactoryTest,CodingCliSessionRunnerArgumentParsingTest" -DskipTests=false -DfailIfNoTests=false test:BUILD SUCCESS, agent 16 tests + CLI 21 tests

### [2026-06-21 15:50] - live environment feasibility check

- 做了什么：检查 `docker`、`wsl`、`qemu-system-x86_64`、`cube/cubesandbox` 命令、CubeSandbox env vars、管理员令牌和官方仓库可访问性。
- 验证结果：`git ls-remote https://github.com/TencentCloud/CubeSandbox.git HEAD` 成功；`docker` 不存在；`wsl.exe` 存在但无可用 Linux 发行版输出；`AI4J_CUBESANDBOX_LIVE`、`CUBE_API_URL`、`CUBE_TEMPLATE_ID`、`CUBE_API_KEY` 均未设置；当前管理员组为 deny-only，不能可靠安装 Docker/WSL/CubeSandbox。
- 下一步：把 live smoke 记录为 `pending-env`，不伪造通过。
- 证据：command:TARGET:Get-Command/docker/wsl/env checks:docker absent, no Cube env vars, wsl has no usable distro; command:TARGET:git ls-remote CubeSandbox HEAD returned 2c079922339f3233fb8733ca3a43672235631605

### [2026-06-21 15:54] - docs-site local dependency restore and build

- 做了什么：首次 `npm --prefix docs-site run build` 因 ignored `node_modules` 缺失失败；随后执行 `npm --prefix docs-site ci` 恢复 worktree 本地依赖，再重跑 build。
- 验证结果：`npm ci` 添加 1289 packages；第二次 build 在 16.3s 内成功并生成 `docs-site/build`。npm audit 报告既有 50 vulnerabilities，未自动改依赖。
- 下一步：跑最终 Java 回归并更新治理记录。
- 证据：command:TARGET:npm --prefix docs-site run build:first failed MODULE_NOT_FOUND for @docusaurus/core; command:TARGET:npm --prefix docs-site ci:success; command:TARGET:npm --prefix docs-site run build:SUCCESS Generated static files in build

### [2026-06-21 15:57] - final CLI/agent sandbox regression pass

- 做了什么：在文档和 CLI 文案修正后重跑最终 Java targeted 回归，加入 `SlashCommandControllerTest`。
- 验证结果：agent sandbox 16 tests + CLI 70 tests 通过；0 failures/errors/skipped。
- 下一步：运行 live-provider opt-in smoke 确认 skip 行为。
- 证据：command:TARGET:mvn -pl ai4j-cli -am "-Dtest=*Sandbox*Test,DefaultCodingCliAgentFactoryTest,CodingCliSessionRunnerArgumentParsingTest,SlashCommandControllerTest" -DskipTests=false -DfailIfNoTests=false test:BUILD SUCCESS, CLI 70 tests and agent 16 tests

### [2026-06-21 15:58] - live CubeSandbox smoke controlled skip

- 做了什么：运行 live-provider profile 下的 `CubeSandboxLiveProviderTest`。
- 验证结果：构建成功，测试 1 个，skipped 1 个；skip 原因是缺少 live env vars，而不是代码失败。
- 下一步：更新 Regression SSoT、Cadence Ledger、review/walkthrough。
- 证据：command:TARGET:mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test:BUILD SUCCESS, Tests run 1 Skipped 1

### [2026-06-21 16:05] - docs and governance sync

- 做了什么：更新 `docs-site/docs/coding-agent/sandbox-routing.md`、`command-reference.md`、`agent/cubesandbox-provider.md`、`real-api-matrix.md`、`sdk-roadmap.md`、`sdk-task-decomposition.md`，并同步 `docs/05-TEST-QA`。
- 验证结果：文档已区分 `attached-live` 与 `attached-metadata-only`，说明 CubeSandbox/cube live attach 只连接已有 session，不创建/认证 provider。
- 下一步：diff hygiene、harness status、提交待审。
- 证据：diff:TARGET:docs-site/docs and docs/05-TEST-QA:CubeSandbox live attach docs/governance updated


### [2026-06-21 16:08] - broad CLI reactor and docs typecheck pass

- 做了什么：补跑更强的 touched-surface baseline：docs-site typecheck 和 `ai4j-cli -am` 完整测试。
- 验证结果：`npm --prefix docs-site run typecheck` 通过；`mvn -pl ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test` 通过，extension API 31、core 103、agent 137、coding 61、CLI 312 tests，0 failures/errors/skipped。
- 下一步：最终 harness status / git status / 提交。
- 证据：command:TARGET:npm --prefix docs-site run typecheck:pass; command:TARGET:mvn -pl ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test:BUILD SUCCESS, CLI 312 tests plus upstream modules

## 残余

- `R-LIVE-ENV`：当前 Windows 会话无法完成真实 CubeSandbox 部署/执行 smoke。原因：Docker 不存在，WSL 无可用 Linux 发行版，CubeSandbox env vars 缺失，当前管理员令牌为 deny-only。Owner：operator。后续动作：在 x86_64 Linux/KVM/Docker 或可用 WSL2+Docker 环境部署 CubeSandbox，设置 `AI4J_CUBESANDBOX_LIVE=true`、`CUBE_API_URL`、`CUBE_TEMPLATE_ID` 和可选 `CUBE_API_KEY` 后重跑 `CubeSandboxLiveProviderTest`。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-review
- Registry update needed：不适用
- Harness Ledger update needed：本任务 closeout 后可由 lifecycle CLI / governance rebuild 统一刷新
- 负责人：coordinator

### [2026-06-21 16:14] - strict review submission repair

- 做了什么：补齐 checker 可解析的严格 ## Agent Review Submission 块，使用 TASKS/2026-06-21-cubesandbox-live-install-and-coding-sandbox-rout-fd63343a 作为 Task Key，并保留既有人工可读 self-review 内容。
- 验证结果：已重新运行 `npx --yes coding-agent-harness status --json .`，确认 missing-materials 队列退出，当前 `reviewQueueState=ready-to-confirm`。
- 下一步：通过 harness status 后提交修复并更新 PR。
- 证据：diff:TASK:review.md/progress.md:strict Agent Review Submission metadata added
