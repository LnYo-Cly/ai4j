---
sidebar_position: 7
---

# Skills 使用与组织

`Skill` 在 AI4J 里不是“一个特殊工具”，也不是“自动注入完整提示词的大段模板”。

在 `Coding Agent` 路径里，它更准确的定义是：

- 一份落在文件系统里的可读工作流说明
- 在 session 启动时先被发现成摘要
- 在任务真正匹配时，再由模型按需读取 `SKILL.md`

所以它解决的是“如何复用经验”，不是“如何执行动作”。

---

## 1. Skill 在 Coding Agent 里的真实位置

把启动链路压成一条线，最容易看懂：

```text
DefaultCodingCliAgentFactory.buildWorkspaceContext(...)
  -> CodingAgentBuilder.build()
  -> CodingSkillDiscovery.enrich(workspaceContext)
  -> Skills.discoverDefault(workspaceRoot, skillDirectories)
  -> WorkspaceContext.availableSkills + allowedReadRoots
  -> CodingContextPromptAssembler.mergeSystemPrompt(...)
  -> 模型先看到 skills 摘要，再按需 read_file 读取某个 SKILL.md
```

这里最关键的不是“发现到了多少个 skill”，而是两件事同时发生：

- `availableSkills` 被写回 `WorkspaceContext`，供 prompt 装配和 `/skills` 展示
- `allowedReadRoots` 也被写回 `WorkspaceContext`，让 skill 根目录可以被 `read_file` 读取

这说明 skill 体系在当前实现里不是单纯的文档扫描器，而是 `workspace` 权限模型的一部分。

---

## 2. 发现链路到底怎么跑

`CodingSkillDiscovery.discover(...)` 本身很薄，它只是把 `WorkspaceContext` 交给 `Skills.discoverDefault(...)`。

真正的发现逻辑都在 `ai4j/skill/Skills.java` 里：

1. 先归一化 workspace 根目录
2. 解析 skill roots
3. 逐个 root 扫描 skill
4. 生成 `SkillDescriptor`
5. 以名字去重
6. 回收 `allowedReadRoots`

默认 root 顺序是：

1. `<workspace>/.ai4j/skills`
2. `~/.ai4j/skills`
3. `workspace.json` 里配置的 `skillDirectories`

`skillDirectories` 的解析规则也很直接：

- 绝对路径，直接使用
- 相对路径，按 workspace root 解析

这里的一个重要后果是：

- workspace 内 skill 和用户 home 下 skill 天生可以并存
- 团队共享 skill 包也可以通过 `skillDirectories` 挂进来
- 但最终是否生效，还要看名字去重结果，不是“后挂载一定覆盖前挂载”

---

## 3. 扫描规则比很多人想得更保守

`Skills.discoverFromRoot(...)` 不是递归扫描整个目录树。

它的规则是：

### 3.1 root 自己就是一个 skill

如果某个 root 目录下直接存在 `SKILL.md` 或 `skill.md`，那这个 root 本身就会被当成一个 skill。

一旦命中这条规则，当前 root 的子目录不会继续被当作 skill 容器再扫描。

这意味着：

- 一个目录要么被当“skill 本体”
- 要么被当“skill 容器”

当前实现不会同时把它两种身份都吃进去。

### 3.2 否则只看第一层子目录

如果 root 自己不是 skill，本轮只会检查它的直接子目录里有没有 `SKILL.md` / `skill.md`。

不会继续向更深层递归。

所以：

- `skills/sql-review/SKILL.md` 会被发现
- `skills/backend/sql-review/SKILL.md` 默认不会被发现

如果你想要分层组织，只能靠“多个 root”而不是“深层嵌套目录”。

---

## 4. `SKILL.md` 里哪些字段真的会被读

当前 descriptor 构造规则在 `Skills.buildDescriptor(...)`。

它只提取几类稳定字段：

- `name`
- `description`
- `skillFilePath`
- `source`

其中 `name` 的解析优先级是：

1. front matter 里的 `name`
2. 第一行 markdown heading
3. skill 文件父目录名

`description` 的解析优先级是：

1. front matter 里的 `description`
2. 第一段正文
3. 默认文案 `No description available.`

这背后的设计很务实：

- front matter 是最稳的结构化元数据
- 没写 front matter 也不至于完全失效
- 但缺少 `name` / `description` 会直接降低 skill 被正确匹配和展示的概率

一个最稳的最小例子仍然建议写成：

```md
---
name: sql-review
description: Review SQL changes with indexing, execution-plan, and migration risk checks.
---

# SQL Review

Use this skill when the task involves SQL schema or query review.
```

---

## 5. 去重规则决定了“谁赢”

`Skills.discover(...)` 会把 skill 名称做 `trim + lowercase` 归一化，然后按第一次出现的名字保留。

这意味着去重是：

- 大小写不敏感
- first-win，不是 last-win
- 不合并内容，只保留第一份 descriptor

结合默认 root 顺序，当前实际效果通常是：

1. workspace skill 优先
2. home skill 次之
3. 额外挂载目录最后

如果你在多个 root 里放了同名 skill，后面的那个通常会被静默遮蔽，而不是报错。

这和 MCP 的冲突处理完全不同。

- skill 同名：通常保留第一份
- MCP tool 同名：直接把 server 视为错误

原因很简单：

- skill 是提示层知识
- MCP 是执行层能力

执行层冲突必须更严格。

---

## 6. 模型在 prompt 里实际看到了什么

`CodingContextPromptAssembler.mergeSystemPrompt(...)` 不会把每个 skill 的正文直接拼进系统提示。

它做的是：

