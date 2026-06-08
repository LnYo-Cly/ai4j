# AI4J extension skill prompt resources wave 6 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Skill / Prompt 是资源，不是模型可见工具

- 背景：Pi 风格 package 可以同时包含 extension 代码、skills 和 prompt templates；AI4J 需要让资源可用，但不能让资源绕过 enable / expose 门禁。
- 发现：现有 Coding Agent 已有 `.ai4j/skills` 发现和 `allowedReadRoots` 机制，适合承接插件资源；系统提示只需要列出资源清单，不应塞入完整正文。
- 影响：本轮选择把已启用插件资源物化到临时只读目录，加入 `availableSkills` / `availablePrompts` 和 `allowedReadRoots`。
- 后续：官方样板插件可以基于该机制发布 Skill / Prompt；资源生命周期清理可在后续任务优化。

### CLI resource 读取应和 command 执行一样显式 enable

- 背景：`extension run` 已要求 `--enable`，避免 classpath 发现导致插件代码自动执行。
- 发现：读取资源虽不执行工具，但仍会触发插件 `apply(...)` 以注册资源，因此也应要求 `--enable`。
- 影响：`ai4j-cli extension resource --enable <id> <skill|prompt> <name>` 作为开发者检查入口，不支持未启用读取。
- 后续：如果未来支持 manifest-only static resource index，需要单独设计，不改变本轮门禁。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 资源消费方式 | 物化为只读文件并通过 read_file 按需读取 | 延续现有 Skill 使用模型，避免把大正文塞进系统提示，也避免新增专用 skill tool | 直接把 resource 内容拼进 system prompt | accepted |
| CLI 资源读取门禁 | 必须 `--enable` | 资源注册需要执行 extension `apply(...)`，不能让 discovery 自动触发插件行为 | `extension resource <id> ...` 自动 inspect/读取 | accepted |
| Prompt 清单模型 | 新增 `CodingPromptDescriptor` 和 `<available_prompts>` | Skill 与 Prompt 语义不同，应分别列出；Prompt 是模板，不冒充 Skill | 把 prompt 当 Skill 展示 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 资源临时目录是否需要生命周期清理 | 当前 JVM 临时目录可接受，后续可加 cleanup hook | coordinator | 大量资源包或长期 session 优化任务 |
