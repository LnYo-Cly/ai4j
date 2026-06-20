# Core SDK configuration and invocation experience upgrade design

## Task ID

`2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f`

## 创建日期

2026-06-07

## 一句话结果

产出 Core SDK 配置与调用体验升级设计，明确哪些改动能降低接入成本，哪些 API 形态暂不应新增。

## 完成后能得到什么

完成后，项目会得到一份基于真实代码的升级设计：Plain Java 如何更少样板地创建 `Configuration` / `AiService`，Spring Boot 如何更自然地绑定多 provider / 多 profile，中转平台如何作为 OpenAI-compatible 配置进入现有体系，以及 Chat、Responses、Tool/MCP、RAG、Memory 应如何组合成可复制 recipe。下一轮实现可以按这份设计拆任务，而不是凭感觉新增 facade。

## 交付物

- 可见产物：`design.md`、`findings.md`、`visual_map.md`、`review.md`
- 修改位置：当前 harness task package；必要时只读源码和 docs-site
- 验证证据：源码扫描、docs-site 示例扫描、`git diff --check`、`harness status --json .`

## 第一眼应该看什么

先读 `design.md` 的升级分层，再读 `findings.md` 的源码证据和决策表。

## 边界

- 范围内：Core SDK 配置体验、provider/profile 体系、OpenAI-compatible 中转平台接入、Spring starter 绑定、组合 recipe 设计。
- 范围外：直接改 Java API、发布新 facade、重写 docs-site 正文、推送远程。
- 停止条件：若设计需要新增公开 API 或改变现有调用合同，先停在设计文档等待人工确认。

## 完成判断

1. `design.md` 明确体验痛点、升级原则、建议 API/配置形态和不做项。
2. `findings.md` 用源码证据说明为什么这些升级贴合现有架构。
3. 设计区分立即可做、需要 API 评审、暂不做三类事项。
4. 验证命令和残余风险写入 `progress.md` / `walkthrough.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：未开始
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

启动任务，审计 Core SDK 配置、registry、starter 和文档示例现状。
