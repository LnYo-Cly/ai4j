---
sidebar_position: 9
---

# Agent Sandbox SPI

`io.github.lnyocly.ai4j.agent.sandbox` 是 `ai4j-agent` 的真实沙箱执行环境抽象。它解决的问题是：

> Agent 已经决定要执行 shell、文件、浏览器或项目命令时，宿主如何把这次执行交给一个真实的隔离环境，并拿回 stdout、stderr、artifact 和事件？

P2-A 只提供 Java 8 SPI 和数据模型，不绑定任何具体云厂商、容器平台或虚拟机实现。你可以把它接到 CubeSandbox、Docker/K8s、E2B、公司内部 VM/microVM 或自己的远端执行平台。

## 1. 它不是什么

Sandbox SPI 不是再加一个普通工具，也不是安全承诺。

| 不是 | 说明 |
| --- | --- |
| 不是 `run_in_sandbox` tool | 它是工具执行环境的 provider/session 合同，不是模型直接看到的一个工具。 |
| 不是本地 permission policy | 权限策略决定能不能执行；Sandbox SPI 决定在哪里执行、怎么取回结果。 |
| 不是内置 VM | AI4J 不在 P2-A 内置 Docker、K8s、浏览器或远端机器。 |
| 不是绕过审批 | 进入 sandbox 不代表自动放开危险能力，仍应经过 `AgentPermissionPolicy`。 |

## 2. 最小 API

P2-A 新增包：

```text
io.github.lnyocly.ai4j.agent.sandbox
```

核心类型：

| 类型 | 作用 |
| --- | --- |
| `SandboxProvider` | 由宿主或插件实现，创建 `SandboxSession`。 |
| `SandboxSession` | 一个可执行命令、列 artifact、取消命令、关闭的隔离执行环境。 |
| `SandboxSpec` | 声明 provider、profile、image、workspace、labels、config。 |
| `SandboxCommand` | 一次执行请求，包含 command、cwd、stdin、timeout、env、metadata。 |
| `SandboxResult` | 一次执行结果，包含 exitCode、stdout、stderr、timeout/cancel、artifact、event。 |
| `SandboxArtifact` | sandbox 产物元数据，例如日志、截图、压缩包。 |
| `SandboxEvent` / `SandboxEventType` | provider-neutral 事件，用于后续 session event log / UI 展示。 |
| `SandboxStatus` | `CREATED` / `RUNNING` / `CLOSED` / `FAILED`。 |
| `SandboxException` | provider/session 操作失败时的 checked exception。 |

## 3. 最小 provider 示例

下面示例只演示合同形状，不代表真实隔离：

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

public class MySandboxProvider implements SandboxProvider {
    @Override
    public String getProviderId() {
        return "my-sandbox";
    }

    @Override
    public boolean supports(SandboxSpec spec) {
        return spec == null
                || spec.getProviderId() == null
                || "my-sandbox".equals(spec.getProviderId());
    }

    @Override
    public SandboxSession createSession(SandboxSpec spec) {
        return new MySandboxSession(spec);
    }
}
```

真实 provider 应该在 `createSession(...)` 中完成隔离环境创建，例如：

- 分配 VM / 容器 / microVM。
- 准备 workspace 或恢复 snapshot。
- 设置超时、网络、文件系统、镜像和资源限制。
- 只返回非敏感的 session 摘要，不把 secret 写进 `SandboxSpec` 或日志。

## 4. 执行命令

```java
SandboxSpec spec = SandboxSpec.builder()
        .providerId("my-sandbox")
        .profile("default")
        .image("java8")
        .workspaceId("task-123")
        .label("project", "ai4j")
        .build();

SandboxSession session = provider.createSession(spec);

SandboxResult result = session.execute(SandboxCommand.builder()
        .command("mvn -pl ai4j-agent -DskipTests=false test")
        .workingDirectory("/workspace")
        .timeoutMillis(120000L)
        .environment("CI", "true")
        .metadata("tool", "project-test")
        .build());

if (Integer.valueOf(0).equals(result.getExitCode())) {
    System.out.println(result.getStdout());
}

