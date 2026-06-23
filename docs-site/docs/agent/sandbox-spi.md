---
sidebar_position: 9
---

# Agent Sandbox SPI

`io.github.lnyocly.ai4j.agent.sandbox` 是 `ai4j-agent` 的真实沙箱执行环境抽象。它解决的问题是：

> Agent 已经决定要执行 shell、文件、浏览器或项目命令时，宿主如何把这次执行交给一个真实的隔离环境，并拿回 stdout、stderr、artifact 和事件？

P2-A 提供 Java 8 SPI 和数据模型；P2-B 把非敏感 sandbox 摘要绑定到 `AgentSession`；P2-C 已提供首个真实 provider：Daytona；P2-D 已提供第二个真实 provider：E2B。你仍然可以把同一套 SPI 接到 CubeSandbox、Docker/K8s、公司内部 VM/microVM 或自己的远端执行平台。

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


## 5. P2-B：绑定到 AgentSession

P2-B 在 P2-A SPI 之上新增了 `AgentSessionSandboxBinding`。它不是 live provider，也不会启动 VM；它只把当前 sandbox 的**非敏感摘要**绑定到 `AgentSession`：

```java
SandboxSession sandbox = provider.createSession(spec);

AgentSession session = agent.newSession()
        .bindSandbox(sandbox);

AgentSessionSnapshot snapshot = session.snapshot();
System.out.println(snapshot.getSandboxBinding().getProviderId());
System.out.println(snapshot.getSandboxBinding().getWorkspaceId());
```

这个 binding 会进入：

- `AgentSession.getSandboxBinding()`
- `AgentSession.snapshot()`
- `AgentSession.restore(snapshot)`
- `AgentSessionStore.save/load(...)`
- session event log：`SANDBOX_BOUND`、`SANDBOX_UPDATED`、`SANDBOX_CLEARED`

可以更新或清除状态：

```java
session.updateSandboxStatus(SandboxStatus.CLOSED);
session.clearSandbox();
```

### 安全边界

`AgentSessionSandboxBinding` 只保存摘要字段：providerId、sandboxSessionId、status、profile、image、workspaceId、labels、boundAt、updatedAt。

它不会保存 `SandboxSpec.config`，因为 provider config 可能包含 token、cookie、API key、连接串或租户信息。label 中包含 `secret`、`token`、`key`、`password`、`credential`、`cookie`、`authorization` 等敏感含义的 key 也会被过滤。

也就是说，P2-B 让 session 能“知道自己绑定了哪个 sandbox”，但不会把真实 sandbox provider 的 secret 带进 snapshot、store、event log 或 docs 示例。

## 6. 与 Permission Policy 的关系

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

## 7. 与 Agent Blueprint 的关系

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

## 8. 与 `ai4j-coding` 的关系

P2-A 只落在 `ai4j-agent`。真正影响 coding agent 的是下一阶段：

| 工具 | 无 sandbox | 有 sandbox 后的目标 |
| --- | --- | --- |
| file | 本地 workspace | sandbox workspace |
| shell | 本机 shell | `SandboxSession.execute(...)` |
| git | 本地 git | sandbox git 命令 |
| browser | 宿主浏览器能力 | provider 暴露的 browser capability |
| project run/test | 本地命令 | sandbox command + artifact |

这部分属于 P3 `ai4j-coding` sandbox routing，不在 P2-A 里实现。

## 9. Fake provider 测试

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

## 10. 常见问题

### 有了 Sandbox SPI，就能马上跑远端命令吗？

不能。P2-A 只是接口和数据模型。你还需要一个 provider 实现，后续还要在 `ai4j-coding` 里把 file/shell/git/browser 工具路由到 sandbox。

### AI4J 会官方内置很多 provider 吗？

不会内置一堆 provider。更稳的路径是：AI4J 提供小而稳定的 SPI，并保留少量官方验证过的真实 provider。Daytona 与 E2B 是当前两个官方真实 provider；CubeSandbox、Docker/K8s、内部平台等可以继续由插件、业务方或后续独立任务接入。

### 每个用户应该一个 sandbox，还是共享一个？

默认应按用户/任务/session 隔离可写执行环境。可以共享镜像、依赖缓存或只读模板，但不要让多个用户共享同一个可写 sandbox。

### sandbox 可以替代权限审批吗？

不能。sandbox 降低执行环境风险，permission policy 管控“是否允许执行”。两者应该叠加，而不是互相替代。


## 11. P2-C：Daytona provider

P2-C 在通用 SPI 之上新增了一个真实可用的 Daytona 接入：

```text
io.github.lnyocly.ai4j.agent.sandbox.daytona
```

核心类：

