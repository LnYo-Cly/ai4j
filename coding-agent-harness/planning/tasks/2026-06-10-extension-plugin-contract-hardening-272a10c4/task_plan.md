# Extension plugin contract hardening

Task Contract: harness-task/v1
Task Package Index: required

## 目标

修复 extension plugin 审查发现的契约漏洞，使第三方插件在 ID/name、tool schema、CLI 参数和文档信任边界上更早、更明确地失败或说明。

## 范围

- 做什么：收紧 `ai4j-extension-api` 公共 ID/name 契约；升级 `ExtensionValidator` tool schema 校验；补 CLI 参数校验与 scaffold 编译/ServiceLoader 烟测；更新插件 docs-site 与回归治理记录。
- 不做什么：不新增远程 marketplace、运行时 jar 热加载、command/Skill/Prompt/Guardrail 粒度 allowlist 或新的插件权限模型。
- 主要风险：schema 校验过宽会继续放过坏插件，过严会破坏现有有效插件；CLI slash command 兼容路径不能被收紧规则误伤；docs 不能暗示已经存在的细粒度插件权限能力。

## 预算选择

选择预算：standard

选择理由：本任务跨 extension API、CLI、docs-site 和 regression governance，但属于既有插件系统契约硬化，不需要新架构或独立 worktree。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ | extension manifest、runtime state、validator 和资源对象的公共契约边界 | coordinator |
| C-002 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/ | CLI extension 命令解析、scaffold 生成器与用户入口 | coordinator |
| C-003 | public-doc | TARGET:docs-site/docs/core-sdk/extension/ | 插件包使用者和作者文档需要同步真实边界 | coordinator |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 固定回归面需要反映新增 scaffold/schema 覆盖 | coordinator |

## 步骤

1. 收紧 extension 公共 ID/name 校验，并保留 `requireId` 给内部非坐标文本字段使用。
2. 用无新增依赖的轻量 JSON 结构校验替换 `ExtensionValidator` 的字符串外观 schema 检查。
3. 修正 CLI 参数校验，保留 `/command` 人工输入兼容，并补 scaffold 编译/ServiceLoader 烟测。
4. 更新 docs-site、Regression SSoT、Cadence Ledger、progress、review、walkthrough。
5. 运行 extension API、agent、CLI、Ask User plugin、docs-site 和 package 目标回归。

## 验收标准

- [ ] malformed JSON schema 即使包含 `"type"` 文本也会被 validator 报错。
- [ ] extension id、tool/command/resource/guardrail name 使用统一公共命名契约；CLI slash-prefixed command 输入仍可执行。
- [ ] generated plugin scaffold 不只做文本断言，还能通过 JavaCompiler 编译并被 ServiceLoader 找到。
- [ ] docs-site 明确 `apply(...)` side-effect-light、enable 整包信任与当前非 tool 资源 allowlist 边界。
- [ ] 目标回归与治理 closeout 已记录。

## 工作树（Worktree）

- 路径：n/a
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：n/a
- 未使用 worktree 的原因：单 coordinator 串行修复，改动范围可控，当前工作区已承载连续任务。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若目标回归出现非本任务可修复的外部阻塞或公共 API 兼容性需要产品决策，则停止并交回用户。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self；前置 review findings 来自用户要求的专门 review
- No-finding 要求：本轮修复后无 open P0/P1/P2 material finding；P3 权限模型扩大项如未实现需记录为 out-of-scope residual。

## 关联

- 相关 Regression Gate：RG-010, RG-004, RG-011, RG-002, RG-007, RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：Extension plugin system review findings

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle command on closeout
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`, `docs/05-TEST-QA/Cadence-Ledger.md`, `walkthrough.md`
