# docs-site Wave 1 真实接入路径设计

## 目标

Wave 1 只解决第一批用户最容易卡住的问题：从哪里开始、按哪条真实对象链写代码、Spring Boot 怎么配、多 provider/profile 怎么接、中转平台怎么填、Tool/MCP/RAG/Memory 如何组合。

## 范围

本轮只改 canonical `docs-site/docs`，不做中文 i18n 全量同步。原因是 `docs-site/sidebars.ts` 已经指向 canonical 新 IA，而中文 i18n 仍保留大量旧路径；两边一起改会扩大风险。

## 页面策略

1. `start-here/five-minute-first-chat.md`
   - 保留首聊主线。
   - 增加 OpenAI-compatible/TroveBox 提示。
   - 明确下一步阅读路径。

2. `start-here/quickstart-java.md`
   - 写成普通 Java 的真实对象链 quickstart。
   - 不引入 `ChatClient`。
   - 强调 `Configuration` 是后续 Tool/MCP/RAG/Memory 的共同根。

3. `start-here/quickstart-spring-boot.md`
   - 写成 starter 接入 quickstart。
   - 覆盖 `AiService`、`AiServiceRegistry`、`ai.platforms`。

4. `spring-boot/configuration-reference.md`
   - 增加 OpenAI-compatible/TroveBox 配置块。
   - 增加多 profile 配置解释。

5. `core-sdk/service-entry-and-registry.md`
   - 与前置调用合同审计对齐。
   - 说明为什么主入口是 `AiService` / `AiServiceRegistry`。

6. 新增 `start-here/openai-compatible-and-trovebox.md`
   - 作为中转平台 recipe。
   - 解释 `api-host`、endpoint path、模型名、密钥来源。

## 明确不做

- 不写“企业采用”这类措辞。
- 不推荐不存在的 API。
- 不新增 Java API。
- 不改站点视觉。
- 不全量迁移 i18n。

## 验证

- `npm run build` in `docs-site/`
- `rg` 扫描不存在的 `ChatClient.openAi` / `Ai4j.chat()` 推荐
- `harness status --json .`
