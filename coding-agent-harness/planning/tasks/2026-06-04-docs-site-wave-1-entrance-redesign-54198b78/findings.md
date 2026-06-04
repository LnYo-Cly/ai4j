# docs site wave 1 entrance redesign - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 首页不能继续承担百科页职责

- 背景：用户指出 docs-site 质量不高，要求每个特色功能都讲清楚，但第一步必须先修正入口。
- 发现：旧 `intro.md` 同时承担项目定义、阅读路径、模块地图和文档约定，信息密度高但行动入口不够直接。
- 影响：首页改为先给普通 Java、Spring Boot、Feature Map 三条入口，再简要说明能力层级和模块地图。
- 后续：后续 Wave 2/3 可继续补各能力深页，不在本任务迁移旧页面。

### Why AI4J 需要诚实定位，而不是硬碰大生态

- 背景：用户明确指出 Spring AI、LangChain4j、AgentScope Java 有大团队和生态，AI4J 是个人项目。
- 发现：文档不应声称在生态规模上压过大框架，而应突出 Java 8+、普通 Java 接入、国内模型/OpenAI-compatible 友好、概念边界清晰和渐进升级。
- 影响：`why-ai4j.md` 增加与相邻方案的关系、适合/不适合场景，并把“降低 Java AI 接入和使用成本”作为核心表达。
- 后续：后续能力页应继续避免夸大成熟度。

### Feature Map 是保留全部能力的安全承接页

- 背景：用户要求每个特色功能都要讲清楚，但一次性全站重写风险较高。
- 发现：先新增 Feature Map 能把完整能力清单、成熟度和深链入口集中呈现，同时不删除旧内容。
- 影响：本轮新增 `start-here/feature-map.md`，使用 `stable`、`advanced`、`preview`、`experimental` 标签区分承诺强度。
- 后续：未有独立页的生态集成先归类到 MCP/Tools/Skills/RAG/FlowGram，不制造断链。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Wave 1 文件范围 | 只改 4 个 docs-site 文件 | 快速修正入口，同时降低全站断链和迁移风险 | 一次性迁移全部 docs-site 目录 | accepted |
| 成熟度标记 | `stable` / `advanced` / `preview` / `experimental` | 让个人项目的文档承诺更诚实，避免把探索能力写成稳定能力 | 不标状态或全部写成 stable | accepted |
| sidebar 位置 | Start Here 中 `choose-your-path` 后、quickstart 前 | Feature Map 先帮助用户理解能力边界，再进入具体 quickstart | 放到 Core SDK 或页面底部 | accepted |
| 生态集成链接 | 没有独立页时只归类，不添加断链 | 保证 Docusaurus build 可验证 | 先写未来链接 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否继续做 Wave 2 深页补强 | 本任务不展开；完成 Wave 1 后由用户确认下一轮 | user / coordinator | Wave 1 review 后 |
