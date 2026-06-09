# AI4J docs site Node heap regression R-005 fix - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### R-005 与 R-004 是两个独立残余

- 背景：RG-008 过去同时记录过 docs-site typecheck heap OOM 和 Docusaurus build Windows `EPERM` 清理失败。
- 发现：本轮只修复默认 Node heap 问题；`docs-site/package.json` 的 `typecheck` / `build` 脚本可直接内置 `node --max-old-space-size=8192`，不需要新增 `cross-env` 或其他依赖。
- 影响：R-005 可以关闭；R-004 仍保持开放，因为它属于 Docusaurus 输出/缓存清理文件锁风险，不应被 heap 脚本修改误关闭。
- 后续：后续单独任务若要关闭 R-004，应围绕 build output/cache 清理策略或 Windows 文件锁复现处理。

### docs workflow 与本地 RG-008 入口需要一致

- 背景：RG-008 定义为先 `npm run typecheck` 后 `npm run build`，但现有 docs workflows 只执行 build。
- 发现：`.github/workflows/docs-build.yml` 和 `.github/workflows/docs-pages.yml` 可以直接增加 `npm run typecheck`，并复用 package scripts 内置 heap。
- 影响：本地和 CI 的 docs-site 回归入口一致，减少“本地通过但 CI 未覆盖 typecheck”的漂移。
- 后续：推送后观察 docs workflow 远端结果；如果失败，再按 workflow evidence 更新任务材料。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 固化 Node heap | 在 package scripts 中直接调用 `node --max-old-space-size=8192` | 跨 Windows / Linux 可用，不新增依赖，维护者继续运行同一 npm script | 要求每个任务手写 `NODE_OPTIONS`；新增 `cross-env` | accepted |
| docs workflow 覆盖 typecheck | build 前执行 `npm run typecheck` | 与 RG-008 的固定 gate 定义一致 | 只保留 build；单独新增 workflow | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| R-004 是否同时关闭 | 不关闭；它是独立 Windows Docusaurus cleanup/file-lock 风险 | coordinator | 后续 R-004 专项 |
