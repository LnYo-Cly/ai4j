# AI4J docs site Node heap regression R-005 fix - 进度

## 状态：审查中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

### [2026-06-10 00:22] - R-005 implementation and local verification

- 做了什么：将 `docs-site/package.json` 的 `typecheck` / `build` 改为直接使用 `node --max-old-space-size=8192` 调本地 TypeScript / Docusaurus CLI；docs-build/docs-pages workflows 增加 `npm run typecheck`；同步 README、Feature SSoT、Regression SSoT、Cadence Ledger 和 docs-site module plan。
- 验证结果：`npm run typecheck` 在未设置外部 `NODE_OPTIONS` 的 shell 中通过；`npm run build` 通过并生成 `docs-site/build`；workflow YAML lint 通过。
- 下一步：运行 diff/harness 检查，提交 Agent Review Submission，推送后观察远端 docs workflow。
- 证据：command:TARGET:docs-site:`npm run typecheck` passed without external NODE_OPTIONS; command:TARGET:docs-site:`npm run build` passed and generated build; command:TARGET:.github/workflows:`npx.cmd --yes yaml-lint .github/workflows/docs-build.yml .github/workflows/docs-pages.yml` passed; diff:TARGET:docs-site/package.json:8GB Node heap baked into typecheck/build scripts; report:TARGET:docs/05-TEST-QA/Regression-SSoT.md:R-005 closed, R-004 remains open

### [2026-06-10 00:37] - remote docs workflow evidence

- 做了什么：推送 `main@0df7094` 后复查 GitHub Actions docs workflows。
- 验证结果：docs-build run `27220942110` 成功；docs-pages run `27220942127` 成功；同一提交上的 `java-regression` run `27220940620` 和 `flowgram-webapp-regression` run `27220942165` 也成功。
- 下一步：重新提交 Agent Review Submission，并保持人工确认待处理。
- 证据：command:URL:https://github.com/LnYo-Cly/ai4j/actions/runs/27220942110:docs-build passed on main@0df7094; command:URL:https://github.com/LnYo-Cly/ai4j/actions/runs/27220942127:docs-pages passed on main@0df7094; command:URL:https://github.com/LnYo-Cly/ai4j/actions/runs/27220940620:java-regression passed on main@0df7094; command:URL:https://github.com/LnYo-Cly/ai4j/actions/runs/27220942165:flowgram-webapp-regression passed on main@0df7094

## 残余

- R-004 仍开放：docs-site build on Windows 可能遇到 Docusaurus 输出/缓存清理 `EPERM` 文件锁，本轮未处理。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：docs-site module plan 已更新 DOCS-02 / DOCS-03 与本任务证据
- Harness Ledger update needed：task-review 后由 CLI 同步
- 负责人：coordinator / 不适用

### [2026-06-09 15:59] - task-start

- 做了什么：开始修复 docs-site Node heap R-005：固化 typecheck/build heap 配置，更新 CI 与回归治理。
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-09 16:33] - task-review

- 做了什么：R-005 docs-site Node heap fix ready: npm run typecheck/build pass without external NODE_OPTIONS, docs workflows align with RG-008, R-005 closed while R-004 remains routed.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
