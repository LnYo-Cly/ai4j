# AI4J FlowGram webapp CI R-007 fix - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### R-007 缺口确认

- 背景：Regression SSoT 中 R-007 说明 RG-009 只有本地映射，没有 dedicated CI workflow。
- 发现：`.github/workflows/` 只有 Java/docs workflows；`ai4j-flowgram-webapp-demo/package.json` 有 `lint`、`ts-check`、`build` scripts，`test` / `test:cov` 是 stub。
- 影响：FlowGram webapp demo 变更无法获得远端固定回归证据，R-007 不能关闭。
- 后续：新增 stable aggregate job `flowgram-webapp-regression`，命中 webapp surface 时运行三道本地 baseline。

### ESLint 配置加载失败

- 背景：本任务要求把 `npm run lint` 纳入 CI，必须先证明该命令在当前项目可执行。
- 发现：原 `.eslintrc.js` 从 `@flowgram.ai/eslint-config` 读取 `defineConfig`，但 1.0.9 包实际导出 `defineFlatConfig` / `FlatCompat`，导致 ESLint 8 加载配置时报 `defineConfig is not a function`。
- 影响：不修复配置时，新增 CI 会稳定失败，且失败发生在配置加载阶段，不是业务代码 lint 结果。
- 后续：改为复用 `@flowgram.ai/eslint-config/eslint.web.config.js` legacy preset，并显式声明 ESLint 8 parser/plugins；`npm run lint` 已通过。

### Lint warning 边界

- 背景：`npm run lint` 通过后输出大量 Prettier/CRLF warning。
- 发现：这些 warning 不影响退出码，主要来自当前 Windows 工作树换行和既有格式；修复它们会导致大规模 webapp 源码重格式化，超出 R-007 CI gate 范围。
- 影响：CI 在 Linux checkout 上可能减少 CRLF warning；即使仍有 warning，当前 lint gate 以退出码为准。
- 后续：本轮不做机械格式化，只记录 warning 边界。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Workflow 形态 | 新增 dedicated `flowgram-webapp-regression` workflow，并使用稳定聚合 job | 关闭 R-007 需要 webapp 独立远端证据；稳定 aggregate job 可作为未来 required check，不会因非 webapp 变更缺失 context | 只在 docs/java workflow 中追加 webapp step | accepted |
| 触发策略 | PR/push/manual 都触发，内部 detect 决定是否运行 webapp checks | 与 Java regression gate 保持一致；非 webapp 变更也能出现稳定 check | workflow-level path filter | accepted |
| ESLint 修复 | 兼容现有 ESLint 8 legacy config | 改动小，保留 `eslint ./src --cache` 脚本；避免迁移到 ESLint 9 flat config | 升级 eslint 到 v9 或改脚本设置 flat config | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 远端 `flowgram-webapp-regression` 首次绿灯 | 待 push 后 GitHub Actions 确认 | coordinator | workflow 推送后 |
| 是否加入 branch protection required check | 倾向在远端绿灯后追加到 `main` / `dev` required checks；如果 API 权限不足，记录残余 | coordinator | 远端 green run 后 |
