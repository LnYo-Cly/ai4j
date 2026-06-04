# live provider test hygiene

## Task ID

`2026-06-04-live-provider-test-hygiene-c392a468`

## 创建日期

2026-06-04

## 一句话结果

为 core / agent / coding 的真实 provider 测试建立默认隔离、显式 opt-in profile 和 env-only 凭据约束。

## 完成后能得到什么

默认本地 Maven 回归不再隐式运行真实 provider、Ollama 或凭据依赖的 usage tests；需要真实 provider 证据时，operator 可以显式使用 `-P live-provider-tests` 和目标 JUnit 类运行。provider API key 不再从代码默认值或 `*.api.key` system property 回退，测试在缺少 env 凭据时以 JUnit Assume 跳过。

## 交付物

- 可见产物：Maven Surefire category/profile、`LiveProviderTest` marker、测试 helper、live provider 测试注解与 env-only 凭据改造。
- 修改位置：`pom.xml`、`ai4j/pom.xml`、`ai4j*/src/test/java/**`、Regression SSoT / Cadence / testing standard。
- 验证证据：`progress.md` 和 `artifacts/INDEX.md` 中的 Maven 命令、扫描结果、残余路由。

## 第一眼应该看什么

1. `review.md`：审查结论、证据摘要、R-008 残余。
2. `progress.md`：实际命令结果和 live profile smoke。
3. `docs/11-REFERENCE/testing-standard.md`：后续使用 `-P live-provider-tests` 的运行方式。

## 边界

- 范围内：live provider / external provider 测试的 Maven profile、JUnit category、env-only key handling、文档和任务证据。
- 范围外：真实 provider 调用、密钥配置、`HandoffPolicyTest` 行为修复、CI branch protection。
- 停止条件：需要真实凭据、发布凭据或更改 agent runtime 行为时，必须另行确认或开独立任务。

## 完成判断

- 默认 core/coding 本地测试不运行 live provider tests。
- core/agent/coding 的 targeted live profile 在无 env 凭据时跳过而非失败。
- 扫描不再发现 live provider 测试中的默认 credential-like key、API key system property 回退或本机绝对路径。
- Regression SSoT / Cadence / testing standard 已同步新 profile 和 R-008 残余。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交 Agent Review，并等待人工在 dashboard 中确认。