| 类型 | 作用 |
| --- | --- |
| `DaytonaSandboxProvider` | `SandboxProvider` 实现，`providerId=daytona`。 |
| `DaytonaSandboxConfig` | 从环境变量和 `SandboxSpec.config` 读取 Daytona 连接、创建、启动和清理配置。 |
| `DaytonaSandboxClient` | Java 8 `HttpURLConnection` 客户端，调用 Daytona API 和 toolbox execute API。 |
| `DaytonaSandboxSession` | 把 `SandboxCommand` 转成 Daytona process execute 请求，并返回 `SandboxResult`。 |

### 最小使用

推荐把密钥放在环境变量里，不要写进代码、YAML 或日志：

```bash
export DAYTONA_API_KEY="..."
# 可选；不传时使用 Daytona 默认 API URL
export DAYTONA_API_URL="https://app.daytona.io/api"
```

Java 侧只声明 provider、workspace 和非敏感策略：

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxProvider;

SandboxSession session = new DaytonaSandboxProvider().createSession(
        SandboxSpec.builder()
                .providerId("daytona")
                .workspaceId("ai4j-demo")
                .config("createIfMissing", Boolean.TRUE)
                .config("deleteOnClose", Boolean.TRUE)
                .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .command("printf ai4j-daytona-ok")
            .timeoutMillis(30000L)
            .build());
    System.out.println(result.getExitCode());
    System.out.println(result.getStdout());
} finally {
    session.close();
}
```

### 配置来源

| 配置 | 来源 | 说明 |
| --- | --- | --- |
| `DAYTONA_API_KEY` / `apiKey` | env / `SandboxSpec.config` | Daytona API key；生产用法优先 env。 |
| `DAYTONA_API_URL` / `apiUrl` | env / config | Daytona API 地址；不传时使用 SDK 默认值。 |
| `DAYTONA_TOOLBOX_PROXY_URL` / `toolboxProxyUrl` | env / config | 可选；不传时 provider 会查询 toolbox proxy URL。 |
| `DAYTONA_ORGANIZATION_ID` / `organizationId` | env / config | 可选组织/租户 header。 |
| `DAYTONA_TARGET` / `target` | env / config | 可选 Daytona target。 |
| `sandboxId` | config | 附加已有 sandbox。 |
| `sandboxName` / `name` / `workspaceId` | config / spec | 附加或创建 sandbox 的名字。 |
| `createIfMissing` | config | attach 404 时是否创建，默认 `true`。 |
| `deleteOnClose` | config | `close()` 时是否删除 sandbox，默认 `false`。 |
| `snapshot` / `image` | config / spec | Daytona snapshot/image。 |
| `env` | config | 创建 sandbox 时注入的非敏感环境变量。 |
| `connectTimeoutMillis`、`readTimeoutMillis`、`startTimeoutMillis`、`pollIntervalMillis` | config | HTTP 和启动轮询超时。 |

### 当前边界

- 支持 create-or-attach、start/poll、process execute、`deleteOnClose` 清理。
- `SandboxCommand` 的 `command`、`workingDirectory`、`stdin`、`environment`、`timeoutMillis` 会映射到 Daytona toolbox execute 请求。
- `cancel(...)` 暂时返回 `false`；artifact 列表暂时为空，后续应随 Daytona artifact/file API 单独接入。
- Live smoke 属于 `live-provider-opt-in`，通过 `-P live-provider-tests` 显式运行，并且只从环境变量读取密钥。

本地确定性回归：

```bash
mvn -pl ai4j-agent -am "-Dtest=DaytonaSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test
```

真实 Daytona 冒烟（需要环境变量）：

```bash
mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=DaytonaSandboxLiveSmokeTest" -DskipTests=false -DfailIfNoTests=false test
```

## 12. P2-D：E2B provider

P2-D 在通用 SPI 之上新增了第二个真实可用的 E2B 接入：

```text
io.github.lnyocly.ai4j.agent.sandbox.e2b
```

E2B 的执行模型与 Daytona 不同：它通过 E2B 控制 API（`X-API-Key`）创建/销毁沙箱，再通过每个沙箱的执行 host（`Authorization: Bearer`）用 Connect server-streaming `process.Process/Start` 协议执行命令。这些协议细节都被 provider 封装，使用者只需要 `SandboxSession.execute(...)`。

核心类：

| 类型 | 作用 |
| --- | --- |
| `E2BSandboxProvider` | `SandboxProvider` 实现，`providerId=e2b`。 |
| `E2BSandboxConfig` | 从环境变量和 `SandboxSpec.config` 读取 E2B 连接、模板、超时和清理配置。 |
| `E2BSandboxClient` | Java 8 `HttpURLConnection` 客户端：control API（create/delete）+ Connect 帧编解码（`buildProcessFrame` / `parseConnectStream`）。 |
| `E2BSandboxSession` | 把 `SandboxCommand` 映射为 `sh -c` 执行（可选 stdin 管道），返回 `SandboxResult`。 |

### 最小使用

推荐把密钥放在环境变量里，不要写进代码、YAML 或日志：

```bash
export E2B_API_KEY="e2b_..."
# 可选；不传时使用 SDK 默认值（域名 e2b.app / 模板 base / 执行端口 49983）
```

Java 侧只声明 provider、模板和非敏感策略：

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxProvider;

SandboxSession session = new E2BSandboxProvider().createSession(
        SandboxSpec.builder()
                .providerId("e2b")
                .config("templateID", "base")
                .config("timeoutSeconds", Integer.valueOf(300))
                .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .command("printf ai4j-e2b-ok")
            .timeoutMillis(30000L)
            .build());
    System.out.println(result.getExitCode());   // 0
    System.out.println(result.getStdout());     // ai4j-e2b-ok
} finally {
    session.close();   // 默认销毁沙箱
}
```

