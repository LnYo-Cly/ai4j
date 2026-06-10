# AI4J Extension Permission and Install UX

Task Contract: harness-task/v1
Task Package Index: required

## 目标

AI4J 插件系统提供非 tool 资源的细粒度授权和安装前 activation plan，让第三方插件接入从“整包启用”升级为“先检查、再按资源授权、再接入运行时”。

## 范围

- 做什么：在 `ai4j-extension-api` 增加 command / skill / prompt / guardrail 授权 API 与 activation plan；在 CLI 增加检查/授权参数；在 Spring Boot starter 增加配置映射；更新插件文档、回归记录和任务 walkthrough。
- 不做什么：不做远程 marketplace、自动写 Maven/Gradle 依赖、运行时热加载 jar、provider 自动注册或 Spring 自动创建 Agent。
- 主要风险：破坏 `enable(...)` 现有兼容语义；让 docs 暗示不存在的远程安装能力；让 Coding Agent Skill/Prompt 投影绕过新授权。

## 预算选择

选择预算：standard

选择理由：本任务跨 API、CLI、Spring starter、Agent/Coding 消费路径和 docs-site，但目标清晰，不需要拆成多 worker worktree。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionRegistry.java` | 当前 discover / enable / expose 与 snapshot 入口 | coordinator / reviewer |
| C-002 | code | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java` | CLI 用户检查、执行 command、读取资源的入口 | coordinator / reviewer |
| C-003 | code | `ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiExtensionProperties.java` | Spring 配置模型需要扩展授权项 | coordinator / reviewer |
| C-004 | public-doc | `docs-site/docs/core-sdk/extension/plugin-packages.md` | 插件使用者路径和当前边界说明 | coordinator / reviewer |
| C-005 | private-plan | `coding-agent-harness/planning/tasks/2026-06-10-extension-plugin-contract-hardening-272a10c4/findings.md` | F-038 residual 指向本轮细粒度授权 | coordinator / reviewer |

## 步骤

1. 诊断现有 extension registry、CLI、Spring 和 Coding Agent 资源消费路径。
2. 在 extension API 中实现兼容默认的 activation policy / plan 和细粒度 allowlist。
3. 将 CLI `inspect` / `run` / `resource` 与 Spring 配置接入授权模型。
4. 更新 docs-site 插件安装与授权说明，明确本地依赖管理和非 marketplace 边界。
5. 运行目标回归，更新 Regression SSoT、Cadence Ledger、task review 和 walkthrough。

## 验收标准

- [x] 默认 `enable(...)` 兼容旧行为；新 API 能切换为显式授权模式。
- [x] 未授权 command / skill / prompt / guardrail 不进入 runtime snapshot；配置了不存在资源会 fail-fast。
- [x] CLI 可以输出 activation plan，并能通过参数只允许指定 command / resource。
- [x] Spring 配置可以表达 tools expose 与非 tool 资源 allowlist。
- [x] docs-site 清楚说明安装依赖、检查、启用、授权、回滚路径，不加入“企业采用”等不合适文案。
- [x] 相关 Java 回归、docs-site build/typecheck 和 harness status 证据记录完整。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：当前 main 工作树干净且任务由 coordinator 单线执行；不涉及并行 worker 交接。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果需要破坏兼容语义或引入远程插件安装能力，停止并请求用户确认。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + task review packet；必要时再交 Dashboard 人工确认
- No-finding 要求：无 open material finding

## 关联

- 相关 Regression Gate：RG-010、RG-011、RG-004、RG-005、RG-007、RG-008
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-10-ai4j-extension-permission-and-install-ux-95f89265/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：F-038 `coding-agent-harness/planning/tasks/2026-06-10-extension-plugin-contract-hardening-272a10c4/`

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：task-review 后由 lifecycle CLI 同步
- Closeout / Regression update needed：已更新 `docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、task-local `walkthrough.md`
