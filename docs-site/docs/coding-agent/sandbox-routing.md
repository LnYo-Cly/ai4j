---
sidebar_position: 11
---

# Sandbox Routing

这一页说明 `ai4j-coding` 如何把 Coding Agent 的执行型工具接到 `ai4j-agent` 的 Sandbox SPI。

先说当前状态：**P3 首切片已经支持 `bash` 的 foreground `exec` 路由到 live `SandboxSession`**。这不是完整云端 Runner，也不是文件系统级隔离平台；它只是把一次性 shell 命令从本地 `LocalShellCommandExecutor` 切换到 `SandboxSession.execute(...)`。

## 1. 能解决什么问题

默认情况下，Coding Agent 的内置工具在宿主机工作区执行：

```text
bash action=exec
  -> BashToolExecutor
  -> LocalShellCommandExecutor
  -> host shell
```

绑定 sandbox 后，`bash action=exec` 变为：

```text
bash action=exec
  -> BashToolExecutor
  -> SandboxShellCommandExecutor
  -> SandboxSession.execute(SandboxCommand)
```

这让宿主应用可以把高风险命令、项目运行、测试命令先送到外部 VM / 容器 / microVM / 远端沙箱中执行，而不是直接在本机执行。

## 2. 当前真实 API

宿主应用先通过自己的 `SandboxProvider` 创建 live `SandboxSession`，再交给 `CodingAgentBuilder`：

```java
SandboxSession sandboxSession = provider.createSession(SandboxSpec.builder()
        .providerId("my-sandbox")
        .workspaceId("/workspace/session-123")
        .label("purpose", "coding-agent")
        .build());

CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("your-model")
        .workspaceContext(WorkspaceContext.builder()
                .rootPath(projectRoot)
                .build())
        .sandbox(sandboxSession)
        .build();
```

之后新建 coding session 时，AI4J 会做两件事：

1. 在底层 `AgentSession` 上绑定非敏感 sandbox 摘要：`session.getDelegate().getSandboxBinding()`。
2. 把 built-in `bash` 的 `exec` 执行器切换为 `SandboxShellCommandExecutor`。

## 3. `bash exec` 的返回结果

当命令进入 sandbox 后，`bash` 的 JSON 结果会带上执行位置：

```json
{
  "command": "mvn test",
  "workingDirectory": "/workspace/session-123",
  "executionEnvironment": "sandbox",
  "sandboxSessionId": "sandbox-session-id",
  "sandboxProviderId": "my-sandbox",
  "stdout": "...",
  "stderr": "...",
  "exitCode": 0,
  "timedOut": false
}
```

没有绑定 sandbox 时，`executionEnvironment` 为 `local`，并继续走本地 shell。也就是说：

```text
没有 sandbox = 当前本地执行语义不变
有 sandbox = bash exec 进入 SandboxSession.execute(...)
```

## 4. 当前没有做什么

P3 首切片故意很窄，避免把路由边界一次做错。当前还没有把下面这些能力自动路由到 sandbox：

| 工具 / 能力 | 当前状态 |
| --- | --- |
| `read_file` | 仍使用本地 `WorkspaceFileService` |
| `write_file` | 仍使用本地写入执行器 |
| `apply_patch` | 仍在本地 workspace 应用 patch |
| `bash start/status/logs/write/stop/list` | 仍由本地 `SessionProcessRegistry` 管理 |
| browser / screenshot | 尚未在 `ai4j-coding` 中接入 sandbox provider |
| git / project run / test runner | 目前仍表现为 shell 命令或后续能力 |

后续切片应该继续做：

1. sandbox 文件读写抽象；
2. patch 在 sandbox workspace 内应用；
3. 长进程生命周期映射到 provider；
4. browser/screenshot/artifact 收集；
5. CLI `/sandbox` 状态展示和切换。

## 5. 与审批的关系

Sandbox routing 不会替代审批。

即使命令已经路由到 VM / 容器 / 远端环境，宿主仍然应该通过 `ToolExecutorDecorator`、CLI approval、ACP permission gateway 或 `ai4j-agent` permission policy 判断是否允许执行。

两层职责不同：

| 层 | 回答的问题 |
| --- | --- |
| approval / permission | 这次工具调用能不能执行 |
| sandbox routing | 如果能执行，在哪里执行 |

## 6. 安全边界

`AgentSessionSandboxBinding` 只保存 providerId、sandboxSessionId、workspaceId、profile、image、labels 等非敏感摘要。

它不会保存 `SandboxSpec.config`，label 中带有 token/key/password/credential/cookie 等敏感含义的字段也会被过滤。

因此，真实 provider token、cookie、API key、租户连接串应该只存在于宿主应用或 provider 实现中，不能写进文档示例、测试 fixture 或 session snapshot。

## 7. 验证入口

当前首切片的最小回归是：

```bash
mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test
```

完整 coding runtime 回归是：

```bash
mvn -pl ai4j-coding -am -DskipTests=false test
```

## 8. 继续阅读

1. [Agent Sandbox SPI](/docs/agent/sandbox-spi)
2. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
3. [会话、流式与进程](/docs/coding-agent/session-runtime)
4. [AI4J Agent SDK Roadmap](/docs/agent/sdk-roadmap)
