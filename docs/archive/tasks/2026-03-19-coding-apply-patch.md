# 2026-03-19 coding-apply-patch

- 状态：COMPLETED
- 所属阶段：Phase 6 / 建立 `ai4j-coding`
- 对应范围：`ai4j-coding` 最小工具集补齐 `apply_patch`
- 关联文档：
  - `docs/plans/2026-03-19-ai4j-2.0-constitution.md`
  - `docs/plans/2026-03-19-ai4j-2.0-implementation-plan.md`
  - `docs/archive/tasks/2026-03-19-ai4j-coding-foundation.md`
  - `docs/archive/tasks/2026-03-19-coding-bash-process-runtime.md`

## 1. 目标

在 `bash + read_file` 的极简工具面基础上，补齐 coding-agent 必需的结构化写能力：

- 对模型暴露 `apply_patch`
- 以 patch 而不是普通 edit/write 作为主要文件修改方式
- 严格受 workspace 边界约束
- 优先覆盖新增、更新、删除三种核心操作

## 2. 预期交付

1. 新增 `apply_patch` tool
2. 支持 patch 操作：
   - `*** Add File`
   - `*** Update File`
   - `*** Delete File`
3. 支持基于上下文/增删行的文件更新
4. 返回结构化 patch 应用结果
5. 增加针对 add/update/delete/越界路径 的测试

## 3. 设计约束

- `apply_patch` 是 patch tool，不退化为简单 edit tool
- workspace 外路径必须拒绝
- 不依赖外部 `patch` 命令，避免平台差异
- 首版先不做 move/rename，先把 add/update/delete 做稳

## 4. 详细任务拆解

| 编号 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| T1 | 创建当前功能任务文档 | COMPLETED | 防止实现漂移 |
| T2 | 设计 patch 文本语法与结果对象 | COMPLETED | 首版语法已锁定为 add/update/delete 三种操作 |
| T3 | 实现 patch 解析与应用器 | COMPLETED | 已支持 add/update/delete 与 workspace 边界校验 |
| T4 | 将 `apply_patch` 接入 built-in tools | COMPLETED | 默认 built-in tools 已成为 `bash/read_file/apply_patch` |
| T5 | 补测试与编译验证 | COMPLETED | `mvn -q -pl ai4j-coding -am -DskipTests=false test` 通过 |
| T6 | 更新总计划文档并归档任务文档 | COMPLETED | 状态已同步，可归档 |

## 5. patch 语法首版

```text
*** Begin Patch
*** Add File: path/to/file
+line 1
+line 2
*** Update File: path/to/file
@@
 context
-old line
+new line
*** Delete File: path/to/file
*** End Patch
```

## 6. 参考资料

- Codex 风格 patch 语法
- Claude Code / OpenCode 常见 patch 编辑模式

## 7. 变更记录

- 2026-03-19：创建任务文档，目标锁定为“内建 apply_patch 结构化写能力”。
- 2026-03-19：完成 `apply_patch` tool，支持 add/update/delete，并接入默认最小工具集。

## 8. 已完成

- `bash` 已成为主命令工具
- `read_file` 已成为默认只读工具
- session 级后台进程管理已打通

## 9. 未完成

- `move/rename` 尚未进入首版 patch 语法
- 更复杂的 patch 匹配与冲突恢复策略尚未开始
- CLI/TUI 对 patch diff 的展示尚未开始

## 10. 归档规则

- 本文档在当前功能完成前保留在 `docs/tasks/`
- 当 `T1-T6` 完成后，移动到 `docs/archive/tasks/`
