# 2026-03-19 ai4j-cli-minimal-coding-session

- 状态：COMPLETED
- 所属阶段：Phase 8 / CLI/TUI 首版
- 对应范围：`ai4j-cli` 最小 coding session 命令行入口
- 关联文档：
  - `docs/plans/2026-03-19-ai4j-2.0-constitution.md`
  - `docs/plans/2026-03-19-ai4j-2.0-implementation-plan.md`
  - `docs/archive/tasks/2026-03-19-ai4j-coding-foundation.md`
  - `docs/archive/tasks/2026-03-19-coding-bash-process-runtime.md`
  - `docs/archive/tasks/2026-03-19-coding-apply-patch.md`

## 1. 目标

为 `ai4j-coding` 建立第一个可本地运行的 CLI 入口，使用户可以：

- 通过命令行创建 coding session
- 选择 provider + protocol + model
- 绑定 workspace 并进入多轮交互
- 观察模型输出与工具调用事件
- 在会话退出时自动清理后台进程

## 2. 预期交付

1. `ai4j-cli` 提供可执行 main 入口
2. 提供 `code` 命令的最小参数解析能力
3. 支持 `chat` / `responses` 两类模型客户端接入
4. 支持 one-shot prompt 与交互式 REPL
5. 支持基础帮助信息、退出命令、工具调用事件展示
6. 补充纯本地测试，不依赖真实 provider

## 3. 设计约束

- 首版先做 CLI，不在本任务内引入 TUI
- CLI 只消费 SDK，不把 provider 实现反向塞进 `ai4j-coding`
- 默认优先服务 coding-agent 场景，而不是泛化为全能命令平台
- 交互命令保持克制，先收敛 `code` 主入口
- 写文件继续通过 `apply_patch` / `bash`，CLI 不新增额外写文件抽象

## 4. 当前假设

- 默认命令结构采用 `ai4j-cli code`
- 默认优先支持 `zhipu + chat` 与 `openai + responses/chat`
- CLI 首版不做颜色主题与复杂终端控制，先保证可用与可测

## 5. 详细任务拆解

| 编号 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| T1 | 创建当前功能任务文档 | COMPLETED | 锁定本次 CLI 范围，避免实现漂移 |
| T2 | 明确 CLI 首版命令结构与配置来源 | COMPLETED | 已收口命令参数、环境变量与默认值 |
| T3 | 建立 provider/protocol 到 model client 的装配层 | COMPLETED | 已支持 chat/responses 装配与 provider 默认协议决策 |
| T4 | 实现 `code` 命令与交互式 session runner | COMPLETED | one-shot + REPL 已覆盖 |
| T5 | 输出最小可读的会话/工具事件信息 | COMPLETED | 已展示 tool call、tool result、final output |
| T6 | 补充本地测试并验证模块构建 | COMPLETED | 已补 CLI 本地测试，并完成模块打包验证 |
| T7 | 更新总计划文档并归档任务文档 | COMPLETED | 当前任务状态已同步，可归档 |

## 6. 参考资料

- `pi-sdk` / `pi-tui`
- Claude Code CLI
- OpenAI Codex CLI
- OpenCode / OpenClaw 的本地命令与进程交互模式

## 7. 变更记录

- 2026-03-19：创建任务文档，锁定 CLI 首版为“最小 coding session + provider/protocol 装配 + REPL”。
- 2026-03-20：完成 CLI 主入口、参数解析、provider/protocol 装配、交互式 session runner、本地测试与模块打包验证。

## 8. 已完成

- `ai4j-coding` 已具备 `workspace`
- `ai4j-coding` 已具备 `bash/read_file/apply_patch`
- session 级后台进程管理已可用

## 9. 未完成

- 无；当前任务范围已完成

## 10. 归档规则

- 本文档在当前功能完成前保留在 `docs/tasks/`
- 当 `T1-T7` 完成后，移动到 `docs/archive/tasks/`
