# P1-A Agent Blueprint schema model loader validator - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 现有路线图已经把 P1 定位为单 Agent YAML Blueprint

- 背景：用户希望 `ai4j-agent` 形成像 Pi SDK 一样可组装、可声明式、可产品化的 Agent SDK，但不希望过度拆 Maven 模块或引入奇怪的新概念。
- 发现：上游架构规划明确 `ai4j-agent` 是主入口，不新增 `AgentHost` / `Host Kernel`；实施拆解路线图把 P1 定为 `AgentBlueprint`、`AgentBlueprintLoader`、`AgentBlueprintValidator`、`AgentFactory`，其中本任务 P1-A 只做 schema/model/loader/validator。
- 影响：本任务必须保持“声明式配置基础层”边界，不应顺手实现 factory、CLI 或远端 runner。
- 后续：P1-B 单独创建 `AgentFactory` 任务。

### 当前 `ai4j-agent` 没有 Blueprint/YAML 包

- 背景：实施前需要确认是否已有可复用实现。
- 发现：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent` 下已有 session/memory/compact/permission/lifecycle 等包，但未发现 blueprint 包；`ai4j-agent/pom.xml` 未发现 YAML parser 依赖。
- 影响：P1-A 应新增 `blueprint` 包；YAML 解析依赖需要在实施时验证 Java 8 兼容。
- 后续：实施任务中选择并验证 YAML dependency。

### docs-site 已有 P1 用户心智，但需要独立页面

- 背景：用户多次要求 docs-site 每个特色功能讲清楚，不能只有浅层 bullet。
- 发现：`docs-site/docs/agent/sdk-roadmap.md` 已有 P1 YAML 示例和“Team/Workflow/FlowGram 后置”说明，但没有专门解释字段、校验错误、非目标和后续路线的页面。
- 影响：实施完成时需要新增 `docs-site/docs/agent/agent-blueprint.md`，并更新 sidebar/roadmap。
- 后续：docs-site build 作为必要回归。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| P1-A 范围 | 只做 schema/model/loader/validator | 控制破坏面，为 Factory/Sandbox/CLI 留出后续任务 | Factory-first 或完整 DSL | accepted |
| 模块位置 | `ai4j-agent` 内新增 `blueprint` 包 | Blueprint 属于通用 Agent SDK，不属于 core provider、CLI 或 starter | 新增 Maven 模块、放入 ai4j-core、放入 CLI | accepted |
| YAML 依赖 | 实施时验证后再固定 | 需要 Java 8 兼容和安全基线，规划阶段不猜最新版 | 手写 parser、JSON-only、提前锁死版本 | proposed |
| Validator 输出 | report/issue 模型，不只抛异常 | 便于 docs-site、CLI、UI builder、Runner 后续复用 | 直接 throw first error | accepted |
| sandbox 字段 | P1-A 只声明和校验，不执行 | Sandbox 是后续 SPI，不应伪装成普通 tool 或真实 provider | 直接接真实 sandbox | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否授权 worker subagent 并行实施 | 默认不启用；coordinator 可单独做，若用户要求并行再授权 | user / coordinator | 开始 EXEC-01 前 |
| YAML parser 版本 | 实施时用 Maven/Java 8 验证后确定 | coordinator | EXEC-01 |
| Unknown fields 是 error 还是 warning | 第一版推荐 warning，避免未来字段扩展破坏用户配置 | reviewer / coordinator | Validator test 编写前 |
