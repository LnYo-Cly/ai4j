# Discovery and Loading

`Skill` 在 AI4J 里不是可执行工具，而是按需读取的方法论资源。

因此这页真正要讲清楚的，不是“怎么调用 skill”，而是：

- 它从哪里被发现
- 如何生成技能目录
- 为什么不应该一开始读取全部正文
- 这些技能文件怎样进入安全读取边界

## 1. 主入口就在 `Skills.java`

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

`discoverDefault(...)` 内部会先调用 `resolveSkillRoots(...)`，当前默认候选根有三类：

1. `<workspace>/.ai4j/skills`
2. `~/.ai4j/skills`
3. 额外挂载的 `skillDirectories`

这里有两个重要点：

### 工作区 skill 和全局 skill 可以同时存在

这让你可以同时拥有：

- 仓库专属 skill
- 用户级跨项目通用 skill

### 相对路径会按 workspace root 解析

如果传入的额外挂载目录不是绝对路径，它会相对当前 workspace root 解析，而不是相对用户 home 或 JVM 启动目录。

## 3. root 是怎样被识别成 skill 的

`Skills` 不是看到一个目录就把它整个当 skill。

当前识别顺序是：

1. 先检查该目录自身是否包含：
   - `SKILL.md`
   - `skill.md`
2. 如果有，就把这个目录识别为一个 skill
3. 如果没有，再扫描它的直接子目录
4. 每个子目录只要含有 `SKILL.md` 或 `skill.md`，就被当成一个 skill

这意味着当前支持两种组织方式：

- 单 skill root
- skill 集合 root

## 4. 一个很重要的限制：当前只扫一层子目录

从 `discoverFromRoot(...)` 的实现看，当前不会无限递归扫描。

也就是说：

- root 本身可以是一个 skill
- 或者 root 的直接子目录可以各自是 skill
- 但更深的孙子目录不会自动继续向下发现

如果你想要更深层级的技能树，当前做法不是依赖递归，而是把更深目录显式作为新的 skill root 挂进来。

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

当前 `SkillDescriptor` 很轻，只保存：

- `name`
- `description`
- `skillFilePath`
- `source`

其中 `source` 由 `resolveSource(...)` 判断：

- skill root 位于 workspace 内 -> `workspace`
- 否则 -> `global`

这个字段很实用，因为它能告诉你某个 skill 是：

- 当前项目真相的一部分
- 还是用户级共享能力

## 7. 去重策略是什么

`discover(...)` 内部会按技能名做去重，去重 key 是：

- `name.trim().toLowerCase(Locale.ROOT)`

并且是“先到先得”：

- 前面 root 里先发现的 skill 会保留
- 后面同名 skill 会被忽略

这带来一个很实际的结论：

- skill 名称在当前体系里本质上是全局 key

因此不要随意让 workspace 和 global skills 出现同名但不同语义的条目。

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

## 12. 当前实现的真实限制

### 没有复杂版本和依赖模型

`SkillDescriptor` 只有基础元数据，不包含：

- 版本
- 依赖
- capability graph

### 没有递归深层扫描

只认 root 或 root 的直接子目录。

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

因为目录发现阶段最先依赖的就是这两项。

## 14. 这页最该记住的结论

AI4J 的 skill discovery 不是“遍历目录后直接塞正文”，而是一套：

- 扫描 root
- 生成目录
- 按需读取
- 把 skill roots 纳入只读边界

的轻量懒加载机制。

也正因为如此，skill 才能既复用方法论，又不把上下文治理做坏。
