---
sidebar_position: 5
---

# Prompt 组装与上下文来源

在 Coding Agent 里，prompt 绝不是“用户输入 + system prompt”这么简单。  
从当前实现看，一次真正送进模型的请求，至少叠了 5 类来源：

- base system prompt
- workspace prompt
- instructions
- session memory / compact / checkpoint
- 当前用户 turn 或 runtime continuation

再加上一个经常被误放进“prompt”里讨论、但其实是独立结构的部分：

- tool schemas

这页的重点就是把这些来源拆开，不再用一句“上下文很复杂”带过去。

## 1. 真正的装配起点在哪

最关键的入口不是 CLI 命令，而是：

- `CodingAgentBuilder.build()`

在这个阶段，builder 会决定是否做：

```java
CodingContextPromptAssembler.mergeSystemPrompt(systemPrompt, resolvedWorkspaceContext)
```

默认受：

- `CodingAgentOptions.prependWorkspaceInstructions = true`

控制。

这说明当前 prompt 组装的第一层关键事实是：

**workspace prompt 是在 agent 构建阶段并入 system prompt 的。**

它不是每轮临时拼一段文本，更不是 slash command 时再额外注入。

## 2. `systemPrompt` 和 `workspace prompt` 不是同一层

当前实现里，这两层虽然最终都进入 system prompt 区域，但来源完全不同。

### `systemPrompt`

来源通常是：

- CLI/TUI 的 `--system`
- Java API 的 `.systemPrompt(...)`

它适合承载：

- 长期稳定的角色约束
- 输出风格要求
- 团队通用规范

### `workspace prompt`

来源是：

- `CodingContextPromptAssembler.buildWorkspacePrompt(workspaceContext)`

它适合承载：

- 当前工作区 root
- workspace description
- built-in tools 列表
- tool 调用规则
- shell 使用指导
- workspace 外访问限制
- discovered skills 摘要

所以从语义上说：

- `systemPrompt` 解决“你应该怎样工作”
- `workspace prompt` 解决“你现在身处什么执行环境”

## 3. `WorkspaceContext` 为什么直接决定 prompt 内容

`WorkspaceContext` 当前至少提供了这些关键字段：

- `rootPath`
- `description`
- `allowOutsideWorkspace`
- `skillDirectories`
- `allowedReadRoots`
- `availableSkills`

而 `CodingContextPromptAssembler` 会把其中多项显式写进 prompt。

这意味着 Coding Agent 的 prompt 不是纯语言引导，而是带宿主状态的环境声明。  
模型看到的不是抽象“你能改文件”，而是更具体的：

- 工作区根目录是什么
- 工作区外默认是否允许依赖
- 可用 skills 有哪些

这就是 coding 场景和普通 chat 场景的本质差异之一。

## 4. skills 为什么会同时影响 prompt 和读路径

`CodingSkillDiscovery.enrich(...)` 当前做两件事：

- 发现 skills
- 把 `allowedReadRoots` 和 `availableSkills` 写回 `WorkspaceContext`

然后这两部分又分别影响两层：

- `availableSkills` 会进入 workspace prompt
- `allowedReadRoots` 会影响 `resolveReadablePath(...)`

所以 skill 在 Coding Agent 里不是“给模型看的一张技能清单”这么简单，它同时还是：

- prompt 能力面
- 只读根目录白名单的一部分

这也解释了为什么模型要先 `read_file` 读取 `SKILL.md`，但又不需要一开始就把所有 skill 正文全塞进上下文。

## 5. `instructions` 和 `systemPrompt` 的边界是什么

`instructions` 当前来自：

- CLI/TUI 的 `--instructions`
- Java API 的 `.instructions(...)`

它不会像 workspace prompt 一样由环境自动生成，而是会话级策略输入。

最稳的分法是：

- `systemPrompt`：长期稳定的全局规则
- `workspace prompt`：当前工作环境声明
- `instructions`：当前会话的附加策略
- 用户输入：这一轮要做什么

如果把这几层混写，你后面排查行为偏差时会非常难定位。

## 6. tool schemas 为什么不属于“prompt 文本”

这是当前很多文档容易讲歪的一点。

工具当然会影响模型行为，但它们进入模型的方式不是“再多拼一段说明文字”。

当前 built-in tools、custom tools、subagent tools、MCP tools 最终都会进入：

- `toolRegistry`
- `toolExecutor`

也就是说，它们是结构化工具面，而不是 prompt 文本的一部分。

workspace prompt 里写“Available built-in tools: bash, read_file, write_file, apply_patch.”  
这只是环境提示，不是 schema 本身。

所以如果模型调用不到某个工具，你首先该查的不是 prompt，而是：

- 这个工具是否真的进入了 registry

## 7. 当前用户输入层来自哪里

当前 user turn 的来源并不只有一个文本框。

它可能来自：

