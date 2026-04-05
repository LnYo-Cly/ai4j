# 2026-03-28 FlowGram WebApp Demo 实施计划

- 状态：Implemented
- 优先级：P0
- 依赖设计：`docs/plans/2026-03-28-flowgram-webapp-demo-design.md`
- 目标目录：`ai4j-flowgram-webapp-demo`
- 关联后端：`ai4j-flowgram-demo`

## 1. 实施目标

新增一个独立前端 demo 工程，完成 FlowGram 工作台的第一阶段能力，并保证：

- 与 `ai4j-flowgram-demo` 可直接联调
- 第一版页面结构清晰，具备 Coze / Dify 风格工作台雏形
- 能完整打通 `validate/run/report/result/cancel`
- 不改动现有 starter contract

## 2. 实施原则

- 先立前端工程骨架，再接 FlowGram 编辑器，再接后端 API
- 先保证工作流编辑与运行闭环，再补视觉细节
- 尽量复用官方 FlowGram 前端思路，不自研画布
- 前端项目不加入 Maven reactor
- 第一阶段不引入不必要的持久化和复杂状态库

## 3. 建议目录

```text
ai4j-flowgram-webapp-demo/
  package.json
  tsconfig.json
  rsbuild.config.ts
  index.html
  public/
  src/
    app/
    pages/
    components/
    features/
      flowgram/
      runtime/
      workspace/
    shared/
    styles/
```

## 4. 阶段拆分

### Phase 0：工程初始化

目标：

- 创建独立 React + TypeScript + Rsbuild 工程
- 约定开发端口与代理规则

任务：

- 新增 `ai4j-flowgram-webapp-demo/package.json`
- 新增 `tsconfig.json`
- 新增 `rsbuild.config.ts`
- 新增基础入口与页面壳子
- 配置 `/flowgram` -> `http://127.0.0.1:18080` 的 dev proxy

验收：

- `npm install`
- `npm run dev`
- 页面可在 `http://127.0.0.1:5173` 打开

### Phase 1：工作台壳子

目标：

- 先把工作台结构搭起来

任务：

- 顶部工具栏
- 左侧节点面板
- 中央画布容器
- 右侧属性面板占位
- 底部运行面板占位

验收：

- 页面布局稳定
- 响应式在桌面主场景下可用

### Phase 2：FlowGram 编辑器集成

目标：

- 接入 FlowGram 编辑器
- 能展示最小 workflow

任务：

- 基于官方推荐方式接入编辑器
- 提供最小内建模板：
  - Start -> End
  - Start -> LLM -> End
- 实现节点选中与右侧面板联动

验收：

- 画布可操作
- 节点能新增、选中、连线

### Phase 3：属性面板 MVP

目标：

- 让内建节点可以通过表单编辑关键字段

任务：

- `Start` 面板
- `LLM` 面板
- `Condition` 面板
- `Loop` 面板
- `End` 面板
- 必要时保留 JSON fallback

验收：

- 修改面板字段后 workflow schema 会同步更新

### Phase 4：运行态 API 对接

目标：

- 打通后端 REST contract

任务：

- 新增 `flowgramApi.ts`
- 新增 workflow serializer
- 新增运行态 polling service
- 接入：
  - validate
  - run
  - report
  - result
  - cancel

验收：

- 可以从前端触发完整工作流执行
- 底部面板能显示状态、结果、错误

### Phase 5：本地草稿与 JSON 工具

目标：

- 增加 demo 可操作性与调试效率

任务：

- `localStorage` 自动保存当前草稿
- `Import JSON`
- `Export JSON`

验收：

- 刷新后可恢复最近工作流
- 能导入/导出当前 schema

### Phase 6：视觉打磨

目标：

- 让页面从“能用”升级到“像产品工作台”

任务：

- 统一颜色变量
- 统一面板与卡片层级
- 提升工具栏、边栏、抽屉观感
- 补必要的交互反馈与加载状态

验收：

- 整体观感接近 Coze / Dify 风格，而非技术裸页

## 5. 关键文件建议

- `ai4j-flowgram-webapp-demo/package.json`
- `ai4j-flowgram-webapp-demo/rsbuild.config.ts`
- `ai4j-flowgram-webapp-demo/src/app/App.tsx`
- `ai4j-flowgram-webapp-demo/src/pages/WorkbenchPage.tsx`
- `ai4j-flowgram-webapp-demo/src/components/toolbar/*`
- `ai4j-flowgram-webapp-demo/src/components/sidebar/*`
- `ai4j-flowgram-webapp-demo/src/components/inspector/*`
- `ai4j-flowgram-webapp-demo/src/components/runtime-panel/*`
- `ai4j-flowgram-webapp-demo/src/features/flowgram/editor/*`
- `ai4j-flowgram-webapp-demo/src/features/runtime/api/flowgramApi.ts`
- `ai4j-flowgram-webapp-demo/src/features/runtime/polling/*`
- `ai4j-flowgram-webapp-demo/src/features/workspace/state/*`
- `ai4j-flowgram-webapp-demo/src/shared/types/*`
- `ai4j-flowgram-webapp-demo/src/styles/*`

## 6. 测试计划

### 本地开发验证

- `npm install`
- `npm run dev`
- 与 `ai4j-flowgram-demo` 同时启动

### 前端逻辑验证

- schema 序列化
- polling 停止条件
- 错误状态展示
- `localStorage` 草稿恢复

### 联调验证

- 最小 Start -> End workflow
- Start -> LLM -> End workflow
- `validate` 错误路径
- `task not found` 路径
- `cancel` 路径

### 浏览器验证

- 使用 `agent-browser` 打开本地页面
- 校验布局与主要交互元素可达
- 跑通一次真实 `glm-4.7` workflow

## 7. 风险与缓解

### 风险 1：FlowGram React 集成方式与预期不同

缓解：

- 先从最小示例落地
- 不在 Phase 0 过早绑定复杂页面结构

### 风险 2：工作台壳子与编辑器状态同步复杂

缓解：

- 统一 workflow state 入口
- 面板只操作受控字段

### 风险 3：API 返回结构与前端展示需求存在缝隙

缓解：

- 所有后端响应先进入适配层，不让页面直接消费原始 DTO

### 风险 4：视觉风格实现拖慢核心联调

缓解：

- Phase 1 到 Phase 4 先保证可用
- Phase 6 再集中打磨视觉

## 8. 完成标志

满足以下条件即视为 P0 完成：

- 前端独立工程建立完成
- 工作台页面可用
- FlowGram 画布能编辑最小 workflow
- 与 `ai4j-flowgram-demo` 的 REST API 联调通过
- 真实 `glm-4.7` LLM 节点从前端可跑通
