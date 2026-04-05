# 2026-03-28 FlowGram WebApp Demo 设计

- 状态：Approved
- 目标目录：`ai4j-flowgram-webapp-demo`
- 关联后端：`ai4j-flowgram-demo`
- 关联设计：`docs/plans/2026-03-27-flowgram-spring-boot-integration-design.md`
- 关联实施计划：`docs/plans/2026-03-28-flowgram-webapp-demo-implementation-plan.md`

## 1. 背景

仓库已经具备：

- `ai4j-agent` 中的 FlowGram runtime kernel
- `ai4j-flowgram-spring-boot-starter` 的 REST 适配层
- `ai4j-flowgram-demo` 的后端 demo 与真实 LLM 验证能力

当前缺口不在后端执行能力，而在前端工作台。项目还缺少一个可直接用于演示和联调的 FlowGram Web 应用，导致：

- 无法以可视化方式编辑工作流
- 无法用接近 Coze / Dify 的交互体验展示 FlowGram 集成能力
- 无法完成真实的前后端 UI 联调闭环

## 2. 目标

新增一个独立前端 demo：`ai4j-flowgram-webapp-demo`，用于承载 FlowGram 可视化工作台，并满足：

- 使用 React + TypeScript 独立构建，不混入 Maven reactor
- 与 `ai4j-flowgram-demo` 前后端分离联调
- 第一版交互风格尽量接近 Coze / Dify 的工作台体验
- 底层尽可能复用官方 FlowGram 推荐的 React 工程方式，而不是手搓画布
- 先打通单页工作台与现有 REST API，再逐步补强体验

## 3. 非目标

- 不做正式可发布的 SDK / starter / Maven artifact
- 不做完整 Coze / Dify 产品复刻
- 不做用户体系、多人协作、模板市场、版本管理
- 不做服务端 workflow 草稿持久化
- 不在第一阶段接入 SSE

## 4. 关键决策

### 4.1 模块边界

采用双 demo 结构：

- `ai4j-flowgram-demo`
  - Spring Boot 后端 demo
  - 暴露 `/flowgram/**` REST API
  - 负责真实 LLM 节点执行
- `ai4j-flowgram-webapp-demo`
  - React 前端 demo
  - 负责 FlowGram 工作台 UI

不采用 `ai4j-flowgram-demo/frontend/` 方案，原因是当前明确接受前后端分离，本地双进程启动成本可接受，而前端重交互工作台更适合独立工程边界。

### 4.2 技术路线

推荐以 FlowGram 官方脚手架为参考基线，优先沿用其 React 工程范式，再叠加工作台外壳和业务面板。

外部参考：

- FlowGram 官网：<https://flowgram.ai/>
- 官方脚手架：<https://www.npmjs.com/package/@flowgram.ai/create-app>

推荐从官方 `Free Layout Demo` 思路起步，而不是自建节点画布。

### 4.3 开发与运行方式

前后端分离开发：

- 前端：`http://127.0.0.1:5173`
- 后端：`http://127.0.0.1:18080`

前端通过 dev proxy 转发 `/flowgram/**` 到后端，避免开发期处理 CORS。

## 5. 设计方案对比

### 方案 A：最薄封装

做法：

- 基本保留官方 demo
- 只补一个任务运行面板与 API 对接

优点：

- 成本最低
- 最快出原型

缺点：

- 很难达到 Coze / Dify 风格
- 页面容易停留在技术 demo 层面

### 方案 B：中等封装，推荐

做法：

- 保留官方 FlowGram 编辑器与画布能力
- 外层包一层工作台壳子
- 自定义左侧节点区、顶部工具栏、右侧属性面板、底部运行面板

优点：

- 兼顾开发效率与产品感
- 不重写核心画布
- 最适合第一阶段落地

缺点：

- 需要一定的 UI 外壳与状态管理设计

### 方案 C：深度定制

做法：

- 大幅改造节点、属性面板和运行态体系
- 强业务化 UI

优点：

- 上限最高

缺点：

- 第一阶段成本过高
- 风险大，容易偏离“先打通联调”的目标

结论：

- 选择方案 B

## 6. 页面结构

第一阶段只做一个页面：`WorkbenchPage`

页面分区如下：

### 6.1 顶部工具栏

展示：

- 工作流标题
- 环境标识
- 后端连接状态
- `Validate`
- `Run`
- `Cancel`
- `Import JSON`
- `Export JSON`

目标：

- 类似 Coze / Dify 顶部命令区，操作集中、反馈明确

### 6.2 左侧边栏

分为两块：

- 节点物料区
  - `Start`
  - `LLM`
  - `Condition`
  - `Loop`
  - `End`
