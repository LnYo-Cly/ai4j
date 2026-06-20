# 首聊可复制代码合同

## Task ID

`2026-06-06-item-885d365a`

## 创建日期

2026-06-06

## 一句话结果

把 docs-site 和 `ai4j-app-builder` 的首聊示例绑定到本仓库可运行的本地回归测试，防止用户复制的第一段代码随 API 漂移失效。

## 完成后能得到什么

完成后，普通 Java 首聊对象链和 Spring Boot starter 注入链路都有无外部 provider、无密钥的本地 smoke 证据。docs-site 与 `ai4j-app-builder` 的第一段示例会明确指向这些回归合同，让后续 agent 修改示例、API、starter 或配置时知道必须跑哪些门禁。这个结果用于提升新用户首聊成功率，并把“文档可复制”从文案承诺变成可重复验证的工程事实。

## 交付物

- 可见产物：首聊 smoke tests、docs-site/skill 文案中的“本地回归保护”说明、Regression SSoT/Cadence 记录。
- 修改位置：`ai4j/`、`ai4j-spring-boot-starter/`、`docs-site/`、`skills/ai4j-app-builder/`、`docs/05-TEST-QA/`。
- 验证证据：RG-001 targeted test、RG-005 targeted test、RG-008 docs typecheck/build。

## 第一眼应该看什么

先读 `task_plan.md` 的范围，再看新增 Java test 和 docs-site `five-minute-first-chat.md` / `quickstart-java.md` / `quickstart-spring-boot.md` 的示例说明，最后看 `progress.md` 里的验证命令。

## 边界

- 范围内：普通 Java 首聊、Spring Boot starter 首聊、用户侧 skill recipes、对应回归台账。
- 范围外：真实 provider live tests、Agent/RAG/MCP/FlowGram 示例扩写、SDK 公共 API 重构、发布流程。
- 停止条件：若需要真实 API Key、外部 provider 或改动公共 API 才能证明示例成立，先停止并回到用户确认。

## 完成判断

- 普通 Java 首聊对象链有本地 JUnit 回归，不依赖真实 provider。
- Spring Boot starter 首聊注入链路有本地 JUnit 回归。
- docs-site 和 `ai4j-app-builder` 文案说明首聊示例受哪些本地门禁保护。
- Regression SSoT 和 Cadence Ledger 记录本次新增/强化的回归范围。
- 任务 progress/review/walkthrough 记录验证证据和残余。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

更新任务计划和发现记录，然后实现两个本地 smoke tests。
