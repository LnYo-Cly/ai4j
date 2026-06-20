# AI4J extension command execution wave 5

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让 AI4J CLI 可以显式启用 classpath 上的插件包并执行其 command 资源，形成第三方插件从 inspect 到人工调用的闭环。

## 范围

- 做什么：新增 `ai4j-cli extension run --enable <extension-id> <command> [arguments...]`；从 `ExtensionRegistry.discover()` 发现插件，显式 enable 后取 `ExtensionRuntimeSnapshot.commandHandlers` 执行 command；补 CLI tests；同步 docs-site 插件文档、Feature SSoT、Regression SSoT、Cadence Ledger、module plan 和 walkthrough。
- 不做什么：不实现 CLI 自动安装依赖、远程 marketplace、运行时 jar hotload、provider plugin、Agent/Coding Agent 新入口、Spring Boot 新属性，也不改变 `ai4j-extension-api` 公共合同。
- 主要风险：第三方插件 command 是任意 Java 代码，不能因为 classpath 上存在就自动执行；CLI 参数解析不能把插件 command 的 `--flag` 参数误判为 AI4J CLI 选项。

## 预算选择

选择预算：complex

选择理由：代码切片集中在 CLI，但它承接 extension public API、用户文档、安全门禁、回归治理和 harness task lifecycle，需要完整任务材料与多 surface 验证。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | private-plan | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/task_plan.md | 前置插件生态分波设计，确认 command 是首批资源类型之一。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension | command spec、handler、runtime snapshot 与 enable gate 的公共合同。 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | Wave 2 已有 list/inspect 入口，本轮在同一 command host 内扩展 run。 | coordinator |
| C-004 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | 确认 CLI/TUI 行为留在 `ai4j-cli`，不泄漏到底层 extension API。 | coordinator / reviewer |
| C-005 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | 确认 CLI touched-surface gate 与 docs-site gate。 | coordinator / reviewer |

## 步骤

1. 诊断 Wave 1-4 extension API、CLI inspect、runtime adapter 和 Spring Boot 配置现状。
2. 在 `CliExtensionCommand` 中新增 `run` 子命令，要求 `--enable`，并把 command 名称后的所有 token 交给插件 handler。
3. 补 `Ai4jCliTest` 覆盖未启用拒绝、启用执行、slash command、参数 `--flag` 和未知 command 错误。
4. 同步插件文档和 root/docs-site README 入口。
5. 更新 Feature SSoT、Regression SSoT、Cadence Ledger、module plan、task progress/review/walkthrough。
6. 运行 RG-004 targeted、RG-007、RG-008、diff check 和 harness status。

## 验收标准

- [x] `extension run` 没有 `--enable` 时返回 argument error，且不调用插件 `apply(...)`。
- [x] `extension run --enable cli-test-pack cli-echo hello world` 可以执行 command handler 并输出结果。
- [x] `/cli-echo` 形式被归一化为 `cli-echo`。
- [x] command 名称后的 `--flag` 参数会传给 handler，而不是被 CLI 拦截。
- [x] 未知 command 返回 extension error。
- [x] `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test`、`mvn -DskipTests package`、docs-site typecheck/build、`git diff --check`、harness status 通过或记录明确残余。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：本轮由 coordinator 串行修改 CLI、docs-site 和治理文件；没有并行 worker 写入。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如果实现需要修改 `ai4j-extension-api` 公共合同或自动执行第三方代码，停止并重新确认。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self；human confirmation 仍由 harness review workbench 完成
- No-finding 要求：无 open P0/P1/P2 material finding。

## 关联

- 相关 Regression Gate：RG-004、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：Wave 1 `2026-06-08-ai4j-extension-system-wave-1-a924bf99`；Wave 2 `2026-06-08-ai4j-extension-cli-inspect-wave-2-35a94c8e`；Wave 3 `2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5`

## 模块关联（启用模块并行时填写）

- Module：`cli-host`
- Step：CLI-EXT-02
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced after module plan / SSoT update
- Registry update needed：`cli-host` add CLI-EXT-02
- Harness Ledger update needed：由 lifecycle CLI / task-review 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`walkthrough.md`
