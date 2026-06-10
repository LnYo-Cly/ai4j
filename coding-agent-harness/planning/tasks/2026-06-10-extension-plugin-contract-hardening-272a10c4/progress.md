# Extension plugin contract hardening - 进度

## 状态：已完成

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

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

## 残余

- P3 非工具资源细粒度 allowlist 未在本轮实现；本轮已在 docs-site 明确 `enable(...)` 是 command / Skill / Prompt / Guardrail 的整包信任边界。后续如果要做 command/resource/guardrail allowlist，应单独设计 API、Spring 配置和 CLI 语义。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：task-review 后由 lifecycle CLI 同步
- 负责人：coordinator

### [2026-06-10 07:53] - task-start

- 做了什么：开始修复 extension plugin 契约审查问题：schema 验证、ID/name 约束、CLI/runtime 文档与 scaffold 回归。
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-10 16:05] - implementation

- 做了什么：收紧 extension 公共 ID/name 校验；新增无外部依赖的 tool schema JSON 结构校验器；修正 CLI extension 参数校验并保留 `/command` 人工输入兼容；新增 validator、registry、CLI scaffold 编译和 ServiceLoader 烟测。
- 验证结果：`mvn -pl ai4j-extension-api -DskipTests=false test` 首轮通过；`mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` 首轮发现 ServiceLoader 测试假设错误，修正后通过。
- 下一步：更新 docs-site、回归台账和 closeout。
- 证据：command:ai4j-extension-api:16 tests passed; command:ai4j-cli:22 targeted tests passed after generated scaffold ServiceLoader smoke fix

### [2026-06-10 16:16] - verification

- 做了什么：完成 docs-site 插件契约说明、Regression SSoT / Cadence Ledger 更新，并运行目标回归矩阵。
- 验证结果：extension API、agent extension adapter、Ask User plugin、CLI targeted、docs-site typecheck/build、monorepo package smoke、diff check 均通过。
- 下一步：提交 Agent Review Submission，等待人工确认。
- 证据：command:ai4j-extension-api:`mvn -pl ai4j-extension-api -DskipTests=false test` passed with 16 tests; command:ai4j-agent:`mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` passed with 5 tests; command:ai4j-plugin-ask-user:`mvn -pl ai4j-plugin-ask-user -am -DfailIfNoTests=false -DskipTests=false test` passed; command:ai4j-cli:`mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed with 22 tests; command:docs-site:`npm run typecheck` and `npm run build` passed; command:repo:`mvn -DskipTests package` passed across 11 reactor projects; command:repo:`git diff --check` passed with CRLF warnings only

### [2026-06-10 16:24] - wider-cli-regression

- 做了什么：补跑完整 CLI 依赖链测试，覆盖 `ai4j-extension-api`、`ai4j`、`ai4j-agent`、`ai4j-coding`、`ai4j-cli`。
- 验证结果：reactor build success；extension API 16 tests、core SDK 103 tests、agent 74 tests、coding runtime 59 tests、CLI 262 tests 全部通过。
- 下一步：提交 Agent Review Submission，等待人工确认。
- 证据：command:repo:`mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` passed across 6 reactor projects with 514 tests

### [2026-06-10 08:28] - task-review

- 做了什么：Extension plugin contract hardening complete; schema/name contracts, CLI scaffold smoke, docs trust boundary, and regression evidence updated.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-10 09:38] - task-complete

- 做了什么：Extension plugin contract hardening closed after human review confirmation HRC-202606100936.
- 验证结果：已记录
- 下一步：完成
- 证据：n/a
