# AI4J Extension Permission and Install UX - 进度

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

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

### [YYYY-MM-DD HH:MM] - [阶段名称]

- 做了什么：[具体操作]
- 验证结果：[运行了什么检查，结果如何]
- 下一步：[下一步动作]
- 证据：[type:path:summary]

## 残余

- 人工 Review Confirmation 尚未执行；本轮只提交 Agent Review Submission，不代办人工确认。
- 远程 marketplace、CLI 自动修改 Maven/Gradle 依赖、运行时热加载 jar、provider 自动注册仍明确不在范围内。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass / synced / n/a
- Registry update needed：[module key, step, status, branch, updated / 不适用]
- Harness Ledger update needed：[task plan path, review path, closeout status / 不适用]
- 负责人：coordinator / 不适用

### [2026-06-10 09:57] - task-start

- 做了什么：Start F-039 extension permission and install UX implementation.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-10 18:20] - implementation

- 做了什么：在 `ai4j-extension-api` 增加 command / Skill / Prompt / Guardrail 显式授权 API、`ExtensionActivationPlan` 和 snapshot 过滤；在 `ai4j-cli` 增加 `extension plan`、strict run/resource allow 参数和 scaffold README 文案；在 Spring Boot starter 增加 `ai.extensions.explicit-resource-activation` 与 `commands/skills/prompts/guardrails.allow` 配置映射；更新 docs-site 插件包、插件作者和 Ask User 插件文档。
- 验证结果：首轮 extension API、CLI targeted、Spring targeted 和 Ask User plugin 回归通过；review 子代理指出 prompt/guardrail allowlist 正向覆盖不足。
- 下一步：补 prompt/guardrail 正向测试并重跑回归。
- 证据：diff:ai4j-extension-api:activation plan and explicit resource allowlists; diff:ai4j-cli:extension plan and strict allow CLI args; diff:ai4j-spring-boot-starter:explicit resource activation properties; review:subagent:prompt/guardrail allowlist positive coverage gap found

### [2026-06-10 18:45] - review-fix

- 做了什么：补齐 review finding：`ExtensionRegistryTest` 覆盖 prompt/guardrail allow 后进入 snapshot 和 activation plan；`Ai4jCliTest` 覆盖 `--allow-prompt` / `--allow-guardrail` active plan 输出；`ExtensionAutoConfigurationTest` 覆盖 Spring prompt/guardrail allow 绑定。
- 验证结果：affected targeted gates 全部通过。
- 下一步：运行 broader package、docs-site、diff check 和 harness status。
- 证据：command:ai4j-extension-api:`mvn -pl ai4j-extension-api -DskipTests=false test` passed with 19 tests; command:ai4j-cli:`mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed with 25 tests; command:ai4j-spring-boot-starter:`mvn -pl ai4j-spring-boot-starter -am -Dtest=ExtensionAutoConfigurationTest -DfailIfNoTests=false -DskipTests=false test` passed with 6 tests

### [2026-06-10 18:49] - verification

- 做了什么：运行本轮固定回归矩阵并检查 harness 状态。
- 验证结果：官方 Ask User 插件、monorepo package smoke、docs-site typecheck/build、diff check、harness status 均通过或无失败；harness status 仅报告当前未提交 dirty-state 警告。
- 下一步：更新 Regression SSoT、Cadence Ledger、review packet、walkthrough，提交 Agent Review Submission。
- 证据：command:ai4j-plugin-ask-user:`mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` passed with extension API 19 tests and Ask User plugin 6 tests; command:repo:`mvn -DskipTests package` passed across 11 reactor projects; command:docs-site:`npm run typecheck` passed; command:docs-site:`npm run build` passed and generated `build`; command:repo:`git diff --check` passed with CRLF warnings only; command:repo:`npx.cmd --yes coding-agent-harness status --json .` returned 0 failures and 1 dirty-state warning
