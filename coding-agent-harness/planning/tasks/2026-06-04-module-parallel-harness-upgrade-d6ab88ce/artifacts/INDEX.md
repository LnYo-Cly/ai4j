# 任务产物索引

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | diff | TARGET:coding-agent-harness/harness.yaml | `module-parallel` capability and modules registry SSoT. | coordinator |
| ART-002 | report | TARGET:coding-agent-harness/planning/modules/Module-Registry.md | Generated view listing 10 registered modules. | harness CLI |
| ART-003 | diff | TARGET:coding-agent-harness/planning/modules/**/brief.md | Project-specific module briefs. | coordinator |
| ART-004 | diff | TARGET:coding-agent-harness/planning/modules/**/module_plan.md | Project-specific module plans with scope, shared surfaces and validation commands. | coordinator |
| ART-005 | command | TARGET:progress.md | Records status and module-list verification summaries. | coordinator |

## 使用规则

- 大段命令输出不单独提交，以 `progress.md` 摘要和可重跑命令为准。
- Module Registry 是生成视图，不直接手工编辑。
- 后续模块任务应引用对应 module `brief.md` / `module_plan.md`。
