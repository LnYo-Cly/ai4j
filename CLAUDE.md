# 中文项目入口（Claude Code）

本项目以 `AGENTS.md` 作为 coding agent 指令的唯一权威入口。

Claude Code 开始任何任务前应当：

1. 先读取 `AGENTS.md`。
2. 按 `AGENTS.md` 的任务阅读矩阵选择当前任务需要的文件。
3. 只加载必要的 `coding-agent-harness/governance/standards/` 标准文件和任务目录上下文。
4. 把本文件视为 Claude Code 兼容入口，而不是第二份项目规范。

不要在这里复制项目规则。长期有效的项目指令应维护在 `AGENTS.md` 及其引用文档中，避免两套规则逐渐漂移。
