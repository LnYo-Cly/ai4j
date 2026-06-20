---
sidebar_position: 10
---

# Remote Agent Runner SPI

`io.github.lnyocly.ai4j.agent.runner` 是 AI4J 给“远端 Agent 产品”预留的运行端合同。它解决的问题是：

> 如果 Agent 不在当前 Java 进程里跑，而是在一个远端 sandbox、VM、容器、浏览器工作区或托管执行环境里跑，Java 宿主应该如何创建 runner、提交请求、接收事件流、拿到结果和 artifact？

当前实现是 **SPI contract + fake-testable DTO**。它不内置任何云服务、VM、Docker、K8s、浏览器或真实托管平台，也不会读取 provider token。

## 1. 它解决什么问题

普通 `Agent` 运行在你的 Java 应用进程里。Sandbox SPI 解决“工具在哪里执行”。Remote Agent Runner SPI 解决更进一步的问题：

```text
Client / Backend / CLI
  -> AgentRunnerProvider
  -> AgentRunnerSession
  -> remote sandbox or hosted workspace
  -> Agent loop + tools + events + artifacts
```

适合这些场景：

- 你想做一个云端 Coding Agent 产品。
- 每个用户或会话需要独立远端工作区。
- Agent 需要在远端安装依赖、运行项目、打开浏览器、截图、收集 artifacts。
- Java 后端只负责控制协议、权限、会话和事件消费，不直接在宿主机执行命令。

## 2. 它不是什么

| 不是 | 说明 |
| --- | --- |
| 不是官方云平台 | AI4J 只定义合同，不提供托管 runner。 |
| 不是内置 Docker/K8s/E2B/CubeSandbox provider | 这些应由第三方插件或业务方实现。 |
| 不是 `SandboxSession.execute(...)` 的替代品 | Sandbox SPI 管工具执行环境；Runner SPI 管完整 Agent loop 的远端运行。 |
| 不是绕过权限系统 | Runner 创建和工具执行仍应由宿主应用或远端 runner 做审批和策略控制。 |
| 不是 token 配置入口 | token、cookie、API key 必须来自宿主 secret store 或 env，不能写入 spec、fixture、文档示例。 |

## 3. 核心类型

包名：

```text
io.github.lnyocly.ai4j.agent.runner
```

| 类型 | 作用 |
| --- | --- |
| `AgentRunnerProvider` | 由宿主或插件实现，创建远端 runner session。 |
| `AgentRunnerSession` | 一个远端 Agent 运行环境，可 `run`、`runStream`、`cancel`、`listArtifacts`、`close`。 |
| `AgentRunnerSpec` | 声明 provider、profile、runner image、workspace、Blueprint、SandboxSpec、labels、config。 |
| `AgentRunnerRequest` | 一次远端 Agent run 请求，包含 runId、input、timeout、metadata。 |
| `AgentRunnerResult` | 最终结果，包含 `AgentResult`、outputText、error、timeout/cancel、artifact、events。 |
| `AgentRunnerEvent` / `AgentRunnerEventType` | provider-neutral 事件流。 |
| `AgentRunnerStatus` | `CREATED`、`STARTING`、`RUNNING`、`IDLE`、`CLOSED`、`FAILED`。 |
| `AgentRunnerException` | provider/session 操作失败时的 checked exception。 |

## 4. 最小 provider 形状

下面示例只演示合同形状，不代表真实远端隔离：

```java
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerException;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerProvider;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerSession;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerSpec;

public class MyRunnerProvider implements AgentRunnerProvider {
    @Override
    public String getProviderId() {
        return "my-runner";
    }

    @Override
    public boolean supports(AgentRunnerSpec spec) {
        return spec == null
                || spec.getProviderId() == null
                || "my-runner".equals(spec.getProviderId());
    }

    @Override
    public AgentRunnerSession createSession(AgentRunnerSpec spec) throws AgentRunnerException {
        return new MyRunnerSession(spec);
    }
}
```

真实 provider 通常会在 `createSession(...)` 里完成：

- 分配远端 workspace / VM / container / microVM。
- 准备 runner image 或启动 runner sidecar。
- 绑定 Blueprint、模型配置、工具权限和 sandbox 策略。
- 建立事件流通道。
- 返回非敏感 session handle。

