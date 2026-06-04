# Version Alignment BOM 模块

## 模块 Key

`bom`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-bom/` 的版本对齐和依赖管理边界。

## 完成后能得到什么

该模块让版本管理任务和功能模块任务分离。涉及 release 版本、dependencyManagement、跨模块 artifact alignment 或下游 starter 使用体验时，应落到 `bom` 或作为共享同步项由 coordinator 处理。

## 交付物

- 可见产物：BOM POM、版本对齐规则和发布相关验证记录。
- 负责范围：`ai4j-bom/`
- 验证证据：`mvn -pl ai4j-bom -DskipTests package` 或全局 package smoke。

## 第一眼应该看什么

先读 `module_plan.md`，再检查根 `pom.xml`、发布 profile 和受影响模块 POM。

## 模块职责

负责版本对齐和发布消费者视角，不承载功能代码。

## 边界

- 负责：`ai4j-bom/pom.xml` 和 BOM 相关发布材料。
- 共享面：根 POM、各模块版本、release profile。
- 不负责：模块功能实现、测试逻辑或 docs site 内容。

## 完成判断

- BOM 变更说明影响的模块和下游消费者。
- release 或 package 验证覆盖版本对齐风险。
- 不把功能代码变更混进 BOM 任务。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
