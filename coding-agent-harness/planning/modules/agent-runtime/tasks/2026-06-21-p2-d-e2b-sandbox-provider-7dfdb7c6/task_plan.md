# P2-D E2B sandbox provider

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-21-p2-d-e2b-sandbox-provider-7dfdb7c6/artifacts/preset/2026-06-21T11-52-41-488Z
Task Package Index: required

## 目标

为 agent sandbox SPI 增加一个 E2B provider，能创建/销毁 E2B 沙箱并通过 Connect server-streaming
`process.Process/Start` 执行命令，行为与 Daytona provider 对齐。

## 范围

- 做什么：在 `ai4j-agent/.../sandbox/e2b/` 下新增 provider（Config/Client/Provider/Session + 3 个
  DTO/异常），实现 create→execute→delete 全链路；镜像 Daytona SPI 与测试结构；离线单测 + live 烟测。
- 不做什么：不接 `process.Process/SendSignal`（cancel 返回 false）；不接 filesystem API
  （listArtifacts 返回空）；不在 create 请求里发 labels/metadata（字段未确认）；不改 core/CLI/starter。
- 主要风险：Connect 协议帧格式与 auth 是通过 live 实测确认的，envd 侧行为以实测为准。

## 预算选择

选择预算：complex

选择理由：新增一个完整 provider，含非平凡的外部协议（Connect 帧编解码、base64、exitCode 陷阱），
需要对齐 Daytona SPI 并自测；不属于简单增量。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/daytona/ | Daytona provider 作为 SPI/测试模板 | worker |
| C-002 | external | URL:https://github.com/e2b-dev/E2B (python-sdk connection_config.py) | 确认 envd 端口 49983、host 格式、auth | worker |
| C-003 | private-plan | PRIVATE:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-21-p2-d-e2b-sandbox-provider-7dfdb7c6/findings.md | Connect 协议实测结论 | worker/reviewer |

## 步骤

1. live 探测 E2B control + envd 接口，确认 Connect 请求帧/响应帧/auth/exitCode 语义。
2. 实现 E2BSandboxConfig（fromEnvironment + spec.config 覆盖）。
3. 实现 E2BSandboxClient（create/delete REST + execute Connect 编解码）。
4. 实现 E2BSandboxProvider/E2BSandboxSession（命令→ sh -c 包装 + stdin 管道 + env 合并）。
5. 离线单测（纯帧 + 本地 HTTP 集成）+ live 烟测；ai4j-agent 全模块回归。

## 验收标准

- [x] `ai4j-agent/.../sandbox/e2b/` 7 个源文件，实现 SandboxProvider + SandboxSession SPI。
- [x] Connect 帧编码/解码有纯单测覆盖（含 exit=0 经 status 解析的陷阱）。
- [x] 本地 HTTP 集成测试覆盖 create→execute→delete 与请求帧结构。
- [x] live 烟测（E2B_API_KEY 存在时）真实创建沙箱、执行命令、验证 exitCode（含非零 7），并销毁。
- [x] `mvn -pl ai4j-agent -am test` 全绿，无回归。

## 工作树（Worktree）

- 路径：主工作区（未用 worktree）
- 分支：`feat/e2b-sandbox-provider`（基于 main `a6c196c`）
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`feat/e2b-sandbox-provider`
- 未使用 worktree 的原因：单 provider 增量，无并行写冲突。

## 长程任务判定

- 是否属于长程任务：否
- 连续执行权限：不适用
- Stop Condition 摘要：不适用

## 审查判定

- 是否需要对抗性审查：是（外部协议实现）
- 报告文件：`review.md`
- Reviewer：self + subagent（待 PR 评审）
- No-finding 要求：reviewer 无重要协议/安全发现

## 关联

- 相关 Regression Gate：`mvn -pl ai4j-agent -am test`；live profile `-Plive-provider-tests`
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-21-p2-c-daytona-sandbox-provider-7263b5b5`（handoff）

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-P2-D-E2B-SANDBOX-PROVIDER-7DFDB7C6
- Module Plan：coding-agent-harness/planning/modules/agent-runtime/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime, T-P2-D, review, feat/e2b-sandbox-provider, updated
- Harness Ledger update needed：本 task_plan + review + closeout 状态
- Closeout / Regression update needed：progress.md

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only if the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
