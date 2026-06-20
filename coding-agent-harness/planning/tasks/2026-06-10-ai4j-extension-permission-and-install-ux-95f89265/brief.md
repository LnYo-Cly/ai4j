# AI4J Extension Permission and Install UX

## Task ID

`2026-06-10-ai4j-extension-permission-and-install-ux-95f89265`

## 创建日期

2026-06-10

## 一句话结果

AI4J 插件系统补齐非 tool 资源的显式授权与安装前检查体验，让使用者在启用第三方插件前能看清资源、权限和实际激活范围。

## 完成后能得到什么

完成后，插件使用者可以继续沿用 `enable(...)` 的兼容语义，也可以切到显式资源授权模式，逐项允许 command、Skill、Prompt 和 Guardrail。CLI 会提供可读的 activation plan，用于在安装 Maven/Gradle 依赖后、接入 Agent 之前检查插件会贡献什么资源、哪些权限需要信任、哪些资源会被激活。Spring Boot 用户可以通过配置表达同样的授权范围。文档需要把本地依赖安装、检查、启用、授权、回滚路径讲清楚，不引入远程 marketplace 或自动安装依赖语义。

## 交付物

- 可见产物：扩展 API activation policy / plan、CLI 检查入口、Spring Boot 配置项、docs-site 插件安装与授权说明。
- 修改位置：`ai4j-extension-api/`、`ai4j-cli/`、`ai4j-spring-boot-starter/`、`docs-site/docs/core-sdk/extension/`、回归与任务记录。
- 验证证据：Extension API、CLI、Spring starter、Ask User 插件、docs-site 的目标回归命令。

## 第一眼应该看什么

先读 `task_plan.md` 的范围与验收，再看 `progress.md` 的命令证据和本文件的边界。实现完成后从 `walkthrough.md` 获取最终决策与 residual。

## 边界

- 范围内：非 tool 扩展资源的显式 allowlist、activation plan/CLI 输出、Spring 配置映射、插件文档更新和对应测试。
- 范围外：远程插件市场、CLI 自动修改 Maven/Gradle 依赖、运行时热加载 jar、provider 自动注册、Agent 自动创建。
- 停止条件：需要破坏 `enable(...)` 默认兼容语义或引入远程安装信任链时，必须回到用户确认。

## 完成判断

- `ExtensionRegistry` 支持 command/skill/prompt/guardrail 的显式授权，并保留现有默认兼容路径。
- CLI 能输出插件 manifest、权限、runtime 贡献和基于授权参数的 activation plan。
- Spring Boot starter 能用配置表达显式授权并 fail-fast 处理未注册资源。
- docs-site 说明本地安装、检查、启用、授权和回滚路径，没有暗示远程 marketplace。
- 目标 Maven 与 docs-site 回归通过，证据记录到 `progress.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

等待人工 Review Confirmation；如审查通过，再推进 task complete / closeout ledger。
