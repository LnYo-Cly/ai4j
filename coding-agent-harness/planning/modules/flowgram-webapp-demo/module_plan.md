# FlowGram Webapp Demo 模块计划

## 模块身份

- 模块 Key：`flowgram-webapp-demo`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-flowgram-webapp-demo/**`
- 共享面：FlowGram demo backend API expectations、demo docs
- 依赖模块：`flowgram-demo`

## 边界

- 可以编辑：webapp demo 前端源码、配置、资源和 package files。
- 禁止编辑：demo backend、FlowGram starter 或 Java modules，除非任务明确批准。
- 外部依赖：Node/npm、browser runtime、backend demo endpoint。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| WEBAPP-01 | 维护 webapp demo contract | planned | none | flowgram-demo |
| WEBAPP-02 | build / browser smoke | planned | none | WEBAPP-01 |
| WEBAPP-03 | backend API sync | planned | none | WEBAPP-01 |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| none | planned | coordinator | none | 有模块任务后替换此行。 |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| frontend build/test | module-local npm command | yes when webapp changes |
| browser smoke | local dev server or static preview evidence | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 npm build/test 或 browser smoke。
- 变更文件：只列 `ai4j-flowgram-webapp-demo/**` 及批准的 docs。
- 残余风险：backend 未联调或浏览器未验证时必须说明。
- 需要 coordinator 同步：API contract 影响 backend 或 docs 时同步。
