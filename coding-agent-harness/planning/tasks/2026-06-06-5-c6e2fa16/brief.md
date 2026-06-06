# 5 分钟首聊主路径文档

## Task ID

`2026-06-06-5-c6e2fa16`

## 创建日期

2026-06-06

## 一句话结果

docs-site 获得一条面向新用户的 5 分钟首聊主路径，并把 Java / Spring Boot Quickstart 与公开 Skill 安装入口收敛到这条路径。

## 完成后能得到什么

新用户打开文档站后，可以先从 `start-here/five-minute-first-chat.md` 获取普通 Java、Spring Boot 和 agent skill 三条入口的最短成功路径。文档会明确依赖版本、环境变量、对象链、可复制代码、成功标准和常见失败点；公开 README 会给出同一条安装和调用方式。该结果用于降低第一次接入成本，并作为后续 Tool、MCP、RAG、Agent 文档的前置入口。

## 交付物

- 可见产物：5 分钟首聊页、更新后的 Java/Spring Boot Quickstart、README 入口和导航入口。
- 修改位置：`docs-site/docs/start-here/`、`docs-site/sidebars.ts`、`docs-site/README.md`、`README.md`、Feature SSoT 与本任务包。
- 验证证据：`docs-site` typecheck/build、链接/文本扫描、harness status。

## 第一眼应该看什么

先读 `docs-site/docs/start-here/five-minute-first-chat.md`，再读 `docs-site/docs/start-here/quickstart-java.md` 与 `docs-site/docs/start-here/quickstart-spring-boot.md`。验证证据见 `progress.md`，收口结论见 `walkthrough.md`。

## 边界

- 范围内：docs-site 入门路径、公开 README 的文档入口、Skill 安装和调用提示、Feature SSoT/Regression 证据记录。
- 范围外：Java API 行为改造、发布流程、英文站点重写、RAG/MCP/Agent 深页大规模扩写。
- 停止条件：发现示例 API 与源码不一致、docs-site 构建失败且无法定位、或需要真实 provider 凭证验证时暂停并记录 residual。

## 完成判断

- `five-minute-first-chat.md` 存在，并覆盖普通 Java、Spring Boot、Skill、成功标准和排障。
- Java/Spring Boot Quickstart 使用当前版本 `2.3.0` 和当前源码包路径。
- intro/sidebar/choose-your-path/feature-map/why-ai4j 指向首聊主路径。
- docs-site README 和根 README 不再把新用户引到旧 `getting-started` 主线。
- RG-008 验证结果记录到 `progress.md`，并完成 walkthrough。

## 执行合同

- Owner：coordinator
- 生命周期状态：未开始
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行 docs-site typecheck/build，并根据结果更新 Regression SSoT、Cadence Ledger 和 walkthrough。
