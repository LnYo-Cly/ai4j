# P1-A Agent Blueprint schema/model/loader/validator 执行规划

## 1. 任务定位

P1-A 是 `ai4j-agent` 声明式组装能力的第一步：先让 SDK 能稳定读取、表达、校验单 Agent 的 YAML Blueprint。它不负责真正创建 `Agent` 实例，也不接入 CLI、FlowGram、远端 Runner 或真实 sandbox。

完成后，后续 P1-B 可以在同一模型上实现 `AgentFactory`，P2 可以把 sandbox 字段连接到 Sandbox SPI，docs-site 可以用同一份字段定义解释“如何声明一个 Agent”。

## 2. 推荐方案

### 方案 A：模型 + Loader + Validator 基础层（推荐）

先在 `ai4j-agent` 内新增 `blueprint` 包，提供 Java 8 DTO、YAML loader、validator、fixture tests 和 docs-site 页面。字段只覆盖单 Agent 第一版：`model`、`instructions`、`plugins`、`tools`、`session.memory`、`session.compact`、`sandbox`、`workflow`。

优点：边界清晰、回归可控、不要求马上改 runtime；后续 `AgentFactory`、Team Blueprint、Workflow Blueprint 都能复用。缺点：用户暂时只能加载/校验配置，不能一步运行 Agent。

### 方案 B：Factory-first

直接从 YAML 创建 `Agent`，把 loader、validator、factory 一起做完。优点是演示更快；缺点是容易把 provider/profile/plugin/tool/sandbox 解析规则和 runtime 绑定过早，增加破坏面。

### 方案 C：完整 DSL / Team / Workflow 一次性做完

一次性覆盖 agents、handoff、workflow graph、FlowGram export。优点是愿景完整；缺点是范围过大，不符合当前 P0-A~P0-D 之后的渐进路线。

结论：采用方案 A。P1-A 只打地基，不制造新主概念，不引入 `AgentHost` / `Host Kernel`，不新增 Maven 模块。

## 3. 目标 API 切片

推荐新增包：

```text
ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/
```

建议类名：

| 类型 | 建议类 | 说明 |
| --- | --- | --- |
| Root DTO | `AgentBlueprint` | 单 Agent Blueprint 根对象，字段包括 version/id/name/model/instructions/plugins/tools/session/sandbox/workflow。 |
| Model DTO | `AgentBlueprintModel` | provider/profile/model/options。`profile` 允许后续从 host 配置解析，不在 P1-A 里读取密钥。 |
| Instructions DTO | `AgentBlueprintInstructions` | system/developer/variables。 |
| Plugin DTO | `AgentBlueprintPlugin` | id/enabled/config。 |
| Tool DTO | `AgentBlueprintTool` | ref/approval/config。 |
| Session DTO | `AgentBlueprintSession` | memory/compact。 |
| Memory DTO | `AgentBlueprintMemory` | enabled/scope/store。 |
| Compact DTO | `AgentBlueprintCompact` | enabled/trigger/strategy/preserve。 |
| Trigger DTO | `AgentBlueprintCompactTrigger` | contextRatio/maxTurns 或后续阈值。 |
| Sandbox DTO | `AgentBlueprintSandbox` | enabled/provider/profile/config。P1-A 只校验，不创建 sandbox。 |
| Workflow DTO | `AgentBlueprintWorkflow` | mode/maxTurns。第一版 mode 只允许 `react`、`codeact`。 |
| Loader | `AgentBlueprintLoader` | `load(String)`、`load(InputStream)`、`load(Path/File)`；返回 DTO 或抛出带上下文的加载异常。 |
| Validator | `AgentBlueprintValidator` | 返回 `AgentBlueprintValidationReport`，不直接启动 Agent。 |
| Report | `AgentBlueprintValidationReport` | `isValid()`、`getIssues()`、`getErrors()`、`getWarnings()`。 |
| Issue | `AgentBlueprintValidationIssue` | severity/path/message/code。错误顺序稳定。 |

## 4. YAML v1 字段边界

第一版示例：

```yaml
version: ai4j.agent/v1
id: coding-assistant
name: Coding Assistant

model:
  provider: openai-compatible
  profile: default
  model: gpt-4.1

instructions:
  system: |
    You are a careful coding agent.

plugins:
  - id: ask-user
  - id: todo

tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe

session:
  memory:
    enabled: true
    scope: project
  compact:
    enabled: true
    trigger:
      contextRatio: 0.75
    strategy: structured-summary
    preserve:
      - instructions
      - open_decisions
      - changed_files
      - failed_commands
      - test_results

sandbox:
  enabled: false

workflow:
  mode: react
  maxTurns: 20
```

