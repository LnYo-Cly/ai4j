# Skills 总览

`Skill` 主归属在 `Core SDK`，不是 `Coding Agent` 才突然出现的概念。

## 1. Skill 是什么

可以先把它理解成一类可被模型发现、按需读取、重复复用的 prompt asset。

最常见的形态就是：

- `SKILL.md`

它解决的不是“执行一个函数”，而是“给模型一份遇到这类任务时应该怎么做的结构化说明”。

## 2. Skill 不是什么

- 不是本地函数工具
- 不是外部协议层
- 不是 runtime 本身

所以它和另外两个常见能力边界必须分开：

- `Function Call`：让模型真的去做
- `Skill`：告诉模型这类任务应该怎么做
- `MCP`：让模型通过协议接外部能力

## 3. 为什么它属于基座

因为在最基础的 `IChatService` / `IResponsesService` 场景里，你就已经可以：

- 发现 skill
- 把 skill 摘要加入 prompt
- 在需要时再读取 `SKILL.md`

`Coding Agent` 只是把这套能力做成了更完整的产品化入口，而不是重新发明了 `Skill`。

## 4. 推荐阅读顺序

1. [Discovery and Loading](/docs/core-sdk/skills/discovery-and-loading)
2. [Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
3. [Coding Agent / Skills](/docs/coding-agent/skills)
