# ai4j dynamic workflow host runtime - 长程任务合同

## 目标

完整落地并验证 AI4J host/runtime executor，让 dynamic workflow 插件的 envelope 在 SDK 宿主侧可执行。

## 范围

### 范围内

- `ai4j-agent` dynamic workflow runtime API、Nashorn host executor、host tool wrapper、测试。
- docs-site dynamic workflow 插件页的 host runtime 章节。
- task-local progress / review / walkthrough 收口。

### 范围外

- 独立插件仓库新功能。
- `ai4j-extension-api` contract 变更。
- 后台 workflow manager、resume journal、worktree isolation、模型 tier 调度、CLI TUI 页面。

### 共享文件 / 冲突风险

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java` 如增加接入糖方法，可能与 agent runtime 其他任务冲突；优先保持 runtime 独立，减少 builder 改动。
- `docs-site/docs/core-sdk/extension/dynamic-workflow-plugin.md` 是上一任务新增文档，本轮只追加 host runtime 信息。

## 主调用入口（Primary Caller / Entry）

- 主调用方（Primary caller）：本地 agent host / API integration。
- 本任务必须支持的入口：Java host 代码通过 `DynamicWorkflowHostToolExecutor` 或 `DynamicWorkflowExecutor` 执行插件 envelope。
- 明确不要求的入口：CLI `/workflows`、TUI 后台管理、生产发布。

## 执行授权（Execution Permission）

- 是否允许连续执行（Continuous execution）：allowed
- 是否允许每轮后不再询问直接继续：yes
- 是否允许启动审查 agent / 子代理：yes，如有必要；本轮默认 self-review
- 是否需要审查报告：yes
- 仍需人工批准的动作：
  - 修改 `ai4j-extension-api` public contract
  - 引入非 Java 8 baseline
  - 发布 release / 上传 secrets / 生产环境调用

## 必需循环

每一轮至少包含：

1. 实现、编辑或配置。
2. 本地运行。
3. 测试、冒烟或检查。
4. 执行 Confidence Challenge。
5. 如合同要求审查者或子代理，更新 `review.md`。
6. 修复 findings。
7. 重新收集证据。
8. 重跑 Confidence Challenge，直到没有 open 重要发现。
9. 更新 `progress.md`。

最低循环次数或无重要发现要求：

- 至少实现后自审 1 轮；`review.md` 必须无 open P0/P1。

## 审查者 / 子代理合同（Reviewer / Subagent）

- 审查者角色（Reviewer role）：[只读审查 / 改代码 worker / 测试验证者]
- 审查者角色（Reviewer role）：只读审查 / self-review
- 审查范围（Reviewer scope）：dynamic workflow runtime API、host execution 安全边界、测试覆盖、docs 口径。
- 如果是 code-change worker：
  - Worktree path：[路径 / 不适用]
  - Branch：[分支 / 不适用]
  - 任务目录：[路径 / 不适用]
  - 交接前提交（Commit before handoff）：[yes / no / 不适用]
  - 交接必须包含：[worktree path / branch / commit SHA / checks / residual risks]
- Reviewer 必须报告：
  - [缺陷]
  - [回归]
  - [缺失测试]
  - [未验证假设]
  - [`review.md` 中的重要发现或无重要发现声明]
- Reviewer 不得：
  - [越权改动 / 重写不相关模块 / 擅自扩大 scope]

## 证据

完成前必需证据：

- [x] `mvn -pl ai4j-agent -am -Dtest=DynamicWorkflow*Test -DskipTests=false test`
- [x] 如 docs-site 改动：`npm run typecheck` / `npm run build`
- [x] `git diff --check`
- [x] `review.md` 已完成且无 open P0/P1
- [x] `walkthrough.md` 已完成

## 完成条件（Stop Condition）

任务只有在以下条件满足后才可停止并声明完成：

- [x] 关键路径通过
- [x] targeted Maven 回归通过。
- [x] docs-site 验证通过或残余明确。
- [x] dynamic workflow runtime 的错误路径测试通过。
- [x] `review.md` 无 open P0/P1 发现。
- [x] 残余风险已记录，且不阻塞本轮目标。

## 暂停条件（Pause Conditions）

遇到以下情况必须暂停并汇报：

- [ ] 目标或范围已经失效。
- [ ] 需要高风险的产品、架构、安全或数据决策。
- [ ] 未知的无关改动与本任务冲突。
- [ ] 环境阻断了所有有用证据的收集。
- [ ] 审查者发现改变了任务方向。

## 交付物（Deliverables）

- [x] 代码 / 配置改动
- [x] 测试 / 回归证据
- [x] 文档更新
- [x] 如要求审查，`review.md` 报告
- [x] `progress.md` / `findings.md` 更新
- [x] Harness Ledger 更新（本任务记录在 task-local 文件；全局 generated ledger 如需刷新由治理命令处理）
- [x] 收口记录
- [x] Lessons 反思与检查：`lesson_candidates.md` 已进入 `no-candidate-accepted`
- [x] PR / commit / 发布说明（提交/推送在最终交付步骤完成）
- [x] 残余风险摘要
