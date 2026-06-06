# 轻量 ChatClient 首聊门面 - 进度

## 状态：进行中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

### [2026-06-06 18:01] - task-start

- 做了什么：开始实现轻量 ChatClient 首聊门面，目标是降低 Plain Java 第一条请求的接入成本，同时保留 AiService/IChatService 完整能力。
- 验证结果：已记录。
- 下一步：继续执行。
- 证据：n/a

### [2026-06-07 00:00] - scope-and-design

- 做了什么：按用户确认的方案 A 固定实现边界：新增薄 `ChatClient` facade，不改现有 `AiService` / `IChatService` 合同；同步 docs-site 与 ai4j-app-builder 首聊推荐路径。
- 验证结果：`git status --short --branch` 显示工作树干净；`npx --yes coding-agent-harness status --json .` 通过，当前 task active。
- 下一步：实现 core facade 与单元测试。
- 证据：command:TARGET:.:git status clean; command:TARGET:.:harness status pass

### [2026-06-07 02:12] - implementation

- 做了什么：新增 `io.github.lnyocly.ai4j.service.ChatClient`，提供 `openAi(apiKey)`、`openAi(apiKey, apiHost)`、`openAi(OpenAiConfig)`、`of(Configuration, PlatformType)`、`chat(model, userMessage)`、`chat(ChatCompletion)` 和底层对象 getter；新增 `ChatClientTest`。
- 验证结果：`mvn -pl ai4j -Dtest=ChatClientTest -DskipTests=false test` 通过，5 tests / 0 failures。
- 下一步：同步 docs-site、root README、ai4j-app-builder recipe。
- 证据：command:TARGET:.:mvn -pl ai4j -Dtest=ChatClientTest -DskipTests=false test passed

### [2026-06-07 02:17] - targeted-regression

- 做了什么：同步首聊文档和 skill recipe 后运行 targeted core regression。
- 验证结果：`mvn -pl ai4j "-Dtest=ChatClientTest,FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test` 通过，10 tests / 0 failures。
- 下一步：执行 RG-001/RG-007/RG-008。
- 证据：command:TARGET:.:targeted first-chat regression passed

### [2026-06-07 02:20] - regression-suite

- 做了什么：运行本任务要求的 core、package、docs-site 和 diff check。
- 验证结果：RG-001 `mvn -pl ai4j -am -DskipTests=false test` 通过，108 tests / 0 failures；RG-007 `mvn -DskipTests package` 通过 9 个 reactor modules；RG-008 `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` 退出 0，`NODE_OPTIONS=--max-old-space-size=8192 npm run build` 通过并生成 `docs-site/build`；`git diff --check` 无空白错误，仅 Windows LF/CRLF 提示。
- 下一步：写回 Regression SSoT/Cadence、review、walkthrough，并提交 review。
- 证据：command:TARGET:.:RG-001 pass; command:TARGET:.:RG-007 pass; command:TARGET:docs-site:RG-008 pass; command:TARGET:.:git diff --check no whitespace errors

### [2026-06-07 02:30] - review-packet

- 做了什么：更新 Regression SSoT、Cadence Ledger、review、walkthrough 和 lesson candidate 结论，准备提交人工确认。
- 验证结果：材料齐备，无 open material finding。
- 下一步：运行 harness lifecycle review 命令并提交 commit。
- 证据：review:TARGET:coding-agent-harness/planning/tasks/2026-06-07-chatclient-d5f84742/review.md:no material findings

## 残余

- 真实 provider key、quota、网络、模型质量不属于本任务默认证据；如后续 provider 协议或 release 任务需要，再按 LV-001 opt-in live gate 执行。
- 前置任务 `2026-06-06-item-885d365a` 仍需独立人工确认；本任务不代办其确认。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：core-sdk/docs-site touched; no module plan status change required
- Harness Ledger update needed：task-review 后由 lifecycle / governance rebuild 刷新
- 负责人：coordinator
