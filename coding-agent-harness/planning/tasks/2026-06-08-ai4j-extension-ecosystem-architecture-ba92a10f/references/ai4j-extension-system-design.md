# AI4J Extension System Design

> Last updated: 2026-06-08

## 1. 目标

AI4J Extension System 的目标不是把主仓拆成更多模块，而是让第三方开发者可以独立发布能力包，让使用者可以按需安装、审查、启用和组合这些能力。

目标体验：

1. 第三方开发者在 AI4J 主仓外写 `ai4j-plugin-*`。
2. 使用者通过 Maven / Gradle / JitPack / GitHub Packages / 私服引入依赖。
3. AI4J 在 classpath 上发现 package，但不会自动启用。
4. 使用者显式启用 package，并显式 allowlist 暴露给 Agent 的工具。
5. CLI / Spring Boot 能 inspect package 的能力、权限和配置项。

## 2. 命名与分层

对外可以继续说“插件生态”，但内部设计必须分四层：

| Layer | 中文名 | 作用 |
| --- | --- | --- |
| Package | 分发包 | Maven artifact、zip、git repo 或未来 marketplace package |
| Manifest | 声明清单 | 描述 id、版本、入口类、资源、权限、配置前缀和能力 |
| Extension | 运行时扩展 | Java 代码入口，注册 tool、command、guardrail、context 等能力 |
| Resource | 静态资源 | skill、prompt、theme、FlowGram node schema、示例配置 |

推荐包结构：

```text
ai4j-plugin-feishu/
  pom.xml
  src/main/java/com/example/FeishuExtension.java
  src/main/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension
  src/main/resources/ai4j-package.yml
  src/main/resources/skills/feishu-assistant/SKILL.md
  src/main/resources/prompts/feishu-daily-summary.md
```

Manifest 示例：

```yaml
id: feishu-tools
name: Feishu Tools
version: 1.0.0
vendor: example
entrypoints:
  - com.example.FeishuExtension
capabilities:
  - tool
  - command
  - skill
  - prompt
permissions:
  network:
    - open.feishu.cn
  tools:
    - feishu.send_message
    - feishu.search_docs
configPrefix: ai4j.plugins.feishu
```

## 3. 核心 API 草案

Wave 1 API 应该小而稳定：

```java
public interface Ai4jExtension {
    ExtensionManifest manifest();

    void apply(ExtensionContext context);
}
```

`ExtensionContext` 只暴露稳定 registry，不直接暴露内部 builder：

```java
context.tools().register(toolSpec, executor);
context.commands().register(commandSpec, handler);
context.guardrails().register(guardrail);
context.skills().register(skillResource);
context.prompts().register(promptResource);
```

关键原则：

- extension 只能贡献能力，不能自动修改全局 runtime。
- extension 不能绕过现有 `AgentToolRegistry`、`ToolExecutor`、`CodingToolPolicyResolver`、`SlashCommandController`。
- extension 的能力必须出现在 manifest 中；未声明能力注册应失败。

## 4. 首批扩展点

Wave 1 支持这些能力：

| Extension Type | 必要性 | 说明 |
| --- | --- | --- |
| ToolExtension | 必做 | 最像 Pi，也是第三方生态最容易贡献的能力 |
| CommandExtension | 必做 | 让 CLI/TUI 增加 `/xxx` 命令和命令面板能力 |
| SkillPackage | 必做 | 把 AI4J / Codex 风格 skill 变成可分发资源 |
| PromptPackage | 必做 | 支持 prompt template 分发和启用 |
| GuardrailExtension | 必做 | 文件、shell、tool 暴露前的安全治理 |

Wave 2 扩展：

| Extension Type | 说明 |
| --- | --- |
| ContextExtension | 长期记忆、session search、上下文压缩、检索上下文 |
| AgentModeExtension | plan-mode、review-mode、research-mode |
| SubAgentExtension | 发布 subagent 定义和 handoff policy |
| McpExtension | 发布 MCP server 配置、tool mapping 和启用策略 |
| EventExtension | 监听 agent/model/tool/session 生命周期 |

Wave 3 扩展：

| Extension Type | 说明 |
| --- | --- |
| UiExtension | TUI 状态栏、footer、renderer、dialog |
| ThemeResource | CLI/TUI 主题 |
| FlowGramNodeExtension | FlowGram 可视化节点与后端执行桥接 |

暂不作为 Extension：

| Type | 原因 |
| --- | --- |
| ProviderExtension | OpenAI-compatible 中转平台不应被随意命名成 provider；新 provider 属于 core SDK 边界，后续单独设计 |
| FullRagExtension | RAG 面过大，loader/vector/retriever/reranker/citation 不应一次性固化 |
| DynamicInstallExtension | Java 依赖解析和 classloader 安全复杂，后置 |

## 5. 安全模型

必须分三道门：

1. **Discovery**：classpath 或 package source 上发现 package。
2. **Enable**：用户配置启用 package。
3. **Expose**：将某个 tool / command / resource 暴露给 agent 或 CLI。

默认规则：

- classpath 发现不等于启用。
- 启用 package 不等于暴露全部 tool。
- tool 暴露必须 allowlist。
- Guardrail 在 shell、file write、tool execution、session mutation 前执行。
- CLI inspect 必须展示 manifest、capabilities、permissions、configPrefix、tools、commands、resources。
- secret 只能来自 env/local config，不能写进 manifest。

Spring Boot 示例：

```yaml
ai4j:
  extensions:
    enabled:
      - feishu-tools
    tools:
      expose:
        - feishu.search_docs
```

CLI 示例：

