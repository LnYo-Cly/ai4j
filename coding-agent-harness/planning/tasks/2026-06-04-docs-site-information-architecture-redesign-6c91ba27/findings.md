# docs site information architecture redesign - 发现记录

## 研究发现

### 当前问题不是缺内容，而是主线重复

- 背景：用户确认 docs-site 必须把每个点讲清楚、每个特色功能都说出来，但当前入口质量不高。
- 发现：`docs-site/docs` 有 232 个 markdown；`core-sdk`、`ai-basics`、`start-here`、`getting-started`、`solutions`、`guides` 等目录之间存在新旧主线重叠。
- 影响：重构策略不能删内容，也不能把所有能力塞回首页；必须用 Feature Map 和页面合同分层承载。
- 后续：实施阶段先新增/改写入口与 Feature Map，再做旧页映射。

### `start-here` 已有骨架，但缺少完整能力矩阵

- 背景：当前 sidebar 已有 `Start Here`，且 `why-ai4j` / `choose-your-path` / quickstart 已存在。
- 发现：已有页面偏体系叙事，能解释“AI4J 是工程底座”，但没有一个单页负责完整列出所有能力、成熟度和入口。
- 影响：用户会在首页或 Why 页面看到过多宏观叙事，而不是“我该先跑哪个示例”。
- 后续：实施 Wave 1 新增 `start-here/feature-map.md`，并把完整能力列表移到该页。

### 高级能力需要状态标签

- 背景：Agent、Coding Agent、FlowGram 是 AI4J 的特色，但部分能力较新。
- 发现：当前 README / docs 里基础 Chat/RAG/MCP 与 Agent/Coding Agent/FlowGram 的展示层级接近。
- 影响：容易让外部用户误以为所有能力同等成熟，削弱可信度。
- 后续：引入 `stable`、`advanced`、`preview`、`experimental` 标签。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 入口设计 | 首页只负责定位和路径选择 | 首页百科化会压住“低门槛”定位 | 首页列出所有功能 | accepted |
| 完整能力呈现 | 新增 Feature Map | 满足“不漏特色功能”，同时避免入口臃肿 | 在 README/intro 堆功能清单 | accepted |
| 旧目录处理 | 先映射再迁移，不直接删除 | 避免 broken links 和历史内容丢失 | 直接删除 `ai-basics/getting-started/guides` | accepted |
| 成熟度表达 | 使用 stable/advanced/preview/experimental | 诚实表达能力状态，避免过度承诺 | 不标注成熟度 | accepted |
| 本轮范围 | design-only | 用户先同意设计，实际改文档需要独立实施确认 | 直接改 docs-site | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否执行 Wave 1 入口修正 | 建议执行 | user | 本设计被确认后 |
| RAG 是否从 `core-sdk/search-and-rag` 抽顶层目录 | 建议实施阶段评估 URL/redirect 成本 | coordinator | Wave 3 前 |
| Coding Agent / FlowGram 状态标签 | 建议先标 preview / advanced | user / coordinator | Wave 4 前 |
