# docs site wave 1 real onboarding recipes

## Task ID

`2026-06-07-docs-site-wave-1-real-onboarding-recipes-f62730fb`

## 创建日期

2026-06-07

## 一句话结果

完成 docs-site Wave 1 真实接入路径重写，让新用户能按真实 AI4J 对象链完成 Java、Spring Boot、多 profile 和中转平台接入。

## 完成后能得到什么

完成后，默认 docs-site 的首屏路径会围绕真实代码组织：普通 Java 使用 `Configuration -> AiService -> IChatService`，Spring Boot 使用 starter 注入 `AiService` / `AiServiceRegistry`，OpenAI-compatible/TroveBox 使用 `platform: openai` + `api-host` 配置，Tool/MCP/RAG/Memory 通过 recipe 组合。中文 i18n 暂不做同步重写，避免扩大范围。

## 交付物

- 可见产物：更新后的 docs-site onboarding / recipe 页面、task review、walkthrough
- 修改位置：`docs-site/docs` 和当前 harness task package
- 验证证据：`npm run build`、链接/文本扫描、`harness status --json .`

## 第一眼应该看什么

先读 `design.md`，再看修改后的 `docs-site/docs/start-here/*`、`spring-boot/*` 和 `core-sdk/service-entry-and-registry.md`。

## 边界

- 范围内：canonical `docs-site/docs` 的首聊、Java quickstart、Spring Boot quickstart、service registry、中转平台/recipe 相关页面。
- 范围外：Java API、docs-site i18n 全量同步、站点视觉重设计、远程推送。
- 停止条件：若发现页面引用不存在 API 或 build 失败且无法定位，需要暂停修正设计。

## 完成判断

1. 首屏路径不再推荐不存在的 `ChatClient` 或隐藏式 `Ai4j.chat()`。
2. Java / Spring Boot / OpenAI-compatible / profile / recipe 路径各有可复制入口。
3. sidebars 中被新增或改动的路径能通过 docs-site build。
4. 验证结果写入 `progress.md` 和 `walkthrough.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：未开始
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补设计文档，启动任务，改 canonical docs-site 页面。