1. 构造 workspace prompt
2. 追加 built-in tools 规则
3. 调 `Skills.buildAvailableSkillsPrompt(...)`
4. 只把 skill 摘要列表放进 prompt

当前拼进去的是这种信息：

- skill 名称
- 文件路径
- description
- “不要一上来读完所有 skill，只在明显匹配时 read_file 读取对应 `SKILL.md`”

这点非常关键。

当前 skill 机制不是：

- 启动时把所有经验包灌进模型上下文

而是：

- 启动时暴露一份“可用技能目录”
- 运行时让模型自己判断要不要读某个 skill 正文

这样做的直接收益是：

- 首轮 prompt 不会随着 skill 数量线性膨胀
- skill 正文可以写得更长一些，而不会每次都污染上下文
- 模型更容易把 skill 当成“按需读取的知识包”，而不是“系统永远绑定的总规则”

---

## 7. 为什么 skill 会影响文件读取边界

`CodingSkillDiscovery.enrich(...)` 不只写入 `availableSkills`，还会把 discovery 返回的 `allowedReadRoots` 写回 `WorkspaceContext`。

而 `WorkspaceContext` 有两套路径语义：

- `resolveWorkspacePath(...)`
- `resolveReadablePath(...)`

区别非常重要：

- `resolveWorkspacePath(...)` 默认只允许 workspace 内路径
- `resolveReadablePath(...)` 除 workspace 外，还接受 `allowedReadRoots`

所以当前 skill 根目录的真实权限模型是：

- 允许读
- 不自动允许写
- 也不自动允许执行

这就是为什么团队共享 skill 可以放在 workspace 外部目录里，但仍然能被 `read_file` 正常打开。

同时也说明：

- skill 是“知识输入面”的例外读路径
- 不是“给模型开了任意外部文件访问权限”

---

## 8. `/skills` 命令到底暴露什么

CLI/TUI 里的 `/skills` 输出不是 skill 正文，而是 discovery 结果。

当前 `CodingCliSessionRunner.renderSkillsOutput(...)` 会展示：

- count
- workspaceConfig 路径
- roots
- 每个 skill 的 `name / source / path / description`

`/skills <name>` 会展示单个 skill 的：

- name
- source
- path
- description
- roots

这有两个现实意义：

1. `/skills` 更像“索引视图”，不是“阅读视图”
2. 如果你看得到 skill 摘要，但模型仍然没用它，问题通常不在发现，而在匹配和提示策略

---

## 9. 最常见的失效路径

### 9.1 skill 放得太深

当前 discovery 只扫描 root 或第一层子目录。

更深层的 `SKILL.md` 默认不会被发现。

### 9.2 没写清楚 `name` 和 `description`

虽然有 heading / paragraph fallback，但匹配质量会明显下降，尤其是多个 skill 主题接近时。

### 9.3 同名 skill 被静默遮蔽

如果 workspace、home、shared roots 里有同名 skill，后发现的不会报错，只是不会进入最终列表。

### 9.4 把 skill 当权限升级手段

skill 只扩展可读根目录，不会自动开放写权限，也不会绕过 `allowOutsideWorkspace` 的写边界。

### 9.5 把 skill 当自动执行器

skill 本身不会执行任何动作。真正执行仍然要靠：

- `read_file`
- `bash`
- `write_file`
- `apply_patch`
- 或 MCP tools

---

## 10. 什么时候该用 Skill，什么时候不该

最稳的判断标准是：你是在沉淀“做法”，还是在暴露“能力”。

### 更适合 Skill 的场景

- 代码评审规范
- SQL 审查清单
- 某类框架的工程约定
- 多步骤排障手册
- 文档写作或发布流程

### 更适合 Tool / MCP 的场景

- 真正需要执行命令
- 真正需要读写外部系统
- 真正需要调用数据库、浏览器、搜索、内部平台 API

### 更适合 Agent Definition 的场景

- 需要固定模型、固定 system prompt、固定 handoff 策略
- 需要把某一类 worker 做成可委派子代理

一句话记忆：

- Skill 定义“怎么做”
- Tool/MCP 提供“做这件事的手”
- Agent Definition 固化“这类 worker 的身份和边界”

---

## 11. 扩展和排障时先看哪些入口

如果你要继续扩 skill 体系，最值得先看的入口是：

- `ai4j-coding/.../CodingSkillDiscovery`
- `ai4j/.../skill/Skills`
- `ai4j-coding/.../workspace/WorkspaceContext`
- `ai4j-coding/.../prompt/CodingContextPromptAssembler`
- `ai4j-cli/.../runtime/CodingCliSessionRunner.renderSkillsOutput`

推荐排障顺序是：

1. `/skills` 看是否被发现
2. 检查 root 路径是否真的在 `roots` 里
3. 检查 `name` 是否与其他 skill 冲突
4. 检查 skill 文件是不是落在 root 或第一层子目录
5. 检查模型是否只是看到了摘要，但没有进一步 `read_file`

---

## 12. 这页最该记住的结论

- Skill 在当前实现里是“文件化工作流知识”，不是执行器
- `CodingSkillDiscovery` 做的不是单纯扫描，而是同时补全 `availableSkills` 和 `allowedReadRoots`
- discovery 默认只看 root 本身或第一层子目录，不做深递归
- 同名 skill 按 first-win 保留，后面的通常会被静默遮蔽
- prompt 里默认只放摘要，不放全部 skill 正文
- skill 可以扩展可读路径，但不会扩展写权限或执行权限

---

## 13. 继续阅读

1. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
2. [MCP 对接](/docs/coding-agent/mcp-integration)
3. [Why Coding Agent](/docs/coding-agent/why-coding-agent)
4. [Coding Agent Architecture](/docs/coding-agent/architecture)
