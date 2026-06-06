# 5 分钟首聊主路径文档 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### docs-site 入口分散

- 背景：用户要求把 docs-site 改成大众可用的 AI SDK 文档，并优先做小白首聊路径。
- 发现：`intro.md`、`choose-your-path.md`、`quickstart-java.md`、`quickstart-spring-boot.md`、`first-chat.md` 都能指导入门，但缺少一页把普通 Java、Spring Boot、Skill 安装和验证标准串起来的主路径。
- 影响：新增 `five-minute-first-chat.md` 作为首屏主路径，再让其它入口分工为细分页。
- 后续：无。

### Quickstart 版本漂移

- 背景：首聊示例必须能被新用户复制。
- 发现：当前 root/module POM 版本是 `2.3.0`，但两个 Quickstart 仍写 `2.1.0`。
- 影响：首聊相关依赖示例统一到 `2.3.0`。
- 后续：后续发布时应检查 docs-site 版本号与 POM 是否同步。

### 公开 Skill 边界

- 背景：用户已决定不保留公开 `$ai4j-sdk`，只保留面向使用者的 `$ai4j-app-builder`。
- 发现：当前 active skill surface 是 `skills/ai4j-app-builder`；历史 harness 记录里的 `$ai4j-sdk` 只作为审计记录。
- 影响：README 和首聊页只宣传 `$ai4j-app-builder`。
- 后续：无。

### Docusaurus 数字前缀文件名

- 背景：新增页最初命名为 `5-minute-first-chat.md` 并加入 sidebar。
- 发现：`npm run build` 报错，Docusaurus 把前导 `5-` 识别为排序前缀并剥离，实际 doc id 变成 `start-here/minute-first-chat`，导致 sidebar 里的 `start-here/5-minute-first-chat` 不存在。
- 影响：文件改名为 `five-minute-first-chat.md`，并显式设置 `slug: /start-here/five-minute-first-chat`，保留用户可读 URL。
- 后续：新增 docs-site canonical 页时避免以数字加连字符作为文件名前缀。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 首聊入口形态 | 新增一页 `five-minute-first-chat.md` | 单页能承载小白首跑、Skill 安装和成功标准，不需要读者在多页间拼路径 | 只改 Quickstart 两页 | accepted |
| 示例 provider | OpenAI-compatible Chat | 当前文档和源码里 OpenAI Chat 是最直观的对象链，且能代表兼容 endpoint | provider-neutral 伪代码 | accepted |
| 证据门禁 | RG-008 docs-site typecheck/build | 本任务只改 docs-site/README 文档，无 Java runtime 改动 | 跑 Maven 全量测试 | accepted |
| 首聊页文件名 | `five-minute-first-chat.md` + explicit slug | 避免 Docusaurus 把数字前缀当作排序前缀剥离，同时保留 `/five-minute-first-chat` URL | `5-minute-first-chat.md` | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要真实 provider 验证 | 不需要；本任务是文档构建与示例路径整理，不改变 provider 行为 | coordinator | closeout |
