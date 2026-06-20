---
sidebar_position: 1
---

# Production Checklist

这页是把 AI4J 接入真实项目之前的上线检查清单。它不是要让每个项目一次性启用所有能力，而是帮助你按已使用的模块确认风险边界。

## 1. 选择最小模块

| 你实际使用什么 | 应检查的模块 |
| --- | --- |
| 只做模型调用或 Tool | `ai4j` |
| Spring Boot 应用接入 | `ai4j` + `ai4j-spring-boot-starter` |
| RAG / Vector / Search | `ai4j` + 你的向量库或搜索服务 |
| MCP | `ai4j` + MCP server / gateway 配置 |
| Agent runtime | `ai4j` + `ai4j-agent` |
| Coding Agent | `ai4j-coding` + `ai4j-cli` |
| FlowGram 后端 | `ai4j-flowgram-spring-boot-starter` |

不要为了“以后可能会用”提前引入所有模块。先让当前路径稳定，再逐步升级。

## 2. 配置和密钥

- [ ] provider key 来自环境变量、secret manager 或部署平台，不写入仓库。
- [ ] baseUrl、model、timeout、proxy 配置可按环境区分。
- [ ] dev、test、prod 的 provider 配置不会互相污染。
- [ ] Spring Boot 项目中配置项有明确 owner。
- [ ] 多 provider 或多实例场景下，`AiServiceRegistry` 的 id 命名可读、可审计。

## 3. 网络和超时

- [ ] HTTP client 有连接超时、读取超时和整体调用超时。
- [ ] streaming 场景有取消、断线和前端关闭处理。
- [ ] 外部 provider 出错时有降级或用户可理解的错误信息。
- [ ] MCP SSE / HTTP transport 有认证、超时和重连策略。
- [ ] 代理、私有 baseUrl 或内网 endpoint 已被测试。

## 4. Tool 和 MCP 暴露面

- [ ] 默认不暴露所有工具。
- [ ] Function Tool 按业务场景白名单传入。
- [ ] MCP server 按服务、用户或租户隔离。
- [ ] 有副作用工具有确认、幂等或审计日志。
- [ ] 工具入参不直接拼 SQL、shell、文件路径或 URL。
- [ ] 工具返回给模型前做必要脱敏和长度限制。

## 5. RAG 和数据

- [ ] 文档入库前有来源、权限和更新时间记录。
- [ ] chunk 保留权限元数据，检索时按用户权限过滤。
- [ ] embedding provider 和向量库符合数据合规要求。
- [ ] rerank 输入不会泄漏超出当前用户权限的内容。
- [ ] 引用展示能追溯到原文来源。
- [ ] 索引重建、增量更新和失败重试有操作手册。

## 6. Agent / Coding Agent

- [ ] `maxSteps`、工具循环和停止条件有上限。
- [ ] memory / session store 不记录真实密钥。
- [ ] trace 记录做敏感信息脱敏。
- [ ] Coding Agent 的文件写入、shell、patch、进程工具有审批规则。
- [ ] workspace 根目录、允许写入路径和禁止路径明确。
- [ ] 子代理或委派任务不会自动获得超出需要的工具权限。

## 7. FlowGram

- [ ] `/flowgram/tasks/*` API 已被网关或 starter auth 保护。
- [ ] 默认内存 task store 不用于需要持久化的生产任务。
- [ ] report node details 和 trace 是否返回给前端已被评估。
- [ ] HTTP / CODE / TOOL / KNOWLEDGE 节点执行器有白名单和超时。
- [ ] 前端编辑态 schema 和后端执行态 schema 的转换已测试。
- [ ] cancel、report、result、validate API 都有错误路径验证。

## 8. 观测和排障

- [ ] 模型请求、工具调用、MCP 调用、RAG 检索、Agent step 有可关联 trace id。
- [ ] 日志级别不会在生产输出完整 prompt、密钥或客户数据。
- [ ] 常见 provider 错误有映射说明。
- [ ] streaming、tool failure、MCP disconnect、vector store failure 有排障路径。
- [ ] 文档里给出团队内部的 provider、向量库、MCP server owner。

## 9. 回归命令

| 改动范围 | 建议命令 |
| --- | --- |
| docs-site | `npm run build` |
| Core SDK | `mvn -pl ai4j -DskipTests=false test` |
| Agent | `mvn -pl ai4j-agent -DskipTests=false test` |
| Coding Agent / CLI | `mvn -pl ai4j-coding -DskipTests=false test`、`mvn -pl ai4j-cli -DskipTests=false test` |
| Spring Boot starter | `mvn -pl ai4j-spring-boot-starter -DskipTests=false test` |
| FlowGram starter | `mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test` |

live provider 测试需要真实凭证时，必须走显式 profile 或单独环境，不要让默认本地测试依赖外部密钥。

## 10. 发布前确认

- [ ] 使用 [Version Compatibility](/docs/reference/version-compatibility) 检查版本和模块。
- [ ] 使用 [Security Overview](/docs/security/overview) 检查安全边界。
- [ ] 使用 [Troubleshooting](/docs/troubleshooting/overview) 准备排障入口。
- [ ] 使用 [Migration Guide](/docs/migration/overview) 标记旧 API、旧文档或旧示例的替代路径。
