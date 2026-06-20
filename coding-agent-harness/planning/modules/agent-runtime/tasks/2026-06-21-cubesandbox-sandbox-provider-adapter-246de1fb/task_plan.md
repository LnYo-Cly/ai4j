# CubeSandbox sandbox provider adapter

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-21-cubesandbox-sandbox-provider-adapter-246de1fb/artifacts/preset/2026-06-20T18-08-57-643Z
Task Package Index: required

## 目标

在 `ai4j-agent` 内提供第一个真实远端 Sandbox SPI adapter：`CubeSandboxProvider`，支持 CubeAPI 创建/连接/销毁 sandbox，并通过 envd Connect 协议执行命令。

## 范围

- 做什么：新增 CubeSandbox adapter、协议级本地测试、live opt-in smoke test、docs-site 使用文档、回归治理记录和任务收口材料。
- 不做什么：不改 `ai4j-coding` 工具路由，不实现 files/Jupyter/snapshot/browser，不新增 Maven module，不把密钥放入 `SandboxSpec` 或文档。
- 主要风险：CubeSandbox 协议字段/Host override 与官方 SDK 不一致；真实 CubeSandbox 环境变量缺失；docs 声称超过当前代码能力；secret 泄露。

## 预算选择

选择预算：complex

选择理由：任务同时触及 agent runtime、协议适配、live-provider opt-in、docs-site、回归治理和审查材料，需要完整证据链与 residual 记录。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/ | 现有 Sandbox SPI 合同 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/AgentSessionSandboxBinding.java | 非敏感 sandbox 摘要边界 | coordinator / reviewer |
| C-003 | public-doc | URL:https://github.com/TencentCloud/CubeSandbox/blob/master/openapi.yml | CubeAPI endpoint / response contract | coordinator / reviewer |
| C-004 | public-doc | URL:https://github.com/TencentCloud/CubeSandbox/blob/master/sdk/go/envd.go | envd Connect envelope 和 process start 参考 | coordinator / reviewer |
| C-005 | public-doc | URL:https://github.com/TencentCloud/CubeSandbox/blob/master/sdk/go/client.go | create/connect payload、proxy transport 参考 | coordinator / reviewer |
| C-006 | public-doc | URL:https://github.com/TencentCloud/CubeSandbox/blob/master/docs/guide/authentication.md | Bearer/API key 鉴权边界 | coordinator / reviewer |
| C-007 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 回归 gate 与 live-provider opt-in 规则 | coordinator / reviewer |

## 步骤

1. 研究 CubeSandbox 官方 OpenAPI / SDK，确认 create/connect/delete、envd Connect、Host override、auth 和 env var 约定。
2. 在 `ai4j-agent` 现有 Sandbox SPI 下实现 `cubesandbox` package，不新增第三方依赖、不拆 Maven module。
3. 编写协议级本地 HTTP server 测试和 live opt-in smoke test。
4. 补 docs-site 页面、sidebar、Sandbox SPI 交叉说明、Regression SSoT / Cadence Ledger。
5. 运行 targeted/broad/docs/diff/Harness 检查；如 live env 缺失，诚实记录 pending-env。
6. 完成 review/walkthrough/lesson routing，提交、推送、PR 并处理 CI。

## 验收标准

- [x] Java 8 编译通过，不新增 runtime 第三方依赖。
- [x] `CubeSandboxProviderTest` 通过并验证真实协议形态，不是纯 mock。
- [x] `CubeSandboxLiveProviderTest` 使用 `LiveProviderTest` category 且缺 env 时 skip。
- [x] 文档不声称 files/browser/full coding routing 已完成。
- [x] API key 不从 `SandboxSpec.config` 读取，不进入 labels/metadata/session snapshot。
- [ ] broad Maven、docs build、diff check、Harness status 完成并记录。
- [ ] PR CI 通过后合并/清理 worktree。

## 工作树（Worktree）

- 路径：`.worktrees/feature/cubesandbox-provider`
- 分支：`feature/cubesandbox-provider`
- Worker owner：coordinator；read-only reviewer subagent `019ee64c-c0d0-7483-8822-92fb12c2576f`
- Worker handoff commit required：no（本轮无写入 worker）
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用，已使用 feature worktree。

## 长程任务判定

- 是否属于长程任务：是
- 若是，合同文件：用户已授权自主连续完成；本任务未单独创建 `long-running-task-contract.md`
- 连续执行权限：已授权
- Stop Condition 摘要：出现无法本地修复的 CI/协议 blocker、需要真实 CubeSandbox secret/endpoint、或需要扩大到 `ai4j-coding` 全量路由时暂停并记录。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + read-only subagent
- No-finding 要求：P1/P2 findings 必须关闭或降级为明确 residual。

## 关联

- 相关 Regression Gate：RG-002、RG-008、LV-002 opt-in
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P2-A Sandbox SPI、P2-B AgentSession sandbox binding、P2-C plugin contribution contract expansion

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-CUBESANDBOX-SANDBOX-PROVIDER-ADAPTER-246DE1FB
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime step active -> review/done after PR
- Harness Ledger update needed：task lifecycle commands will sync generated ledger
- Closeout / Regression update needed：Regression SSoT、Cadence Ledger、walkthrough already in scope

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Agent runtime module scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Active task and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Not present in this checkout; use module plan if needed. |
