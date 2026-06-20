---
sidebar_position: 10
---

# CubeSandbox Provider

`CubeSandboxProvider` 是 AI4J 在 `ai4j-agent` 里提供的第一个真实远端 sandbox 适配器。它把 `SandboxProvider` / `SandboxSession` 映射到开源 [TencentCloud/CubeSandbox](https://github.com/TencentCloud/CubeSandbox) 的 CubeAPI 控制面和 envd 进程执行 API。

它的定位很明确：

- **AI4J 不内置 VM**：隔离环境由你部署的 CubeSandbox 集群提供。
- **SDK 只做 adapter**：负责 create / connect / execute / close 的 Java 合同映射。
- **不会自动启用**：只有宿主显式构造 `CubeSandboxProvider` 并调用 `createSession(...)` 或 `connect(...)`，才会访问 CubeSandbox。
- **不保存密钥**：API key 只从 provider builder 或环境变量读取，不从 `SandboxSpec.config` 读取，避免 session snapshot 或 YAML 配置误持久化 secret。

## 1. 适合什么场景

| 场景 | 是否适合 |
| --- | --- |
| 为每个 Agent/Coding Agent 会话创建一个远端 Linux sandbox | 适合 |
| 把 shell / project test / artifact 收集从本机迁到隔离环境 | 适合，当前先提供 `SandboxSession.execute(...)` |
| 连接已有 CubeSandbox sandbox 并执行命令 | 适合，使用 `provider.connect(...)` |
| 直接替代 `ai4j-coding` 的本地 file/git/browser 工具 | 还不是；后续需要 coding tool routing |
| 本地无 CubeSandbox 集群时开箱即用 | 不适合，需要先部署 CubeSandbox 或接入已有 CubeAPI |

## 2. 依赖与配置

Java 侧不新增第三方依赖。适配器使用 Java 8、JDK HTTP/Socket 和项目已有 JSON 依赖。

推荐用环境变量保存真实连接信息：

```bash
export CUBE_API_URL="http://127.0.0.1:3000"
export CUBE_API_KEY="..."              # 可选；也兼容 E2B_API_KEY
export CUBE_TEMPLATE_ID="your-template"
export CUBE_PROXY_NODE_IP="127.0.0.1"  # 本地/内网直连 CubeProxy 时常用
export CUBE_PROXY_PORT_HTTP="80"
export CUBE_SANDBOX_DOMAIN="cube.app"
export CUBE_ENVD_PORT="49983"
```

兼容变量：

| 变量 | 说明 |
| --- | --- |
| `CUBE_API_URL` / `E2B_API_URL` | CubeAPI 地址，默认 `http://127.0.0.1:3000` |
| `CUBE_API_KEY` / `E2B_API_KEY` | 控制面鉴权 token；以 `Authorization: Bearer <key>` 发送 |
| `CUBE_TEMPLATE_ID` | 默认 template ID |
| `CUBE_PROXY_NODE_IP` | 可选。设置后 Java adapter 会直连该 IP:port，并保留 sandbox Host 头 |
| `CUBE_PROXY_PORT_HTTP` | CubeProxy HTTP 端口，默认 `80` |
| `CUBE_PROXY_SCHEME` | `http` 或 `https`。当前 `proxyNodeIp` 直连只支持 `http` |
| `CUBE_SANDBOX_DOMAIN` | sandbox 域名后缀，默认 `cube.app` |
| `CUBE_ENVD_PORT` | envd 数据面虚拟端口，默认 `49983`；如果你的模板/部署按 Go SDK 的 `49999` 暴露，可覆盖为 `49999` |
| `CUBE_TIMEOUT` | sandbox TTL，默认 300 秒 |
| `CUBE_REQUEST_TIMEOUT` | 控制面请求超时，默认 30 秒 |

## 3. 创建并执行命令

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxSession;

CubeSandboxProvider provider = new CubeSandboxProvider();
CubeSandboxSession session = provider.createSession(SandboxSpec.builder()
        .providerId("cubesandbox")
        .image(System.getenv("CUBE_TEMPLATE_ID"))
        .workspaceId("/workspace")
        .label("task", "demo")
        .config("allowInternetAccess", Boolean.FALSE)
        .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .commandId("hello")
            .command("printf ai4j-cubesandbox-ok")
            .workingDirectory("/workspace")
            .timeoutMillis(30000L)
            .build());

    System.out.println(result.getExitCode());
    System.out.println(result.getStdout());
} finally {
    session.close(); // createSession 创建的 sandbox 默认会 DELETE 销毁
}
```

`SandboxCommand.command(...)` 会按 CubeSandbox 官方 SDK 的命令形态执行：

```text
/bin/bash -l -c <command>
```

返回值会映射到 `SandboxResult.exitCode/stdout/stderr/durationMillis/artifacts/events`。

## 4. 连接已有 sandbox

如果 sandbox 已经由外部系统创建，可以只连接：

```java
CubeSandboxSession session = provider.connect("sandbox-id", SandboxSpec.builder()
        .providerId("cubesandbox")
        .workspaceId("/workspace")
        .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .command("pwd")
            .build());
} finally {
    session.close(); // connect(...) 连接的既有 sandbox 默认不销毁远端实例
}
```

这和 `createSession(...)` 的生命周期不同：

| 方法 | 远端 sandbox 来源 | `close()` 默认行为 |
| --- | --- | --- |
| `createSession(...)` | AI4J 调 `POST /sandboxes` 创建 | 调 `DELETE /sandboxes/{id}` 销毁 |
| `connect(...)` | 外部已有 sandbox | 只关闭本地连接，不销毁远端实例 |

## 5. `SandboxSpec.config` 支持项

`SandboxSpec.config` 只适合放非敏感、任务级配置：

| key | 作用 |
| --- | --- |
| `templateId` / `templateID` | template ID；也可用 `SandboxSpec.image` 表达 |
| `envVars` | 创建 sandbox 时注入的环境变量 map |
| `metadata` | 创建 sandbox 时写入的 metadata map；敏感 key 会过滤 |
| `allowInternetAccess` | 为 `false` 时关闭公网访问 |
| `network` | 透传 CubeSandbox network 配置 |
| `timeoutSeconds` / `timeout` | sandbox TTL 秒数 |
| `requestTimeoutMillis` / `requestTimeout` | 控制面请求超时 |
| `closeDestroysSandbox` / `destroyOnClose` | `createSession(...)` 创建的 session close 时是否销毁 |
| `proxyNodeIp` / `proxyNodeIP` | 任务级 proxy node override |
| `proxyPortHttp` / `proxyPortHTTP` / `proxyPort` | 任务级 proxy 端口 |
| `proxyScheme` | `http` 或 `https` |
| `sandboxDomain` / `domain` | sandbox host 域名后缀 |
| `envdPort` / `envdHTTPPort` / `dataPort` | envd 数据面虚拟端口，默认 `49983` |
| `user` | envd Basic auth 用户，默认 `root` |
| `connectEnvelopeLimitBytes` | Connect message 上限，默认 64MiB |

不要把 `apiKey`、token、cookie、连接串放进 `SandboxSpec.config` 或 labels。`CubeSandboxConfig.withSpec(...)` 会有意忽略 `apiKey`；labels 和 metadata 中包含 `secret/token/key/password/passwd/credential/authorization/cookie` 的 key 会被过滤。

## 6. 协议映射

| AI4J 行为 | CubeSandbox 行为 |
| --- | --- |
| `provider.health()` | `GET /health` |
| `createSession(...)` | `POST /sandboxes`，payload 包含 `templateID`、`timeout`、可选 `envVars/metadata/allowInternetAccess/network` |
| `connect(...)` | `POST /sandboxes/{sandboxID}/connect` |
| `session.execute(...)` | envd `POST /process.Process/Start`，`application/connect+json` |
| `session.close()` | 新建 session 默认 `DELETE /sandboxes/{sandboxID}` |

envd Connect stream 使用 5-byte envelope：1 byte flags + 4 byte big-endian size。stdout/stderr 按 CubeSandbox envd 事件中的 base64 字段解码。

AI4J 默认访问 envd 端口 `49983`，这是 CubeSandbox envd/BYOI/files 文档中的默认端口。部分 CubeSandbox SDK/模板会通过 `49999` 访问 Jupyter/code-interpreter gateway；如果你的模板把 `/process.Process/Start` 暴露在 `49999`，请设置 `CUBE_ENVD_PORT=49999` 或 `spec.config.envdPort=49999`。

当设置 `CUBE_PROXY_NODE_IP` 时，Java adapter 会像官方 SDK 的 transport 一样直连 `proxyNodeIp:proxyPortHttp`，同时在 HTTP `Host` 中保留 `<envdPort>-<sandboxID>.<domain>`。这是为了支持本地或内网不能解析 sandbox wildcard DNS 的部署。

## 7. 本地与 live 验证

确定性协议级测试：

```bash
mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test
```

这个测试会启动本地 HTTP server，验证 CubeAPI create/connect/delete、envd Connect envelope、Host header override、stdout 解码、错误帧和 partial frame 失败路径。它不需要真实密钥。

真实 CubeSandbox smoke 是显式 opt-in：

```bash
export AI4J_CUBESANDBOX_LIVE=true
export CUBE_API_URL="..."
export CUBE_TEMPLATE_ID="..."
# 如部署启用鉴权，再设置 CUBE_API_KEY 或 E2B_API_KEY
mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test
```

如果缺少 live 环境变量，测试会通过 JUnit `Assume` 跳过；不要在日志、文档、PR 或 Harness 材料里写出真实 key。

## 8. 当前边界

- 当前只实现命令执行，不包含文件上传/下载、Jupyter code API、snapshot、browser、artifact 下载等高阶能力。
- `proxyNodeIp` + `https` 直连未实现；需要 TLS/SNI 定制时应在后续 adapter 迭代中单独设计。
- `ai4j-coding` 的 file/shell/git/browser 路由还没有全部接到 `SandboxSession`，所以 coding agent 仍需要后续任务才能达到“所有工具都跑在远端 sandbox”的体验。

## 9. 相关文档

- [Agent Sandbox SPI](/docs/agent/sandbox-spi)
- [Approval / Permission Policy](/docs/agent/approval-permission-policy)
- [Coding Agent Sandbox Routing](/docs/coding-agent/sandbox-routing)
- [Remote Agent Runner SPI](/docs/agent/remote-agent-runner-spi)