for (SandboxArtifact artifact : result.getArtifacts()) {
    System.out.println(artifact.getPath());
}
```

## 5. 与 Permission Policy 的关系

两者是不同层：

```text
Agent / Coding Tool
  -> AgentPermissionPolicy: 能不能执行
  -> SandboxProvider: 在哪里执行
  -> SandboxSession: 怎么执行、怎么返回结果
```

推荐规则：

1. 工具执行前仍然先过 `AgentPermissionPolicy`。
2. policy 可以根据 `AgentExecutionEnvironment.SANDBOX` / `REMOTE_SANDBOX` 做不同决策。
3. 但 `AgentExecutionEnvironment` 只是 metadata；真实路由要等 P3 `ai4j-coding` 接入 `SandboxSession`。
4. 任何 provider 都不应该把 token、cookie、API key 写进 `SandboxSpec.config`、`SandboxEvent.message` 或 artifact 名称。

## 6. 与 Agent Blueprint 的关系

P1 的 YAML 里已经有声明字段：

```yaml
sandbox:
  enabled: true
  provider: my-sandbox
  profile: default
  config:
    image: java8
```

P2-A 仍不让 Blueprint 自动创建 sandbox。后续 P2-B/P3 会把声明转成安全的 `SandboxSpec`，并在 host 显式允许时绑定到 `AgentSession` 或 coding session。

## 7. 与 `ai4j-coding` 的关系

P2-A 只落在 `ai4j-agent`。真正影响 coding agent 的是下一阶段：

| 工具 | 无 sandbox | 有 sandbox 后的目标 |
| --- | --- | --- |
| file | 本地 workspace | sandbox workspace |
| shell | 本机 shell | `SandboxSession.execute(...)` |
| git | 本地 git | sandbox git 命令 |
| browser | 宿主浏览器能力 | provider 暴露的 browser capability |
| project run/test | 本地命令 | sandbox command + artifact |

这部分属于 P3 `ai4j-coding` sandbox routing，不在 P2-A 里实现。

## 8. Fake provider 测试

P2-A 的确定性测试使用内联 fake provider，验证：

- provider 能根据 `SandboxSpec` 创建 `SandboxSession`。
- `SandboxCommand` 能携带 command、cwd、timeout、env、metadata。
- `SandboxResult` 能返回 exitCode、stdout、stderr、artifact 和 events。
- DTO 返回 defensive copies，外部不能篡改内部状态。
- session close 后拒绝继续执行。

本地回归命令：

```bash
mvn -pl ai4j-agent -am "-Dtest=AgentSandboxSpiModelTest" -DskipTests=false -DfailIfNoTests=false test
```

## 9. 常见问题

### 有了 Sandbox SPI，就能马上跑远端命令吗？

不能。P2-A 只是接口和数据模型。你还需要一个 provider 实现，后续还要在 `ai4j-coding` 里把 file/shell/git/browser 工具路由到 sandbox。

### AI4J 会官方内置很多 provider 吗？

不建议。更稳的路径是：AI4J 提供小而稳定的 SPI，官方最多提供 fake/demo provider；第三方或业务团队自己接实际平台。

### 每个用户应该一个 sandbox，还是共享一个？

默认应按用户/任务/session 隔离可写执行环境。可以共享镜像、依赖缓存或只读模板，但不要让多个用户共享同一个可写 sandbox。

### sandbox 可以替代权限审批吗？

不能。sandbox 降低执行环境风险，permission policy 管控“是否允许执行”。两者应该叠加，而不是互相替代。

## 10. 下一步

推荐后续顺序：

1. P2-B：把 `SandboxSpec` / `SandboxSession` 的非敏感摘要绑定到 `AgentSession` snapshot / event log。
2. P2-C：允许第三方插件声明和贡献 `SandboxProvider`。
3. P3：让 `ai4j-coding` 的 file/shell/git/browser/project run/test runner 根据 sandbox binding 路由执行。
4. P4：在 CLI/TUI 中显示 `/sandbox status`、provider、workspace、最近执行位置和 artifact。