### 配置来源

| 配置 | 来源 | 说明 |
| --- | --- | --- |
| `E2B_API_KEY` / `apiKey` | env / `SandboxSpec.config` | E2B API key；生产用法优先 env。 |
| `E2B_DOMAIN` / `apiDomain` | env / config | E2B 域名，默认 `e2b.app`。 |
| `E2B_API_URL` / `apiUrl` | env / config | 控制 API 地址；不传时派生为 `https://api.<domain>`。 |
| `E2B_TEMPLATE_ID` / `templateId` / `templateID` / `image` | env / config / spec | 模板，默认 `base`。 |
| `E2B_ENVD_PORT` / `envdPort` | env / config | 执行 host 端口，默认 `49983`。 |
| `E2B_TIMEOUT` / `timeoutSeconds` | env / config | 沙箱存活秒数，默认 `300`。 |
| `E2B_SANDBOX_URL` / `sandboxUrl` | env / config | 可选；覆盖派生的执行 host（例如走自建代理）。 |
| `E2B_ACCESS_TOKEN` / `envdAccessToken` | env / config | 可选；secure 流程的 envd 访问令牌（走 `X-Access-Token`）。不传时用 API key 作 Bearer。 |
| `useShellWrap` | config | 是否用 `sh -c` 包装命令，默认 `true`。 |
| `deleteOnClose` | config | `close()` 时是否删除沙箱，默认 `true`。 |
| `env` | config | 注入的非敏感环境变量。 |
| `connectTimeoutMillis`、`readTimeoutMillis` | config | HTTP 超时。 |

### 执行模型与边界

- 命令默认包成 `sh -c <command>`，支持管道、重定向、多语句（与 Daytona 的 shell 语义对齐）。
- `SandboxCommand.stdin` 非空时，通过 `printf '%s' '<stdin>' | ( <command> )` 管道喂入并保留退出码。`useShellWrap=false` 时改为按空白拆分直接 exec（不支持 stdin）。
- stdout / stderr 是 base64 流式输出；exit code 取自 Connect `end.exitCode`，退出码为 0 时从 `"exit status N"` 状态字符串解析。
- `cancel(...)` 暂时返回 `false`（process `SendSignal` 未接）；`listArtifacts()` 暂时为空（filesystem API 未接）。
- Live smoke 属于 `live-provider-opt-in`，通过 `-P live-provider-tests` 显式运行，并且只从环境变量读取密钥。

本地确定性回归：

```bash
mvn -pl ai4j-agent -am "-Dtest=E2BSandboxClientTest,E2BSandboxProviderTest,E2BSandboxConfigTest" -DskipTests=false -DfailIfNoTests=false test
```

真实 E2B 冒烟（需要环境变量）：

```bash
mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=E2BSandboxLiveSmokeTest" -DskipTests=false -DfailIfNoTests=false test
```

## 13. 下一步

推荐后续顺序：

1. P2-B：已把 `SandboxSpec` / `SandboxSession` 的非敏感摘要绑定到 `AgentSession` snapshot / event log。
2. P2-C / P2-D：已落地 Daytona 与 E2B 两个官方真实 provider，并保留 provider registry / 插件贡献 provider 的后续扩展空间。
3. P3：已让 `ai4j-coding` 的 `bash exec` 根据 sandbox binding 路由执行；file/git/browser/project runner 仍应按边界继续拆小切片。
4. P4：在 CLI/TUI 中显示 `/sandbox status`、provider、workspace、最近执行位置和 artifact。
