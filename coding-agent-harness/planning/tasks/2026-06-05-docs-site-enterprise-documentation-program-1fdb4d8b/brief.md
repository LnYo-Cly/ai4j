# docs-site 文档重构总任务

## Task ID

`2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b`

## 创建日期

2026-06-05

## 一句话结果

把 docs-site 从“入口页已修正但深页仍分裂”的状态推进为更适合大众用户和生产项目接入的 AI SDK 文档站。

## 完成后能得到什么

本任务完成后，docs-site 应具备清晰的 canonical 阅读主线：Start Here、Core SDK、MCP、Spring Boot、Agent、Coding Agent、FlowGram、Solutions、Reference、Security、Operations、Migration、Troubleshooting、Comparison。用户能先判断 AI4J 是否适合自己，再按 Java、Spring、RAG、MCP、Agent、Coding Agent 或 FlowGram 路径进入，并在上线前找到版本、发布、安全、排障和生产检查入口。

## 交付物

- 可见产物：新增 documentation map、版本兼容、发布 artifact、安全边界、生产检查、迁移、排障、选型对比页面；重写 Core/Agent/Coding Agent/FlowGram 总览页；修正 sidebar 和入口链接。
- 修改位置：`docs-site/docs/**`、`docs-site/sidebars.ts`、`docs-site/docusaurus.config.ts`。
- 验证证据：`docs-site` 下 `npm run build` 通过。

## 第一眼应该看什么

1. `docs-site/docs/start-here/documentation-map.md`
2. `docs-site/docs/intro.md`
3. `docs-site/docs/core-sdk/overview.md`
4. `docs-site/docs/agent/overview.md`
5. `docs-site/docs/coding-agent/overview.md`
6. `docs-site/docs/flowgram/overview.md`
7. `docs-site/docs/operations/production-checklist.md`

## 边界

- 范围内：docs-site 信息架构、入口页、总览页、生产接入辅助页、sidebar/include/footer、文档链接收口、任务证据记录。
- 范围外：Java API 变更、业务代码变更、真实发布流程改造、删除 legacy 历史目录、远程推送。
- 停止条件：构建失败且需要更大范围迁移、发现旧页强内容必须批量合并、或需要人工确认旧页面删除策略时，回到 coordinator 或用户确认。

## 完成判断

- [ ] 新用户能从 Start Here 找到正式阅读路径和模块选择路径。
- [ ] Core、Agent、Coding Agent、FlowGram 总览页先讲适合场景、开始路径和边界，再讲内部对象。
- [ ] 版本、发布、安全、生产检查、迁移、排障和选型对比有独立入口。
- [ ] legacy 目录不再被当成新增主线，正式路径由 documentation map 和 sidebar 声明。
- [ ] `npm run build` 通过，并记录到 progress / review。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并经 task-review / human review / task-complete 收口。

## 当前下一步

补齐 harness 任务材料、检查 git 边界、强制纳入被 `.gitignore` 隐藏的新 docs 文件，然后提交本地 commit。
