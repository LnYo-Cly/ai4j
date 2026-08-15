---
title: "Discovery and Loading"
description: "详解 Skills 发现与加载：扫描工作区与全局根目录、识别 SKILL.md、提取名称描述、按名去重，以及 allowedReadRoots 如何把 skill 目录联动进宿主只读安全边界。"
tags: [concept]
---

# Discovery and Loading

`Skill` 在 AI4J 里不是可执行工具，而是按需读取的方法论资源。

因此这页真正要讲清楚的，不是“怎么调用 skill”，而是：

- 它从哪里被发现
- 如何生成技能目录
- 为什么不应该一开始读取全部正文
- 这些技能文件怎样进入安全读取边界

:::tip 本页代码都是可跑通的
下面的发现示例来自
[`SkillsDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/SkillsDocExamplesTest.java)，
它建一个临时工作区放 SKILL.md，跑通完整发现链。无需密钥、在普通 CI 里跑。
:::

## 1. 主入口就在 `Skills.java`

一次完整发现：

```java
// 工作区下放：<workspace>/.ai4j/skills/code-review/SKILL.md
Skills.DiscoveryResult result = Skills.discoverDefault(workspaceRoot);

List<SkillDescriptor> skills = result.getSkills();          // 发现到的技能
List<String> readRoots = result.getAllowedReadRoots();       // 只读根（联动进安全边界）
```

每个 `SkillDescriptor` 带 `name` / `description` / `skillFilePath` / `source` / `disableModelInvocation`。

这套机制几乎都集中在：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/skill/Skills.java`

最关键的方法有：

- `discoverDefault(...)`
- `discover(...)`
- `buildAvailableSkillsPrompt(...)`
- `createToolContext(...)`

配套数据对象是：

- `SkillDescriptor`
- `Skills.DiscoveryResult`

## 2. 默认会扫描哪些根目录

`discoverDefault(...)` 内部会先调用 `resolveSkillRoots(...)`，当前默认候选根有 4 个（分两组，每组同时支持 `.ai4j` 和 `.agents` 命名空间），加上额外的挂载目录：

1. `<workspace>/.ai4j/skills`
2. `<workspace>/.agents/skills`
3. `~/.ai4j/skills`
4. `~/.agents/skills`
5. 额外挂载的 `skillDirectories`（相对路径按 workspace root 解析）

也就是说，工作区和用户主目录下、`.ai4j/skills` 与 `.agents/skills` 两套约定都会被扫到。`.agents/skills` 让 AI4J 与其它遵循 `.agents` 约定的工具（如 Claude / Agents.md 生态）共享同一份 skill 目录，不必复制。

这里有两个重要点：

### 工作区 skill 和全局 skill 可以同时存在

这让你可以同时拥有：

- 仓库专属 skill（`<workspace>/.ai4j/skills` 或 `<workspace>/.agents/skills`）
- 用户级跨项目通用 skill（`~/.ai4j/skills` 或 `~/.agents/skills`）

### 相对路径会按 workspace root 解析

如果传入的额外挂载目录不是绝对路径，它会相对当前 workspace root 解析，而不是相对用户 home 或 JVM 启动目录。

## 3. root 是怎样被识别成 skill 的

`Skills` 不是看到一个目录就把它整个当 skill。

当前识别顺序是：

1. 先检查该目录自身是否包含：
   - `SKILL.md`
   - `skill.md`
2. 如果有，就把这个目录识别为一个 skill
3. 如果没有，再递归扫描其子目录
4. 任意子目录只要含有 `SKILL.md` 或 `skill.md`，就被当成一个 skill，并停止继续扫描该 skill 目录的内部

这意味着当前支持两种组织方式：

- 单 skill root
- skill 集合 root

## 4. 支持嵌套目录，skill 目录本身是叶子

`discoverFromRoot(...)` 会递归扫描 skill root，因此可以按团队、领域或业务线组织目录。

- root 本身可以是一个 skill
- root 下任意深度的目录都可以成为一个 skill
- 一旦某个目录含有 `SKILL.md` 或 `skill.md`，该目录会作为一个完整 skill，内部不再继续发现子 skill
- 符号链接 root、目录和 `SKILL.md` 文件会跳过，避免 discovery 扩展到未声明的读取边界

发现结果按规范化路径稳定排序；同名 skill 仍按 root 优先级先到先得。

## 5. 一个 skill 的名字和描述是怎么来的

`buildDescriptor(...)` 当前的提取顺序非常明确。

### 名称提取优先级

1. front matter 里的 `name`
2. 第一个 markdown heading
3. skill 目录名

### 描述提取优先级

1. front matter 里的 `description`
2. 正文里的第一段非标题段落
3. 默认文案 `No description available.`

这意味着即使你的 `SKILL.md` 写得很简单，只要：

- 顶部 front matter 比较规范
- 或者 heading / 第一段比较清楚

AI4J 仍然能构造出可用的技能目录。

## 6. `SkillDescriptor` 里到底保存什么

当前 `SkillDescriptor` 很轻，保存：

- `name`
- `description`
- `skillFilePath`
- `source`
- `disableModelInvocation`
- `content` —— 可选的宿主提供的 `SKILL.md` 正文

其中 `source` 由 `resolveSource(...)` 判断：

- skill root 位于 workspace 内 -> `workspace`
- 否则 -> `global`

这个字段很实用，因为它能告诉你某个 skill 是：

- 当前项目真相的一部分
- 还是用户级共享能力

### `content`：内存型 / 宿主提供的 skill

`content` 为可选字段，用于支持两类磁盘之外的 skill：

- **内存型 skill**：宿主在运行时构造的 skill，没有落盘文件。此时 `content` 直接持有 `SKILL.md` 正文。
- **远程 / 受限源 skill**：skill 正文来自远端或受控源。此时 `skillFilePath` 是一个稳定的虚拟位置（由宿主的 scoped skill reader 解析，而不是本地文件系统），正文由 `content` 提供。

`content` 缺省为 `null`；此时 skill 走常规的磁盘发现路径，`skillFilePath` 指向真实文件。提供 `content` 时宿主需自行保证正文可被按需读取——`Skills.appendAvailableSkillsPrompt(...)` 只负责把 `name`/`description`/`location` 投影进模型目录，`content` 本身不会拼进 prompt。

## 7. 去重策略是什么

`discover(...)` 内部会按技能名做去重，去重 key 是：

- `name.trim().toLowerCase(Locale.ROOT)`

并且是“先到先得”：

- 前面 root 里先发现的 skill 会保留
- 后面同名 skill 会被忽略

这带来一个很实际的结论：

- skill 名称在当前体系里本质上是全局 key

:::warning
因此不要随意让 workspace 和 global skills 出现同名但不同语义的条目。
:::

`disable-model-invocation: true` 会保留在 discovery 结果中，供宿主显式选择，但不会进入模型自动选择的 `<available_skills>` 目录。它不改变 Tool 或 MCP 的权限。

## 8. 为什么 AI4J 不会直接读取全部 `SKILL.md`

`buildAvailableSkillsPrompt(...)` 的生成文本里，明确告诉模型：

- 先看到可用技能目录
- 不要预先读取全部 skill
- 只有任务明显匹配时，再用 `read_file` 去读对应 `SKILL.md`

这是这套设计最关键的原则之一。

如果你一上来就把全部 skill 正文拼进 prompt，那么：

- skill discovery 退化成“大 prompt 拼接器”
- 懒加载价值消失
- 上下文污染会迅速变严重

所以当前 skill 机制的本质，其实就是：

- 方法论文档的目录暴露
- 正文的延迟加载

## 9. `buildAvailableSkillsPrompt(...)` 实际生成什么

这个方法不会返回 skill 正文，而是生成一段目录提示，大致包含：

- name
- path
- description

并用 `<available_skills>` 包住。

同时还会明确告诉模型：

- 不要先读所有 skill
- 只有匹配时再读取 `SKILL.md`
- 使用最小相关 skill 集

这就是当前 AI4J skill 体系的 prompt 契约。

## 10. `allowedReadRoots` 为什么是这套机制的关键

`DiscoveryResult` 不只返回 skills，还会返回：

- `allowedReadRoots`

随后 `Skills.createToolContext(...)` 会把它们写入：

- `BuiltInToolContext.allowedReadRoots`

这意味着 skill discovery 和宿主工具安全是联动的：

- 模型知道有哪些 skill
- `read_file` 也知道哪些 skill 根目录允许只读访问

因此 skill 不是“纯提示词特性”，它和宿主读取边界是一起设计的。

## 11. `createToolContext(...)` 真正做了什么

这个方法会构造：

- `workspaceRoot`
- `allowedReadRoots`

对应的含义是：

- 正常工作区路径仍由 workspace root 约束
- skill roots 额外作为只读目录放开

这正是为什么模型可以读取工作区外的全局 skill 文件，但默认不能随便写这些目录。

## 11.1 `createSkillToolContext(...)`：把 skill 读权限收紧到只读根

`Skills.createToolContext(...)` 把 workspace root 和 skill roots 同时开放给 `read_file`——模型既能读工作区，也能读 skill。但某些场景（例如宿主只想暴露一个 scoped skill reader，完全不让模型碰当前工作区）需要更严格的边界。`Skills.createSkillToolContext(skillRoots)` 正是为这个场景设计的：

- 只把传入的 `skillRoots` 写进 `allowedReadRoots`；
- 把 `BuiltInToolContext.restrictReadToAllowedRoots` 置为 `true`；
- `workspaceRoot` 被指到一个无关的占位目录（没有 skill roots 时是一个不存在的 `.ai4j-skill-read-denied` 路径），从而**主动放弃工作区读权限**。

`restrictReadToAllowedRoots = true` 在 `BuiltInToolContext.resolveReadablePath(...)` 里的语义是：候选路径必须落在某个 `allowedRoot` 之下（且还要通过 `resolvesWithin` 校验，防止符号链接绕过），工作区 root 本身不再作为允许的读取来源。换言之：

| 场景 | workspace 可读 | skill roots 可读 | 写工作区 |
|------|---------------|------------------|----------|
| `createToolContext(...)`（默认） | 是 | 是（只读） | 受 `resolveWorkspacePath` 约束 |
| `createSkillToolContext(...)`（`restrict=true`） | 否 | 是（只读） | 占位 root，实际不可写 |

这让 skill 懒加载可以跑在一个最小权限上下文里：模型只看到 skill 目录，连工作区源码都读不到，更别说写。

## 12. 当前实现的真实限制

### 没有复杂版本和依赖模型

`SkillDescriptor` 只有基础元数据，不包含：

- 版本
- 依赖
- capability graph

### 没有复杂的运行时激活模型

Core SDK 提供发现和模型目录投影；UI、租户授权、slash command、业务 Tool 绑定和运行时激活仍由宿主应用决定。

### 没有正文级缓存层

当前重点是发现目录和只读边界，正文读取仍交给 `read_file` 等宿主工具完成。

这些都不是 bug，而是当前实现选择了轻量而清晰的 skill 模型。

## 13. 目录组织建议

### 仓库内 skill 适合放什么

- 项目专属开发规范
- 仓库特有脚本和工作流
- 只对当前 monorepo 成立的方法论

### 全局 skill 适合放什么

- 跨项目复用能力
- 通用分析 SOP
- 个人长期使用的标准模板

### `SKILL.md` 顶部最值得认真写什么

- `name`
- `description`
- `disable-model-invocation: true`（仅允许宿主显式选择，不向模型自动公布）

因为目录发现阶段最先依赖的就是这两项。

## 14. 这页最该记住的结论

AI4J 的 skill discovery 不是“遍历目录后直接塞正文”，而是一套：

- 扫描 root
- 生成目录
- 按需读取
- 把 skill roots 纳入只读边界

的轻量懒加载机制。

也正因为如此，skill 才能既复用方法论，又不把上下文治理做坏。
