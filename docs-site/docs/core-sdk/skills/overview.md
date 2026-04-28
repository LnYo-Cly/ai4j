# Skills 总览

`Skill` 主归属在 `Core SDK`，不是 `Coding Agent` 才突然出现的概念。

## 1. 先确定它在整套体系里的位置

`Skill` 属于基座层的说明资产能力。

也就是说，它先在 `ai4j/` 这一层成立，然后才在 `Coding Agent` 里被进一步做成更完整的发现、加载和产品化体验。

## 2. Skill 是什么

可以先把它理解成一类可被模型发现、按需读取、重复复用的 prompt asset。

最常见的形态就是：

- `SKILL.md`

它解决的不是“执行一个函数”，而是“给模型一份遇到这类任务时应该怎么做的结构化说明”。

## 3. Skill 不是什么

- 不是本地函数工具
- 不是外部协议层
- 不是 runtime 本身

所以它和另外两个常见能力边界必须分开：

- `Function Call`：让模型真的去做
- `Skill`：告诉模型这类任务应该怎么做
- `MCP`：让模型通过协议接外部能力

## 4. 为什么它属于基座

因为在最基础的 `IChatService` / `IResponsesService` 场景里，你就已经可以：

- 发现 skill
- 把 skill 摘要加入 prompt
- 在需要时再读取 `SKILL.md`

`Coding Agent` 只是把这套能力做成了更完整的产品化入口，而不是重新发明了 `Skill`。

## 5. 你应该在什么场景想到 Skill

更适合 Skill 的场景通常是：

- 重复出现的任务方法论
- 需要模型按需读取的流程说明
- 不想把所有说明都硬塞进 system prompt
- 希望说明资产能被发现、复用、维护

这也是为什么它特别适合：

- 编码方法论
- 评审规范
- 写作/分析模板
- 分步骤执行指南

## 6. 推荐阅读顺序

1. [Discovery and Loading](/docs/core-sdk/skills/discovery-and-loading)
2. [Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
3. [Coding Agent / Skills](/docs/coding-agent/skills)

如果你现在的困惑是“Skill 为什么不是 Tool”，建议先连读本章和 `Skill vs Tool vs MCP`，不要直接跳到更深的产品页。

## 7. 关键对象

`Skill` 的基座实现主线，至少要先认识三个对象：

- `skill/Skills.java`：skill 的发现、筛选和读取入口
- `skill/SkillDescriptor.java`：skill 元信息的结构化描述
- `SKILL.md`：面向模型暴露的方法论与执行说明载体

这三个层次分别解决“怎么找到”“怎么描述”“怎么被模型读取”。

## 8. 这一层的设计重点

- `Skill` 是说明资产，不承担函数执行
- `Skill` 先属于 `Core SDK`，上层产品只是在此基础上做更完整的发现与交互体验
- `Skill` 的价值在于按需加载，而不是把所有 SOP 永久塞进系统 prompt

如果这一点没有讲清楚，后面很容易把 `Skill` 写成一堆无法治理的超长提示词，或者误拿它承担权限、连接、执行这些本不属于它的职责。

## 9. 和上层产品化能力的关系

`Coding Agent` 对 `Skill` 做的是产品化封装，而不是概念改写。

也就是说：

- `Core SDK` 负责 skill 的发现、描述和读取机制
- 上层 runtime 负责何时暴露、如何交互、怎样和任务流结合

这个关系讲清楚后，就不会再把 `Skill` 误解成某个产品层私有能力。
