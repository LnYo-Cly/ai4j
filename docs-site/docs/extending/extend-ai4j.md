---
sidebar_position: 8
title: 扩展 ai4j
description: 把 ai4j 让 agent 与 SDK 做更多的四条扩展线收进一个入口：插件包贡献合约（discover/enable/allow/expose）、按需加载的 Skill 方法论、插件 Prompt 资源、新增 LLM 后端的 provider/model/service 扩展，标清各自入口与安全边界。
tags: [concept]
---

# 扩展 ai4j

> **ai4j 的"扩展"= 给 agent / SDK 增加它出厂时不带的能力。** 但 ai4j 把这件事拆成了四条线，每条线的入口、代价和安全边界都不一样——选错线，就会改错地方。

这页是一个聚合入口：它不重复每个专题页的内容，只负责告诉你**四条扩展线分别是什么、解决什么问题、从哪一页开始读**。每条线下面都有一句定义和一个真实的文档入口。

---

## 先看一张总表

| 扩展线 | 一句话 | 入口机制 | 是否模型可见 |
| --- | --- | --- | --- |
| **插件包（Plugin）** | 把工具 / 命令 / Skill / Prompt / Guardrail 打成一个 jar 贡献给 agent | `ai4j-extension-api` + `ServiceLoader` + 三段式门禁 | 工具需 `exposeTool` 后才可见 |
| **Skill** | 给模型按需读取的方法论资源（"这类任务该怎么做"） | `.ai4j/skills` 扫描 + `SKILL.md` | 模型先看摘要，再 `read_file` |
| **Prompt** | 插件贡献的可复用提示资源，进 agent 的 `available_prompts` | 插件 `prompts().register(...)` | 否，宿主或 agent 按需读取 |
| **Provider / Model / Service 扩展** | 给 SDK 新增一个 LLM 后端或顶层能力面 | 改 `PlatformType` + `AiService` + `Registry` 主链 | 不涉及，是协议层扩展 |

这四条线的边界是 ai4j 文档里最容易被混掉的部分。下面逐条展开。

---

## 1. 插件包：贡献合约（`ai4j-extension-api`）

第三方开发者把工具、命令、Skill、Prompt、Guardrail 打包成一个普通 Maven jar，使用者引入 classpath 后再**发现、启用、授权、暴露**。它不是应用商店，也不是远程下载器——稳定路径是 jar + `ServiceLoader` + 显式门禁。

关键边界是**三段式门禁**：`discover()` 只发现不执行，`enable(...)` 注册资源但不暴露给模型，`exposeTool(...)` 才把指定工具交给 agent tool registry。这个设计刻意不做"安装即自动可用"。

→ [Plugin Packages](/docs/extending/plugins/plugin-packages)（概念与门禁）｜[Plugin Recipes](/docs/extending/plugins/plugin-recipes)（可复制接入配方）

---

## 2. Skill：按需加载的方法论能力

`Skill` 不是"给模型补一点说明文字"，而是一套正式的**上下文治理机制**：session 启动时只发现摘要（名称 + 描述），任务真正匹配时模型再用 `read_file` 读取 `SKILL.md` 正文。它属于 Core SDK（`Skills.java`），不只 是 Coding Agent 的产品特性。

最常见的载体就是 `SKILL.md`。它解决"方法论如何复用"，而不是"动作如何执行"——所以它不直接承担执行，也不会自动塞满上下文。

→ [Skills 总览](/docs/capabilities/skills/overview)｜[Discovery and Loading](/docs/capabilities/skills/discovery-and-loading)｜[Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp)｜[Coding Agent Skills 使用与组织](/docs/products/coding-agent/skills)

---

## 3. Prompt：可复用提示资源

ai4j 没有独立的"slash 提示模板引擎"。**Prompt 是插件资源的一种**：插件在 `apply(...)` 里用 `context.prompts().register(...)` 注册一个 `ExtensionPromptResource`（指向 jar 内的 markdown 资源），启用后它会被物化成只读文件，进入 agent 的 `<available_prompts>` 清单，由宿主或 Coding Agent 按需读取。

也就是说，ai4j 的 Prompt 和 Skill 共享同一套"摘要先行、按需读取"的上下文治理思路，区别在资源类型。ai4j 有**两个官方参考插件**，各有侧重：

- **`ask-user`**——最小插件（host-mediated 用户澄清 tool + command + Skill + Prompt），适合学习扩展的基本骨架。
- **`dynamic-workflow`**——🚀 **生产级旗舰参考**（同时贡献 4 种能力 + host-mediated workflow 信封 + 零运行时依赖 + live 闭环测试）。适合学习如何构建一个完整的、安全的、可发布的 ai4j 插件。详见 [Dynamic Workflow Plugin](/docs/extending/plugins/dynamic-workflow-plugin)。

