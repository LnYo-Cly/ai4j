---
sidebar_position: 8
---

# Skill 主题

这页讲的是 ai4j 在基础 `AiService / IChatService` 层如何使用 `Skill`。

这里的 `Skill` 不是新的模型协议，也不是新的工具执行器，而是一种“可复用提示词与资源包”约定：

- 一个 skill 通常以 `SKILL.md` 或 `skill.md` 为入口
- 可以和额外说明文件、样例、资源目录放在同一个 skill 根目录里
- 模型并不会自动加载全部 skill 正文，而是先看到 skill 清单，再按需用 `read_file` 读取对应 `SKILL.md`

## 1. Skill 在 ai4j 里的定位

在 ai4j 里，Skill 解决的是“把一组可复用的任务方法论、角色约束、操作步骤沉淀成可挂载资源”的问题。

它和其他能力的边界是：

- 和 `Tool` 不同：skill 不是可执行函数，本身不会被模型直接 `tool_call`
- 和 `MCP` 不同：skill 不引入新的协议或远程服务
- 和 `Agent` 不同：skill 不负责多步循环控制，只负责给模型提供一份可按需读取的能力说明

所以 skill 更像“可发现、可路由、可延迟读取”的 prompt asset。

## 2. 为什么不是单独做一个 Skill Tool

当前实现刻意没有再额外发明一个 `skill` 工具，而是复用基础能力里的两层机制：

1. `Skills` 负责发现 skill、生成 skill 清单、组装提示词
2. `read_file` 等 built-in tool 负责让模型按需读取 `SKILL.md`

这样做有几个直接好处：

- 基础 `AiService`、`Agent`、`Coding Agent` 可以共用同一套 skill 约定
- 不需要额外维护一套 skill runtime 协议
- 模型只在需要时读取相关 skill，避免把所有 skill 正文一次性塞进上下文
- skill 仍然走普通 tool loop，工程语义更统一

## 3. 核心源码入口

基础 skill 能力目前主要落在下面这些类：

- `io.github.lnyocly.ai4j.skill.Skills`
- `io.github.lnyocly.ai4j.skill.SkillDescriptor`
- `io.github.lnyocly.ai4j.tool.BuiltInTools`
- `io.github.lnyocly.ai4j.tool.BuiltInToolContext`
- `io.github.lnyocly.ai4j.tool.BuiltInToolExecutor`

如果你继续走老的 `functions("read_file")` 形式，实际还会经过这些兼容包装：

- `io.github.lnyocly.ai4j.tools.ReadFileFunction`
- `io.github.lnyocly.ai4j.tools.WriteFileFunction`
- `io.github.lnyocly.ai4j.tools.ApplyPatchFunction`
- `io.github.lnyocly.ai4j.tools.BashFunction`

也就是说，skill 现在已经不再只属于 coding-agent，基础 chat 调用链也能直接挂载和使用。

## 4. Skill 的发现规则

默认发现入口由 `Skills.discoverDefault(...)` 提供。

当前默认 roots 是：

1. `<workspace>/.ai4j/skills`
2. `~/.ai4j/skills`
3. 额外传入的 `skillDirectories`

发现结果会返回：

- `skills`：每个 skill 的 `name / description / skillFilePath / source`
- `allowedReadRoots`：built-in `read_file` 允许读取的 skill roots

其中 `source` 当前只区分：

- `workspace`
- `global`

同名 skill 会按发现顺序去重，当前语义是“先发现的优先保留”。

## 5. Skill 是如何工作的

完整链路通常是：

1. 先发现当前 workspace 和全局 skill
2. 把 skill 列表追加进 system prompt
3. 暴露 `read_file`，必要时再暴露 `bash / write_file / apply_patch`
4. 模型看到 `<available_skills>` 清单后，自主判断是否需要某个 skill
5. 真正需要时，再调用 `read_file` 去读取对应 `SKILL.md`
6. 读取后按照 skill 里的约束继续完成任务

这一点很关键：  
ai4j 当前暴露给模型的是“skill 索引”，不是“skill 正文全集”。

## 6. 基础使用方式

### 6.1 继续使用老的 `functions(...)`

如果你已经在项目里使用：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("MiniMax-M2.7")
        .messages(Arrays.asList(
                ChatMessage.withSystem(systemPrompt),
                ChatMessage.withUser("使用合适的 skill 完成任务")
        ))
        .functions("read_file", "bash")
        .build();
