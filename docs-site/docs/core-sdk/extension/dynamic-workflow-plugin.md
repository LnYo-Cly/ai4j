# Dynamic Workflow Plugin

`ai4j-plugin-dynamic-workflow` 是 AI4J 的动态工作流样板插件，推荐作为独立 GitHub 仓库维护和单独发版，而不是并入 `ai4j-sdk` reactor。它参考 Pi dynamic workflows 的核心生态模式：**模型先把复杂任务写成一段可检查的 workflow script，再由宿主决定如何把脚本拆给 subagent、worktree、审批和模型路由执行**。

和 Pi 的完整实现不同，AI4J 这个插件首版刻意保持在 `ai4j-extension-api` 边界内：插件只贡献 tool、command、Skill 和 Prompt 资源，并返回 host-mediated JSON envelope；它不会在插件进程里执行 JavaScript、创建子 Agent、操作 git worktree 或绕过宿主审批。

## 1. 为什么选这个形态

这次对比了两个 Pi 实现：

| 实现 | 适合作为参考的部分 | 不直接照搬的部分 |
| --- | --- | --- |
| `Michaelliv/pi-dynamic-workflows` | 小而清晰的核心 primitive：`workflow` tool、`export const meta`、`agent()` / `parallel()` / `pipeline()` / `phase()`、确定性脚本约束 | 它仍依赖 Pi 的 in-memory subagent session 和 Node `vm`，不能直接放进 AI4J Java plugin API |
| `QuintinShaw/pi-dynamic-workflows` | 生产化功能很完整：后台运行、`/workflows` 管理、模型 tier、resume journal、worktree isolation、saved workflows、内置 deep research/review | 约 10x 代码量和大量 Pi TUI/模型注册/持久化假设；对 AI4J 首版插件会过度耦合 |

所以 AI4J 首版采用 Michaelliv 的最小核心语义作为公开契约：`workflow` 是一个工具入口，脚本有确定性约束，实际执行由宿主 runtime 接管。QuintinShaw 版本里的后台、resume、模型 tier 和 worktree isolation 更适合作为后续 `ai4j-agent` / `ai4j-coding` 的宿主能力，而不是塞进 extension-api-only 插件。

## 2. 仓库和引入依赖

独立仓库建议命名为：

```text
https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow
```

这样它可以展示 AI4J plugin 生态，而不绑定到 SDK monorepo 的发布节奏。`ai4j-sdk` 侧只保留这篇文档入口和插件契约说明；插件源码、CI、版本号和发行说明由独立仓库维护。

插件发布后直接引入独立 artifact：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-dynamic-workflow</artifactId>
  <version>0.1.0</version>
</dependency>
```

快照或本地验证阶段可以使用 `0.1.0-SNAPSHOT`。该插件仍依赖 `ai4j-extension-api`；独立仓库的 POM 应显式声明兼容的 AI4J extension API 版本，例如：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-extension-api</artifactId>
  <version>2.4.0</version>
</dependency>
```

不要假设它已经进入 `ai4j-bom`；只有当插件回到 SDK release train 或 BOM 明确收录后，才省略版本号。

## 3. 启用和暴露

普通 Java：

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("dynamic-workflow")
        .exposeTool("workflow");
```

严格授权：

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("dynamic-workflow")
        .requireExplicitResourceActivation()
        .allowCommand("workflow")
        .allowSkill("dynamic-workflow-orchestration")
        .allowPrompt("dynamic-workflow-script")
        .exposeTool("workflow");
```

Spring Boot：

```yaml
ai:
  extensions:
    enabled:
      - dynamic-workflow
    tools:
      expose:
        - workflow
```

Spring Boot 严格授权：

```yaml
ai:
  extensions:
    enabled:
      - dynamic-workflow
    explicit-resource-activation: true
    tools:
      expose:
        - workflow
    commands:
      allow:
        - workflow
    skills:
      allow:
        - dynamic-workflow-orchestration
    prompts:
      allow:
        - dynamic-workflow-script
```

## 4. 它贡献了哪些能力

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| Extension id | `dynamic-workflow` | classpath 发现和 enable 使用的插件 ID |
| Tool | `workflow` | Agent 可调用的动态工作流请求工具 |
| Command | `workflow` | CLI / 宿主人手动触发的工作流请求入口 |
| Skill | `dynamic-workflow-orchestration` | 何时使用 workflow、如何写脚本的说明 |
| Prompt | `dynamic-workflow-script` | 生成确定性 workflow script 的 prompt 模板 |