- one-shot `--prompt`
- CLI/TUI 交互输入
- ACP `session/prompt`
- `/cmd <name> [args]` 渲染结果
- runtime continuation

也就是说，“当前用户输入”本身也是一个多来源面。

## 8. `/cmd` 为什么本质上是 prompt macro，而不是系统层扩展

当前自定义命令会从：

- `~/.ai4j/commands`
- `<workspace>/.ai4j/commands`

读取模板。

然后渲染变量，例如：

- `$ARGUMENTS`
- `$WORKSPACE`
- `$SESSION_ID`
- `$ROOT_SESSION_ID`
- `$PARENT_SESSION_ID`

渲染完成后，它会作为新的用户 turn 送进会话。  
所以 `/cmd` 的本质是：

- 用户输入模板

而不是：

- 新的 system prompt 层
- 新的 instructions 层

这个边界说清后，很多“为什么 /cmd 没覆盖系统行为”的问题就自然消失了。

## 9. ACP 的 `session/prompt` 为什么也不是“系统层”

ACP 传入的是结构化 prompt 数组，但当前 Coding Agent 消费它时，仍然是把它变成本轮输入交给 session runtime。

也就是说，ACP 的结构化只是：

- 传输层结构化

而不是：

- 模型消费层天然多模态多段结构化

这点要说透，否则容易误以为 ACP 一定天然拥有比 CLI 更“高级”的 prompt 编排语义。当前并不是这样。

## 10. continuation prompt 是哪一层来的

Coding Agent 的 outer loop 在需要继续时，会由：

- `CodingAgentLoopController`

生成 continuation 语义，再让 session 继续跑下一轮。

这类 continuation 并不是新的用户消息，而是 runtime 内部推进机制的一部分。  
所以在会话里出现多轮自动推进时，你看到的是：

- 会话持续运行

但底层并不意味着多了多条真实用户输入。

这对于理解：

- 为什么会 auto-continue
- 为什么历史里没有同样数量的用户 turn

非常重要。

## 11. compact / checkpoint 为什么会改变后续 prompt 行为

一旦 session 变长，`CodingSession` 会做：

- tool-result micro compact
- session compact
- checkpoint 复用或重建

这意味着老上下文不再总是以原始对话全文形态存在。  
后续回合模型看到的，可能已经是：

- summary
- checkpoint goal
- constraints
- blocked items
- next steps
- critical context
- process snapshots

所以同一句当前输入，在：

- 新 session
- 已 compact 的老 session

里表现不一样，是当前架构下的正常现象，不一定是模型波动。

## 12. runtime rebuild 为什么会影响 prompt

因为 workspace prompt 是在 build 阶段注入的，所以只要发生 runtime rebuild，下面这些内容都可能重新影响最终 system prompt：

- workspace description
- discovered skills
- allowOutsideWorkspace
- experimental surface

这也是为什么一些配置切换后，行为会立即变化。  
不是因为“模型突然换了性格”，而是 prompt 装配环境本身变了。

## 13. 排查行为偏差时该按什么顺序看

如果你觉得模型行为不对，建议按下面顺序排：

1. `systemPrompt` 是否过强或互相冲突
2. workspace description 是否把项目说偏
3. `instructions` 是否覆盖了原本意图
4. 当前 turn 是否其实来自 `/cmd` 模板
5. skill 是否已发现、且 `SKILL.md` 是否真的可读
6. 当前 session 是否经历过 compact / checkpoint
7. 当前可见工具面是否发生了 runtime rebuild

按这个顺序查，通常比反复调一句用户 prompt 有效得多。

## 14. 最容易踩坑的 5 个点

### 14.1 把 workspace prompt 当成每轮临时拼接文本

当前它主要是在 builder 阶段并入 system prompt。

### 14.2 把 tool schemas 也算进“prompt 文本”

工具是结构化 registry 面，不是纯说明文字。

### 14.3 把 `/cmd` 当系统层机制

它本质上仍是用户输入模板。

### 14.4 忽略 skill 同时影响 prompt 与读路径

skills 不是只有展示作用。

### 14.5 忽略 compact 后上下文已经被摘要化

老 session 的行为变化，很多时候来自上下文形态变化，而不是模型随机性。

## 15. 这页最该记住的结论

AI4J 当前的 Coding Agent prompt 组装，不是单层字符串拼接，而是多来源环境组合：

- `systemPrompt` 定长期规则
- workspace prompt 定当前执行环境
- `instructions` 定会话策略
- 当前 user turn 定本轮任务
- session memory / compact / checkpoint 定历史承接
- tool schemas 作为独立结构决定可调用能力

把这些来源层分清楚，后面你不管是调行为、排查偏差，还是扩宿主，都能更快定位问题。

## 16. 推荐阅读

1. [Runtime 架构](/docs/coding-agent/runtime-architecture)
2. [会话、流式与进程](/docs/coding-agent/session-runtime)
3. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
4. [Skills 使用与组织](/docs/coding-agent/skills)
5. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