## 5. 提交一次远端 run

```java
AgentRunnerSpec spec = AgentRunnerSpec.builder()
        .providerId("my-runner")
        .profile("default")
        .runnerImage("ai4j-runner:java8")
        .workspaceId("workspace-123")
        .sandboxSpec(SandboxSpec.builder()
                .providerId("my-sandbox")
                .workspaceId("workspace-123")
                .build())
        .label("project", "ai4j")
        .build();

AgentRunnerSession session = provider.createSession(spec);

AgentRunnerResult result = session.run(AgentRunnerRequest.builder()
        .input("修复测试并给出结果")
        .timeoutMillis(300000L)
        .metadata("source", "web-console")
        .build());

System.out.println(result.getOutputText());
for (SandboxArtifact artifact : result.getArtifacts()) {
    System.out.println(artifact.getPath());
}
```

## 6. 事件流

`runStream(...)` 让宿主 UI 或 CLI 能实时显示远端 runner 状态：

```java
session.runStream(request, event -> {
    switch (event.getType()) {
        case RUN_STARTED:
            System.out.println("run started");
            break;
        case MODEL_DELTA:
            System.out.println(event.getPayload());
            break;
        case ARTIFACT_CREATED:
            System.out.println("artifact: " + event.getMessage());
            break;
        case RUN_FINISHED:
            System.out.println("done");
            break;
        default:
            break;
    }
});
```

首版事件类型包括：

```text
SESSION_CREATED
SESSION_STARTED
RUN_STARTED
MODEL_DELTA
TOOL_CALL_STARTED
TOOL_CALL_FINISHED
ARTIFACT_CREATED
RUN_FINISHED
RUN_FAILED
RUN_CANCELED
SESSION_CLOSED
```

## 7. 与 Sandbox SPI 的关系

两者不是同一层：

```text
Host-driven sandbox tools
  Agent 在宿主 Java 进程里跑
  shell/file/browser 等工具进 SandboxSession

Remote Agent Runner
  Agent loop 本身在远端 runner 里跑
  工具也在同一个远端隔离环境里执行
```

`AgentRunnerSpec.sandboxSpec(...)` 用来告诉 runner 应该使用哪类执行环境，但它不会在 Java 宿主里自动创建真实 sandbox。

## 8. 与 Agent Blueprint 的关系

`AgentRunnerSpec.blueprint(...)` 可以携带一份已经加载和校验过的 `AgentBlueprint`。推荐做法是：

1. 宿主加载 Blueprint。
2. 宿主校验 provider/profile/plugin 权限。
3. 宿主只把非敏感定义传给 runner。
4. runner 从自己的 secret store/env 中解析真实 provider token。

不要把 token、cookie、API key 写进 Blueprint、RunnerSpec 或文档示例。

## 9. 当前验证方式

当前仓库用 fake runner 测试证明合同可用：

```bash
mvn -pl ai4j-agent -am "-Dtest=AgentRunnerSpiContractTest" -DskipTests=false -DfailIfNoTests=false test
```

测试覆盖：

- fake provider 创建 runner session。
- `run(...)` 返回 `AgentRunnerResult`、`AgentResult`、artifact 和事件。
- `runStream(...)` 把事件推给 listener。
- DTO 使用 defensive copies。
- `cancel(...)` 和 `close()` 行为稳定。
- `AgentRunnerEvent` 缺少 type 时失败。

## 10. 下一步

Remote Runner 后续仍应分阶段推进：

1. 在 `ai4j-extension-api` 中已定义第三方 Runner provider 的 manifest-level 插件贡献方式：`ExtensionContributionType.RUNNER_PROVIDER`，见 [Plugin Contribution Contract](/docs/agent/plugin-contribution-contract)。
2. 在 `ai4j-cli` 增加 attach/status/logs 等可见 UX。
3. 在 docs-site 增加云端 Agent 产品化 guide。
4. 等真实 provider 需求明确后，再决定是否新增独立 runner 模块或 starter。

不要在这个阶段直接承诺官方云 runner，也不要把某个中转平台或 sandbox 厂商名称写成 AI4J 的 SDK 概念。
