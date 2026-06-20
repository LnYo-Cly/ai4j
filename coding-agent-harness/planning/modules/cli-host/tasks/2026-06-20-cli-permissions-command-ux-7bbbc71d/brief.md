# CLI permissions command UX - Brief

## 任务目标

为 `ai4j-cli` 增加只读 `/permissions` slash command，让用户在 coding-agent CLI/TUI/ACP 中一眼确认当前 approval / permission 边界。

## 为什么做

当前 `origin/dev` 已有 `--approval <auto|safe|manual>`、CLI/TUI approval prompt、ACP `session/request_permission` 和 `ai4j-agent` permission policy，但 CLI 内缺少一等诊断入口。用户需要知道“当前工具调用会不会问我、sandbox 是否改变审批、ACP 怎么处理 permission request”。

## 范围

- 新增 `/permissions` 与 `/permissions status`；
- 同步 SlashCommandController、CodingCliSessionRunner、ACP slash command support、help/palette、docs-site；
- 添加 deterministic tests；
- 不做运行时权限编辑器，不打印 raw tool input / prompt / secret。

## 验收摘要

- [x] `/permissions` 在 CLI/TUI root、补全、help、ACP command list 中可见；
- [x] 输出只包含 approval mode 和边界说明；
- [x] docs-site 解释 `/permissions` 与 `/status`、ACP permission request、sandbox 的关系；
- [x] targeted CLI tests 通过；
- [x] broad CLI tests 通过；
- [x] docs-site build 通过；
- [ ] final `git diff --check` 与 Harness status 通过后提交/PR。

## 当前下一步

完成 final static checks，提交 feature diff，然后执行 Harness `task-review` 并创建 PR 到 `dev`。