```bash
ai4j extension list
ai4j extension inspect feishu-tools
ai4j extension enable feishu-tools
```

第一版不做 `ai4j extension install`。

## 6. 模块落点

| Module | 职责 |
| --- | --- |
| `ai4j-extension-api` | 稳定 API、manifest model、capability enum、extension context 接口 |
| `ai4j` | core tool/profile/resource registry adapter；不依赖 CLI |
| `ai4j-agent` | ToolExtension、GuardrailExtension 的 agent runtime 接入 |
| `ai4j-coding` | SkillPackage、PromptPackage、ContextExtension、SubAgentExtension 的 coding-agent 接入 |
| `ai4j-cli` | list/inspect/enable、CommandExtension、TUI/Theme 后续接入 |
| `ai4j-spring-boot-starter` | `ai4j.extensions.*` 配置绑定和 bean 装配 |
| `docs-site` | 插件生态文档、开发者指南、官方插件索引 |
| `ai4j-bom` | extension API 和官方插件版本对齐 |

如果 Wave 1 不想立即新增 Maven 模块，可以短期把 API 放在 `ai4j` 的 `extension` 包；但长期应抽为 `ai4j-extension-api`，让第三方插件依赖更轻。

## 7. 用户体验

普通 Java：

```java
Ai4jExtensions extensions = Ai4jExtensions.discover()
    .enable("web-search")
    .exposeTool("web.search")
    .build();

AgentToolRegistry tools = extensions.tools();
ToolExecutor executor = extensions.toolExecutor();
```

Spring Boot：

```yaml
ai4j:
  extensions:
    enabled:
      - web-search
      - ask-user
    tools:
      expose:
        - web.search
        - ask_user
```

CLI：

```bash
ai4j extension list
ai4j extension inspect web-search
ai4j extension enable web-search
```

## 8. 第三方开发体验

第三方开发者只需要：

1. 依赖 `ai4j-extension-api`。
2. 实现 `Ai4jExtension`。
3. 提供 `META-INF/services/...Ai4jExtension`。
4. 提供 `ai4j-package.yml`。
5. 发布到 Maven Central、JitPack、GitHub Packages 或私服。

示意：

```java
public final class AskUserExtension implements Ai4jExtension {
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
            .id("ask-user")
            .name("Ask User")
            .capability("tool")
            .capability("command")
            .permission("ui.prompt")
            .build();
    }

    public void apply(ExtensionContext context) {
        context.tools().register(AskUserTool.spec(), new AskUserToolExecutor());
        context.commands().register(AskUserCommand.spec(), new AskUserCommandHandler());
    }
}
```

## 9. 官方样板插件

第一批官方样板应尽量像 Pi，而不是只像传统 Java connector：

| Package | 能力 | 价值 |
| --- | --- | --- |
| `ai4j-plugin-ask-user` | tool + command | 让 agent 结构化询问用户，适合小白协作 |
| `ai4j-plugin-web-search` | tool + prompt | SearXNG/Tavily/Exa 等 provider 统一搜索面 |
| `ai4j-plugin-guardrails` | guardrail | 文件保护、危险命令确认、secret 防写入 |
| `ai4j-plugin-todo` | tool + context | 会话任务列表，后续接 compaction 和 session restore |

后续样板：

- `ai4j-plugin-memory-sqlite`
- `ai4j-plugin-mcp-pack`
- `ai4j-plugin-review-mode`
- `ai4j-plugin-flowgram-node-pack`

## 10. 分波路线

| Wave | 交付 | 不做 |
| --- | --- | --- |
| Wave 1 | manifest、ServiceLoader discovery、enable/disable、list/inspect、tool/command/skill/prompt/guardrail registry adapter | install、marketplace、hot reload |
| Wave 2 | ask-user、web-search、guardrails、todo 官方样板；Spring Boot 配置启用 | provider plugin、完整 RAG API |
| Wave 3 | ContextExtension、AgentModeExtension、SubAgentExtension、McpExtension | UI extension |
| Wave 4 | docs-site 插件中心、第三方发布规范、示例项目 | 自动审查第三方源码 |
| Wave 5 | CLI install/update；支持 Maven 坐标、本地 package、JitPack 指引 | 私服依赖冲突自动解决 |
| Wave 6 | UiExtension、Theme、FlowGramNodeExtension | 破坏现有 TUI / FlowGram 协议 |

## 11. 验收标准

第一版合格标准：

- 第三方可在主仓外实现一个 extension。
- 用户加依赖后 AI4J 能发现 extension。
- 用户显式启用后，extension 才进入 active package set。
- 工具必须 allowlist 后才进入 AgentToolRegistry。
- CLI 能 inspect package 的能力、权限、工具、命令、资源和配置前缀。
- 至少一个 guardrail 能拦截受保护文件写入或危险 shell。
- Java 8 下 core tests 和 extension loader tests 通过。
- docs-site 有“使用插件”“开发插件”“发布插件”“安全审查”四类文档。

## 12. 风险和约束

| Risk | Mitigation |
| --- | --- |
| 过早公开太多扩展点 | Wave 1 只公开 tool/command/skill/prompt/guardrail |
| 第三方插件任意代码执行 | inspect + manifest permission + explicit enable + allowlist expose |
| OpenAI-compatible 平台被误写成 provider | docs 和 API 明确 endpoint/profile 配置，不进入 provider plugin |
| CLI install 复杂度过高 | 后置，只先做 list/inspect/enable |
| Java 8 ServiceLoader 能力有限 | 先 classpath discovery，不做 runtime jar hotload |
| 插件绕过模块边界 | ExtensionContext 只暴露 registry adapter，不暴露底层 builder |
