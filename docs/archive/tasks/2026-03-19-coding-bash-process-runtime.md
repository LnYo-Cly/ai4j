# 2026-03-19 coding-bash-process-runtime

- 状态：COMPLETED
- 所属阶段：Phase 6 / 建立 `ai4j-coding`
- 对应范围：`ai4j-coding` 命令执行主战场收敛
- 关联文档：
  - `docs/plans/2026-03-19-ai4j-2.0-constitution.md`
  - `docs/plans/2026-03-19-ai4j-2.0-implementation-plan.md`
  - `docs/archive/tasks/2026-03-19-ai4j-coding-foundation.md`

## 1. 目标

将 `ai4j-coding` 当前偏分散的本地工具能力收敛为更符合 coding-agent 实战的最小工具面：

- 对模型主暴露单一命令工具 `bash`
- `bash` 同时支持一次性执行与后台进程管理
- 进程能力必须绑定到 `CodingSession`，避免跨会话污染
- 默认 built-in tools 继续保持精简，避免无谓 token 消耗

## 2. 预期交付

1. `bash` 工具支持：
   - `exec`
   - `start`
   - `status`
   - `logs`
   - `write`
   - `stop`
   - `list`
2. `CodingSession` 具备 session 级进程注册与清理能力
3. 默认 built-in tools 收敛为最小集合
4. 补充后台进程与会话清理测试

## 3. 设计约束

- 对外 tool name 固定叫 `bash`
- 对内不绑死 bash 二进制，Windows / Unix 走各自 shell
- 不为了“功能多”继续堆更多默认 tool
- 背景进程必须可追踪、可查询、可停止

## 4. 详细任务拆解

| 编号 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| T1 | 创建当前功能任务文档 | COMPLETED | 防止实现漂移 |
| T2 | 建立 session 级 process registry | COMPLETED | 已支持后台进程注册、状态、日志、stdin、停止 |
| T3 | 实现统一 `bash` tool executor | COMPLETED | 已支持 `exec/start/status/logs/write/stop/list` |
| T4 | 收敛默认 built-in tools | COMPLETED | 默认内置工具已收敛为 `bash/read_file` |
| T5 | 将 session 级 bash runtime 接入 `CodingSession` | COMPLETED | 已按 session 注入独立 process runtime |
| T6 | 补测试与编译验证 | COMPLETED | `mvn -q -pl ai4j-coding -am -DskipTests=false test` 通过 |
| T7 | 更新总计划文档并归档任务文档 | COMPLETED | 状态已同步，任务文档可归档 |

## 5. 参考资料

- Pi 文档：`https://openclawlab.com/en/docs/pi/`
- OpenClaw Exec 文档：`https://openclawlab.com/en/docs/tools/exec/`
- OpenClaw deepwiki 总结：`https://deepwiki.com/openclaw/openclaw/3.4.1-exec-tool-and-exec-approvals`

## 6. 变更记录

- 2026-03-19：创建任务文档，目标锁定为“单一 bash 工具 + session 级 process runtime”。
- 2026-03-19：完成 `bash` tool 与 `CodingSession` 级 process registry 接入，默认 built-in tools 收敛为 `bash/read_file`。

## 7. 已完成

- `ai4j-coding` foundation 已建立 workspace / session / shell 基础骨架

## 8. 未完成

- `apply_patch` 尚未进入最小工具集
- `bash` 的 PTY / 更完整交互式终端能力尚未开始
- CLI/TUI 对后台进程的展示与控制尚未开始

## 9. 归档规则

- 本文档在当前功能完成前保留在 `docs/tasks/`
- 当 `T1-T7` 完成后，移动到 `docs/archive/tasks/`
