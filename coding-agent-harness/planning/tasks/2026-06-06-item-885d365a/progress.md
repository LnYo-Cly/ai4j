# 首聊可复制代码合同 - 进度

## 状态：审查中

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

- Global sync status：pending-coordinator-pass / synced / n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle closeout
- 负责人：coordinator / 不适用

### [2026-06-06 10:33] - task-start

- 做了什么：开始为首聊 public docs 和 ai4j-app-builder 示例建立可复制代码回归合同。
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-06 10:36] - scope-and-design

- 做了什么：读取工程/测试/执行标准、Feature SSoT、Regression SSoT、Cadence Ledger，并确认本任务采用“首聊可复制代码合同”方案。
- 验证结果：当前工作区在任务开始前干净；`Configuration` 已有默认 OkHttpClient，Spring starter 会注入配置化 OkHttpClient。
- 下一步：实现普通 Java 与 Spring Boot starter 的本地 smoke tests。
- 证据：report:TARGET:coding-agent-harness/planning/tasks/2026-06-06-item-885d365a/findings.md:记录首聊示例真实运行边界

### [2026-06-06 18:38] - implementation

- 做了什么：新增 `FirstChatCopyableCodeTest` 和 `AiServiceFirstChatAutoConfigurationTest`，并同步 docs-site / `ai4j-app-builder` recipe 的首聊回归合同说明。
- 验证结果：Spring starter targeted test 首次通过；core targeted test 首次运行暴露 PowerShell 逗号解析问题，随后暴露 JUnit primitive long 断言写法问题，均已修复。
- 下一步：重跑 targeted regression 和固定门禁。
- 证据：diff:TARGET:ai4j/src/test/java/io/github/lnyocly/ai4j/docs/FirstChatCopyableCodeTest.java:Plain Java first-chat copyable code contract

### [2026-06-06 18:44] - verification

- 做了什么：运行本轮 touched surface 的 targeted regression 和固定门禁。
- 验证结果：RG-001、RG-005、RG-007、RG-008 均通过；无 live provider/API Key 依赖。
- 下一步：提交 Agent Review Submission，等待人工确认。
- 证据：command:TARGET:.:mvn -pl ai4j "-Dtest=FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test passed with 5 tests
- 证据：command:TARGET:.:mvn -pl ai4j -am -DskipTests=false test passed with 103 tests
- 证据：command:TARGET:.:mvn -pl ai4j-spring-boot-starter -Dtest=AiServiceFirstChatAutoConfigurationTest -DskipTests=false test passed with 1 test
- 证据：command:TARGET:.:mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test passed with core 103 tests and starter 3 tests
- 证据：command:TARGET:docs-site:NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck passed
- 证据：command:TARGET:docs-site:NODE_OPTIONS=--max-old-space-size=8192 npm run build passed and generated docs-site/build
- 证据：command:TARGET:.:mvn -DskipTests package passed across 9 reactor modules

### [2026-06-06 10:53] - task-review

- 做了什么：First-chat copyable code contract is implemented and verified: core/starter smoke tests added, docs-site and ai4j-app-builder guards updated, RG-001/RG-005/RG-007/RG-008 passed.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
