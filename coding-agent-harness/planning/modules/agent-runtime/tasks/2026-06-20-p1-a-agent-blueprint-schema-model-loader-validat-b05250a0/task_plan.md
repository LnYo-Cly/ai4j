# P1-A Agent Blueprint schema model loader validator

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0/artifacts/preset/2026-06-19T21-41-19-781Z
Task Package Index: required

## 目标

在 `ai4j-agent` 内建立单 Agent YAML Blueprint 的 schema/model/loader/validator 基础，让声明式 Agent 配置可以被稳定加载、校验、测试和文档化，但暂不创建或运行 `Agent`。

## 范围

- 做什么：新增 `io.github.lnyocly.ai4j.agent.blueprint` 包、Java 8 DTO、YAML loader、validator/report/issue、fixture tests、docs-site Agent Blueprint 页面、必要的回归 SSoT 更新。
- 不做什么：不做 `AgentFactory`，不接 CLI/FlowGram/Runner，不做 Team/Workflow graph DSL，不接真实 sandbox，不读取 provider token 或本地 profile。
- 主要风险：YAML 依赖版本与 Java 8 兼容性；字段范围膨胀到 Factory/Runner；sandbox 字段被误解为真实执行环境；docs 示例误导用户写入 token。

## 预算选择

选择预算：complex

选择理由：该任务横跨 runtime API、配置格式、测试 fixture、docs-site 和回归治理；虽然不直接改变 Agent 执行路径，但会形成后续 P1-B/P2/P4 的公共配置合同，需要明确非目标和验证边界。

## 设计方案

采用“模型 + Loader + Validator 基础层”方案：

1. P1-A 只解决表达、读取和校验。
2. P1-B 再基于同一 DTO 做 `AgentFactory`。
3. P2/P3/P4 再消费 `sandbox`、`tools`、`workflow` 字段。

未采用方案：

- Factory-first：演示更快，但会过早耦合 runtime/provider/plugin/sandbox。
- 完整 DSL：范围过大，容易把 Team、Workflow graph、FlowGram export 混进首版。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | private-plan | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-enhancement-plan.md | 上游架构结论：不新增 AgentHost/Host Kernel，`ai4j-agent` 是 Agent SDK 主入口，Blueprint 是声明式配置层。 | coordinator / reviewer / worker |
| C-002 | private-plan | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-implementation-decomposition-and-26846add/references/ai4j-agent-implementation-roadmap.md | P0-P5 实施拆解，明确 P1 第一版只支持单 Agent Blueprint。 | coordinator / reviewer / worker |
| C-003 | public-doc | TARGET:docs-site/docs/agent/sdk-roadmap.md | docs-site 已公开的 P1 用户心智和 YAML 示例，实施后要保持一致。 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-agent/pom.xml | 当前依赖和 Java 8 Maven 模块边界；实施时决定 YAML 依赖。 | worker |
| C-005 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | 模块边界、Java 8、secret 和安全约束。 | coordinator / reviewer / worker |
| C-006 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | JUnit 4、docs-site build、Regression SSoT/Cadence 触发规则。 | coordinator / reviewer / worker |
| C-007 | task-reference | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0/references/agent-blueprint-p1a-execution-plan.md | 本任务完整字段边界、API 切片、validator 规则和回归命令。 | coordinator / reviewer / worker |

## 步骤

