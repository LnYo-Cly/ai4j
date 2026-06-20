# docs site agent sdk real api completeness pass

## Task ID

`2026-06-20-docs-site-agent-sdk-real-api-completeness-pass-d9906610`

## 创建日期

2026-06-20

## 一句话结果

补齐 docs-site 的 Agent SDK 真实 API 能力矩阵，让用户能按“能力 -> 真实类/命令 -> 当前可用状态 -> 下一篇文档”理解 AI4J Agent SDK，而不是从零散页面或伪 API 示例判断。

## 完成后能得到什么

本任务完成后，Agent 文档入口会新增 `Agent SDK 真实 API 能力矩阵`，覆盖 Agent 主链、Session/Memory/Compact、Blueprint、插件生态、Permission/Sandbox/Remote Runner、Workflow/SubAgent/Team、CLI/TUI 等用户可见能力。页面只引用当前 `dev` 源码中已经存在的类、接口或命令，并明确区分“可直接使用”“SPI/合同已存在”“Host/CLI 绑定中”“规划中”。同时修正 `reference-core-classes.md` 中过期的 `AgentSession` 描述，避免继续把 session 简化成“只切换 memory”。

## 交付物

- 可见产物：`docs-site/docs/agent/real-api-matrix.md`
- 修改位置：`docs-site/docs/agent/overview.md`、`docs-site/docs/agent/quickstart.md`、`docs-site/docs/agent/reference-core-classes.md`、`docs-site/sidebars.ts`
- 验证证据：`npm --prefix docs-site run typecheck`、`npm --prefix docs-site run build`、`git diff --check`、`npx --yes coding-agent-harness status --json .`

## 第一眼应该看什么

1. `docs-site/docs/agent/real-api-matrix.md`：能力矩阵主产物。
2. `docs-site/docs/agent/reference-core-classes.md`：`AgentSession` 过期描述修正。
3. `progress.md`：验证命令和结果。
4. `review.md`：自审查结论和残余风险。

## 边界

- 范围内：docs-site Agent 章节的真实 API 能力索引、入口链接、过期 session 描述修正、任务包证据记录。
- 范围外：不改 Java 实现；不新增未实现 API；不接真实 provider/sandbox；不使用或提交任何用户 token；不把 docs-site 文档写成完整云平台承诺。
- 停止条件：如果发现文档需要描述未落地能力，只能标成 SPI/规划/host-bound，不得写成可直接使用；如果要改 public API，必须另开实现任务。

## 完成判断

- [x] 新增真实 API 能力矩阵页面并加入 Agent sidebar。
- [x] Overview / Quickstart 能引导用户先查真实 API 矩阵。
- [x] `reference-core-classes.md` 的 `AgentSession` 描述与当前源码职责一致。
- [x] docs-site typecheck/build 通过。
- [x] Harness status 本地通过（failures=0；提交前仅 dirty warning）。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并提交/推送 PR 到 `dev`。

## 当前下一步

运行 docs-site typecheck/build，修复链接或 MDX 问题，然后提交并创建 PR。
