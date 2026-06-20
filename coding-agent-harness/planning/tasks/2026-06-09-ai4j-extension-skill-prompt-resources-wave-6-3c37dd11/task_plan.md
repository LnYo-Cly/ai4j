# AI4J extension skill prompt resources wave 6

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让已启用 AI4J 插件贡献的 Skill / Prompt 资源可以被 Coding Agent 按需读取，并提供 CLI 资源读取入口用于插件开发者检查打包内容。

## 范围

- 做什么：在 `ai4j-extension-api` 增加资源来源标记与 classpath 资源读取器；在 `ai4j-coding` 中把已启用插件的 Skill / Prompt 物化成只读可读资源并注入 prompt 清单；在 `ai4j-cli` 增加 `extension resource --enable <id> <skill|prompt> <name>`；补充测试和插件文档。
- 不做什么：不做插件 marketplace、自动安装、运行时 jar 热加载、provider plugin、guardrail enforcement、远程资源读取。
- 主要风险：资源读取不能绕过启用门禁，物化资源不能扩大 workspace 写权限，系统提示不能直接塞入完整资源正文导致上下文污染。

## 预算选择

选择预算：complex

选择理由：该任务横跨 `ai4j-extension-api`、`ai4j-coding`、`ai4j-cli`、docs-site 与回归治理，并改变 Coding Agent 的上下文装配行为，需要完整任务包和多模块回归证据。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | design | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | 确认 SkillPackage / PromptPackage 属于 Wave 1 资源扩展，不做 marketplace 或热加载。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension | 资源注册、runtime snapshot 和 ExtensionRegistry 的边界。 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding | Coding Agent skill discovery、workspace prompt、read-only root 逻辑。 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | CLI 插件 list/inspect/run 命令的现有解析和门禁方式。 | coordinator / reviewer |
| C-005 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | 模块边界、Java 8 和安全约束。 | coordinator / reviewer |

## 步骤

1. 扩展资源契约：给 Skill / Prompt resource 标记贡献插件 ID，并新增 UTF-8 classpath resource resolver。
2. Coding Agent 消费：启用插件后，把 Skill / Prompt 物化到临时只读目录，加入 `availableSkills` / `availablePrompts` 与 `allowedReadRoots`。
3. CLI 检查入口：新增 `extension resource --enable <id> <skill|prompt> <name>`，读取已启用插件的资源正文。
4. 测试与文档：补资源 fixture、JUnit 覆盖、docs-site 插件页、README 入口、Regression SSoT / Cadence Ledger。
5. 验证、收口并提交推送。

## 验收标准

- [x] classpath discovery 不会自动读取资源；CLI resource 读取必须显式 `--enable`。
- [x] Coding Agent 只把资源清单注入 prompt，完整 Skill / Prompt 正文由 `read_file` 按需读取。
- [x] 物化资源只进入 `allowedReadRoots`，写入仍被 workspace root 边界拒绝。
- [x] CLI 可以读取已启用插件贡献的 Skill 和 Prompt 资源正文。
- [x] targeted Java tests、package smoke、docs-site typecheck/build、harness status 和 diff check 通过或记录残余。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本轮由 coordinator 单线修改，范围集中在插件资源桥接与文档治理，无并行 worker 写入。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若需要引入自动安装、provider plugin、热加载或 guardrail enforcement，必须另开任务。

## 审查判定

- 是否需要对抗性审查：是，采用 coordinator self-review 的 architecture/security/regression challenge。
- 若是，报告文件：`review.md`
- Reviewer：self；人工确认仍由 review queue 处理，agent 不代办。
- No-finding 要求：无 P0/P1/P2 阻塞发现；残余必须记录。

## 关联

- 相关 Regression Gate：RG-010、RG-003、RG-004、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f`、`2026-06-09-ai4j-extension-command-execution-wave-5-3b0bed77`

## 模块关联（启用模块并行时填写）

- Module：extension-api / coding-runtime / cli-host / docs-site
- Step：Wave 6
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 lifecycle CLI / task-review 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`walkthrough.md`
