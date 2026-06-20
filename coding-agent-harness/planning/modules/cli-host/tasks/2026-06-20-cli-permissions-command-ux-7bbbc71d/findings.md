# CLI permissions command UX - 发现记录

## 发现

| ID | Severity | Finding | Evidence | Decision |
| --- | --- | --- | --- | --- |
| F-001 | medium | `origin/dev` 已有 `--approval <auto|safe|manual>`、CLI/TUI approval prompt 和 ACP `session/request_permission`，但没有 `/permissions` 一等诊断入口。 | `rg "approval|permission" ai4j-cli/src/main/java` | 新增只读 `/permissions`，不做编辑器。 |
| F-002 | medium | Sandbox 会改变工具执行位置，但不能替代 permission/approval。 | docs-site `sandbox-routing.md` 和 `approval-permission-policy.md` | `/permissions` 输出必须明确 sandbox does not grant permission。 |
| F-003 | high | 权限诊断如果打印 raw tool input 或 prompt 可能泄露敏感信息。 | task threat review | 输出只写策略摘要和边界，不打印 raw input/output。 |
