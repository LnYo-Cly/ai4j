# Agent SDK task decomposition and technical docs - Brief

## 背景

用户已经认可 AI4J Agent SDK / Coding Agent CLI/TUI / Sandbox / Plugin / YAML Blueprint / docs-site 的增强方向，并要求开始完成“所有任务拆解”，同时继续使用 worktree、PR、合并、自测和 docs-site 技术文档更新流程。

当前 `origin/dev` 已经包含大量基础切片；本任务的重点不是重复实现，而是把当前事实状态、后续任务队列、依赖关系、验证命令和 docs-site 接手入口整理清楚。

## 本任务目标

1. 在 Harness 任务包中记录完整、可执行的 Agent SDK 后续任务拆解。
2. 在 docs-site 增加 `Agent SDK 任务拆解` 技术文档页，并从 Agent sidebar / overview / roadmap 链接。
3. 明确每个切片的模块边界、当前状态、下一步、验证命令和禁止事项。
4. 保持本轮只做规划和文档，不混入生产代码实现。

## 范围

- 允许修改：本任务包、docs-site Agent 文档、sidebar、roadmap/overview 链接。
- 不修改：Java 源码、CLI runtime、provider 配置、token、真实 sandbox provider。
- 输出受众：后续 coordinator / worker / reviewer / 用户自己。

## 完成定义

- task-local reference 存在并覆盖 T0-T10 后续任务。
- docs-site 页面存在并进入导航。
- overview / roadmap 能引导读者到任务拆解页。
- task package 无模板占位，lesson decision 完成。
- docs build、diff check、token scan、Harness status 通过。
