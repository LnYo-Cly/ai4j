# Ask User Plugin

`ai4j-plugin-ask-user` 是 AI4J 的官方样板插件。它展示一件事：**插件可以把 Agent 需要的人类确认或补充信息，表达成结构化请求，由宿主应用负责展示、收集答案和恢复执行**。

它不是 UI 组件，也不会阻塞读取 stdin。它只贡献 tool、command、Skill 和 Prompt 资源。

## 1. 适合解决什么

Agent 在这些场景里不应该继续猜：

- 业务规则缺少关键参数
- 多个执行路径都合理，但后果不同
- 文件、接口、数据库或配置改动需要用户选择
- 默认值存在，但继续执行前最好让宿主确认

`ask-user` 插件把这类问题转成统一 JSON envelope。宿主可以把它渲染成 CLI 提示、Web 表单、IDE 弹窗、消息队列事件或审批任务。

## 2. 引入依赖

如果已经使用 `ai4j-bom`：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
</dependency>
```

如果没有使用 BOM：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
  <version>${ai4j.version}</version>
</dependency>
```

## 3. 启用和暴露

普通 Java：

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .exposeTool("ask_user");
```

如果宿主希望 command、Skill 和 Prompt 也逐项授权：

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .requireExplicitResourceActivation()
        .allowCommand("ask-user")
        .allowSkill("ask-user-collaboration")
        .allowPrompt("ask-user-question")
        .exposeTool("ask_user");
```

Spring Boot：

```yaml
ai:
  extensions:
    enabled:
      - ask-user
    tools:
      expose:
        - ask_user
```

Spring Boot 严格授权：

```yaml
ai:
  extensions:
    enabled:
      - ask-user
    explicit-resource-activation: true
    tools:
      expose:
        - ask_user
    commands:
      allow:
        - ask-user
    skills:
      allow:
        - ask-user-collaboration
    prompts:
      allow:
        - ask-user-question
```

只 `enable("ask-user")` 时，兼容模式下 Skill、Prompt 和 command 会注册进 runtime snapshot，但 `ask_user` 不会进入模型可见工具列表。只有 `exposeTool("ask_user")` 后，Agent / Coding Agent 才能调用它。开启 `requireExplicitResourceActivation()` 或 `ai.extensions.explicit-resource-activation=true` 后，Skill、Prompt 和 command 还需要对应 `allow*` 配置才会进入运行态。

## 4. 它贡献了哪些能力

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| Extension id | `ask-user` | classpath 发现和 enable 使用的插件 ID |
| Tool | `ask_user` | Agent 可调用的结构化提问工具 |
| Command | `ask-user` | CLI / 宿主人手动触发的提问请求入口 |
| Skill | `ask-user-collaboration` | 何时询问用户、如何提问的工作流说明 |
| Prompt | `ask-user-question` | 生成用户问题的 prompt 模板 |

Manifest 还声明了：

```text
vendor: ai4j
permission: ui.prompt
configPrefix: ai4j.extensions.ask-user
```

`ui.prompt` 表示插件会产生需要宿主展示给用户的问题，但插件本身不会打开窗口、读控制台或访问网络。

## 5. Tool 输入

`ask_user` 的输入 schema：

```json
{
  "type": "object",
  "properties": {
    "question": {
      "type": "string",
      "description": "The exact question to show to the user"
    },
    "reason": {
      "type": "string",
      "description": "Why the agent needs this answer before continuing"
    },
    "choices": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Optional short choices the host can render"
    },
    "defaultChoice": {
      "type": "string",
      "description": "Optional recommended default choice"
    },
    "blocking": {
      "type": "boolean",
      "description": "Whether the agent should pause until the host receives an answer"
    }
  },
  "required": [
    "question"
  ]
}
```

示例调用参数：

```json
{
  "question": "Should I create a migration file for this schema change?",
  "reason": "The code change is safe only if the database migration is part of the same release.",
  "choices": [
    "Create migration",
    "Code only",
    "Stop"
  ],
  "defaultChoice": "Create migration",
  "blocking": true
}
```

## 6. Tool 输出

插件返回的是宿主可识别的 JSON envelope：

```json
{
  "type": "ai4j.ask_user.request",
  "source": "tool",
  "tool": "ask_user",
  "status": "pending_user_input",
  "hostAction": "render_question_to_user",
  "blocking": "host_decides",
  "argumentsRaw": "{\"question\":\"Should I create a migration file for this schema change?\",\"reason\":\"The code change is safe only if the database migration is part of the same release.\",\"choices\":[\"Create migration\",\"Code only\",\"Stop\"],\"defaultChoice\":\"Create migration\",\"blocking\":true}"
}
```

`argumentsRaw` 是模型传给 tool 的原始参数字符串。插件不在这里解析 JSON，目的是保证 envelope 本身始终是合法 JSON；宿主如果需要结构化字段，可以按 `ask_user` 的 schema 自行解析 `argumentsRaw`。

`blocking` 的最终语义由宿主决定。AI4J 插件层不假设当前运行在 CLI、Web、IDE 还是服务端队列里。

## 7. Command 路径

接入前可以先看启用计划：

```bash
ai4j-cli extension plan ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
```

如果宿主要用 CLI command 触发：

```bash
ai4j-cli extension run --enable ask-user --allow-command ask-user ask-user "Should I continue with this file rewrite?"
```

返回 envelope 的 `source` 会是 `command`：

```json
{
  "type": "ai4j.ask_user.request",
  "source": "command",
  "command": "ask-user",
  "status": "pending_user_input",
  "hostAction": "render_question_to_user",
  "blocking": "host_decides",
  "arguments": {
    "question": "Should I continue with this file rewrite?"
  },
  "argumentsRaw": "Should I continue with this file rewrite?"
}
```

`extension run` 是人或宿主显式执行 command，不会把 tool 暴露给模型。

## 8. 资源读取

插件内置 Skill：

```bash
ai4j-cli extension resource --enable ask-user --allow-skill ask-user-collaboration skill ask-user-collaboration
```

插件内置 Prompt：

```bash
ai4j-cli extension resource --enable ask-user --allow-prompt ask-user-question prompt ask-user-question
```

这些命令仍然要求 `--enable ask-user`。`--allow-skill` / `--allow-prompt` 会让本次读取进入显式资源授权模式；如果宿主只使用兼容模式，省略 allow 参数后也可以读取已启用插件注册的资源。Coding Agent 启用插件后，也可以把这些资源物化成只读上下文资源，让 Agent 按需读取；严格模式下只有被 allow 的资源会被物化。

## 9. 本地校验

源码仓里可以直接跑：

```bash
mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test
```

这会验证：

- manifest 是否声明完整
- ServiceLoader 是否能发现插件
- `ExtensionValidator` 是否通过
- tool / command 是否返回稳定 envelope
- Skill / Prompt classpath 资源是否可读

## 10. 当前边界

`ai4j-plugin-ask-user` 当前不做这些事：

- 不打开 UI
- 不读取 stdin
- 不阻塞等待用户输入
- 不保存答案
- 不决定 Agent 如何恢复执行
- 不替宿主做审批权限判断

这些属于宿主应用、CLI/TUI、Web UI、IDE 插件或未来更高层 runtime 的责任。插件只负责把“需要问用户”变成可发现、可验证、可路由的 AI4J extension package。
