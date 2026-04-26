---
sidebar_position: 5
---

# 配置体系

Coding Agent 的配置并不只是“填一个 API Key”，而是要同时解决两件事：

- 当前会话到底该用哪个 provider / protocol / model
- 这些设置应该沉淀到全局、工作区，还是只在本次运行里临时覆盖

把这层关系理顺之后，基本就能稳定管理不同仓库、不同 provider 和不同模型。

---

## 1. 配置层级

当前生效顺序可以理解成四层：

1. CLI 显式参数
2. workspace 配置
3. 全局 profile / MCP 注册表
4. 环境变量、system properties 与内建默认值

推荐职责分工：

- CLI 参数负责“这次临时覆盖”
- workspace 负责“这个仓库默认怎么跑”
- 全局配置负责“长期可复用的 provider / MCP 资产”
- 环境变量负责“密钥和跨环境注入”

---

## 2. 三个核心配置文件

### 2.1 `~/.ai4j/providers.json`

保存所有可复用的 provider profile。

```json
{
  "defaultProfile": "zhipu-main",
  "profiles": {
    "zhipu-main": {
      "provider": "zhipu",
      "protocol": "chat",
      "model": "glm-4.7",
      "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
      "apiKey": "${ZHIPU_API_KEY}"
    },
    "openai-main": {
      "provider": "openai",
      "protocol": "responses",
      "model": "gpt-5-mini",
      "apiKey": "${OPENAI_API_KEY}"
    }
  }
}
```

它适合沉淀：

- 长期稳定的 provider 连接
- 团队约定的模型
- 全局默认 profile

---

### 2.2 `<workspace>/.ai4j/workspace.json`

保存当前仓库的局部偏好。

```json
{
  "activeProfile": "zhipu-main",
  "modelOverride": "glm-4.7-plus",
  "experimentalSubagentsEnabled": true,
  "experimentalAgentTeamsEnabled": true,
  "enabledMcpServers": ["fetch", "mysql-dev"],
  "skillDirectories": [
    ".ai4j/skills",
    "../shared-skills"
  ]
}
```

常见字段：

- `activeProfile`：当前仓库默认使用哪个 profile
- `modelOverride`：当前仓库的模型覆盖
- `experimentalSubagentsEnabled`：是否注入实验性的后台工作 subagent tool
- `experimentalAgentTeamsEnabled`：是否注入实验性的交付团队 subagent tool
- `enabledMcpServers`：当前仓库启用哪些 MCP server
- `skillDirectories`：额外的 skill 根目录

补充说明：

- 这两个 experimental 字段为空时，当前实现按 `true` 处理，也就是 `on (default)`
- `/experimental subagent on|off` 和 `/experimental agent-teams on|off` 会直接修改这两个字段
- 它们控制的是运行时是否注入对应 agent tool surface，而不是 provider/profile 本身

与 `workspace.json` 同级，当前工作区还会产生一些运行时目录：

```text
<workspace>/.ai4j/
  workspace.json
  sessions/
  teams/
    state/<teamId>.json
    mailbox/<teamId>.jsonl
```

这里要区分：

- `workspace.json`：配置
- `sessions/`：coding session 的持久化快照与事件账本
- `teams/state`：agent team 的最近一次结构化状态
- `teams/mailbox`：agent team 成员之间的消息流水

也就是说，`teams/` 属于 runtime artifact，不是 hand-written config。

---

### 2.3 `~/.ai4j/mcp.json`

保存全局 MCP server 注册表。

这份文件负责“有哪些 server 可以被启用”，而不是“每个仓库当前是否启用”。后者仍然在 `workspace.json` 的 `enabledMcpServers` 里声明。

---

## 3. 生效顺序

当前运行时按下面顺序解析：

1. CLI 显式参数
2. workspace 配置
3. `activeProfile`
4. `defaultProfile`
5. 环境变量 / system properties
6. 内建默认值

这样做的意义是：

- provider profile 负责长期沉淀
- workspace 负责仓库级绑定
- CLI 参数负责本轮临时试验

---

## 4. 密钥与环境注入

不建议把真实密钥直接写死在文档或仓库配置里。

推荐做法：

- `providers.json` 中使用 `${OPENAI_API_KEY}` 这类占位表达
- 实际密钥通过环境变量注入
- CI 或桌面宿主场景下，可通过 system properties 或启动参数注入

最重要的一条是：不要把真实 API Key 提交进仓库。

---

## 5. 什么时候该写到哪一层

可以按下面的经验判断：

- 会被多个仓库复用的 provider：写进 `providers.json`
- 只对当前仓库成立的模型试验：写进 `workspace.json`
- 只在这次启动里临时覆盖：用 CLI 参数
- 涉及密钥、环境切换、CI 注入：用环境变量或 system properties

---

## 6. Runtime 行为配置

除了 provider/profile 这类“连接配置”，`Coding Agent` 还有一组直接影响运行时行为的 `CodingAgentOptions`。

典型写法：

```java
CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("gpt-5-mini")
        .workspaceContext(workspaceContext)
        .codingOptions(CodingAgentOptions.builder()
                .autoCompactEnabled(true)
                .compactContextWindowTokens(128000)
                .compactReserveTokens(16384)
                .compactKeepRecentTokens(20000)
                .compactSummaryMaxOutputTokens(400)
                .toolResultMicroCompactEnabled(true)
                .autoContinueEnabled(true)
                .maxAutoFollowUps(2)
                .maxTotalTurns(6)
                .build())
        .build();
```

最常调的不是全部字段，而是这几组：

- compact 预算：
  `autoCompactEnabled`、`compactContextWindowTokens`、`compactReserveTokens`、`compactKeepRecentTokens`、`compactSummaryMaxOutputTokens`
- tool result 压缩：
  `toolResultMicroCompactEnabled`、`toolResultMicroCompactKeepRecent`、`toolResultMicroCompactMaxTokens`
- outer loop：
  `autoContinueEnabled`、`maxAutoFollowUps`、`maxTotalTurns`、`continueAfterCompact`
- 停止条件：
  `stopOnApprovalBlock`、`stopOnExplicitQuestion`
- 保护机制：
  `autoCompactMaxConsecutiveFailures`

可以把它理解成：

- provider/profile 解决“连谁”
- `CodingAgentOptions` 解决“怎么跑”

更深入的 compact / checkpoint 说明见：

- [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)

---

## 7. 常用动作

### 7.1 新建 profile

```text
/provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]
```

### 7.2 保存当前运行时

```text
/provider save <profile-name>
```

### 7.3 切换 profile

```text
/provider use <profile-name>
```

### 7.4 覆盖模型

```text
/model <name>
/model reset
```

---

## 8. 相关专题

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [Skills 使用与组织](/docs/coding-agent/skills)
3. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
4. [命令参考](/docs/coding-agent/command-reference)
5. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
