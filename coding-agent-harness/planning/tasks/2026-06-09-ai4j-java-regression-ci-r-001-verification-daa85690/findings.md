# AI4J Java regression CI R-001 verification - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### R-001 远端状态

- 背景：R-001 要求 Java PR workflow 的首次绿灯与 required branch protection 都有远端证据。
- 发现：`gh auth status` 已登录 `LnYo-Cly` 且具备 repo/workflow 权限；`gh run list --workflow java-regression.yml --limit 10` 只返回 2026-04-26/27 两次失败的 PR run；`gh api repos/LnYo-Cly/ai4j/branches/main/protection` 和 `.../branches/dev/protection` 均返回 `Branch not protected`。
- 影响：R-001 不能只靠本地 Maven gate 关闭，必须先让 workflow 具备可手动触发和稳定 required-check 名称，再跑出远端 green run 并配置 branch protection。
- 后续：已完成。push run `27202972949` 在 `main@41ca7bd` 上通过，`java-regression` 聚合 job 成功；`main` 和 `dev` branch protection 均已要求 strict `java-regression` required status check。

### Java CI 覆盖面缺口

- 背景：AGENTS.md 把 `ai4j-flowgram-demo/` 纳入 Java monorepo 范围，Cadence Ledger 也要求 FlowGram demo backend 命中 RG-006 / RG-007。
- 发现：父 POM 包含 `ai4j-flowgram-demo`，但旧 `.github/workflows/java-regression.yml` 的 PR paths 和 module matrix 都没有覆盖 `ai4j-flowgram-demo/`。
- 影响：FlowGram demo backend 变更可能绕过 Java PR workflow，不足以支撑 R-001 的“monorepo CI”结论。
- 后续：已完成。`.github/workflows/java-regression.yml` 已将 `ai4j-flowgram-demo` 纳入 change detection 和 module matrix；本地 demo/starter gate 通过，远端 run `27202972949` 中 `module-tests (ai4j-flowgram-demo)` 通过。

### ai4j-cli Linux/JDK8 CI 稳定性

- 背景：第一次 push run `27201785049` 中 `module-tests (ai4j-cli)` 在 Ubuntu/JDK8 失败，导致聚合 `java-regression` 失败。
- 发现：`CodeCommandTest` 的 fake bash 命令使用 Windows-only `type sample.txt`；Ubuntu 下 `type` 是 shell command lookup builtin，不能读取文件内容。`JlineShellTerminalIOTest` 使用 `TerminalBuilder` 构建测试终端，Linux CI 中走 `PosixPtyTerminal` pump 线程，导致 ByteArrayOutputStream 输出捕获和 interrupt watch 断言不稳定。
- 影响：这是 CI 环境差异暴露出的测试夹具问题，不是 CLI 业务逻辑变更目标。R-001 不能关闭，直到 `ai4j-cli` 在远端 matrix 中通过。
- 后续：已将 fake bash 样例读取命令改为 OS-aware，并将 JLine 单元测试固定到直接 `DumbTerminal` 夹具；本地 `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` 通过，远端 `module-tests (ai4j-cli)` 在 run `27202972949` 中通过。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Required check 形态 | 增加稳定聚合 job `java-regression` | GitHub branch protection 绑定单一稳定 check，比绑定每个 matrix job 更抗模块增删和名称漂移 | 直接要求 `package-smoke` 和所有 `module-tests (...)` contexts | accepted |
| Workflow 触发 | 保留 PR 触发，同时增加 `push` 和 `workflow_dispatch` | R-001 需要首次 green run 证据；PR-only workflow 在没有新 PR 时无法主动验证 | 只等待下一次真实 PR | accepted |
| 非 Java 改动处理 | workflow 总是触发，内部 detect job 决定是否运行 matrix；聚合 job对非 Java surface 直接 pass | branch protection required check 需要对所有 PR 都可用，否则 docs-only PR 会缺 required check | 保留 workflow-level path filter | accepted |
| ai4j-cli Linux 测试修复方式 | 修正测试夹具的跨平台假设，不改 CLI 运行时行为 | 失败来自测试命令和 JLine terminal 构造差异；业务实现未出现远端行为缺陷 | 在 workflow 上跳过失败测试或只在 Windows 运行 CLI 测试 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| `java-regression` 远端首次绿灯 | 已确认：run `27202972949` completed success，聚合 job `java-regression` 成功 | coordinator | closed 2026-06-09 |
| `main` / `dev` branch protection | 已确认：GitHub API 返回 `required_status_checks.strict=true` 且 contexts 为 `java-regression` | coordinator | closed 2026-06-09 |
