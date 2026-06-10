# AI4J FlowGram webapp real test gate R-003 fix - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### R-003 可由纯逻辑 contract test 关闭

- 背景：R-003 的问题是 webapp `test` / `test:cov` / `watch` 都是占位脚本，RG-009 只能依赖 lint/type/build。
- 发现：`src/utils/backend-workflow.ts` 是确定性的前端到后端 workflow 序列化边界，不依赖浏览器、后端或真实 provider，适合作为第一批真实 `npm test` 覆盖面。
- 影响：本轮选择轻量 Node + TypeScript transpile runner，不引入 Vitest/Jest/Playwright；CI 可在 lint/type/build 前先执行 `npm test`。
- 后续：后续若要覆盖完整 UI / backend demo 行为，应走 LV-003 opt-in，不并入本次 R-003。

### 测试揭示 loop 归一化缺口

- 背景：`loopFor` 需要被投影到 backend `inputs` 和 `inputsValues`，否则后端 task run 接收的 loop 节点输入 schema 不完整。
- 发现：节点类型先从 `loop` 映射成 `LOOP`，但旧判断使用 `type === 'Loop'`，导致 loop 分支不会执行。
- 影响：修复为 `type === 'LOOP'`，并用测试锁定 `loopFor` schema/value 归一化。
- 后续：无。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Test runner | 自包含 `scripts/run-tests.cjs`，使用已声明的 `typescript` dependency 转译 `.test.ts` | 避免新增测试框架和 lockfile churn，适合当前纯逻辑 contract tests | Vitest/Jest/tsx/ts-node；均需要新增或依赖未声明工具 | accepted |
| Test scope | backend workflow normalization / serialization | 直接覆盖 R-003 能力缺口，稳定、不依赖 browser/backend | 浏览器 E2E 或 live demo scenario | accepted |
| Runtime imports | `backend-workflow.ts` 保留 FlowGram types，运行时使用本地节点字符串常量 | Node CJS runner 加载 FlowGram package 会触发 runtime mismatch；本逻辑只需要稳定节点字符串 | 在测试 runner 中动态 ESM import FlowGram package | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 远端 `flowgram-webapp-regression` 是否通过包含 `npm test` 的新顺序 | 本地通过；推送后补录 GitHub Actions run evidence | coordinator | push 后 |
