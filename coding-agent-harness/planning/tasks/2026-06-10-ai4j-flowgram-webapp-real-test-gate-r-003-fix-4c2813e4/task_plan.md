# AI4J FlowGram webapp real test gate R-003 fix

Task Contract: harness-task/v1
Task Package Index: required

## 目标

关闭 R-003：FlowGram webapp demo 的 test scripts 不再是占位命令，RG-009 固定 gate 先运行真实 `npm test`，再运行 lint/type/build。

## 范围

- 做什么：新增轻量测试 runner 和 backend workflow 归一化测试；修复测试揭示的 loop 归一化缺口；将 CI `webapp-checks` 改为 test -> lint -> typecheck -> build；同步 R-003/RG-009 治理记录。
- 不做什么：不做浏览器 E2E、不启动 demo backend、不引入 Vitest/Jest/Playwright、不修改 Java modules 或 FlowGram starter。
- 主要风险：测试 runner 必须能在 `npm ci` 后稳定运行；测试不应进入生产 bundle；本地 test gate 不能被描述成 LV-003 端到端验证。

## 预算选择

选择预算：standard

选择理由：任务跨 webapp 脚本、测试文件、CI workflow 和回归台账，风险高于 simple；但不涉及多模块 runtime 设计或 live backend，因此不需要 complex。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-flowgram-webapp-demo/package.json | 识别占位 scripts 并更新固定 npm gate。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts | 被测试和修复的 workflow backend serialization contract。 | coordinator / reviewer |
| C-003 | code | TARGET:.github/workflows/flowgram-webapp-regression.yml | RG-009 remote gate 的执行顺序和 required check 入口。 | coordinator / reviewer |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md | legacy Regression SSoT 的 R-003/RG-009 当前事实。 | coordinator / reviewer |

## 步骤

1. 诊断 webapp scripts、现有 CI 和 backend workflow 可测试边界。
2. 增加不依赖浏览器/后端的 deterministic `npm test`，覆盖 workflow normalization/serialization。
3. 把 CI webapp checks 调整为先 test 再 lint/type/build。
4. 运行本地 RG-009 完整 gate 并检查测试文件没有进入 generated `dist`。
5. 同步 Feature SSoT、Regression SSoT、Cadence Ledger、任务 review/walkthrough。
6. 提交、推送并补录远端 workflow evidence。

## 验收标准

- [x] `ai4j-flowgram-webapp-demo/package.json` 的 `test`、`test:cov`、`watch` 不再是 `exit` 占位。
- [x] `npm run test` 覆盖 backend workflow normalization/serialization contract 并通过。
- [x] `.github/workflows/flowgram-webapp-regression.yml` 在 lint/type/build 前执行 `npm test`。
- [x] 本地 `npm run test`、`npm run lint`、`npm run ts-check`、`npm run build` 通过。
- [x] R-003/RG-009 在 legacy 与 v2 回归台账同步更新。

## 工作树（Worktree）

- 路径：same checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：单一 coordinator 在当前 checkout 内处理窄范围 webapp/test/CI/governance 切片，没有并行 worker 写入。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：需要 browser/backend live validation 或新增测试框架时暂停确认。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self；人工确认由 review-confirm 门禁处理，agent 不代办。
- No-finding 要求：无 open P0/P1/P2 material finding；必须明确 LV-003 不在本轮范围。

## 关联

- 相关 Regression Gate：RG-009；关闭 residual R-003。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：R-007 FlowGram webapp CI fix 已建立 remote aggregate gate。

## 模块关联（启用模块并行时填写）

- Module：flowgram-webapp-demo
- Step：WEBAPP-01 / WEBAPP-02
- Module Plan：`coding-agent-harness/planning/modules/flowgram-webapp-demo/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：`flowgram-webapp-demo` module plan references this active task.
- Harness Ledger update needed：lifecycle CLI records task-review / closeout state.
- Closeout / Regression update needed：legacy and v2 Regression SSoT / Cadence Ledger updated.
