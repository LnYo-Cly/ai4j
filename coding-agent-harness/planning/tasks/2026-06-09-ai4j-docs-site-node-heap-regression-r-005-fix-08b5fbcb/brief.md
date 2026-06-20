# AI4J docs site Node heap regression R-005 fix

## Task ID

`2026-06-09-ai4j-docs-site-node-heap-regression-r-005-fix-08b5fbcb`

## 创建日期

2026-06-09

## 一句话结果

docs-site 的 typecheck/build 入口内置 8GB Node heap，R-005 不再要求维护者手动设置 `NODE_OPTIONS`。

## 完成后能得到什么

完成后，维护者、CI 和下一轮 agent 都使用同一组命令验证 docs-site：`npm run typecheck` 后接 `npm run build`。这两个脚本内部直接调用本地 TypeScript / Docusaurus CLI，并固定 `node --max-old-space-size=8192`，避免 Windows 或 CI 中因为默认 Node heap 不足而重新写临时 workaround。RG-008 仍保留 R-004 的 Docusaurus 文件锁风险，但 R-005 heap 残余独立关闭。

## 交付物

- 可见产物：docs-site 标准 npm scripts、docs workflows、回归台账和任务材料。
- 修改位置：`docs-site/package.json`、`.github/workflows/docs-build.yml`、`.github/workflows/docs-pages.yml`、`docs-site/README.md`、Regression SSoT / Cadence Ledger、docs-site module plan。
- 验证证据：`npm run typecheck`、`npm run build`、workflow YAML lint。

## 第一眼应该看什么

先读 `task_plan.md` 的范围，再看 `progress.md` 的 RG-008 证据和 `review.md` 的 R-004 / R-005 边界说明。

## 边界

- 范围内：docs-site package scripts、docs CI workflow、README 使用说明、Regression SSoT / Cadence Ledger、任务包材料。
- 范围外：docs 内容重构、Docusaurus 版本升级、Windows `EPERM` 清理残余 R-004、branch protection required check 调整。
- 停止条件：标准 `npm run typecheck` 或 `npm run build` 在内置 heap 下失败且不是简单配置问题。

## 完成判断

- `npm run typecheck` 不设置外部 `NODE_OPTIONS` 通过。
- `npm run build` 不设置外部 `NODE_OPTIONS` 通过并生成 `docs-site/build`。
- docs workflows 执行 `npm run typecheck` 和 `npm run build`。
- legacy 和 v2 Regression SSoT 均关闭 R-005，并保留 R-004。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交 review 前运行 `git diff --check` 和 `npx.cmd --yes coding-agent-harness status --json .`。
