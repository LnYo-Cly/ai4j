# AI4J FlowGram webapp CI R-007 fix - 进度

## 状态：进行中

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

## 残余

- R-007 当前仍 open，待新增 workflow、本地 webapp gate 和远端 green run 后关闭。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：task-review / closeout 时由 lifecycle CLI 同步
- 负责人：coordinator

### [2026-06-09 13:28] - task-start

- 做了什么：Start R-007: add dedicated FlowGram webapp regression CI and synchronize RG-009 governance.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-09 13:35] - 范围和计划落盘

- 做了什么：诊断现有 workflow、`ai4j-flowgram-webapp-demo/package.json` scripts、R-007/RG-009 状态，并将任务合同、Feature SSoT 和执行策略改成真实内容。
- 验证结果：现有 `.github/workflows/` 只有 Java/docs workflows；webapp package scripts 提供 `lint`、`ts-check`、`build`，`test` 是 stub；R-007 仍标记为 open。
- 下一步：新增 `flowgram-webapp-regression` workflow。
- 证据：command:TARGET:.github/workflows:workflow inventory showed no webapp regression workflow; command:TARGET:ai4j-flowgram-webapp-demo/package.json:scripts are lint, ts-check, build with test stub

### [2026-06-09 13:43] - workflow 和本地 webapp gate

- 做了什么：新增 `.github/workflows/flowgram-webapp-regression.yml`，包含 `detect-webapp-changes`、`webapp-checks` 和稳定聚合 job `flowgram-webapp-regression`；修复 webapp `.eslintrc.js`，改用 FlowGram legacy web preset 并显式声明 ESLint 8 parser/plugins；将 `.eslintcache` 加入 webapp `.gitignore`。
- 验证结果：`npx.cmd --yes yaml-lint .github/workflows/flowgram-webapp-regression.yml` 通过；`npm run lint` 通过但有 Prettier/CRLF warnings；`npm run ts-check` 通过；`npm run build` 通过但有既有 bundle / Node module-type warnings。
- 下一步：更新 R-007/RG-009 治理记录，提交并推送，等待远端 `flowgram-webapp-regression` run。
- 证据：diff:TARGET:.github/workflows/flowgram-webapp-regression.yml:stable webapp regression workflow added; diff:TARGET:ai4j-flowgram-webapp-demo/.eslintrc.js:legacy ESLint config repaired; command:TARGET:.github/workflows/flowgram-webapp-regression.yml:`npx.cmd --yes yaml-lint .github/workflows/flowgram-webapp-regression.yml` passed; command:TARGET:ai4j-flowgram-webapp-demo:`npm run lint` passed with warnings; command:TARGET:ai4j-flowgram-webapp-demo:`npm run ts-check` passed; command:TARGET:ai4j-flowgram-webapp-demo:`npm run build` passed
