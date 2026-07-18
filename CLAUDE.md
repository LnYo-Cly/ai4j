# 中文项目入口（Claude Code）

本项目以 `AGENTS.md` 作为 coding agent 指令的唯一权威入口。

Claude Code 开始任何任务前应当：

1. 先读取 `AGENTS.md`，再运行 `ha doctor --json` 和 `ha status --json`。
2. 按 `AGENTS.md` 的任务阅读矩阵选择当前任务需要的文件。
3. 使用 Harness Anything 管理新任务、progress、fact、decision、review 和 complete。
4. 把本文件视为 Claude Code 兼容入口，而不是第二份项目规范。

`harness/` 是私有 HA ledger，`.harness/` 是可重建 projection/cache；二者与代码 PR 分离。

不要在这里复制项目规则。长期有效的项目指令应维护在 `AGENTS.md` 和 `docs/11-REFERENCE/harness-anything-standard.md` 中，避免两套规则逐渐漂移。
