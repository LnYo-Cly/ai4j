# docs-site 第八阶段实施清单（第一批）

日期：2026-03-28

## 目标

第八阶段先补齐“发布、安装、分发”中的第一批正式入口：

- 让外部用户知道 `ai4j-cli` 当前如何获取和启动
- 让维护者知道 GitHub Release 应该发布什么资产
- 让“一键安装”有明确的产品化落地路径

---

## 范围

### 1. Coding Agent 发布入口

- [x] 新增“发布、安装与 GitHub Release”页面
- [x] 明确区分当前已实现能力与推荐发布形态
- [x] 说明 fat jar、本地启动、Release 资产、安装脚本之间的关系

### 2. 导航与阅读路径

- [x] 将新页面加入 `Coding Agent` 侧边栏
- [x] 在总览和快速开始中加入相关阅读入口

### 3. 文档站验证

- [x] 站点构建通过

---

## 文件级变更

- [x] `docs-site/docs/coding-agent/release-and-installation.md`
- [x] `docs-site/sidebars.ts`
- [x] `docs-site/docs/coding-agent/overview.md`
- [x] `docs-site/docs/coding-agent/quickstart.md`

---

## 验收标准

- [x] 外部用户能判断当前该通过源码打包还是下载发布版使用
- [x] 维护者能看懂 GitHub Release 的最小资产清单
- [x] 一键安装的脚本职责与边界被文档讲清楚
