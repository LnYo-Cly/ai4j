# 5 分钟首聊主路径文档

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让 docs-site 拥有一条小白用户可执行的 5 分钟首聊主路径，并把公开 README、sidebar 和 Start Here 入口统一指向该路径。

## 范围

- 做什么：新增 `start-here/five-minute-first-chat.md`，重写普通 Java / Spring Boot Quickstart，更新 docs-site README、根 README、sidebar 和 Start Here 交叉链接，记录 RG-008 验证。
- 不做什么：不改 Java API，不新增 provider 行为，不重写英文 README，不扩展 RAG/MCP/Agent 深页，不引入“企业采用”等措辞。
- 主要风险：文档示例可能与当前源码包路径或版本漂移；Docusaurus 在 Windows 上可能遇到历史 EPERM/OOM 残余。

## 预算选择

选择预算：standard

选择理由：该任务触及多个公开文档入口和 harness/SSoT 记录，需要完整任务包、验证和 walkthrough，但不需要独立 worktree 或 worker handoff。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc | `docs-site/docs/intro.md` | 当前文档站首页入口，决定新用户第一跳 | coordinator / reviewer |
| C-002 | public-doc | `docs-site/docs/start-here/*.md` | Start Here 主线和 Quickstart 内容 | coordinator / reviewer |
| C-003 | code | `ai4j/src/main/java/io/github/lnyocly/ai4j/service/*` and `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/chat/entity/*` | 校验示例 import、对象链和响应读取路径 | coordinator / reviewer |
| C-004 | public-doc | `docs-site/README.md` and `README.md` | 公开入口和 Skill 安装命令 | coordinator / reviewer |
| C-005 | private-plan | `docs/05-TEST-QA/Regression-SSoT.md` and `docs/05-TEST-QA/Cadence-Ledger.md` | docs-site 触发 RG-008 的证据和批次记录 | coordinator / reviewer |

## 步骤

1. 诊断 docs-site 入口、Quickstart、Skill README 和当前源码 API。
2. 新增 5 分钟首聊页，覆盖普通 Java、Spring Boot、Skill、成功标准和排障。
3. 更新 Java/Spring Boot Quickstart、intro、sidebar、choose-your-path、feature-map、why-ai4j、README 入口。
4. 运行 docs-site typecheck/build、链接/文本扫描和 harness status。
5. 更新 Regression SSoT、Cadence Ledger、review packet、lesson decision 和 walkthrough。

## 验收标准

- [x] 新用户入口可从 sidebar、intro、choose-your-path、feature-map 到达 `five-minute-first-chat.md`。
- [x] 首聊页和 Quickstart 示例使用 `2.3.0`、环境变量密钥和当前 Java 包路径。
- [x] README 提供 `$ai4j-app-builder` 安装命令和首聊/Spring Boot 调用提示。
- [x] `docs-site` 的 `npm run typecheck` 与 `npm run build` 完成或记录明确 residual。
- [x] Feature SSoT、Regression SSoT、Cadence Ledger、task progress 和 walkthrough 与结果一致。

## 工作树（Worktree）

- 路径：same checkout
- 分支：当前分支
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：文档入口改动集中在少量文件，且无并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：示例 API 与源码不一致、docs-site 构建无法收敛、或需要真实 provider 凭证时暂停。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：无 open material finding 后提交 review packet。

## 关联

- 相关 Regression Gate：RG-008 docs-site build
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-06-5-c6e2fa16/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：用户确认优先做 5 分钟首聊主路径。

## 模块关联（启用模块并行时填写）

- Module：[module key，例如 reader / graph / 不适用]
- Step：[step ID，例如 RDR-02 / 不适用]
- Module Plan：[link to module_plan.md / 不适用]

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator / 不适用
- Global sync status：pending-coordinator-pass / synced / n/a
- Registry update needed：[module key, step, status, branch, updated / 不适用]
- Harness Ledger update needed：[task plan path, review path, closeout status / 不适用]
- Closeout / Regression update needed：[路径或 n/a]
