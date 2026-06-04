# CLI Host 模块计划

## 模块身份

- 模块 Key：`cli-host`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-cli/**`
- 共享面：CLI docs、root build config、coding runtime integration contracts
- 依赖模块：`coding-runtime`

## 边界

- 可以编辑：CLI host 源码、测试、模块 POM。
- 禁止编辑：coding runtime 内核、core SDK、starter 模块，除非任务明确批准。
- 外部依赖：terminal capabilities、ACP client behavior、本地 session 文件。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| CLI-01 | 维护 CLI/TUI/ACP host 合同 | planned | none | coding-runtime |
| CLI-02 | session/runtime integration 回归 | planned | none | CLI-01 |
| CLI-03 | 用户文档和发布影响同步 | planned | none | CLI-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| none | planned | coordinator | none | 有模块任务后替换此行。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-cli -DskipTests=false test` | yes |
| 依赖构建 | `mvn -pl ai4j-cli -am -DskipTests package` | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 CLI targeted test 或 smoke command。
- 变更文件：只列 `ai4j-cli/**` 及批准的共享文件。
- 残余风险：平台或终端差异未验证时必须说明。
- 需要 coordinator 同步：影响 coding-runtime、docs 或 release config 时同步。
