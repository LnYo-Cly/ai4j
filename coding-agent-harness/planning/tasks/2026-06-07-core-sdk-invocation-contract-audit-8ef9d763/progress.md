# core sdk invocation contract audit - 进度

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

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

## 残余

- 无

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 已同步 generated ledger
- 负责人：coordinator

### [2026-06-07 11:13] - task-start

- 做了什么：开始审计 Core SDK 调用主线，不改代码，只产出设计结论
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-07 18:18] - source-audit

- 做了什么：读取 `Configuration`、`AiService`、`AiServiceRegistry`、`IChatService`、`OpenAiChatService`、`ToolUtil`、`DefaultRagService`、`ChatMemory`、Spring starter 和 start-here 文档。
- 验证结果：确认当前主线是对象链；Tool/MCP 是请求级白名单；RAG 是独立服务；Memory 是 Chat/Responses 共享事实层；Spring starter 注入 `AiService` / `AiServiceRegistry`。
- 下一步：写入设计结论。
- 证据：report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java:configuration aggregation; report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java:single-instance factory; report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java:multi-instance entry; report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java:tool/mcp dispatcher; report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/DefaultRagService.java:rag service contract; report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/memory/ChatMemory.java:memory projection

### [2026-06-07 18:25] - design-written

- 做了什么：新增 `design.md`，更新 `brief.md`、`task_plan.md`、`findings.md`、`execution_strategy.md`、`visual_map.md`、`lesson_candidates.md`。
- 验证结果：待运行模板残留扫描、diff hygiene 和 harness status。
- 下一步：补 review / walkthrough 后提交审查。
- 证据：diff:TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763:design package written

### [2026-06-07 19:24] - verification

- 做了什么：执行公开路径扫描、diff hygiene 和 harness status 预提交检查。
- 验证结果：`ChatClient.openAi` 未在公开源码/docs-site/README 路径中出现；`git diff --check` 通过，仅有 LF/CRLF 提示；`harness status --json .` 仅报告本任务包未提交导致的 dirty-state warning。
- 下一步：提交 task package，并重新运行 harness status 后提交 agent review。
- 证据：command:TARGET:.:`rg -n "ChatClient\.openAi|Ai4j\.chat\(|\.memory\(memory\)" -S .`; command:TARGET:.:`git diff --check`; command:TARGET:.:`npx.cmd --yes coding-agent-harness status --json .`
