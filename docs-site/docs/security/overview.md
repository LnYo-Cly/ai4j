---
sidebar_position: 1
---

# Security Overview

AI4J 的安全边界不是单个开关，而是由密钥、网络、Tool、MCP、RAG、Agent、Coding Agent 和 FlowGram 多层共同组成。接入前应先明确：哪些能力能被模型看见，哪些能力能被模型调用，哪些结果能回写到用户或日志。

## 基本原则

| 原则 | 要求 |
| --- | --- |
| 密钥不入仓 | provider key、MCP token、数据库密码只走环境变量或外部配置 |
| Tool 默认最小暴露 | 只把当前任务需要的 Function Tool 或 MCP Tool 传给模型 |
| 本地文件和命令要有边界 | Coding Agent 的 workspace、shell、patch、进程能力必须受审批和路径规则约束 |
| MCP 不等于可信边界 | 第三方 MCP server 的工具、资源和 prompt 都要按外部系统处理 |
| RAG 不等于权限系统 | 检索层必须继承业务权限，不能把所有文档索引成一个全局知识库 |
| Trace 可能含敏感信息 | prompt、工具参数、模型输出、节点 report 都可能进入日志或 UI |

## 密钥和配置

不要在代码、文档示例或测试数据中写真实密钥。推荐：

```bash
export OPENAI_API_KEY=...
export AI4J_PROVIDER_API_KEY=...
```

Spring Boot 项目中推荐把密钥放到环境变量、密钥管理系统或部署平台 secret，再由配置引用。不要把真实 key 写进 `application.yml` 并提交。

## Tool 安全

Tool 的安全边界分两层：

- `ToolRegistry` 或工具列表决定模型能看见什么。
- `ToolExecutor` 或实际执行器决定调用时发生什么。

建议：

1. 只暴露当前业务需要的工具。
2. 工具入参做显式校验，不要直接拼 SQL、shell 或 URL。
3. 对有副作用的工具增加确认、幂等键或审计日志。
4. 对外部 API 工具设置超时、重试上限和错误降级。
5. 把工具返回内容做脱敏后再回给模型或用户。

相关页面：

- [Tools Overview](/docs/core-sdk/tools/overview)
- [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
- [MCP Tool Exposure Semantics](/docs/mcp/tool-exposure-semantics)

## MCP 安全

AI4J 的 MCP 主线包括 client、transport、gateway 和 server 发布。不同路径的安全关注点不同。

| MCP 路径 | 主要风险 | 建议 |
| --- | --- | --- |
| 连接第三方 MCP server | 工具行为不透明、资源泄漏、prompt 注入 | 白名单 server、限制工具、隔离 token |
| 本地 stdio MCP | 进程权限过大、环境变量泄漏 | 独立用户或沙箱运行，明确工作目录 |
| SSE / HTTP MCP | 网络暴露、认证不足、中间人风险 | 使用 HTTPS、鉴权、超时和 allowlist |
| MCP Gateway | 多服务混用、用户隔离错误 | 明确全局 client 和用户级 client 的 key 规则 |
| 发布 Java MCP server | 误暴露内部方法或资源 | 注解扫描范围最小化，资源返回脱敏 |

顶层 [MCP Overview](/docs/mcp/overview) 是正式主线；`core-sdk/mcp/*` 只作为过渡期深层参考。

## RAG 和知识库安全

RAG 的最大风险通常不是模型，而是索引和权限。

生产接入时应确认：

- 文档入库前是否做敏感信息过滤。
- 切分后 chunk 是否仍携带原文权限元数据。
- 检索时是否按用户、租户、部门或项目过滤。
- 引用和 trace 是否会暴露未授权文档标题或片段。
- 向量库、embedding provider 和 rerank provider 是否符合数据合规要求。

相关页面：

- [Search & RAG Overview](/docs/core-sdk/search-and-rag/overview)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)

## Agent 与 Coding Agent 安全

通用 Agent 要重点看 tool loop、memory 和 trace。Coding Agent 还要额外看 workspace、shell、patch 和 approval。

建议：

- 默认把 shell、文件写入、网络请求、包管理命令视为高风险工具。
- 对写文件、改配置、运行外部命令设置审批。
- session 保存时避免记录真实密钥、私有代码片段和未脱敏客户数据。
- 子代理或委派任务必须继承最小权限，而不是自动继承所有工具。

相关页面：

- [Agent Tools and Registry](/docs/agent/tools-and-registry)
- [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)

## FlowGram 安全

FlowGram starter 默认更偏 demo / 内网集成姿态。正式上线前必须确认：

- `auth.enabled` 是否开启或被业务网关保护。
- `/flowgram/tasks/*` 是否只对授权用户开放。
- task report 是否会返回节点细节和 trace。
- HTTP、CODE、TOOL、KNOWLEDGE 节点是否受白名单约束。
- 默认内存 task store 是否满足生产持久化要求。

相关页面：

- [FlowGram Overview](/docs/flowgram/overview)
- [Production Checklist](/docs/operations/production-checklist)

## 上线前最小安全清单

- [ ] 没有真实 provider key、MCP token、数据库密码进入仓库。
- [ ] Tool / MCP 暴露范围按业务场景白名单收敛。
- [ ] RAG 检索继承业务权限和租户隔离。
- [ ] Trace、日志、task report、session store 做敏感信息处理。
- [ ] Coding Agent 的文件、shell、patch、进程工具有审批边界。
- [ ] FlowGram task API 有鉴权或网关保护。
- [ ] 所有外部 provider、MCP server、向量库都有超时和失败处理。
