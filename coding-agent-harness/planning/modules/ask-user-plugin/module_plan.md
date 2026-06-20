# Ask User Plugin 模块计划

## 模块身份

- 模块 Key：`ask-user-plugin`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-plugin-ask-user/`
- 共享面：根 `pom.xml`、`ai4j-bom/pom.xml`、README、docs-site、Regression SSoT、Cadence Ledger、harness context
- 依赖模块：`extension-api`

## 边界

- 可以编辑：`ai4j-plugin-ask-user/**`，以及与该模块直接相关的文档、BOM 和治理记录。
- 禁止编辑：`ai4j-extension-api` 公共合同、Agent/Coding Agent runtime 适配器、CLI 命令实现，除非任务明确扩大范围。
- 外部依赖：无外部服务；模块默认不触发网络、provider、数据库或真实 UI。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| MOD-01 | 定义模块运行合同 | done | `coding-agent-harness/planning/tasks/2026-06-09-ai4j-official-ask-user-plugin-wave-10-10f4445f/task_plan.md` | none |
| MOD-02 | 首个官方 ask-user 插件实现 | done | `coding-agent-harness/planning/tasks/2026-06-09-ai4j-official-ask-user-plugin-wave-10-10f4445f/task_plan.md` | MOD-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-09-ai4j-official-ask-user-plugin-wave-10-10f4445f` | review | coordinator | RG-011 pass; RG-007 pass; RG-008 pass; diff check pass; harness status 0 failures before commit | 首个官方 ask-user 插件模块，等待用户侧人工确认。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块任务合同 | `npx.cmd --yes coding-agent-harness status --json .` | yes |
| 插件模块测试 | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | yes |
| 共享包构建 | `mvn -DskipTests package` | yes when root POM/BOM changed |

## 交接

- 分支：`main`
- Commit SHA：pending until commit
- 检查：`mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` passed; `mvn -DskipTests package` passed; docs-site typecheck/build passed; `git diff --check` passed; harness status had 0 failures before commit
- 变更文件：`ai4j-plugin-ask-user/`、root POM、BOM、README/docs-site、harness context、regression docs
- 残余风险：无远程市场、无运行时 jar 热加载、无 UI 阻塞语义；这些是明确范围外。
- 需要 coordinator 同步：推送后由用户侧决定是否执行 human review confirmation；agent 不运行 `review-confirm`

## 模板边界

模块根目录默认只拥有 `brief.md` 和 `module_plan.md`。`execution_strategy.md`、
`visual_map.md`、`review.md`、`walkthrough.md` 等执行合同属于具体任务目录。