- 示例与说明区
  - 最小 workflow
  - LLM workflow
  - 条件分支 workflow

目标：

- 保持高频编辑入口可见
- 强化 demo 可操作性

### 6.3 中央画布区

直接承载 FlowGram 编辑器：

- 拖拽节点
- 连线
- 缩放
- 框选
- 自由布局

目标：

- 保持官方画布能力
- 避免在第一阶段重写图编辑能力

### 6.4 右侧属性面板

根据选中节点动态切换表单：

- `Start`
  - 输入 schema
  - 默认值
- `LLM`
  - `serviceId`
  - `modelName`
  - `prompt`
- `Condition`
  - 条件规则表格
- `Loop`
  - `loopFor`
  - 输出映射
- `End`
  - 最终输出映射

目标：

- 不让用户直接编辑整段 JSON
- 先把高频字段做成可理解表单

### 6.5 底部运行面板

展示：

- 当前任务 ID
- 校验结果
- 任务运行状态
- 节点报告
- 最终结果
- 错误信息

目标：

- 类似 Dify 的调试面板
- 让运行态与编辑态分离但紧密关联

## 7. 交互流

### 7.1 编辑流

- 用户在画布上拖入节点
- 在右侧面板编辑节点参数
- 工作流 schema 保存在前端内存状态
- 同步缓存到本地 `localStorage`

### 7.2 校验流

- 点击 `Validate`
- 前端序列化当前 workflow
- 调用 `POST /flowgram/tasks/validate`
- 将错误展示到：
  - 顶部全局提示
  - 底部运行面板
  - 节点局部高亮（第一阶段先做基础映射）

### 7.3 运行流

- 点击 `Run`
- 调用 `POST /flowgram/tasks/run`
- 获取 `taskId`
- 按固定轮询周期拉取：
  - `GET /flowgram/tasks/{taskId}/report`
  - `GET /flowgram/tasks/{taskId}/result`
- 终态后停止轮询

### 7.4 取消流

- 点击 `Cancel`
- 调用 `POST /flowgram/tasks/{taskId}/cancel`
- UI 切换到 canceled 状态

## 8. API 对接边界

第一阶段只对接现有稳定 REST 接口：

- `POST /flowgram/tasks/validate`
- `POST /flowgram/tasks/run`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

不新增前端专属后端接口。

前端必须通过独立适配层访问这些接口，页面层不能直接拼 HTTP 请求。

## 9. 状态管理

前端状态分两类：

### 9.1 编辑态

- 当前 workflow schema
- 选中节点
- 当前激活面板
- 顶部标题与工作区元信息

### 9.2 运行态

- 当前 `taskId`
- validate 结果
- report 快照
- result 快照
- 是否轮询中
- 错误信息

第一阶段优先使用 React 自身状态与少量 context，不引入重型全局状态库。

## 10. 视觉方向

目标风格：

- 更接近 Coze / Dify 的工作台，而不是纯技术 demo

设计原则：

- 浅色工作台为主
- 冷色强调色，不走默认紫色系
- 中高信息密度
- 明确的边界、卡片和面板层级
- 保持编辑器主画布为视觉中心

## 11. 第一阶段交付范围

### 包含

- `WorkbenchPage`
- 左侧节点面板
- 顶部操作栏
- 中央 FlowGram 画布
- 右侧属性面板
- 底部运行面板
- `localStorage` 草稿保存
- `JSON import/export`
- `validate/run/report/result/cancel` 全链路

### 不包含

- 登录鉴权
- 服务端草稿保存
- 模板市场
- 多人协作
- 版本历史
- SSE
- 大规模自定义节点生态

## 12. 验收标准

- 能在前端创建并编辑最小 workflow
- 能对选中节点进行面板式配置
- 能执行 `Validate`
- 能执行 `Run`
- 能轮询查看 `report/result`
- 能展示失败原因和节点状态
- 能跑通真实 `glm-4.7` 的 `LLM` 节点
- 整体页面观感达到“产品工作台”而不是“裸 demo”水平

## 13. 风险与缓解

### 风险 1：官方 FlowGram 前端示例改造成本超预期

缓解：

- 第一阶段只包一层工作台壳子，不深改底层编辑器

### 风险 2：属性面板过早做成复杂表单系统

缓解：

- 只覆盖当前内建节点的关键字段
- 其余字段保留 JSON 兜底入口

### 风险 3：运行态 UI 与 REST contract 耦合过深

缓解：

- 所有请求与响应收口到统一 API 适配层

### 风险 4：视觉投入过多拖慢联调

缓解：

- 先立住布局和主要交互，再补细节样式
