# docs-site 第五阶段实施清单

日期：2026-03-28

## 目标

第五阶段聚焦 Agent 专题的框架化入口：

- 给第一次使用 Agent 的开发者一条最短起步路径
- 保持现有深度页，但让阅读顺序更合理
- 清理带对话痕迹的文档语气

---

## 范围

### 1. 框架入口

- [x] 新增“最小 ReAct Agent”页
- [x] 讲清最小闭环、工具白名单、何时升级 Runtime
- [x] 给出继续阅读路径

### 2. 总览收敛

- [x] 调整 Agent 总览阅读顺序
- [x] 让“最小 ReAct Agent”成为正式起步页

### 3. 文风清理

- [x] 清理 Trace 页的对话痕迹

---

## 文件级变更

- [x] `docs-site/docs/agent/minimal-react-agent.md`
- [x] `docs-site/docs/agent/overview.md`
- [x] `docs-site/docs/agent/trace-observability.md`
- [x] `docs-site/sidebars.ts`

---

## 验收标准

- [x] 用户第一次进入 Agent 专题时知道该从哪里开始
- [x] 总览页阅读顺序更符合“先起步、再深入”
- [x] 站点构建通过
