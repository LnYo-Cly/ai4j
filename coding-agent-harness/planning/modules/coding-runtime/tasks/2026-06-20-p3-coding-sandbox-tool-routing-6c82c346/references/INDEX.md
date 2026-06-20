# 任务参考资料索引

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | code | TARGET:AGENTS.md | 仓库模块边界、Java 8、Harness 任务位置和回归记录规则。 | coordinator / reviewer |
| REF-002 | code | TARGET:docs/11-REFERENCE/engineering-standard.md | 工程边界：`ai4j-coding` owns workspace/code execution；starters/docs 不承载生产逻辑。 | coordinator / reviewer |
| REF-003 | code | TARGET:docs/11-REFERENCE/testing-standard.md | 选择 RG-003/RG-008 验证深度和证据格式。 | coordinator |
| REF-004 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox | P2 Sandbox SPI source contract consumed by P3 routing. | coordinator / reviewer |
| REF-005 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/AgentSessionSandboxBinding.java | Session 只持久化非敏感 sandbox 摘要，不保存 live handle。 | coordinator / reviewer |
| REF-006 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/tool/BashToolExecutor.java | 本轮远端化的 foreground `bash action=exec` 工具入口。 | coordinator / reviewer |
| REF-007 | code | TARGET:docs-site/docs/agent/sandbox-spi.md | P2 Sandbox SPI 文档；本轮 docs 与其对齐。 | docs reviewer |
| REF-008 | code | TARGET:docs-site/docs/coding-agent/tools-and-approvals.md | Approval 与 sandbox routing 分层说明入口。 | docs reviewer |

## 使用规则

- 每条参考资料都要说明用途，否则不要登记。
- 外部链接需要写清访问日期或版本线索，避免后续复查时语境漂移。
- reviewer 或 worker 只应读取与其 scope 相关的条目。