→ [Ask User Plugin](/docs/extending/plugins/ask-user-plugin)（同时贡献 Prompt 的样板）｜[Dynamic Workflow Plugin](/docs/extending/plugins/dynamic-workflow-plugin)（脚本化 Prompt + Skill 样板）

---

## 4. Provider / Model / Service 扩展：新增 LLM 后端

这条线和插件包**不是一回事**。它是改 SDK 代码主链，把一个新的模型平台或顶层能力面正式纳入平台分发体系。当前 ai4j 对 provider 的建模是**显式枚举驱动**，不是"注册一个实现就自动可见"。

三个粒度按代价从轻到重排：

- **Model Extension**——同 provider 下补模型名、请求字段或能力变体，主战场在请求对象和 provider 适配层，不碰 `PlatformType`。
- **Provider Extension**——新增一个模型平台（新 `PlatformType` + 配置 + 工厂分支 + Registry + starter），必须同时触碰一整条链。
- **Service Extension**——新增一条顶层能力契约（如新的 `IXxxService`），会扩大整个 SDK 的公共 API 面，代价最高。

:::warning 边界提醒
插件包**不能**用来新增 provider。要接一个新 LLM 后端，仍然走代码主链；插件包只负责给 agent 暴露运行时资源（工具 / 命令 / Skill / Prompt / Guardrail）。
:::

→ [Provider Extension](/docs/extending/code-level/provider-extension)｜[Model Extension](/docs/extending/code-level/model-extension)｜[Service Extension](/docs/extending/code-level/service-extension)｜[Extension 总览](/docs/extending/overview)

---

## 最小可跑示例：让一个 Agent 多一个工具

最常见的"扩展"动作，是把一个插件工具接进通用 Agent loop。下面用官方 `ask-user` 插件演示 discover → enable → expose 三步（Java 8 风格）。

引入依赖：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
  <version>2.4.2</version>
</dependency>
```

启用并暴露给 Agent：

```java
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;

// 1. discover: 从 classpath 发现插件（不执行工具，不暴露给模型）
// 2. enable:   调用插件 apply(...) 注册 tool / command / Skill / Prompt / Guardrail
// 3. exposeTool: 把指定工具交给模型可见的 tool registry
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .exposeTool("ask_user");

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .extensions(registry)
        .build();
```

运行时会把暴露出的 `ExtensionToolSpec` 转成普通 `Tool`，把 `ExtensionToolExecutor` 路由到现有 `ToolExecutor`——Agent 主循环不用认识插件实现类。

:::note Spring Boot
Spring Boot 项目可以用配置完成同一件事：`ai.extensions.enabled` + `ai.extensions.tools.expose`，starter 会自动装配 `ExtensionRegistry` / `ExtensionRuntimeSnapshot`，但不会自动创建 Agent。详见 [Plugin Recipes](/docs/extending/plugins/plugin-recipes)。
:::

---

## 边界一句话

- **插件包** = 可插拔的**贡献合约**（jar 贡献资源，门禁控制可见性）。
- **Skill** = 按需加载的**方法论资源**（告诉模型"这类任务怎么做"）。
- **Prompt** = 插件贡献的**可复用提示资源**（进 `available_prompts`，按需读取）。
- **Provider / Model / Service 扩展** = 改 SDK **代码主链**新增 LLM 后端或能力面。

遇到"现有 SDK 不够用"时，先判断你碰到的是**资源复用**（走插件包 / Skill）、还是**平台边界 / 模型变体 / 能力新增**（走 provider/model/service 扩展）。判断顺序见 [Extension 总览 §5 扩展决策顺序](/docs/extending/overview)。

---

## 继续阅读

- [Extension 总览](/docs/extending/overview)——四条扩展线的全景与决策顺序
- [Plugin Packages](/docs/extending/plugins/plugin-packages)——插件包概念与三段式门禁
- [Skills 总览](/docs/capabilities/skills/overview)——Skill 作为上下文治理层
- [Tools](/docs/capabilities/tools/overview)——工具是模型在宿主内的执行能力（Skill / Tool / MCP 三者对比见 [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp)）
- [Agent Runtime](/docs/agent/overview)——插件工具接入通用 Agent loop 的宿主
- [Coding Agent](/docs/products/coding-agent/overview)——插件工具 + Skill + Prompt 在 coding session 里的使用
