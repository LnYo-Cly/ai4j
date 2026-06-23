# Add Anthropic Messages adapter

## Task ID

`2026-06-22-add-anthropic-messages-adapter-3876ee40`

## 创建日期

2026-06-22

## 一句话结果

在 `ai4j` 核心模块新增一个手写的 Anthropic Messages 格式适配器（与现有 12 家 OpenAI 兼容 provider 同构），让 SDK 既能调 Claude 本体，也能调合作厂家的 Anthropic 兼容入口（智谱 Coding Plan、Minimax-M3 等）。

## 为什么要做

实测确认（2026-06-22）：同一个智谱 coding-plan key 打 OpenAI 格式端点 `api/paas/v4` 报「余额不足」，打 Anthropic 格式端点 `api/anthropic/v1/messages` 秒回正常 `glm-5.2` 回复。Minimax-M3 文档同样首推 Anthropic 入口。**当前 SDK 零 Anthropic 支持**（无 `platform/anthropic/` 包、`PlatformType` 无 ANTHROPIC），所以 coding-plan 这类 key 在 SDK 里走不通。

厂家生态正收敛到「OpenAI 格式 + Anthropic 格式」两套线协议。SDK 已覆盖 OpenAI 格式这半边（12 家）；补一个 Anthropic 适配器即覆盖另外半边，是从「统一 N 家」进化到「统一 2 种协议 × N 个端点」的关键一步。

## 完成后能得到什么

- 能用 Anthropic Messages 格式调：Claude 本体（`api.anthropic.com`）、智谱 Coding Plan（`open.bigmodel.cn/api/anthropic`）、Minimax-M3 Anthropic 入口、未来任何 Anthropic 兼容厂家。
- 用户手里的 coding-plan key 能在 ai4j 里正常跑（当前报余额不足是因为端点/协议选错）。
- 纯 Java + fastjson2 实现，零 Kotlin/Jackson 依赖，与现有 provider 风格一致，可即时改源码。

## 交付物

- 新增 `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/anthropic/`：`AnthropicConfig`、Messages 请求/响应/流实体、`AnthropicChatService implements IChatService`。
- `PlatformType` 增加 `ANTHROPIC`；`Configuration` 增加 `anthropicConfig`；`AiServiceFactory` 注册。
- 单元测试（请求/响应/流映射）+ Live 测试（`@Category(LiveProviderTest.class)`，读 `ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL`）。
- 验证证据：`mvn -pl ai4j -DskipTests=false test` 通过（现有 103 测试不受影响 + 新增）。

## 第一眼应该看什么

先看 `task_plan.md`、`findings.md`，然后看 `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/chat/OpenAiChatService.java`（参考适配器）、`ai4j/src/main/java/io/github/lnyocly/ai4j/service/IChatService.java`（要实现的接口）、`ai4j/src/main/java/io/github/lnyocly/ai4j/platform/zhipu/`（紧凑 provider 范例）。

## 边界

- 范围内：`ai4j` 核心模块的 provider 适配器；Messages 格式的 chat / stream / tool_use；真实单测 + live 烟测。
- 范围外：不替换/重构现有 OpenAI 兼容适配器；不改 docs-site；不在首轮接入 agent/coding/cli 层（那是后续任务）；不引入官方 Kotlin SDK。
- 停止条件：若 Anthropic 协议映射出现数据丢失、或现有测试回归失败、或需要拉进 Kotlin/Jackson 才能继续。

## 完成判断

1. `platform/anthropic/` 实现并编译通过，`AnthropicChatService` 实现 `IChatService`。
2. 请求/响应/流映射单测通过；live 烟测对 `open.bigmodel.cn/api/anthropic` + coding-plan key 返回内容。
3. `mvn -pl ai4j -DskipTests=false test` 全绿。
4. task-local review / walkthrough / findings / lesson 文件已收口。

## 执行合同

- Owner：coordinator
- 生命周期状态：planned
- 必需文件：`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

按 `execution_strategy.md` 执行：实现 `platform/anthropic/` 骨架 → 映射 Messages 请求/响应 → SSE 流 → 单测 → live 烟测 → 回归 → 收口。
