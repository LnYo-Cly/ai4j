---
sidebar_position: 7
---

# Skills 使用与组织

`Skill` 不是一段可执行代码，而是一份可被模型读取和复用的说明、模板或工作流。

从产品心智上看，`Skill` 和 `Tool`、`MCP` 一样，都是 AI 系统里的常见能力层。

区别在于：

- `Tool`：给模型一个可执行能力
- `MCP`：给模型一套外部工具接入协议
- `Skill`：给模型一份“遇到这类任务该怎么做”的可读说明

当前 AI4J 里，`Skill` 的一等公民接入主要落在 `Coding Agent`。

它的作用是：让 Coding Agent 在匹配到任务时，优先复用已有方法，而不是每次从零推断。

---

## 1. Skill 如何被发现

当前会扫描三类根目录：

1. `<workspace>/.ai4j/skills`
2. `~/.ai4j/skills`
3. `workspace.json` 里的 `skillDirectories`

对于 `skillDirectories`：

- 绝对路径会直接使用；
- 相对路径会按 workspace 根目录解析。

---

## 2. Skill 文件结构

当前支持的核心约定是 `SKILL.md` 或 `skill.md`。

最小示例：

```md
---
name: sql-review
description: Review SQL changes with indexing and execution-plan concerns.
---

# SQL Review

Use this skill when the task involves SQL schema or query review.
```

发现时会提取：

- `name`
- `description`
- skill 文件路径
- 来源（`workspace` / `global`）

---

## 3. 在会话里怎么用

### 3.1 查看全部技能

```text
/skills
```

### 3.2 查看单个技能

```text
/skills <name>
```

当前会显示：

- name
- source
- path
- description
- roots

不会直接把整个 `SKILL.md` 正文回显到终端。

---

## 4. 模型如何使用 Skill

Coding Agent 不会在一开始把所有 Skill 正文全部塞进提示词。

当前策略更接近：

- 先把技能目录扫描成摘要清单；
- 当任务明显匹配某个 Skill 时，再读取对应 `SKILL.md`；
- 鼓励模型只读最相关的 skill，而不是全量加载。

这样做的好处是：

- 减少上下文污染；
- 避免技能越多，首轮 prompt 越臃肿；
- 让 Skill 更接近“按需读取的知识包”。

从实现上看，这条链路对应的是：

- `CodingSkillDiscovery`
- `CodingSkillDescriptor`
- `WorkspaceContext.availableSkills`
- `CodingContextPromptAssembler`

---

## 5. 如何组织 Skill

推荐做法：

- 把团队共享 skill 放进独立目录，通过 `skillDirectories` 挂载；
- 把仓库私有 skill 放进 `<workspace>/.ai4j/skills`；
- 保持一个 skill 只解决一类明确任务；
- 在 front matter 里写清楚 `name` 和 `description`。

不推荐：

- 一个 skill 覆盖太多主题；
- skill 名称模糊；
- 把大量无关背景材料直接塞进 skill。

---

## 6. Skill 与 Tool 的区别

- Skill：告诉模型“应该怎么做”
- Tool：让模型“真的去做”

比如：

- `java-springboot` 这种更像 Skill；
- `read_file` / `bash` 这种是 Tool。

实际使用中，经常是：

1. 模型先匹配 Skill；
2. 再用 Tool 落地执行。

---

## 7. Skill 为什么是通用能力，而不只是 Coding Agent 功能

虽然当前文档入口放在 `Coding Agent`，但 `Skill` 的概念本身更通用。

它适合任何需要“把团队经验沉淀成可复用说明”的系统，例如：

- 代码审查规范
- SQL 优化规范
- MCP server 开发规范
- 前端设计规范

现在 AI4J 优先在 `Coding Agent` 里把这套能力产品化，是因为它最直接依赖：

- workspace
- 文件读取权限
- 会话级 prompt 组装

后续如果你做自己的 Agent 平台，也可以沿用同样思路，把 `Skill` 作为按需读取的知识包，而不是 prompt 大杂烩。

---

## 8. 继续阅读

1. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
2. [MCP 对接](/docs/coding-agent/mcp-integration)
3. [Prompt 组装与上下文来源](/docs/coding-agent/prompt-assembly)
4. [Memory 记忆管理与压缩策略](/docs/agent/memory-management)
