# docs-site 第三阶段实施清单

日期：2026-03-28

## 目标

第三阶段聚焦 AI4J 的统一基础能力接入层：

- 把 `ai-basics` 写成真正的 SDK 主线
- 明确统一服务入口、平台路由和返回读取方式
- 让用户知道应该先学哪条主线，再进入各个单项服务专题

---

## 范围

### 1. 主入口

- [x] 新增统一服务入口页
- [x] 讲清 `Configuration`、`AiService`、`PlatformType`
- [x] 串起 Chat / Responses / Embedding / Audio / Image / Realtime

### 2. 总览收敛

- [x] 将 `AI基础能力接入总览` 改成官网入口页
- [x] 明确这一章与 MCP / Agent / Coding Agent 的边界
- [x] 给出清晰阅读顺序

### 3. 平台适配页收敛

- [x] 让 `平台适配与统一接口` 更聚焦“平台差异与能力矩阵”
- [x] 补充跨页跳转

---

## 文件级变更

- [x] `docs-site/docs/ai-basics/overview.md`
- [x] `docs-site/docs/ai-basics/unified-service-entry.md`
- [x] `docs-site/docs/ai-basics/platform-adaptation.md`
- [x] `docs-site/sidebars.ts`

---

## 验收标准

- [x] 用户能先从统一入口理解 AI4J 的服务选择方式
- [x] 用户能区分 Chat、Responses 与其它服务的使用路径
- [x] `ai-basics` 总览不再只是目录说明，而是真正的官网入口
- [ ] 站点构建通过
