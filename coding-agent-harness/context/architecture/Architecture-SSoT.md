# 架构事实源 / Architecture SSoT

Context Doc Type: architecture-ssot
Owner: project coordinator
Last Verified: 2026-06-08
Confidence: medium

## System Summary

`ai4j-sdk` 是 Java 8 Maven monorepo，提供轻量扩展 API、统一大模型 SDK、Agent runtime、Coding Agent runtime、CLI/TUI/ACP、Spring Boot starters、FlowGram starter/demo、BOM，以及相邻的 Docusaurus 文档站和 FlowGram Web demo。

本仓不包含外部 provider、向量数据库、FlowGram.ai 前端库、Dify/Coze/n8n 等外部系统的实现；它只维护 SDK/runtime/starter/demo 层的接入代码、配置绑定、契约适配和测试基线。

## Current Architecture Facts

| ID | Fact | Source Evidence | Last Verified | Confidence | Read Before |
| --- | --- | --- | --- | --- | --- |
| ARCH-001 | 根 `pom.xml` 定义 9 个 Maven module，Java baseline 为 1.8。 | `pom.xml` | 2026-06-08 | high | `AGENTS.md` |
| ARCH-008 | `ai4j-extension-api/` 是第三方扩展生态的轻量公共合同模块，负责 manifest、ServiceLoader discovery、explicit enable/expose gates 和 tool/command/skill/prompt/guardrail 中立资源 spec。 | `ai4j-extension-api/pom.xml`; `ai4j-extension-api/src/main/java`; `coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-system-wave-1-a924bf99/task_plan.md` | 2026-06-08 | high | `docs/11-REFERENCE/engineering-standard.md` |
| ARCH-002 | `ai4j/` 是 core SDK surface，覆盖 provider access、Chat/Responses、RAG、MCP、vector、image、audio、realtime。 | `AGENTS.md`; `ai4j/pom.xml`; `ai4j/src/main/java` | 2026-06-04 | high | `docs/11-REFERENCE/engineering-standard.md` |
| ARCH-003 | `ai4j-agent/` 承载 agent runtime、workflow、trace、memory、subagent/team orchestration。 | `AGENTS.md`; `ai4j-agent/src/main/java`; `AGENT.md` | 2026-06-04 | high | `AGENT.md` |
| ARCH-004 | `ai4j-coding/` 和 `ai4j-cli/` 承载 coding-agent runtime、workspace tools、outer loop、CLI/TUI/ACP host。 | `AGENTS.md`; `ai4j-coding/src/main/java`; `ai4j-cli/src/main/java` | 2026-06-04 | high | `docs/11-REFERENCE/testing-standard.md` |
| ARCH-005 | `docs-site/` 是 Node 20+ Docusaurus 文档站；`ai4j-flowgram-webapp-demo/` 是 React/Rsbuild FlowGram demo。 | `docs-site/package.json`; `ai4j-flowgram-webapp-demo/package.json` | 2026-06-04 | high | `AGENTS.md` |
| ARCH-006 | 新任务、progress、review 和 walkthrough 的默认 SSoT 是 `coding-agent-harness/planning/tasks/`；编号 `docs/` 主要保留历史材料、回归台账和 reference 标准，不再默认接收新任务 closeout。 | `AGENTS.md`; `coding-agent-harness/harness.yaml`; user confirmation 2026-06-07 | 2026-06-07 | high | `AGENTS.md` |
| ARCH-007 | 用户确认当前没有外部架构文档、接口文档、流程图、会议纪要、链接或导出包需要摄取。 | user confirmation 2026-06-04 | 2026-06-04 | high | `coding-agent-harness/context/development/external-source-packs/README.md` |

## Promotion Log

| Source Task | Promoted Fact | Destination | Decision | Date |
| --- | --- | --- | --- | --- |
| bootstrap conversation 2026-06-04 | `zh-CN` + `core,dashboard`; no external source packs | `coding-agent-harness/harness.yaml`; context docs | accepted | 2026-06-04 |
