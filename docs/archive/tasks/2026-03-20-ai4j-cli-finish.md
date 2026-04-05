# 2026-03-20 ai4j-cli-finish

- 状态：COMPLETED
- 所属阶段：Phase 8 / CLI/TUI 首版
- 对应范围：收尾 `ai4j-cli` 最小 coding session CLI，使其可编译、可测试、可打包
- 关联文档：
  - `docs/tasks/2026-03-19-ai4j-cli-minimal-coding-session.md`
  - `docs/plans/2026-03-19-ai4j-2.0-constitution.md`
  - `docs/plans/2026-03-19-ai4j-2.0-implementation-plan.md`

## 1. 目标

围绕已落地的 `ai4j-cli` 初版代码做一次收尾，确保：

- CLI 主入口类可以正常编译与装配
- 参数解析对非法 provider / protocol 明确报错，而不是静默降级
- `code` 命令的顶层分发与帮助信息有本地测试覆盖
- 模块可在不依赖真实模型 API 的前提下完成单测和打包验证

## 2. 详细任务拆解

| 编号 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| T1 | 创建当前功能任务文档 | COMPLETED | 锁定本次 CLI 收尾范围 |
| T2 | 修正 CLI 主入口编译问题 | COMPLETED | `Ai4jCli` 主类已可正常编译与分发命令 |
| T3 | 收紧 provider / protocol 参数校验 | COMPLETED | 非法 provider 现在直接 fail-fast |
| T4 | 补充 CLI 顶层分发与参数校验测试 | COMPLETED | 已覆盖 help / unknown command / invalid provider |
| T5 | 跑最小测试矩阵与模块打包验证 | COMPLETED | 已完成本地单测与 `jar-with-dependencies` 打包 |
| T6 | 同步原 CLI 任务文档状态 | COMPLETED | 已同步 `2026-03-19` CLI 任务文档状态 |

## 3. 变更记录

- 2026-03-20：创建收尾任务文档，准备完成 `ai4j-cli` 首版闭环。
- 2026-03-20：完成 CLI 主入口修正、参数 fail-fast、本地测试与模块打包验证。
