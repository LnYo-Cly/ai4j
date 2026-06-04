# FlowGram Webapp Demo 模块

## 模块 Key

`flowgram-webapp-demo`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-flowgram-webapp-demo/` 的 FlowGram web demo frontend surface。

## 完成后能得到什么

该模块让 web demo UI、前端构建和 backend contract 变更独立可追踪。涉及 FlowGram demo 前端、页面交互、构建配置或与 demo backend API 的契约时，任务应落到 `flowgram-webapp-demo`，并同步 `flowgram-demo`。

## 交付物

- 可见产物：web demo UI、前端资源、构建或交互验证记录。
- 负责范围：`ai4j-flowgram-webapp-demo/`
- 验证证据：模块本地 frontend build/test 命令或浏览器 smoke。

## 第一眼应该看什么

先读 `module_plan.md`，再查看 webapp package/config 和 demo backend API 期望。

## 模块职责

负责 demo frontend，不承载 backend production logic 或 FlowGram starter 行为。

## 边界

- 负责：`ai4j-flowgram-webapp-demo/**`
- 共享面：demo backend API contract、docs/demo usage。
- 不负责：backend endpoint、starter integration、Java production code。

## 完成判断

- webapp 任务明确验证 build 或浏览器 smoke。
- API contract 变化同步给 `flowgram-demo`。
- UI 变更不修改 Java production logic。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
