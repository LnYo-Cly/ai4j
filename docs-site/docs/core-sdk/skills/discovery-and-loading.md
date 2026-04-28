# Discovery and Loading

`Skill` 在 AI4J 里不是可执行工具，而是**按需读取的方法论资源**。所以这页的重点不是“怎么调用 skill”，而是“skill 是如何被发现、列出、再被安全读取的”。

## 1. 为什么 AI4J 要单独做一套 skill discovery

如果把所有方法论、模板、长说明都塞进系统提示词，问题会非常明显：

- 上下文污染
- 初始 prompt 过大
- 模型很难只在需要时读取相关说明

AI4J 的 skill 设计就是在解决这个问题：

- 先暴露 skill 清单
- 不自动读全部正文
- 只有任务明确匹配时，再去读 `SKILL.md`

也就是说，skill 的核心价值是 **减少 prompt 污染，同时保留方法论复用能力**。

## 2. 核心源码入口

这一层几乎都在 `ai4j/src/main/java/io/github/lnyocly/ai4j/skill/Skills.java` 里：

- `discoverDefault(Path workspaceRoot)`
- `discoverDefault(Path workspaceRoot, List<String> skillDirectories)`
- `discover(Path workspaceRoot, List<Path> roots)`
- `buildAvailableSkillsPrompt(...)`
- `createToolContext(...)`

配套的数据对象是：

- `SkillDescriptor`
- `DiscoveryResult`

## 3. 默认会扫哪些目录

`resolveSkillRoots(...)` 默认会把三类目录纳入候选：

1. `<workspace>/.ai4j/skills`
2. `~/.ai4j/skills`
3. 调用方额外挂载的 `skillDirectories`

这里有两个重要设计点：

- 项目私有 skill 和用户级全局 skill 可以共存
- 相对路径会相对 workspace root 解析

这正好对应两类常见需求：

- 团队共享一组仓库内 skill
- 个人维护一组跨项目复用 skill

## 4. 一个 skill 目录如何被识别

AI4J 不是看到一个目录就默认当 skill。

它会优先查找：

- `SKILL.md`
- `skill.md`

如果根目录本身就有 `SKILL.md`，它会把这个目录当成一个 skill；否则会继续扫描子目录，寻找每个子目录里的 `SKILL.md`。

这意味着 skill root 支持两种组织方式：

- 单技能目录
- 技能集合目录

## 5. `SkillDescriptor` 里到底保存了什么

发现成功后，AI4J 会构建 `SkillDescriptor`，其中至少包括：

- `name`
- `description`
- `skillFilePath`
- `source`

`source` 会根据目录是否位于 workspace 内，被标成：

- `workspace`
- `global`

这个字段很有用。它告诉你某个 skill 是项目真相的一部分，还是用户级公共能力。

## 6. 为什么 AI4J 不会一上来就读取全部 `SKILL.md`

`buildAvailableSkillsPrompt(...)` 的设计非常明确：

- 只生成可用 skill 目录
- 明确告诉模型：不要预先读取所有 skill
- 只有任务明确匹配时，再去读对应 `SKILL.md`

这不是一个小优化，而是这套机制成败的关键。

如果一开始就把几十个 skill 正文全部塞给模型，那么：

- skill discovery 就退化回“大 prompt”
- 上下文治理优势直接消失
- 模型会更难做按需推理

所以 AI4J 这里其实是在做一种**方法论文档的懒加载**。

## 7. `allowedReadRoots` 是干什么的

`DiscoveryResult` 除了 skill 列表，还会返回：

- `allowedReadRoots`

这组目录随后会被 `createToolContext(...)` 写进 `BuiltInToolContext`。它的意义是：

- skill 文件不是“全盘任意可读”
- 内建的 `read_file` 一类工具可以知道哪些目录属于技能只读区

换句话说，skill discovery 和工具读取权限在 AI4J 里是连着设计的，不是各自独立。

## 8. 这套机制怎么和宿主 prompt 配合

宿主通常会先把 `buildAvailableSkillsPrompt(...)` 生成的技能目录加进系统上下文。模型先看到的是：

- 有哪些 skill
- 每个 skill 的名字、路径、描述
- 应该按需读取，而不是全读

这一步做完以后，模型才有机会进一步决定：

- 这个任务是否匹配某个 skill
- 是否应该调用 `read_file`
- 读取哪个 `SKILL.md`

这和“把方法论直接写死在系统提示词里”是完全不同的控制思路。

## 9. 目录管理建议

### 仓库内 skill 适合放什么

- 项目专属开发规范
- 仓库特有脚本和工作流
- 只对这个 monorepo 成立的方法论

### 用户级 skill 适合放什么

- 通用 agent 能力
- 跨项目复用的 SOP
- 私人偏好的工作方式

### `SKILL.md` 顶部最该认真写什么

- `name`
- `description`

因为 discovery 阶段构建目录时，最先依赖的就是这两项。

## 10. 注意事项

### 10.1 把 skill 当成工具

skill 不执行动作，它只是说明“这类任务应该怎么做”。

### 10.2 把所有 skill 一次性读完

这会直接破坏 skill 体系存在的意义。

### 10.3 skill 描述写得像市场文案

模型真正需要的是任务边界、适用场景和工作流，不是空泛宣传语。

## 11. 设计摘要

AI4J 的 skill 不是 prompt 附件，而是一套按需发现、按需读取的资源体系。`Skills.discoverDefault(...)` 先生成目录和可读根，再通过 `BuiltInToolContext` 把 skill 读取边界交给宿主工具，因此它本质上是在做方法论文档的懒加载和上下文治理。

## 12. 继续阅读

- [Skills / Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
- [Tools / Function Calling](/docs/core-sdk/tools/function-calling)
