# core sdk invocation contract audit

## Task ID

`2026-06-07-core-sdk-invocation-contract-audit-8ef9d763`

## 创建日期

2026-06-07

## 一句话结果

完成 Core SDK 调用主线审计，明确当前应保留真实对象链，不新增隐藏式 ChatClient 门面。

## 完成后能得到什么

完成后，下一轮 agent 可以直接读取 `design.md`，知道 AI4J 当前真实调用主线、Spring Boot 入口、Tool/MCP/RAG/Memory 的接入边界，以及后续 API 升级的禁区和可做方向。本任务不写新 API，只给出设计判断，避免再次加入看似简化但实际偏离现有 SDK 合同的 facade。

## 交付物

- 可见产物：`design.md`、`findings.md`、任务审查和 walkthrough
- 修改位置：当前 harness task package
- 验证证据：源码/文档目标扫描、`git diff --check`、`harness status --json .`

## 第一眼应该看什么

先读 `design.md` 的结论，再读 `findings.md` 的证据表，最后看 `review.md` 和 `walkthrough.md` 的收口。

## 边界

- 范围内：审计 core SDK 调用入口、Spring starter 入口、Tool/MCP/RAG/Memory 接入方式，并形成设计结论。
- 范围外：新增或修改 Java API、改 docs-site 正文、改 Spring starter 行为、推送远程。
- 停止条件：若设计结论需要新增公开 API，先停在设计文档，不直接实现。

## 完成判断

1. `design.md` 明确当前对象链主线和不新增 `ChatClient` 门面的理由。
2. `findings.md` 记录源码证据和技术决策。
3. 任务包无模板占位内容。
4. `git diff --check` 和 `harness status --json .` 通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

完成审计材料并提交 agent review。
