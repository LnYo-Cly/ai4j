# 任务参考资料索引

仅在任务需要外部资料、跨仓上下文、reviewer 输入包或生成参考材料时使用。不要把无关背景资料堆进来。

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | design | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | AI4J Extension System 的 Package / Manifest / Extension / Resource 分层和 Wave 路线。 | coordinator / reviewer |
| REF-002 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/skill/CodingSkillDiscovery.java | 现有 coding skill discovery 和 allowed read root 机制。 | coordinator / reviewer |
| REF-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | 现有 extension list/inspect/run CLI 命令和 enable 门禁。 | coordinator / reviewer |

## 使用规则

- 每条参考资料都要说明用途，否则不要登记。
- 外部链接需要写清访问日期或版本线索，避免后续复查时语境漂移。
- reviewer 或 worker 只应读取与其 scope 相关的条目。
