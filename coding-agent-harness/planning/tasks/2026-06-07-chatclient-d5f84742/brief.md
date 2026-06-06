# 轻量 ChatClient 首聊门面

## Task ID

`2026-06-07-chatclient-d5f84742`

## 创建日期

2026-06-07

## 一句话结果

为 core SDK 增加一个轻量 `ChatClient` 首聊门面，让 Plain Java 用户用最少对象链完成第一条 OpenAI chat 请求。

## 完成后能得到什么

用户能在不理解 `Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse`
完整对象链的情况下先跑通第一条请求；进阶用户仍可从 `ChatClient` 拿到底层 `Configuration`、
`AiService` 与 `IChatService`，继续使用工具调用、流式、RAG、MCP 等完整能力。docs-site 与
`ai4j-app-builder` recipe 会同步把 `ChatClient` 作为 Plain Java 首聊推荐路径，并保留完整对象链作为进阶路径。

## 交付物

- 可见产物：`ChatClient.openAi(...).chat(model, message)` 可复制首聊入口。
- 修改位置：`ai4j/` core SDK、`docs-site/` 首聊文档、`skills/ai4j-app-builder/references/recipes.md`。
- 验证证据：RG-001 core tests、RG-007 monorepo package、RG-008 docs-site typecheck/build。

## 第一眼应该看什么

先读 `ai4j/src/main/java/io/github/lnyocly/ai4j/service/ChatClient.java` 和
`ai4j/src/test/java/io/github/lnyocly/ai4j/service/ChatClientTest.java`，再读 docs-site 的
首聊页面与 `skills/ai4j-app-builder/references/recipes.md`。

## 边界

- 范围内：新增 core SDK 轻量 chat facade；补充本地单元测试；把 Plain Java 首聊文档与 skill recipe 调整为 `ChatClient` 优先。
- 范围外：不重构 `AiService` 工厂、不改变现有 `IChatService` 合同、不新增 provider、不调整 agent/runtime/FlowGram 行为。
- 停止条件：如果需要破坏现有对象链 API、改 Maven 坐标、或引入非 Java 8 语法，必须暂停并重新确认。

## 完成判断

- `ChatClient.openAi(String apiKey)` 与可注入 `apiHost` 的测试路径存在，并能通过本地 provider double 返回文本。
- `ChatClient` 不遮蔽底层能力，仍能暴露 `Configuration`、`AiService`、`IChatService` 与原始 response 方法。
- docs-site 首聊入口和 ai4j-app-builder recipe 使用 `ChatClient` 作为 Plain Java 推荐首聊路径。
- RG-001、RG-007、RG-008 相关命令通过或把环境性 residual 明确记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交 harness review，等待人工确认。
