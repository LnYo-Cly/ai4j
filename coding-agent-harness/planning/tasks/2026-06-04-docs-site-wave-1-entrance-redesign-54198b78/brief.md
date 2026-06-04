# docs site wave 1 entrance redesign

## Task ID

`2026-06-04-docs-site-wave-1-entrance-redesign-54198b78`

## 创建日期

2026-06-04

## 一句话结果

把 docs-site 的第一屏入口从泛泛架构介绍改成“普通 Java / Spring Boot / 功能地图”三条可执行路径，并新增 Feature Map 承接完整能力清单。

## 完成后能得到什么

用户或下一轮 agent 打开文档站时，可以先判断 AI4J 是否适合自己的 Java 项目，再按普通 Java、Spring Boot 或具体功能进入。首页负责降低第一次接入成本，Why AI4J 负责讲清个人项目的现实定位和差异化取舍，Feature Map 负责列出当前能力、成熟度和深链入口。后续 docs-site 可以沿这张地图继续补充能力页，而不需要再把所有卖点堆在首页。

## 交付物

- 可见产物：新的 docs-site 首页、重写后的 `Why AI4J`、新增 `Feature Map`。
- 修改位置：`docs-site/docs/intro.md`、`docs-site/docs/start-here/why-ai4j.md`、`docs-site/docs/start-here/feature-map.md`、`docs-site/sidebars.ts`。
- 验证证据：`docs-site` 构建、`git diff --check`、harness status 和本任务 review packet。

## 第一眼应该看什么

1. `docs-site/docs/intro.md`
2. `docs-site/docs/start-here/why-ai4j.md`
3. `docs-site/docs/start-here/feature-map.md`
4. `review.md` 和 `walkthrough.md`

## 边界

- 范围内：入口信息架构、定位表达、功能地图和 sidebar 挂载。
- 范围外：迁移旧目录、删除旧页面、重写 Core SDK 深页、改 README、调整 Docusaurus 主题或样式。
- 停止条件：发现新增页面链接不存在、docs-site 构建失败且无法在当前范围内修复，或需要对能力成熟度做产品承诺时回到用户确认。

## 完成判断

- 首页能在第一屏给出 Java、Spring Boot、Feature Map 三条明确入口。
- `Why AI4J` 明确说明 AI4J 不是比拼生态规模，而是降低 Java AI 接入和使用成本。
- `Feature Map` 覆盖 Core SDK、Tools、Skills、MCP、RAG、Spring Boot、Agent、Coding Agent、FlowGram、Solutions，并标注成熟度。
- sidebar 中 `Start Here` 已挂入 `feature-map`。
- `docs-site` 构建和基础 diff 检查完成，验证证据落到 `progress.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`，并由 `task-review` 提交待人工确认。

## 当前下一步

完成文档编辑后运行 `npm run build`、`git diff --check`，再提交本地 commit 并推进 harness lifecycle。
