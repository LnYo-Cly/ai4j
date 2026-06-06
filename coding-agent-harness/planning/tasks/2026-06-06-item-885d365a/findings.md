# 首聊可复制代码合同 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 当前首聊示例的真实运行边界

- 背景：上一轮 docs-site 已补 5 分钟首聊路径，但用户侧示例仍需要可执行证据，而不是只靠文案校对。
- 发现：`Configuration` 当前默认初始化 `new OkHttpClient()`；`OpenAiChatService` 从 `configuration.getOkHttpClient()` 发起请求；Spring starter 在 `AiConfigAutoConfiguration#initOkHttp` 中构造并写入 OkHttpClient。
- 影响：普通 Java 示例“不必先手动 setOkHttpClient”在当前代码上成立；本任务应补的是首聊对象链和 starter 注入链路的本地 smoke 合同，而不是改公共 API。
- 后续：新增无 provider/API Key 的本地 tests，并在 docs/skill 说明对应验证命令。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 首聊验证方式 | 本地 JUnit smoke + docs build | 成本低、确定性强、可进入默认本地/CI门禁 | 只修文档；新增独立 examples 模块 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要 live provider 证据 | 不需要；本任务验证复制代码合同，不验证 provider 质量 | coordinator | closeout |
