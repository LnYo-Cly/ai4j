# Skills 总览

`Skill` 在 AI4J 里不是“给模型补一点说明文字”，而是一套正式的上下文治理机制。

它解决的问题是：

- 方法论如何被发现
- 哪些说明值得进入当前上下文
- 如何让模型按需读取，而不是一上来吃完整个 SOP 库

这就是为什么 `Skill` 应该被放在基座层，而不是只当成 `Coding Agent` 的产品特性。

## 1. `Skill` 在架构里的真实位置

从源码看，skill 主线在：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/skill/Skills.java`
- `SkillDescriptor.java`

它们属于 `ai4j/` 核心模块，而不是 `ai4j-coding/`。

这说明 `Skill` 先是 Core SDK 的一种能力：

- 发现方法论资源
- 给模型一个可选目录
- 必要时再读取正文

`Coding Agent` 只是把这条能力链做成更完整的产品化入口，不是重新定义了 skill 概念。

## 2. Skill 到底是什么

最常见的 skill 载体就是：

- `SKILL.md`

但 skill 的重点不在文件扩展名，而在职责：

- 它是方法论资源
- 它是按需读取的上下文资产
- 它不直接承担执行

你可以把它理解成“给模型的一份结构化工作说明书”，而不是“模型可以直接调用的功能”。

## 3. 为什么它属于 AI 基座

如果从 AI 基座拆层看：

- `Skill` 负责上下文治理和方法论复用
- `Tool` 负责宿主内执行能力
- `MCP` 负责宿主外能力接入

所以前面你问“skills 难道不是 ai 基座吗”，答案是肯定的：

- 是，而且它属于基座里的方法论/上下文层

它不是 `Tool` 的附属物，也不是 `Coding Agent` 的私有概念。

## 4. 这套机制真正解决了什么问题

如果没有 skill，常见做法只有两种：

- 把所有流程说明永久塞进 system prompt
- 完全靠模型自己猜应该怎么做

两者都不理想。

skill 体系解决的是第三条路线：

- 先暴露目录，不暴露全部正文
- 让模型先判断是否匹配
- 匹配后再读取具体 `SKILL.md`

因此它的核心价值是：

- 降低 prompt 污染
- 提高方法论复用率
- 保留说明资产的可治理性

## 5. Skill 不是什么

这几个边界必须明确。

### 不是 Tool

skill 不执行动作。它只是告诉模型：

- 这类任务应该怎么做
- 先后顺序是什么
- 需要注意哪些约束

### 不是 MCP

skill 不负责：

- transport
- 外部服务连接
- 多服务网关

### 不是 runtime

skill 不负责：

- 多步推进
- 审批
- checkpoint
- 任务状态机

它的职责是“说明”，不是“执行”和“调度”。

## 6. Skill 子系统由哪几层组成

从 `Skills.java` 看，当前技能机制至少有 4 层：

### 6.1 发现层

扫描：

- `<workspace>/.ai4j/skills`
- `~/.ai4j/skills`
- 调用方额外挂载目录

### 6.2 描述层

每个 skill 被整理成 `SkillDescriptor`：

- `name`
- `description`
- `skillFilePath`
- `source`

### 6.3 暴露层

`buildAvailableSkillsPrompt(...)` 不直接塞正文，而是先给模型一个可用技能目录。

### 6.4 读取边界层

`createToolContext(...)` 会把 skill roots 写进 `BuiltInToolContext.allowedReadRoots`，确保模型读取 skill 文件时有明确的只读边界。

这四层一起构成的，才是当前 AI4J 的 skill 体系。

## 7. 典型工作流是什么

一条标准 skill 工作流通常是：

1. 宿主调用 `Skills.discoverDefault(...)`
2. 得到可用 skill 列表和只读根
3. 把 `buildAvailableSkillsPrompt(...)` 生成的技能目录加入系统上下文
4. 模型根据任务匹配选择是否读取某个 `SKILL.md`
5. 读取后按 skill 指南执行

这套流程和“把所有 SOP 一开始全部喂给模型”是完全不同的设计。

## 8. 哪些场景最适合 Skill

### 方法论型任务

例如：

- 代码评审规范
- 文档写作套路
- 调研流程
- 多阶段执行 SOP

### 高复用说明资产

一套说明会在多个请求、多次任务里反复出现，但没必要每次都全部塞进上下文。

### 需要显式读取再执行的任务

例如：

- 某仓库专有开发约定
- 某团队专有发布流程
- 某领域专有分析模板

## 9. 当前设计的真实边界

从实现看，有几个边界值得提前知道。

### Skill 不是执行权限

一个 skill 告诉模型“应该这么做”，不代表模型因此获得了：

- 写文件权限
- 执行命令权限
- 访问远端服务权限

这些仍然要靠 Tool / MCP 暴露面决定。

### Skill 不是永久上下文

AI4J 的设计重点是：

- 技能目录先进入 prompt
- 正文按需读取

不是把所有 skill 全部常驻进 system prompt。

### Skill 元数据比较轻

`SkillDescriptor` 当前只有：

- 名称
- 描述
- 文件路径
- 来源

这是一套轻量目录机制，不是复杂的版本依赖管理系统。

## 10. 和上层产品化能力的关系

`Coding Agent` 对 skill 做的是产品化封装，例如：

- 更丰富的交互入口
- 更强的宿主环境配合
- 与任务流更紧的整合

但底层原则没有变：

- Core SDK 负责发现、描述、读取边界
- 上层 runtime 决定什么时候把它们暴露给模型、怎样和执行流结合

## 11. 推荐阅读顺序

1. [Discovery and Loading](/docs/core-sdk/skills/discovery-and-loading)
2. [Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
3. [Coding Agent / Skills](/docs/coding-agent/skills)

## 12. 这页最该记住的结论

AI4J 的 `Skill` 是基座里的方法论与上下文治理层。

它不负责执行，而负责：

- 让方法论资源可发现
- 让模型按需读取
- 让读取边界和宿主工具约束保持一致

这正是它和 Tool、MCP 根本不同的地方。
