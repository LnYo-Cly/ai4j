# docs site information architecture redesign

## Task ID

`2026-06-04-docs-site-information-architecture-redesign-6c91ba27`

## 创建日期

2026-06-04

## 一句话结果

产出一份 docs-site 信息架构重构设计包，明确 AI4J 文档如何在“不漏功能”的前提下，把入口、快速成功路径、功能详解和参考资料分层。

## 完成后能得到什么

下一轮 agent 或维护者可以直接按设计执行 docs-site 改造：先重写入口和 Feature Map，再按页面合同逐步整理 Core SDK、RAG、MCP、Agent、Coding Agent、FlowGram 与 integrations。设计会保留所有特色功能的说明入口，但将成熟能力、进阶能力和探索能力分层展示，避免首页和 Start Here 变成百科式堆叠。

## 交付物

- 可见产物：docs-site 当前盘点、目标信息架构、页面写作合同、分阶段迁移计划。
- 修改位置：本任务目录下的 `brief.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`references/`。
- 验证证据：现有 docs-site markdown 计数、目录/侧边栏检查、harness status。

## 第一眼应该看什么

先读 `references/docs-site-redesign-design.md`，再读 `references/docs-site-current-inventory.md`。如果要开始实施，先从 `references/docs-site-page-contracts.md` 的页面合同和 `task_plan.md` 的分阶段计划切入。

## 边界

- 范围内：设计 docs-site 信息架构、页面职责、迁移波次、功能展示矩阵和写作模板。
- 范围外：本轮不批量移动 docs-site 文件、不重写实际产品文档、不改 Docusaurus 侧边栏和构建配置。
- 停止条件：如果需要开始实际重构 docs-site 页面、删除旧页面或改变 URL，需要用户单独确认实施阶段。

## 完成判断

- 当前 docs-site 的重复主线和目录重叠被列清。
- 新信息架构能同时满足“入口清晰”和“特色功能不漏”。
- 页面合同能指导每类页面怎么写，而不是只给抽象建议。
- 分阶段迁移计划明确先改哪些入口、哪些旧目录先映射不删除。
- 验证证据记录到 `progress.md`，并且 harness status 无失败。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补齐 task-local 设计材料，然后提交给用户确认是否进入第一波 docs-site 实施。