```

这条路现在仍然可以正常工作。

配合 `Skills` 的典型写法是：

```java
Path workspaceRoot = Paths.get(".").toAbsolutePath().normalize();

Skills.DiscoveryResult discovery = Skills.discoverDefault(workspaceRoot);
String systemPrompt = Skills.appendAvailableSkillsPrompt(
        "You are a helpful assistant.",
        discovery.getSkills()
);

ChatCompletion request = ChatCompletion.builder()
        .model("MiniMax-M2.7")
        .messages(Arrays.asList(
                ChatMessage.withSystem(systemPrompt),
                ChatMessage.withUser("使用最相关的 skill 完成任务")
        ))
        .functions("read_file", "bash")
        .build();
```

这种写法的特点是：

- 对旧项目改动最小
- 仍然享受自动 schema 组装和自动 tool loop
- workspace 内 skill 在默认 cwd 语义下可以直接使用

### 6.2 使用显式 `tools(...)`

如果你更希望直接传工具定义，也可以只按需暴露：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(Arrays.asList(
                ChatMessage.withSystem(systemPrompt),
                ChatMessage.withUser("使用最相关的 skill 完成任务")
        ))
        .tools(BuiltInTools.tools(
                BuiltInTools.READ_FILE,
                BuiltInTools.BASH
        ))
        .build();
```

这比一次性塞入 4 个 built-in tools 更细粒度。

## 7. 什么时候需要 `BuiltInToolContext`

如果 skill 只在当前 workspace 下，并且你的进程工作目录就是这个 workspace，通常可以不显式传 `builtInToolContext`。

但下面这些情况，建议显式传：

- 要读取 `~/.ai4j/skills` 下的全局 skill
- 要挂载额外 `skillDirectories`
- 需要把 workspace 根目录固定到某个明确路径，而不是依赖当前 cwd

典型写法：

```java
Path workspaceRoot = Paths.get("/project").toAbsolutePath().normalize();
Skills.DiscoveryResult discovery = Skills.discoverDefault(
        workspaceRoot,
        Arrays.asList("../shared-skills")
);

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(messages)
        .functions("read_file", "bash")
        .builtInToolContext(Skills.createToolContext(workspaceRoot, discovery))
        .build();
```

这里的 `BuiltInToolContext` 主要负责：

- workspace 根目录解析
- skill roots 的只读放行
- `bash` 相对目录解析

## 8. 与自动 tool loop 的关系

基础 `IChatService` 的自动 tool loop 现在已经能直接配合 skill 使用：

1. 模型返回 `read_file`
2. SDK 自动执行 built-in `read_file`
3. SDK 把 `tool(result)` 回填给模型
4. 模型继续决定是否执行 `bash` 或直接给出最终答案

所以如果你只是做基础 chat 接入，不需要自己再手写一层 skill loop。

## 9. 与 Coding Agent 的关系

`Coding Agent` 里的 skill 体验更完整，原因是它额外提供了：

- workspace 指令层组装
- `/skills` 命令
- CLI / TUI / ACP 交互
- coding 专用 built-in tools 和审批控制

但基础原理和这里是一致的：

- 先发现 skill
- 再把 skill 清单暴露给模型
- 真正使用时按需读取 `SKILL.md`

所以这页讲的是“基础能力层的 skill”，不是 coding-agent 专属技能系统。

## 10. 使用建议

- 一个 skill 只解决一类明确任务，不要做成万能提示词包
- `name` 和 `description` 要让模型容易路由
- 把长资料、样例、补充文档放到 skill 目录里，让模型按需 `read_file`
- 如果 skill 需要执行终端命令，至少同时暴露 `bash`
- 如果 skill 需要修改文件，再按需暴露 `write_file` 或 `apply_patch`

## 11. 什么时候不适合用 Skill

下面这些场景更适合直接上 tool / MCP / Agent，而不是 skill：

- 需要访问真实外部系统或远程 API
- 需要强结构化输入输出约束
- 需要多轮路由、子智能体协作或显式状态机
- 需要权限隔离和审计级工具治理

skill 更适合沉淀“做事方法”和“局部领域知识”，而不是取代执行系统本身。
