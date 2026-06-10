# AI4J FlowGram webapp real test gate R-003 fix

## Task ID

`2026-06-10-ai4j-flowgram-webapp-real-test-gate-r-003-fix-4c2813e4`

## 创建日期

2026-06-10

## 一句话结果

FlowGram webapp demo 的 `npm test` 从占位命令变成可重复运行的真实本地 gate，并纳入 CI 的 RG-009 顺序。

## 完成后能得到什么

完成后，`ai4j-flowgram-webapp-demo/` 的固定回归不再只依赖 lint/type/build。`npm test` 会验证发送给后端的 workflow 归一化合同：过滤 UI-only 节点、删除无效边、映射后端节点类型、处理 loop 节点的 `loopFor` 输入，并确认序列化入口接受对象和字符串两种输入。CI 会在 lint/type/build 前先执行这个 test gate；R-003 可以从“test script stub”关闭为已解决。

## 交付物

- 可见产物：真实 `npm test` 入口、backend workflow contract tests、CI test step、R-003/RG-009 台账更新。
- 修改位置：`ai4j-flowgram-webapp-demo/`、`.github/workflows/flowgram-webapp-regression.yml`、Regression SSoT / Cadence Ledger、当前 harness task package。
- 验证证据：`npm run test`、`npm run lint`、`npm run ts-check`、`npm run build`、generated `dist` 负向扫描、harness status。

## 第一眼应该看什么

先读 `progress.md` 的 2026-06-10 12:26 条目和 `review.md` 的 Evidence Checked；代码入口看 `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts` 与 `src/utils/backend-workflow.test.ts`。

## 边界

- 范围内：webapp demo 的 test 脚本、纯逻辑测试 runner、backend workflow 归一化逻辑、CI webapp gate 顺序、R-003/RG-009 治理记录。
- 范围外：浏览器 E2E、真实 backend demo 联调、FlowGram starter / Java production 逻辑、引入大型测试框架或远程插件生态改动。
- 停止条件：如果需要 live backend、浏览器人工代理、或新增前端测试依赖，回到用户/coordinator 确认。

## 完成判断

- `ai4j-flowgram-webapp-demo/package.json` 的 `test`、`test:cov`、`watch` 不再是 `exit` 占位。
- `npm test` 覆盖 backend workflow normalization/serialization contract 并通过。
- `.github/workflows/flowgram-webapp-regression.yml` 在 lint/type/build 前执行 `npm test`。
- RG-009 和 R-003 在 legacy/v2 回归台账中同步更新。
- 本地 `test`、`lint`、`ts-check`、`build` 均通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

等待 Agent Review Submission 与远端 `flowgram-webapp-regression` push evidence。
