# 2026-03-19 ai4j-coding-foundation

- 状态：COMPLETED
- 所属阶段：Phase 6 / 建立 `ai4j-coding`
- 对应范围：`ai4j-coding` 首个可用基础骨架
- 关联文档：
  - `docs/plans/2026-03-19-ai4j-2.0-constitution.md`
  - `docs/plans/2026-03-19-ai4j-2.0-implementation-plan.md`

## 1. 目标

为 `ai4j-coding` 建立第一批真正面向 Coding Agent 的产品层基础设施，明确它不是 `CodeAct` 的重复实现，而是：

- 面向工作区的会话层
- 面向本地编码场景的内置工具层
- 面向后续 CLI / TUI / approval / sandbox / policy 的承载层

## 2. 预期交付

1. `ai4j-coding` 使用独立包路径 `io.github.lnyocly.ai4j.coding`
2. 建立 coding agent 的基础对象：
   - `CodingAgent`
   - `CodingAgentBuilder`
   - `CodingSession`
   - `CodingAgentRequest`
   - `CodingAgentResult`
   - `CodingAgentOptions`
3. 建立工作区与本地执行抽象：
   - `WorkspaceContext`
   - `WorkspaceFileService`
   - `ShellCommandExecutor`
4. 建立第一批内置 coding tools：
   - `list_files`
   - `read_file`
   - `write_file`
   - `run_command`
5. 建立至少一组可运行验证：
   - 工具执行验证
   - 基础 agent loop 集成验证

## 3. 设计约束

- 不把 `CodeAct` 再搬进 `ai4j-coding`
- 不在第一步就引入复杂 sandbox / approval 编排
- 先把“工作区 + 会话 + 本地工具 + 通用 Agent Runtime 复用”打通
- 保持对用户统一，底层继续复用现有 `AgentBuilder` / `AgentSession`

## 4. 详细任务拆解

| 编号 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| T1 | 建立当前功能文档并冻结首批范围 | COMPLETED | 防止实现漂移 |
| T2 | 设计 `ai4j-coding` 首批包结构与对象边界 | COMPLETED | 已切到 `io.github.lnyocly.ai4j.coding` 独立包路径 |
| T3 | 实现工作区文件服务与命令执行器 | COMPLETED | 已提供 workspace + shell 本地执行抽象 |
| T4 | 实现内置 coding tool registry / executor | COMPLETED | 已内置 `list_files/read_file/write_file/run_command` |
| T5 | 实现 `CodingAgentBuilder` 与 `CodingSession` | COMPLETED | 已形成工作区型 coding agent 入口 |
| T6 | 补充测试并完成编译验证 | COMPLETED | `mvn -q -pl ai4j-coding -am -DskipTests=false test` 通过 |
| T7 | 更新总计划文档状态 | COMPLETED | 已同步 2.0 实施计划中的阶段状态 |

## 5. 参考资料

- Pi 文档：`https://openclawlab.com/en/docs/pi/`
- Pi coding-agent 代码结构：`https://github.com/badlogic/pi-mono/tree/main/packages/coding-agent`
- OpenCode 仓库：`https://github.com/sst/opencode`
- OpenAI Codex CLI 仓库：`https://github.com/openai/codex`
- Claude Code 文档：`https://docs.anthropic.com/`

## 6. 变更记录

- 2026-03-19：创建任务文档，首批目标锁定为“工作区型 coding agent 基础骨架”。
- 2026-03-19：完成 `ai4j-coding` 首批可用骨架，包含 session、workspace、shell、built-in tools 与测试验证。

## 7. 已完成

- 已完成 `ai4j 2.0` 总纲与实施计划的第一轮文档冻结
- 已完成 `ai4j-core` / `ai4j-model` / `ai4j-agent` 的基础拆分

## 8. 未完成

- patch / git / search / grep / approval / sandbox / policy 仍未进入首版
- CLI / TUI 会话层仍未开始
- 更高级的 coding harness、计划器、检查点恢复仍未开始

## 9. 归档规则

- 本文档在当前功能完成前保持在 `docs/tasks/`
- 当 `T1-T7` 全部完成后，移动到 `docs/archive/tasks/`
- 若中途拆出子功能，则继续新增 `当前日期 + 功能.md`，并在本文档中追加引用
