# 开发上下文 / Development Context

Context Doc Type: development-index
Owner: project coordinator
Last Verified: unknown
Confidence: low

## Purpose

这个文件夹是 Agent 的开发输入包。它说明如何在本仓工作、外部服务不可见时如何开发、哪些内容只能 mock 或 stub、以及哪些假设不能直接成立。

Keep the English field names and section headings because CLI checks rely on them.

## Boundary

- 本地启动、代码地图、外部服务开发摘要、mock、stub、跨仓调试放这里。
- 长期系统结构放 `coding-agent-harness/context/architecture/`。
- API、event、webhook 等具体契约放 `coding-agent-harness/context/integrations/`。

## Structure Contract

| 文件 / 路径 | 必须维护的事实 | 写入规则 |
| --- | --- | --- |
| `local-setup.md` | 本仓启动、依赖、环境变量、常见失败 | 只写开发启动事实，不写生产架构 |
| `codebase-map.md` | 本仓代码入口、目录职责、阅读顺序 | Agent 改代码前先看这里 |
| `external-context/<service-key>.md` | 外部服务对本仓开发的影响、mock/stub、调试入口 | 一个外部服务一个文件 |
| `external-source-packs/` | 外部团队提供的大量资料、索引、摘要、投影状态 | 资料摄取层，不是最终事实层 |
| `stubs-and-mocks.md` | 本仓可用 mock/stub 策略 | 写可执行路径或命令 |
| `cross-repo-debugging.md` | 跨仓问题定位顺序和证据 | 写调试流程，不写服务职责总览 |

## External Service Rule

如果本仓依赖多个微服务，不要把所有外部知识写进一个大文档。只要某个外部服务会影响本仓开发或测试，就创建：

- `coding-agent-harness/context/architecture/services/<service-key>.md`：该服务是什么、负责什么。
- `coding-agent-harness/context/development/external-context/<service-key>.md`：本仓开发时如何 mock、stub、调试它。
- `coding-agent-harness/context/integrations/<contract>.md`：具体 API/event/webhook 契约。

`context/development` 只放“开发时怎么处理这个外部服务”。不要在这里维护完整系统拓扑，也不要把 payload schema 塞进来。

## External Source Pack Rule

如果外部团队给了多份文档、截图、导出包、会议纪要或链接，不要直接塞进 `context/{architecture,development,integrations}`。先读 `coding-agent-harness/governance/standards/external-source-intake-standard.md`，再决定是否创建 `external-source-packs/<source-key>/`。

`external-source-packs/` 只负责资料索引、摘要和投影状态。稳定结论必须回写到：

- `coding-agent-harness/context/architecture/services/<service-key>.md`
- `coding-agent-harness/context/development/external-context/<service-key>.md`
- `coding-agent-harness/context/integrations/<contract>.md`

## External Context Index

| Service Key | Why It Matters To This Repo | Local Stub / Mock | Debug Entry | Architecture Link | Contract Link | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
