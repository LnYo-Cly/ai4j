# P1-A Agent Blueprint schema model loader validator

## Task ID

`2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0`

## 创建日期

2026-06-20

## 一句话结果

为 `ai4j-agent` 规划并实现单 Agent YAML Blueprint 的 schema、Java 模型、loader、validator 与 deterministic fixture 回归。

## 完成后能得到什么

完成后，AI4J 会具备声明式单 Agent 配置的基础层：开发者可以把模型、指令、插件、工具、memory、compact、sandbox 开关和 workflow 参数写入 YAML，并由 `ai4j-agent` 读取成稳定 Java 对象、输出可定位的校验错误。该任务先解决“配置能否被可靠表达和校验”，为后续 `AgentFactory`、CLI/Runner 产品化、FlowGram 导出和插件生态组装打基础。

## 交付物

- 可见产物：`AgentBlueprint` Java DTO、`AgentBlueprintLoader`、`AgentBlueprintValidator`、validation report/issue、YAML fixtures、JUnit 4 测试、docs-site Blueprint 页面。
- 修改位置：`ai4j-agent/**`、`docs-site/docs/agent/**`、`docs-site/sidebars.ts`，必要时更新 `docs/05-TEST-QA/**` 与本任务包。
- 验证证据：targeted loader/validator test、`ai4j-agent` 模块回归、docs-site build、Harness status、diff check。

## 第一眼应该看什么

1. `references/agent-blueprint-p1a-execution-plan.md`：字段边界、推荐 API、验证规则和不做事项。
2. `task_plan.md`：阶段、验收和 worktree/branch 合同。
3. `visual_map.md`：执行阶段和依赖关系。
4. `progress.md`：当前任务状态和已记录证据。

## 边界

- 范围内：单 Agent YAML Blueprint 的模型、loader、validator、fixture tests、文档和回归记录。
- 范围外：`AgentFactory`、Team Blueprint、Workflow graph、CLI 命令、FlowGram 导出、真实 sandbox provider、远端 Agent Runner、provider token/profile 读取。
- 停止条件：需要新增跨模块公共合同、引入非 Java 8 兼容依赖、或发现必须先改 runtime/factory 才能完成 loader/validator 时，暂停并回到 coordinator 重新定界。

## 完成判断

- `ai4j-agent` 能加载合法 YAML Blueprint，并稳定映射到 Java 8 DTO。
- Validator 能对缺失 version/id/model、非法 workflow mode、非法 compact ratio、sandbox provider/profile 缺失等输出确定性错误。
- Invalid YAML 能以稳定 load error 进入测试，不打印密钥或本机敏感路径。
- docs-site 有独立 Agent Blueprint 页面并能通过 build。
- 任务本地 progress/review/walkthrough 记录 targeted、module、docs 和 Harness 验证证据。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并在 closeout 前完成 `walkthrough.md`

## 当前下一步

创建 `.worktrees/feature/agent-blueprint-schema-loader` 和 `feature/agent-blueprint-schema-loader` 分支，在该 worktree 内实施 P1-A。实施前先确认 YAML 依赖选择和 Java 8 兼容性。
