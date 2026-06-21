# CubeSandbox live install and coding sandbox routing

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在不扩大到完整云端 Runner 的前提下，让 CLI attach 已有 CubeSandbox session 后具备真实 `SandboxSession` 路由能力，并把不能现场安装/验证 live CubeSandbox 的环境原因记录为受控残余。

## 范围

- 做什么：新增 CLI sandbox session resolver；把 `cubesandbox` / `cube` providerId 映射到 `CubeSandboxProvider.connect(...)`；在 `CodingCliSessionRunner` 中持有/切换/回滚/关闭 live `SandboxSession`；更新 docs-site、Regression SSoT、Cadence Ledger 和任务包。
- 不做什么：不从 CLI 创建 CubeSandbox；不保存或打印 provider key；不把其它 provider 假装 live；不安装半成品 Docker/WSL；不实现 file/git/browser/long process 全量 sandbox routing。
- 主要风险：用户误以为 CLI 会创建 sandbox；attach 失败后错误地回退本机；runtime switch 失败泄漏 session；文档仍保留旧 metadata-only 口径；live smoke 被误报为通过。

## 预算选择

选择预算：complex

选择理由：本任务跨 CLI runtime、agent sandbox provider、docs-site、回归治理和外部环境探测；还涉及失败回滚和真实 live 验证边界，不能作为 simple 任务处理。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/cubesandbox/CubeSandboxProvider.java` | 确认 `connect(sessionId, spec)` 已存在且 connect 模式 close 不销毁远端实例 | coordinator / reviewer |
| C-002 | code | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java` | `/sandbox` 命令、runtime rebuild、status/help/palette 文案入口 | coordinator / reviewer |
| C-003 | docs | `docs-site/docs/coding-agent/sandbox-routing.md` | 用户理解 sandbox routing 边界的主文档 | coordinator / reviewer |
| C-004 | governance | `docs/05-TEST-QA/Regression-SSoT.md` and `docs/05-TEST-QA/Cadence-Ledger.md` | 记录新增固定回归和 live pending-env 证据 | coordinator / reviewer |

## 步骤

1. 诊断 CubeSandbox provider 现状和 CLI `/sandbox` 当前 metadata-only 逻辑。
2. 新增 resolver，把 CubeSandbox/cube attach 升级为 live session，其它 provider 保持 metadata-only。
3. 在 CLI runtime 中处理 attach/disable/run close 生命周期和 runtime switch rollback。
4. 增加 deterministic tests 覆盖 live route、disable close、rollback close、resolution failure。
5. 更新 docs-site 和 CLI help/palette 文案，消除旧的“CubeSandbox 仍 metadata-only”误导。
6. 尝试本机 live 环境探测与 opt-in smoke；若缺底座，记录 `pending-env`。
7. 更新 Regression/Cadence、task progress/review/walkthrough，提交待审。

## 验收标准

- [x] `DefaultCliSandboxSessionResolverTest` 覆盖 CubeSandbox/cube live connector 和 unknown metadata-only。
- [x] `CodingCliSessionRunnerSandboxTest` 覆盖 live bash route、disable close、switch rollback close、resolve failure no switch、run exit close。
- [x] docs-site build 通过。
- [x] live provider test 在缺 env 时受控 skip，并且环境 blocker 被记录。
- [x] `git diff --check` 和 harness status 在最终收口阶段执行并记录。

## 工作树（Worktree）

- 路径：`.worktrees/feature/cubesandbox-live-routing`
- 分支：`feature/cubesandbox-live-routing`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`feature/cubesandbox-live-routing`
- 未使用 worktree 的原因：不适用；本任务已使用隔离 worktree。

## 长程任务判定

- 是否属于长程任务：是
- 若是，合同文件：当前任务包 + 用户连续授权对话
- 连续执行权限：已授权
- Stop Condition 摘要：遇到需要管理员/重启/外部 CubeAPI 的 live 环境安装或真实 key 时停止并记录 residual，不伪造 live pass。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self；尝试启动子 agent 审查但因 TroveBox 502 失败，改为本地自审 + 回归证据。
- No-finding 要求：无开放 P0/P1/P2 material finding；live env blocker 作为 P3 残余。

## 关联

- 相关 Regression Gate：RG-002、RG-004、RG-008、LV-002
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：SRB-064 CubeSandbox provider adapter；P3/P4 sandbox routing/CLI commands

## 模块关联（启用模块并行时填写）

- Module：ai4j-cli / docs-site / governance
- Step：Sandbox live attach follow-up
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-review
- Registry update needed：不适用
- Harness Ledger update needed：task plan / review / walkthrough 已更新；generated rebuild 可后续统一执行
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`
