# regression baseline live split

## Task ID

`2026-06-04-regression-baseline-live-split-b2f834db`

## 创建日期

2026-06-04

## 一句话结果

把 ai4j-sdk 的默认回归基线拆成 deterministic local-required gates 与 opt-in live/credential gates，并同步 Cadence Ledger 的触发节奏和最低证据深度。

## 完成后能得到什么

下一轮 agent 能直接判断某个改动该跑哪些本地 gate、什么时候需要真实 provider 或凭证 gate、以及 evidence depth 应该到 L1/L2 还是 L3-L5。默认任务 closeout 不再把 live provider、发布签名或浏览器端到端行为混进本地基线；这些场景必须明确 opt-in、记录 env var 名称和脱敏证据。回归残余也被更新为后续可执行项：live provider profile/runbook、provider test hygiene、FlowGram webapp CI。

## 交付物

- 可见产物：回归分层、live/credential opt-in gate、Cadence 节奏术语、触发表和残余路由。
- 修改位置：`coding-agent-harness/governance/regression/Regression-SSoT.md`、`coding-agent-harness/governance/regression/Cadence-Ledger.md`，并同步 legacy docs projection。
- 验证证据：diff 检查、关键字段扫描、`coding-agent-harness status --json`。

## 第一眼应该看什么

先读 tracked v2 回归事实源：`coding-agent-harness/governance/regression/Regression-SSoT.md` 和 `coding-agent-harness/governance/regression/Cadence-Ledger.md`。再看 `findings.md` 中关于 live 测试和 CI 现状的诊断。

## 边界

- 范围内：回归 SSoT、Cadence Ledger、testing standard 投影、任务材料和 harness 验证。
- 范围外：不改测试代码、不引入 Maven category/profile、不配置新 CI workflow、不运行真实 provider 或发布凭证命令。
- 停止条件：发现需要修改业务测试、提交密钥、运行真实 provider 或改变 CI required checks 时暂停并回到用户确认。

## 完成判断

- `Regression-SSoT.md` 明确 local-required、live-provider-opt-in、credential-release-opt-in。
- `Cadence-Ledger.md` 每条触发规则有必跑 gate、opt-in gate、节奏和最低证据深度。
- live provider / provider test hygiene / webapp CI 缺口被路由为残余。
- harness status 通过，任务材料无模板残留，并提交 Agent Review Submission。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补齐任务计划、发现记录和审查材料，然后运行 harness status 验证。
