---
title: "Skill 激活"
description: 讲清 Skill 正文进入上下文的三条路径（模型自选 read_file、宿主 selectedSkills、宿主 providedContents）、受限执行器的安全模型，以及为什么 AI4J 不提供专用 activate_skill 工具。
tags: [concept]
---

# Skill 激活

[Skill 发现](/docs/capabilities/skills/discovery) 讲的是「SKILL.md 怎么被发现、目录怎么生成」。
这一页讲的是更靠后的那一步：**目录生成之后，skill 正文到底怎么进入模型上下文**。

这件事比看上去复杂，因为正文不是一开始就全塞进去的——它有三条路径，每条路径的安全边界和上下文成本都不同。

## 1. 先把一个常见误解拿掉

很多人第一反应是：「应该提供一个 `activate_skill(name)` 工具，让模型按名加载 skill 正文，而不是靠通用文件读取。」

AI4J **刻意不提供**这个工具。这不是遗漏，是对齐了 [Agent Skills 官方规范](https://agentskills.io/) 的设计决策。规范把激活方式明确列为两种并列合法模式：

- **文件读取激活**：模型用标准的文件读取工具，按目录里的路径读 `SKILL.md`。这是 AI4J 采用的模式。
- **专用工具激活**：注册一个 `activate_skill` 工具，按名返回正文。规范说它「在模型无法直接读文件时**必需**，能读文件时**可选**」。

AI4J 选择前者，原因在最后一节展开。先看它实际怎么做。

## 2. 路径一：模型自选（默认）

这是最常用的路径，完全自动：

```text
Skills.discoverDefault(workspace)
  → 生成 <available_skills> 目录（只有 name / description / location）
  → 目录拼进 system prompt
  → 模型匹配任务后，调 read_file 读某个 SKILL.md 的正文
```

目录里每个条目长这样：

```xml
<available_skills>
  <skill>
    <name>code-review</name>
    <description>How to do a thorough code review</description>
    <location>/path/to/.ai4j/skills/code-review/SKILL.md</location>
  </skill>
</available_skills>
```

目录只给模型「有哪些 skill、各自干什么」，**正文不进 prompt**。模型判断匹配后才发起读取。

:::note 为什么复用 read_file，而不是专用工具
目录里明确写着：「reuse `read_file` instead of asking for a dedicated skill tool」。
复用既有工具意味着零新增工具槽位（不占 schema、不增加模型选择负担），而安全边界靠下一节讲的受限执行器收紧——拿到了专用工具的隔离性，没付出多一个工具的成本。
:::

## 3. 受限执行器：read_file 在 skill 语境下被收紧

模型自选路径下，`read_file` 不是裸的文件读取。在 Agent runtime 里它被 `SkillReadFileToolExecutor`（`ai4j-agent` 模块）接管：

- **只接受 `read_file` 这一个工具名**，其它工具一律拒绝
- 跑在**每次运行独立的 `BuiltInToolContext` 里**，只读根限定为 skill 目录（`Skills.createSkillToolContext(skillRoots)`）
- 支持 `startLine` / `endLine` / `maxChars` 分段读，长 skill 不会一次性撑爆上下文

也就是说，模型名义上调的是通用 `read_file`，实际执行被限制在 skill 只读根内。这是 AI4J 比「裸 read_file」更紧的地方。

:::tip 工具名冲突时自动别名
如果宿主已经占用了 `read_file` 这个名字，runtime 会把 skill 读取器别名成 `read_skill_file`，目录里也会自动引用正确的名字，宿主的原工具不受影响。
:::

## 4. 路径二：宿主点名（`selectedSkills`）

有时不该让模型自己选——比如用户在 UI 里明确点了一个 skill，或业务逻辑固定要走某个流程。

`AgentRequest.selectedSkills` 让宿主**直接点名**，正文被直接读出来注入上下文，**模型完全不需要发起工具调用**：

```java
agent.run(AgentRequest.builder()
        .input("走发票退款流程")
        .selectedSkills(Collections.singletonList("invoice-refund"))
        .build());
```

两个关键细节：

- **manual-only skill 只能走这条路径**。`SKILL.md` 里标 `disable-model-invocation: true` 的 skill 不会出现在模型目录里（模型看不到、无法自选），但宿主可以点名。这是「宿主强制启用某个流程」的正门。
- **正文走 memory items，不进 system prompt**。点名的 skill 正文通过 memory overlay 注入，**不动 system prompt 前缀**——这样目录稳定时 system prompt 可被 prompt cache 复用，正文变动只影响 items。

## 5. 路径三：宿主直接注入正文（`providedContents`）

skill 不一定来自磁盘。`AgentSkillRuntimeSupport` 支持 `providedContents`——宿主直接把正文塞进去，连文件都不读：

- skill 来自 classpath / JAR / 数据库 / 远程服务时，这条路径是唯一可行方案
- 模型仍通过 `read_file(path=...)` 读取，但 executor 发现 path 在 `providedContents` 里，直接返回注入的正文，**不碰磁盘**

这条路径目前用 skill 文件路径当 key 来寻址——对 classpath/JAR 来源有语义错位，是一个已知待改进点。

## 6. 三条路径的对比

| 路径 | 谁决定 | 正文怎么进上下文 | 适合 |
| --- | --- | --- | --- |
| 模型自选 | 模型 | 目录进 system prompt → 模型调受限 `read_file` | 通用情况；skill 多、按任务匹配 |
| 宿主点名 | 宿主代码 | `selectedSkills` → 正文走 memory items | 用户 UI 选定；manual-only skill |
| 宿主注入 | 宿主代码 | `providedContents` → executor 直接返回 | classpath/JAR/DB 来源；无文件系统 |

三条路径的执行器都是受限的，skill 只读根统一由 `createSkillToolContext` 收口。

## 7. 多租户隔离

`AgentSkillResolver` 决定每次 run 扫哪些根。自定义 resolver 默认只用**显式根**——注释里写明：「这样租户策略不会意外暴露服务端全局 Skill」。

也就是说，服务端部署时，每个租户/请求只能看到 resolver 给它的根，全局 `~/.ai4j/skills` 不会自动泄漏。这是把 skill 发现也纳入了租户隔离，而不是默认全局可见。

## 8. 为什么不做专用 `activate_skill` 工具

这是文档里被问最多的设计决策。完整理由：

1. **它替代不了通用读取**。skill 目录里除 `SKILL.md` 还有 `references/*.md`、`scripts/`、`assets/`（规范称为 tier-3）。所有参考实现（Spring AI、Claude SDK、LangChain4j）里，tier-3 仍走文件读取。专用工具是**净增一条并行路径**，不是替换。
2. **安全收益约等于零**。Anthropic 官方原话：skill 工具是「context filter, not a sandbox」——只要 `Read`/`Bash` 还在，文件照样读得到。而 AI4J 已有 root 白名单 + symlink 拒绝 + realpath 校验，实际比 Claude Code 默认组合更紧。
3. **cache 友好度更差**。专用工具的 description 是运行时动态生成的（聚合所有 skill 的 name+description），skill 增删即改 tool schema，破坏 prompt cache；而目录在 system prompt 前缀里、排序稳定，cache 更友好。
4. **它的唯一不可替代价值**是：runtime 完全不给文件系统工具时（`supportsSkillReadFile=false`），模型仍能自选 skill。那条分支目前靠宿主 `selectedSkills` 兜底。**如果该分支出现真实用户，再做**；否则按"不做没场景的接口"搁置。

:::note 两个真实缺口（比加 skill tool 值钱）
- **正文没有防压缩标记**：skill 正文被上下文压缩时，compaction 无法把它和普通文件读取区分开。规范警告这会"静默降低 agent 性能，无任何可见错误"。改进点：在 `SkillReadFileToolExecutor` 返回里加 `skill: true` 标记或 `<skill_instructions>` 包裹。
- **虚拟 skill 寻址**：`providedContents` 用假文件路径当 key，对 classpath/JAR 来源有语义错位（见路径三）。
:::

## 9. 继续阅读

- [Agent / Skills](/docs/agent/skills) — Agent runtime 侧的 skill 接线（resolver、selectedSkills、工具别名）
- [Tool Exposure Semantics](/docs/capabilities/mcp/tool-exposure-semantics) — 受限执行器和工具白名单的关系
- [Skills 总览](/docs/capabilities/skills/overview) — Skill 在架构里的定位
