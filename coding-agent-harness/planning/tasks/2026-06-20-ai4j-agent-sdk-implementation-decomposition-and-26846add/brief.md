# AI4J Agent SDK implementation decomposition and docs roadmap

## Task ID

`2026-06-20-ai4j-agent-sdk-implementation-decomposition-and-26846add`

## 创建日期

2026-06-20

## 一句话结果

把已认可的 `ai4j-agent` 架构增强规划拆成 P0-P5 实施队列，并同步更新 docs-site 的 Agent 技术路线文档。

## 完成后能得到什么

后续开发者可以直接读取本任务包，知道先做哪个模块、每个阶段做什么/不做什么、需要跑哪些回归，以及 docs-site 对外如何解释 `ai4j-agent`、Blueprint、Sandbox、Runner 的演进路线。

## 交付物

- `references/ai4j-agent-implementation-roadmap.md`
- `docs-site/docs/agent/sdk-roadmap.md`
- `docs-site/docs/agent/overview.md` 的路线入口
- `docs-site/sidebars.ts` 的 Agent sidebar 入口

## 边界

- 范围内：任务拆解、docs-site 技术路线文档、Harness 记录、自测。
- 范围外：Java 生产代码、真实 sandbox provider、CLI `/sandbox` 实现、provider token 持久化。

## 完成判断

- [ ] P0-P5 实施队列清晰落盘。
- [ ] docs-site 有可访问的 Agent SDK 技术路线页。
- [ ] `npm run build` 验证 docs-site 通过。
- [ ] `npx --yes coding-agent-harness status --json .` 通过或仅剩已解释 residual。
- [ ] 分支已推送并创建 PR。
