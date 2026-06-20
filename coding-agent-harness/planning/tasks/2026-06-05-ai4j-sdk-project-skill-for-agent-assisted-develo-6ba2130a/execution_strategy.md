# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | not used |
| worker subagent | not authorized | write only after user approval | n/a | n/a | n/a | n/a | not used |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 变更是独立 Skill 包，验证面明确，`quick_validate.py` 和内容扫描足够覆盖本轮目标。 | self-check 后进入人工 review。 |
| Would a worker subagent materially help? | no | 没有可并行拆分的实现切片，使用 worker 会增加协调成本。 | coordinator 直接执行。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-05 | `skills/ai4j-sdk/**` | current checkout | 无需 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责创建 Skill、验证、提交和 harness 收口。 |
| Subagent 模式 | none | 单一独立目录，使用 self-check 更直接。 |
| 审查模型 | self-check + human review | Skill 语法、元数据和内容边界可本地验证；最终确认交给用户。 |
| Worktree 策略 | same checkout | 当前工作树干净，变更范围窄。 |
| 冲突控制 | coordinator owns shared files | 只修改 `skills/ai4j-sdk/**` 与本任务目录。 |
| 证据深度 | L1 | 使用 skill-creator 校验脚本和内容扫描，不需要运行 Maven 或 docs-site build。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | 检查 `.gitignore`、现有 skill 状态、生成文件列表 | `progress.md` | 目录选择合理且没有覆盖本地安装缓存。 |
| L0 | `rg` 扫描 TODO、模板文档名、错误 `$ai4j-sdk` 展开 | `progress.md` | 无残留或已修复。 |
| L1 | `quick_validate.py skills/ai4j-sdk` | `progress.md` | 返回 `Skill is valid!`。 |
| L1 | `git diff --check -- skills/ai4j-sdk` | `progress.md` | 无空白错误。 |

## 暂停 / 升级条件

- 用户要求新增公开安装页或发布到远程。
- Skill 需要适配某个具体 agent 工具的非 OpenAI 元数据格式。
- 校验脚本或安装器对 Skill 目录结构提出额外约束。
- 发现本仓库已有另一套公开 Skill 分发目录，需先合并而不是新增。
