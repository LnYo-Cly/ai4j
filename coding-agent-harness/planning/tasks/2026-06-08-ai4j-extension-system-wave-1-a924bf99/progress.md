# AI4J extension system wave 1 - 进度

## 状态：进行中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

### [2026-06-08 09:13] - task-start

- 做了什么：启动 AI4J Extension System Wave 1 实现：先做 classpath discovery、explicit enable、manifest/capability validation 和最小 registry context。
- 验证结果：已记录。
- 下一步：诊断模块边界和前置设计。
- 证据：command:TARGET:.:`npx.cmd --yes coding-agent-harness task-start 2026-06-08-ai4j-extension-system-wave-1-a924bf99 ...`

### [2026-06-08 17:20] - scope-and-module-boundary

- 做了什么：读取前置 extension 设计、Pi 调研、engineering/testing/execution 标准，检查根 POM、BOM、`ai4j`、`ai4j-agent`、`ai4j-coding`、`ai4j-cli` 的模块结构和现有 tool/command/skill 接口。
- 验证结果：确认 `ai4j` 依赖面较重，`AgentToolRegistry` / `ToolExecutor` / CLI command / skill 不是合适的第三方公共 API；采用独立 `ai4j-extension-api`。
- 下一步：新增模块和公共合同。
- 证据：report:TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-system-wave-1-a924bf99/findings.md:module-boundary decisions

### [2026-06-08 17:28] - implementation

- 做了什么：新增 `ai4j-extension-api`，实现 `Ai4jExtension`、`ExtensionManifest`、`ExtensionCapability`、`ExtensionContext`、`ServiceLoaderExtensionLoader`、`ExtensionRegistry`、`ExtensionRuntimeSnapshot`、tool/command/skill/prompt/guardrail spec 与 runtime state。
- 验证结果：`mvn -pl ai4j-extension-api -DskipTests=false test` 第一次失败，因为新模块缺少父 POM 引用的 `io.github.lnyocly.ai4j.test.LiveProviderTest` category marker；补 test-scope marker 后通过。
- 下一步：同步治理和 CI。
- 证据：diff:TARGET:ai4j-extension-api:新增扩展 API 和测试；command:TARGET:.:first test failure due to missing LiveProviderTest marker; command:TARGET:.:rerun passed 7 tests

### [2026-06-08 17:34] - regression-and-governance

- 做了什么：将新模块加入根 POM、BOM、Java CI matrix、AGENTS、engineering/testing 标准、Regression SSoT、Cadence Ledger、harness.yaml、module plan、context 架构事实源和服务目录。
- 验证结果：`mvn -pl ai4j-extension-api -DskipTests=false test` 通过，7 tests / 0 failures；`mvn -DskipTests package` 通过根 POM + 9 个 Java/BOM 模块。
- 下一步：补齐 task package、diff hygiene 和 harness status。
- 证据：command:TARGET:.:`mvn -pl ai4j-extension-api -DskipTests=false test` pass; command:TARGET:.:`mvn -DskipTests package` pass, reactor 10 build items

### [2026-06-08 18:28] - module-registry-sync

- 做了什么：检查 harness v2 模块视图同步。`harness module list --json .` 已读到 `extension-api`；`module scaffold --all` 不刷新 `Module-Registry.md`；`module register extension-api ...` 因模块已存在报错。
- 验证结果：手动同步 `coding-agent-harness/planning/modules/Module-Registry.md`，并在 `findings.md` 记录该 CLI 限制。
- 下一步：提交 review packet。
- 证据：command:TARGET:.:`npx.cmd --yes coding-agent-harness module list --json .` shows extension-api; command:TARGET:.:`harness module register extension-api ...` rejected Module already registered; diff:TARGET:coding-agent-harness/planning/modules/Module-Registry.md:manual generated view sync

### [2026-06-08 18:43] - final-materials-check

- 做了什么：修复 `execution_strategy.md` 中的模板占位和 worker 授权字段，重新运行 harness status。
- 验证结果：`npx.cmd --yes coding-agent-harness status --json .` 返回 warn：failures 0；唯一 warning 是当前实现尚未提交导致 dirty-state。
- 下一步：运行最终回归并提交实现。
- 证据：command:TARGET:.:`npx.cmd --yes coding-agent-harness status --json .` status warn, failures 0, dirty-state only

### [2026-06-08 18:45] - final-regression

- 做了什么：执行最终回归。
- 验证结果：`git diff --check` 通过，仅有 CRLF warning；`mvn -pl ai4j-extension-api -DskipTests=false test` 通过，7 tests / 0 failures；`mvn -DskipTests package` 通过，root reactor 10 build items 全部 SUCCESS。
- 下一步：提交当前实现，然后运行 `task-review`。
- 证据：command:TARGET:.:`git diff --check` pass with CRLF warnings only; command:TARGET:.:`mvn -pl ai4j-extension-api -DskipTests=false test` pass, 7 tests; command:TARGET:.:`mvn -DskipTests package` pass, reactor 10 build items

## 残余

- Wave 1 未接入 `ai4j-agent`、`ai4j-coding`、`ai4j-cli` 和 Spring Boot starter；这是有意范围控制。后续需要 Wave 2/3 适配任务。
- `Module-Registry.md` 本次为手动同步；当前 harness CLI 能读取模块但没有独立刷新 generated view 的命令。
- 真实第三方扩展 jar、Maven Central 发布和 CLI install 不在本任务验证范围。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：`extension-api` 已加入 `harness.yaml`、`Module-Registry.md`、`coding-agent-harness/planning/modules/extension-api/*`
- Harness Ledger update needed：task-review 后由 lifecycle / governance rebuild 刷新
- 负责人：coordinator
