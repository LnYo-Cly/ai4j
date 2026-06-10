# Core SDK configuration and invocation experience upgrade design - 进度

## 状态：已完成

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

### [2026-06-07 15:31] - task-start

- 做了什么：开始 Core SDK 配置与调用体验升级设计；本轮只做设计，不改 Java API。
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-07 19:46] - source-doc-audit

- 做了什么：审计 `Configuration`、`AiService`、`AiServiceRegistry`、`DefaultAiServiceRegistry`、Spring Boot starter 配置绑定、OpenAI 配置字段、README/docs-site 示例和相关测试。
- 验证结果：确认多 profile 能力已存在；OpenAI-compatible 中转平台可用 `platform: openai` + `api-host` 表达；Plain Java 的主要痛点是配置样板；docs-site 已回到真实对象链但 recipe 仍需组织。
- 下一步：写入 `design.md`、`findings.md`、review 和 walkthrough。
- 证据：report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java:configuration sample cost; report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java:profile registry entry; report:TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiConfigProperties.java:ai.platforms binding; report:TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/config/OpenAiConfig.java:apiHost supports compatible endpoints; report:TARGET:docs-site:real object-chain docs

### [2026-06-07 19:52] - design-written

- 做了什么：新增 `design.md`，更新 `findings.md`，明确 P0 docs/recipe、P1 配置 helper、P2 registry/starter 默认 profile 三个波次。
- 验证结果：待运行占位符扫描、`git diff --check` 和 harness status。
- 下一步：提交 task review。
- 证据：diff:TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f:design package written

### [2026-06-07 19:57] - verification

- 做了什么：执行模板残留扫描、diff hygiene 和 harness status 预提交检查。
- 验证结果：模板扫描无阻塞占位符；`git diff --check` 通过，仅有 LF/CRLF 提示；`harness status --json .` 仅报告当前任务包未提交导致的 dirty-state warning，材料状态为 ready。
- 下一步：提交设计包并提交 agent review。
- 证据：command:TARGET:.:`rg -n "<template placeholder patterns>" coding-agent-harness/planning/tasks/2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f`; command:TARGET:.:`git diff --check`; command:TARGET:.:`npx.cmd --yes coding-agent-harness status --json .`

### [2026-06-07 15:40] - task-review

- 做了什么：Core SDK configuration and invocation experience design completed; recommends docs/recipe first, Configuration helper API second, registry/starter default profile third.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-10 12:23] - task-complete

- 做了什么：Human review confirmed; closeout finalized after user confirmation.
- 验证结果：已记录
- 下一步：完成
- 证据：n/a
