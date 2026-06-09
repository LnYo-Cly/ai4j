# AI4J Extension Scaffold Author Experience Wave 11 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### scaffold README 是第三方插件作者体验的关键入口

- 背景：Wave 9 已提供 `extension init`，但生成 README 只说明最小内容和 `mvn test`，不足以指导第三方作者交付可判断风险的插件包。
- 发现：docs-site `plugin-packages.md` 已经定义三段式门禁、CLI validate/resource/run 和发布建议；scaffold README 应投影这些稳定事实。
- 影响：本轮选择增强 README 和 docs cookbook，而不是新增公共 manifest 字段或远程分发生态。
- 后续：如果未来需要版本兼容矩阵或插件索引，应单独建任务，不能把它写成当前已实现能力。

### RG-004 有两个不同残余

- 背景：本轮触及 `ai4j-cli`，需要运行 CLI touched-surface 证据。
- 发现：`-am` broad gate 仍受上游 R-008 阻塞；直接 CLI 模块测试又暴露出 ACP/JLine 非 extension scaffold 断言漂移。
- 影响：targeted `Ai4jCliTest` 可作为本轮 scaffold 合同证据，但 RG-004 不能宣称全绿。
- 后续：R-009 已加入 Regression SSoT，后续单独修复 CLI ACP/JLine 测试漂移。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 作者体验改进位置 | 先改生成 README + docs-site cookbook | 这是第三方作者第一接触点，能提升可用性且不扩大 runtime 语义 | 新增远程 marketplace、依赖自动安装、manifest 字段扩容 | accepted |
| 运行时语义 | 不改 `ai4j-extension-api` 和 Agent/Coding Agent runtime | 当前问题是作者流程说明不足，不是公共合同缺口 | 新增兼容字段、插件索引或热加载 | accepted |
| 验证策略 | targeted CLI + docs build + package smoke | 能覆盖本轮实际改动；broad CLI gate 的失败已路由到 R-008/R-009 | 在本任务内修复不相关 ACP/JLine 失败 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要远程插件目录 | 当前不纳入，本轮只做本地 Maven/classpath 插件作者体验 | owner | 未来插件生态分发任务 |
