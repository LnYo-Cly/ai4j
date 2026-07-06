# AI4J dynamic workflow host runtime

## Task ID

`2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb`

## 创建日期

2026-07-06

## 一句话结果

在 ai4j-agent 里补出一个 host-side dynamic workflow runtime：它能接收 `ai4j.dynamic_workflow.request`，把请求编译成 AI4J 可执行的 workflow/dispatch 流程，并保持插件侧只负责产出 envelope。

## 完成后能得到什么

完成后，AI4J 会多出一个可落地的动态工作流执行入口：上层插件只负责发请求，host 侧负责校验、编排、执行、取消、持久化或拒绝。这样后续无论是文档示例、CLI 接线，还是更复杂的 agent fan-out / parallel / pipeline 场景，都能在同一套 host contract 下面继续扩展，而不用把执行逻辑塞回插件仓库。

## 交付物

- 可见产物：ai4j-agent 里的 host runtime / dispatcher、对应测试、必要的 docs-site 说明
- 修改位置：优先 `ai4j-agent/**`，必要时最小化触碰 `ai4j-extension-api/**` 或 `docs-site/**`
- 验证证据：`mvn -pl ai4j-agent -am -DskipTests=false test` 及必要的定向 smoke / 文档构建

## 第一眼应该看什么

1. `G:\My_Project\java\ai4j-plugin-dynamic-workflow\README.md`
2. `G:\My_Project\java\ai4j-plugin-dynamic-workflow\src\main\java\io\github\lnyocly\ai4j\plugin\dynamicworkflow\DynamicWorkflowExtension.java`
3. `G:\My_Project\java\ai4j-plugin-dynamic-workflow\src\main\java\io\github\lnyocly\ai4j\plugin\dynamicworkflow\DynamicWorkflowPayloads.java`
4. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/AgentWorkflow.java`
5. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/SequentialWorkflow.java`
6. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/StateGraphWorkflow.java`
7. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

## 边界

- 范围内：host runtime / dispatcher、workflow 编排、必要测试、必要 docs-site 说明
- 范围外：plugin 仓库改动、ai4j-cli 新命令、core SDK 大改、通用 JS 引擎重构
- 停止条件：如果 envelope 需要的能力明显超出当前 `ai4j-agent` / `ai4j-extension-api` 边界，就先回到 coordinator 重新定界，不要先把方案扩大到别的模块

## 完成判断

- [ ] `ai4j.dynamic_workflow.request` 可以被 host 侧接收并路由到明确的执行路径或拒绝理由
- [ ] host runtime 优先复用现有 `AgentWorkflow` / `SequentialWorkflow` / `StateGraphWorkflow` / sandbox 能力
- [ ] 测试覆盖 envelope 解析、workflow 组装和至少一条端到端最小路径
- [ ] docs-site / module docs 解释 host 与 plugin 的边界
- [ ] 若触及固定回归面，`Regression-SSoT.md` 和 `Cadence-Ledger.md` 已同步

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，收口时必须有 `walkthrough.md`

## 当前下一步

先把 envelope -> workflow 的最小合同和复用点钉死，再决定是走 Java-native workflow 编译层，还是必须补一个受控的脚本执行适配层。