P1-A 只承认 `version: ai4j.agent/v1`。未来版本通过 validator 和 loader 扩展，不在本轮做兼容矩阵。

## 5. Validator 规则

必须作为错误处理：

1. `version` 必填，且必须等于 `ai4j.agent/v1`。
2. `id` 必填，建议只允许稳定 slug：字母、数字、点、下划线、中划线。
3. `model` 必填；`model.provider` 必填；`model.model` 或 `model.profile` 至少一个能定位模型。
4. `tools[*].ref` 必填；空 ref 是错误。
5. `plugins[*].id` 必填；空 id 是错误。
6. `workflow.mode` 如填写，只允许 `react` / `codeact`。
7. `workflow.maxTurns` 如填写，必须为正整数。
8. `session.compact.trigger.contextRatio` 如填写，必须在 `(0, 1]` 范围内。
9. `sandbox.enabled=true` 时，`sandbox.provider` 或 `sandbox.profile` 至少填写一个；但 P1-A 不创建真实 sandbox。
10. YAML parse failure 必须转换为稳定、可测试的 load error，不泄露本机路径或密钥。

可作为 warning 处理：

- 未知 top-level 字段。
- `session.memory.enabled=true` 但未声明 scope/store。
- `tools[*].approval` 使用未知值时先 warning，后续 P0-D permission policy 可升级规则。

## 6. 明确不做

- 不实现 `AgentFactory`。
- 不从 YAML 直接创建或运行 `Agent`。
- 不接 provider credentials，不读取用户本地 profile 文件。
- 不接 CLI `/agent`、`/blueprint`、FlowGram、Runner。
- 不做 Team Blueprint、Workflow graph、handoff、subagent DSL。
- 不接 CubeSandbox/E2B/Docker/K8s 等真实 sandbox。
- 不把 OpenAI-compatible 中转平台写成 SDK 专有概念。
- 不在 fixture、docs 或测试里写 provider token。

## 7. 实施步骤

1. 在独立 worktree 创建 `feature/agent-blueprint-schema-loader`。
2. 盘点 `ai4j-agent/pom.xml` 当前依赖，选择 Java 8 兼容 YAML 解析依赖；如新增依赖，只加在 `ai4j-agent`。
3. 新建 `blueprint` 包和 DTO/loader/validator/report/issue。
4. 增加 YAML fixture：valid minimal、valid roadmap-style、missing required fields、invalid ratio、invalid workflow mode、invalid YAML。
5. 增加 JUnit 4 测试：`AgentBlueprintLoaderValidatorTest`。
6. 新增 docs-site 页面：`docs-site/docs/agent/agent-blueprint.md`，并更新 `docs-site/sidebars.ts`、`docs-site/docs/agent/sdk-roadmap.md`。
7. 若新增固定回归面，更新 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`。
8. 记录验证和 walkthrough，然后 `task-review`。

## 8. 回归命令

最小目标回归：

```powershell
mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintLoaderValidatorTest" -DskipTests=false -DfailIfNoTests=false test
```

模块回归：

```powershell
mvn -pl ai4j-agent -am -DskipTests=false test
```

docs-site 回归：

```powershell
cd docs-site
npm run build
```

Harness / diff hygiene：

```powershell
npx --yes coding-agent-harness status --json .
git diff --check
```

## 9. PR / merge 建议

- Branch：`feature/agent-blueprint-schema-loader`
- Worktree：`.worktrees/feature/agent-blueprint-schema-loader`
- Commit：`feat(agent): add blueprint schema loader foundation`
- PR base：按当前仓库主线使用 `main`，除非后续任务指定其他 base。
- PR body 必须引用本任务：`MODULES/agent-runtime/2026-06-20-p1-a-agent-blueprint-schema-model-loader-validat-b05250a0`。

## 10. 风险和残余

| 风险 | 当前处理 |
| --- | --- |
| YAML 依赖版本与 Java 8 / 安全基线冲突 | 实施时先验证 Maven 解析和 Java 8 兼容；不在规划阶段固定未经验证的最新版。 |
| Factory 需求诱导范围扩大 | 明确 P1-A 不做 factory；P1-B 再建任务。 |
| Blueprint 字段提前绑定 CLI/Runner/FlowGram | 字段保持 runtime-neutral；消费方适配后置。 |
| Sandbox 字段被误解为真实实现 | 文档和 validator 均说明 P1-A 只做声明/校验，不创建 sandbox。 |
| OpenAI-compatible 中转平台命名污染 SDK | 字段只使用通用 `openai-compatible`，不把任何平台品牌固化为 SDK 概念。 |