Manifest 还声明：

```text
vendor: ai4j
permission: agent.workflow.request
configPrefix: ai4j.extensions.dynamic-workflow
```

> `agent.workflow.request` 是宿主策略提示，不会自动授予执行 JavaScript、创建 worktree、联网或调用 provider 的权限。真实执行仍由 host policy、Agent/Coding Agent 工厂和工具暴露配置决定。

## 5. Tool 输入

`workflow` 的输入 schema：

```json
{
  "type": "object",
  "properties": {
    "script": {
      "type": "string",
      "description": "Raw JavaScript workflow script. First statement should be: export const meta = { name, description, phases }."
    },
    "args": {
      "description": "Optional JSON value exposed to the workflow script as args."
    },
    "background": {
      "type": "boolean",
      "description": "Whether the host may run the workflow out of band."
    },
    "maxAgents": {
      "type": "integer",
      "minimum": 1,
      "maximum": 1000,
      "description": "Optional host-enforced maximum number of subagents."
    },
    "tokenBudget": {
      "type": "integer",
      "minimum": 1,
      "description": "Optional host-enforced token budget."
    }
  },
  "required": ["script"]
}
```

示例：

```json
{
  "script": "export const meta = { name: 'auth_audit', description: 'Audit routes for missing auth checks', phases: [{ title: 'Scan' }, { title: 'Verify' }] }\n\nphase('Scan')\nconst findings = await parallel(args.files.map(file => () => agent('Audit ' + file + ' for missing auth checks.', { label: 'audit ' + file })))\n\nphase('Verify')\nreturn await agent('Synthesize and verify these findings:\n' + findings.join('\n\n'), { label: 'final review' })",
  "args": {
    "files": ["src/routes/user.ts", "src/routes/admin.ts"]
  },
  "background": true,
  "maxAgents": 16
}
```

## 6. Tool 输出

插件返回宿主可识别的 JSON envelope：

```json
{
  "type": "ai4j.dynamic_workflow.request",
  "source": "tool",
  "tool": "workflow",
  "status": "pending_host_workflow_execution",
  "hostAction": "execute_dynamic_workflow",
  "scriptRuntime": "host_mediated",
  "blocking": "host_decides",
  "argumentsRaw": "{... original tool arguments ...}"
}
```

`argumentsRaw` 会保留模型传入的原始参数字符串，并在 64 KiB 后截断，截断时增加：

```json
{"argumentsTruncated": true}
```

这和 `ask-user` 插件的 envelope 思路一致：插件层保证输出始终是合法 JSON；宿主如果要实际执行 workflow，必须按自己的安全策略解析、校验和运行 `script`。

## 7. Command 路径

接入前可以检查：

```bash
ai4j-cli extension plan dynamic-workflow --enable \
  --expose-tool workflow \
  --allow-command workflow \
  --allow-skill dynamic-workflow-orchestration \
  --allow-prompt dynamic-workflow-script \
  --strict
ai4j-cli extension check dynamic-workflow --enable \
  --expose-tool workflow \
  --allow-command workflow \
  --allow-skill dynamic-workflow-orchestration \
  --allow-prompt dynamic-workflow-script \
  --strict
```

命令入口：

```bash
ai4j-cli extension run --enable dynamic-workflow --allow-command workflow workflow "Audit this repository for duplicated plugin contracts"
```

返回 envelope 的 `source` 会是 `command`，`hostAction` 会是 `synthesize_dynamic_workflow`。这表示宿主可以把自然语言 goal 再交给 Agent 生成脚本，也可以直接拒绝、排队或转成人工审批。

## 8. 当前边界和后续方向

首版没有实现 QuintinShaw 版本里的这些宿主级能力：

- 后台 run manager / `/workflows` TUI
- resume journal
- per-agent model tier routing
- per-agent git worktree isolation
- saved workflow command registry
- 内置 deep research / adversarial review 命令

这些能力需要 `ai4j-agent` / `ai4j-coding` 持有模型客户端、session、工具注册表、审批策略和 workspace 上下文后再实现。插件包只负责把“请求一个动态 workflow”的稳定资源面交给生态使用。

## 9. 推荐阅读

- [Plugin Packages](/docs/core-sdk/extension/plugin-packages)
- [Plugin Recipes](/docs/core-sdk/extension/plugin-recipes)
- [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
- [Agent / Orchestration](/docs/agent/workflow-stategraph)
- [Coding Agent / Tools and Approvals](/docs/coding-agent/tools-and-approvals)
