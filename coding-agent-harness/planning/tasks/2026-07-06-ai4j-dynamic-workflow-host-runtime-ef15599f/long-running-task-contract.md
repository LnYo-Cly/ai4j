# ai4j dynamic workflow host runtime - 长程任务合同

## 目标

[一句话说明本轮要完整收掉的主问题。只保留一个主目标。]

## 范围

### 范围内

- [允许修改的目录、模块、能力面]

### 范围外

- [本轮明确不做的事项]

### 共享文件 / 冲突风险

- [可能与其他任务冲突的共享文件；如无写“无”]

## 主调用入口（Primary Caller / Entry）

- 主调用方（Primary caller）：[CLI / 本地 agent / UI / API / automation / integration / 其他]
- 本任务必须支持的入口：[列出]
- 明确不要求的入口：[列出]

## 执行授权（Execution Permission）

- 是否允许连续执行（Continuous execution）：[allowed / not allowed]
- 是否允许每轮后不再询问直接继续：[yes / no]
- 是否允许启动审查 agent / 子代理：[yes / no]
- 是否需要审查报告：[yes / no；如 yes，必须写 `review.md`]
- 仍需人工批准的动作：
  - [高风险操作，例如 destructive migration / production deploy / secret change]

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

- [例如：至少 2 轮；或自审 + 审查者均无重要发现]

## 审查者 / 子代理合同（Reviewer / Subagent）

- 审查者角色（Reviewer role）：[只读审查 / 改代码 worker / 测试验证者]
- 审查范围（Reviewer scope）：[文件 / 模块 / 问题域]
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

- [ ] [lint / typecheck / build command]
- [ ] [unit / integration / e2e test command]
- [ ] [本地冒烟命令]
- [ ] [浏览器 / UI / 人工检查]
- [ ] [线上环境冒烟]
- [ ] [审查者无重要发现]
- [ ] [如要求审查，`review.md` 已完成]
- [ ] [walkthrough / PR / 发布说明]

## 完成条件（Stop Condition）

任务只有在以下条件满足后才可停止并声明完成：

- [ ] [关键路径通过]
- [ ] [必需测试或回归门禁通过]
- [ ] [runtime / console / request 错误已清除，或已记录为非阻塞残余]
- [ ] [如要求审查者，审查者无 open 重要发现]
- [ ] [如要求审查，`review.md` 无 open P0/P1 发现]
- [ ] [残余风险已记录，且不阻塞本轮目标]

## 暂停条件（Pause Conditions）

遇到以下情况必须暂停并汇报：

- [ ] 目标或范围已经失效。
- [ ] 需要高风险的产品、架构、安全或数据决策。
- [ ] 未知的无关改动与本任务冲突。
- [ ] 环境阻断了所有有用证据的收集。
- [ ] 审查者发现改变了任务方向。

## 交付物（Deliverables）

- [ ] 代码 / 配置改动
- [ ] 测试 / 回归证据
- [ ] 文档更新
- [ ] 如要求审查，`review.md` 报告
- [ ] `progress.md` / `findings.md` 更新
- [ ] Harness Ledger 更新
- [ ] 收口记录
- [ ] Lessons 反思与检查：`lesson_candidates.md` 已进入 `no-candidate-accepted` / `needs-promotion` / `promoted` / `rejected`
- [ ] PR / commit / 发布说明
- [ ] 残余风险摘要