1. **INIT-01：任务规划落盘**
   - 读取工程/测试标准、上游 roadmap、docs-site P1 示例和当前 module plan。
   - 写入本任务 `brief.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`findings.md`、`progress.md` 和 reference plan。
2. **INIT-02：worktree 准备**
   - 从 clean `main` 创建 `.worktrees/feature/agent-blueprint-schema-loader`。
   - 分支：`feature/agent-blueprint-schema-loader`。
   - 在 worktree 内继续本任务，避免污染主工作区。
3. **EXEC-01：模型和 loader**
   - 新建 `blueprint` 包和 Java 8 DTO。
   - 引入或验证 YAML parser；loader 支持 String/InputStream/Path/File。
   - Invalid YAML 转成稳定错误。
4. **EXEC-02：validator 和 fixtures**
   - 实现 validation report/issue。
   - 添加合法/非法 YAML fixtures 和 JUnit 4 测试。
5. **EXEC-03：docs-site 和回归治理**
   - 新增 Agent Blueprint 文档页面。
   - 更新 sidebar 和 roadmap。
   - 若新增固定回归面，更新 Regression SSoT 和 Cadence Ledger。
6. **VERIFY-01：本地验证**
   - 运行 targeted test、`ai4j-agent` 模块回归、docs-site build、Harness status、diff check。
7. **GATE-01：Agent Review Submission**
   - 写 review、walkthrough、lesson decision。
   - `task-review` 提交给人工确认队列。
8. **GATE-02：PR / CI / merge**
   - 推送分支，创建 PR，等待 CI，通过后合并并清理 worktree。

## 验收标准

- [ ] `ai4j-agent` 存在 `blueprint` 包，包含 root/model/instructions/plugin/tool/session/memory/compact/sandbox/workflow DTO。
- [ ] `AgentBlueprintLoader` 能加载 valid minimal 和 roadmap-style YAML fixture。
- [ ] `AgentBlueprintValidator` 对必填字段、workflow mode、maxTurns、compact ratio、sandbox provider/profile 等输出确定性错误。
- [ ] Invalid YAML 有稳定测试，不依赖 provider credentials，不泄漏本机敏感路径。
- [ ] `AgentBlueprintLoaderValidatorTest` 通过，`mvn -pl ai4j-agent -am -DskipTests=false test` 通过。
- [ ] `docs-site/docs/agent/agent-blueprint.md` 解释每个字段、示例、非目标和后续路线，`npm run build` 通过。
- [ ] 若回归面变化，`docs/05-TEST-QA/Regression-SSoT.md` 与 `docs/05-TEST-QA/Cadence-Ledger.md` 已同步。

## 工作树（Worktree）

- 路径：`.worktrees/feature/agent-blueprint-schema-loader`
- 分支：`feature/agent-blueprint-schema-loader`
- Worker owner：coordinator；如用户授权，可派 worker subagent 只改 implementation worktree。
- Worker handoff commit required：yes（如果启用 worker）；coordinator 自己实施时不适用。
- Coordinator integration branch：`feature/agent-blueprint-schema-loader`
- 未使用 worktree 的原因：不适用。P1-A 会改代码和 docs，实施阶段应使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如果 YAML parser 依赖不兼容 Java 8、字段设计必须跨模块改公共合同、或 scope 滑向 Factory/Runner/真实 sandbox，则暂停重新定界。

## 审查判定

- 是否需要对抗性审查：是，使用 task-local `review.md` 做 self/adversarial checklist；不单独创建全局审查任务。
- 若是，报告文件：`review.md`
- Reviewer：self + 可选 read-only reviewer subagent
- No-finding 要求：review 必须明确检查 scope creep、Java 8、secret/docs 示例、validation deterministic、docs/code drift。

## 关联

- 相关 Regression Gate：RG-002 Agent runtime tests；RG-008 docs-site build；Harness status。
- 审查报告：`coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P0-D `MODULES/agent-runtime/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5`

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-P1-A-AGENT-BLUEPRINT-SCHEMA-MODEL-LOADER-VALIDAT
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-implementation
- Registry update needed：agent-runtime step active/planned，实施完成后改为 implementation-verified / merged
- Harness Ledger update needed：task package lifecycle surfaces already generated by Harness CLI
- Closeout / Regression update needed：实施完成后更新 walkthrough、Regression SSoT、Cadence Ledger

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Task reference | coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0/references/agent-blueprint-p1a-execution-plan.md | Use this as the implementation contract for P1-A. |
