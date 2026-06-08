# AI4J official ask-user plugin wave 10 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 插件应该是 host-mediated，而不是 UI/runtime 实现

- 背景：`ask_user` 的价值是让 Agent 在关键不确定点向用户提问，但 AI4J extension jar 不知道宿主是 CLI、Web、IDE 还是服务端队列。
- 发现：把插件设计成结构化 JSON envelope 可以保持 Java 8、本地测试和 host-neutral；UI、阻塞等待、答案保存和恢复执行都应由宿主负责。
- 影响：实现只注册 tool、command、Skill、Prompt，并返回 `ai4j.ask_user.request`；不读 stdin、不打开窗口、不阻塞线程。
- 后续：后续若要做真实交互体验，应在 CLI/TUI、Web、IDE 或更高层 runtime 单独建任务。

### 官方样板插件需要独立模块和独立回归面

- 背景：用户要求插件生态能让第三方开发者自行写插件并让使用者组合接入。
- 发现：一个可编译、可测试、可通过 ServiceLoader 发现的官方样板，比只写文档更能降低第三方插件作者学习成本。
- 影响：新增 `ai4j-plugin-ask-user` 模块、BOM 依赖管理项、CI matrix 项、RG-011 回归面和 docs-site 页面。
- 后续：第三方插件包规范继续保留在 `docs-site/docs/core-sdk/extension/plugin-packages.md`。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 插件类型 | 官方 Maven jar 样板插件 | 符合当前 classpath / Maven / ServiceLoader 生态，不引入未实现 marketplace 能力 | 远程 marketplace、runtime jar hot load、CLI install | accepted |
| `ask_user` 输出 | 返回 host-mediated request envelope | 插件层保持稳定 JSON 和宿主中立 | 插件直接解析 UI 流程或阻塞等待答案 | accepted |
| Tool 参数处理 | `argumentsRaw` 保留模型传入字符串 | 即使参数不是合法 JSON，envelope 仍稳定合法，宿主可自行按 schema 解析 | 插件强解析 JSON 并在 malformed 时失败 | accepted |
| 能力暴露 | enable 后 command/Skill/Prompt 可见，tool 仍需 `exposeTool` | 维持 extension API 的显式工具暴露门禁 | enable 后自动暴露 tool | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要 human review confirmation | 需要由用户侧确认；agent 只提交 review packet，不运行 `review-confirm` | human | 推送后或用户审查时 |
| 是否推进远程插件市场 | 本任务不做，当前文档明确不承诺 | owner | 后续插件生态任务 |
