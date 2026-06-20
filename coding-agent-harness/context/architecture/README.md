# 架构 / Architecture

Context Doc Type: architecture-index
Owner: project coordinator
Last Verified: unknown
Confidence: low

## Purpose

这个文件夹是系统结构事实源。它说明当前仓库负责什么、属于哪个更大的系统、以及 Agent 改代码前必须理解哪些服务、流程和架构决策。

Keep the English field names and file names because CLI checks rely on them.

## Read Order

1. `Architecture-SSoT.md`
2. `local-repo-context.md`
3. `system-map.md`
4. `service-catalog.md`
5. `critical-flows.md`
6. `services/<service-key>.md`
7. `decisions/ADR-*.md`

## Boundary

- 系统结构、服务责任、归属关系、关键流程放这里。
- Payload、endpoint 参数、event schema、SDK 细节放 `coding-agent-harness/context/integrations/`。
- 本地启动、mock、stub、跨仓调试经验放 `coding-agent-harness/context/development/`。

## Structure Contract

| 文件 / 路径 | 必须维护的事实 | 写入规则 |
| --- | --- | --- |
| `Architecture-SSoT.md` | 当前架构状态、关键决策、已知风险 | 只写跨任务仍成立的系统事实 |
| `local-repo-context.md` | 当前仓库在整体系统中的职责和边界 | 说明本仓负责什么、不负责什么 |
| `system-map.md` | 服务/模块拓扑、上下游关系、部署边界 | 用图或表表达全局关系，不写 payload |
| `service-catalog.md` | 服务总表；每个服务/微服务一行 | 新增服务先加这里，再决定是否建 profile |
| `services/<service-key>.md` | 单个服务的职责、数据、接口摘要、阅读入口 | 一个服务一个文件，不要多个服务混写 |
| `critical-flows.md` | 跨服务关键流程 | 写业务/系统流，不写接口字段明细 |

## Microservice Rule

如果系统有多个微服务，`service-catalog.md` 是总索引。每个已知服务至少有一行；只要该服务会影响本仓开发、调试、接口或任务判断，就为它创建 `services/<service-key>.md`。

每个 `services/<service-key>.md` 只回答三个问题：

1. 这个服务负责什么、拥有什么数据。
2. 它和本仓有什么关系。
3. Agent 修改本仓前还需要读哪些 `context/development` 和 `context/integrations` 文档。

不要在 `context/architecture` 里堆接口字段、mock 指令或临时调试记录。那些内容分别放到 `context/integrations` 和 `context/development`。
