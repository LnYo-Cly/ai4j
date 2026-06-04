# docs-site 当前盘点

## 摘要

当前 `docs-site/docs` 不是缺内容，而是内容已经进入“新旧主线并存”的状态。站点有 232 个 markdown 文件，侧边栏主线已经迁到 `start-here`、`core-sdk`、`spring-boot`、`agent`、`coding-agent`、`flowgram`、`solutions`，但 `getting-started`、`ai-basics`、`guides` 仍被 Docusaurus include，且与新主线有大量语义重叠。

## 目录计数

| Section | Markdown files | 观察 |
| --- | ---: | --- |
| `core-sdk` | 63 | 新主线中最大目录，已经承载模型访问、工具、MCP、RAG、扩展，但仍有旧式平铺页。 |
| `ai-basics` | 37 | 与 `core-sdk` 大面积重叠，像上一代基础能力目录。 |
| `agent` | 36 | 通用 Agent 内容较多，但混入若干旧 coding-agent / provider profile 页面。 |
| `coding-agent` | 20 | 新主线存在，但与 `agent` 内旧页面有重复。 |
| `flowgram` | 15 | 有 `built-in-nodes` / `builtin-nodes`、`custom-nodes` / `custom-node-extension` 等重复命名风险。 |
| `getting-started` | 12 | 与 `start-here` 重叠，像旧快速开始目录。 |
| `mcp` | 11 | 既作为顶层目录，又被 Core SDK sidebar 嵌入。 |
| `guides` | 10 | 与 `solutions` 几乎一一对应，像迁移前案例目录。 |
| `solutions` | 10 | 当前 sidebar 采用的案例主线。 |
| `start-here` | 8 | 当前入口主线，但缺少 Feature Map / status matrix。 |
| `spring-boot` | 6 | 结构清晰，适合保留为独立主线。 |
| `deploy` | 1 | 独立运维页。 |

## 当前侧边栏事实

`docs-site/sidebars.ts` 暴露的主线是：

1. `intro`
2. `Start Here`
3. `Core SDK`
4. `Spring Boot`
5. `Agent`
6. `Coding Agent`
7. `Flowgram`
8. `Solutions`
9. `Deploy`
10. `faq`
11. `glossary`

这说明站点已经有目标骨架，但还缺三件事：

- `Feature Map`：完整展示所有特色能力和成熟度，而不是把能力堆在首页。
- `Page Contract`：每类页面用同一套结构写，避免有些页面像源码笔记，有些页面像教程。
- `Legacy Mapping`：明确 `ai-basics/getting-started/guides` 的旧内容是迁移、合并、保留还是隐藏。

## 重复主线

| 重复面 | 当前表现 | 处理建议 |
| --- | --- | --- |
| 快速开始 | `start-here/*` 与 `getting-started/*` 并存 | `start-here` 做唯一入口，`getting-started` 进入 legacy mapping，保留可引用内容后逐步合并。 |
| 基础能力 | `core-sdk/*` 与 `ai-basics/*` 并存 | `core-sdk` 为 canonical，`ai-basics` 逐页映射到 Core SDK / RAG / MCP / Reference。 |
| 案例教程 | `solutions/*` 与 `guides/*` 并存 | `solutions` 为 canonical，`guides` 转为迁移来源或历史博客映射。 |
| Agent / Coding Agent | `agent/coding-agent-*` 与 `coding-agent/*` 并存 | Coding Agent 内容归入 `coding-agent`，`agent` 只保留通用 Agent runtime。 |
| FlowGram 页面命名 | `built-in-nodes` / `builtin-nodes`，`custom-nodes` / `custom-node-extension` | 选 canonical slug，非 canonical 页先保留并标注迁移目标，实施阶段再处理 redirect。 |

## 主要质量问题

1. **入口像百科**：首页和 Why 页面说明完整，但用户第一眼看到的是体系叙事，不是“我该怎么跑通”。
2. **特色没有状态标签**：Agent、Coding Agent、FlowGram 等探索性能力和基础 Chat/RAG/MCP 放在同等宣传位，会降低可信度。
3. **新用户路径不够短**：需要更强的 `Plain Java 3 minutes`、`Spring Boot 3 minutes`、`Provider switch`、`First Tool`、`First RAG`。
4. **页面形态不统一**：部分页面解释源码和边界很有价值，但不适合作为第一层功能入口。
5. **旧目录被 include**：Docusaurus 会构建旧目录，即使 sidebar 不展示，也会带来维护和链接漂移风险。

## 不建议立即做的事

- 不建议直接删除 `ai-basics`、`getting-started`、`guides`。
- 不建议先批量移动文件，容易造成 broken links。
- 不建议把 Coding Agent / FlowGram 从文档移除；应降到 advanced / preview 层，而不是消失。
- 不建议把首页写成完整能力列表；完整能力应进入 Feature Map。
