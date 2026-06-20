# P1-B Agent Blueprint to AgentFactory

## Task ID

`2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210`

## 创建日期

2026-06-20

## 一句话结果

将已通过 P1-A 校验的单 Agent Blueprint 转换为可运行的 `AgentBuilder` / `Agent`，并明确 Factory 只使用宿主提供的依赖，不读取 token、profile secret 或创建真实 sandbox。

## 完成后能得到什么

完成后，开发者可以在宿主应用中加载一份 `agent.yaml`，先经过 `AgentBlueprintLoader` / `AgentBlueprintValidator`，再通过 `AgentFactory` 和 `AgentFactoryContext` 组装为可运行 Agent。这个结果打通 P1-A 声明式 Blueprint 与后续 P1-C CLI `ai4j run agent.yaml`，也为 docs-site 提供可复制的 Java API 示例。Factory 的边界是 deterministic：模型客户端、工具、memory、permission、context projector、event publisher、session store 等都由宿主传入；profile 只是宿主元数据；`sandbox.enabled=true` 只做 guard，不启动真实沙箱。

## 交付物

- 可见产物：`AgentFactory`、`AgentFactoryContext`、`AgentFactoryException`、`AgentBlueprintFactoryTest`。
- 修改位置：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/`、`ai4j-agent/src/test/java/io/github/lnyocly/agent/`、`docs-site/docs/agent/`、本任务包、Regression SSoT / Cadence Ledger。
- 验证证据：targeted `AgentBlueprintFactoryTest`、broad `mvn -pl ai4j-agent -am -DskipTests=false test`、docs-site `npm run build`、Harness `status --json`。

## 第一眼应该看什么

1. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/AgentFactory.java`
2. `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentBlueprintFactoryTest.java`
3. `docs-site/docs/agent/agent-blueprint.md`
4. `progress.md` 与 `review.md` 的验证记录

## 边界

- 范围内：Blueprint validation 后的 AgentBuilder/Agent 组装、host-supplied context、mapping 文档、确定性异常、P1-B 回归记录。
- 范围外：读取 provider token、解析本机 profile secret、安装插件、创建真实 sandbox、实现 CLI `ai4j run agent.yaml`、新增 Maven 模块。
- 停止条件：如果需要真实 sandbox provider、CLI runtime、外部服务凭证或改动核心 provider secret 解析，停止并拆到后续 P1-C/P2 任务。

## 完成判断

- `AgentFactory.create(...)` 能从有效 Blueprint 和宿主 `AgentFactoryContext` 创建可运行 Agent。
- Factory 不读取环境变量、profile secret 或用户 token；缺少 `AgentModelClient` 时给出确定性错误。
- `sandbox.enabled=true` 默认被拒绝，只有宿主显式接受声明时才允许继续，且仍不创建真实 sandbox。
- targeted / broad Maven 回归和 docs-site build 均通过。
- Harness 任务材料进入 review 队列，等待人工确认。

## 执行合同

- Owner：coordinator
- 生命周期状态：review
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`
- 完成条件：验证证据记录到 `progress.md`，并通过 `task-review` 进入 review / ready-to-confirm 状态。

## 当前下一步

修复 brief / execution strategy 模板残留后，重新运行 Harness status；若材料通过，则提交修复并推送 PR。
