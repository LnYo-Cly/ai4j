---
sidebar_position: 8
---

# Agent Approval / Permission Policy

`AgentPermissionPolicy` 是 `ai4j-agent` 的工具执行前置策略层。它解决的问题很明确：

> 模型已经决定要调用某个工具，但宿主程序是否允许它现在执行？

这不是一个真实沙箱，也不会创建 VM、容器或远端环境。它只是把工具执行前的权限判断固定成一个小而可测试的 Java API，供普通 Java Agent、后续 Blueprint、CLI/TUI 审批界面和 Sandbox SPI 复用。

## 1. 什么时候需要它

如果你的 Agent 具备工具调用能力，就应该思考这一层。

| 场景 | 是否适合 |
| --- | --- |
| 只调用一次模型，不暴露工具 | 不需要 |
| 只暴露纯查询工具，例如查天气、读只读缓存 | 可选 |
| 暴露写文件、执行命令、发请求、改数据库、触发工作流 | 需要 |
| 想在 CLI/TUI 中让用户审批危险工具 | 需要，P0-D 先提供策略基础 |
| 想接真实远端 sandbox | 需要，但真实 sandbox 属于后续 Sandbox SPI |

没有配置 `permissionPolicy(...)` 时，现有 `ToolExecutor` 行为保持不变。

## 2. 最小示例

下面的例子允许普通工具执行，但禁止 `bash`：

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;

import java.util.Collections;

Agent agent = Agents.react()
        .modelClient(modelClient)
        .toolRegistry(toolRegistry)
        .toolExecutor(toolExecutor)
        .permissionPolicy(
                AgentPermissionPolicies.denyTools(
                        Collections.singleton("bash"),
                        "local shell is disabled for this agent"))
        .executionEnvironment(AgentExecutionEnvironment.LOCAL)
        .build();
```

当模型调用 `bash` 时：

1. runtime 先校验工具调用结构是否合法。
2. 通过结构校验后进入 `AgentPermissionToolExecutor`。
3. policy 返回 `DENY`。
4. delegate `ToolExecutor` 不会执行。
5. runtime 把异常包装成可观察的 `TOOL_ERROR` 工具结果，后续模型轮次可以看到这个失败。

## 3. API 组成

P0-D 新增的核心类型在：

```text
io.github.lnyocly.ai4j.agent.permission
```

| 类型 | 作用 |
| --- | --- |
| `AgentPermissionPolicy` | 策略接口，输入一次工具调用请求，输出决策 |
| `AgentPermissionRequest` | 策略输入，包含工具调用和执行环境元数据 |
| `AgentPermissionDecision` | 策略输出，表达允许、拒绝或需要审批 |
| `AgentPermissionDecisionType` | `ALLOW` / `DENY` / `REQUIRE_APPROVAL` |
| `AgentExecutionEnvironment` | `LOCAL` / `SANDBOX` / `REMOTE_SANDBOX` 元数据 |
| `AgentPermissionToolExecutor` | 包装真实 `ToolExecutor` 的执行前 gate |
| `AgentPermissionException` | policy 拒绝时抛出 |
| `AgentApprovalRequiredException` | policy 要求审批时抛出 |
| `AgentPermissionPolicies` | 常用策略工厂 |

## 4. 自定义策略

你可以实现自己的策略，例如只允许远端沙箱环境执行浏览器工具：

```java
AgentPermissionPolicy policy = request -> {
    if ("browser".equals(request.getToolName())
            && request.getEnvironment() != AgentExecutionEnvironment.REMOTE_SANDBOX) {
        return AgentPermissionDecision.deny("browser must run in remote sandbox");
    }
    return AgentPermissionDecision.allow();
};
```

策略能看到：

- `request.getToolName()`
- `request.getArguments()`
- `request.getCallId()`
- `request.getToolCall()`
- `request.getEnvironment()`

注意：`getEnvironment()` 只是元数据。设置为 `REMOTE_SANDBOX` 不会自动创建远端沙箱，也不会自动把工具路由到远端机器。

## 5. `REQUIRE_APPROVAL` 的语义

`REQUIRE_APPROVAL` 表示：

> 当前工具调用不是永久禁止，但需要宿主或用户批准后才能继续。

P0-D 只提供这个状态，不实现交互式等待。当前 runtime 会把它包装成工具错误结果。这样做的好处是：

- Java API 可以先稳定。
- CLI/TUI 后续可以捕获 `AgentApprovalRequiredException`，显示审批弹窗或命令行确认。
- Blueprint YAML 后续可以把 `approval: safe | ask | deny` 映射到同一套 policy。
- 真实 sandbox provider 后续也可以复用该策略，而不是另起一套审批语义。

## 6. 与 `ToolExecutor` 的关系

`ToolExecutor` 是 `ai4j-agent` 的工具执行边界：

```java
public interface ToolExecutor {
    String execute(AgentToolCall call) throws Exception;
}
```

P0-D 的实现方式是包装它：

```text
AgentRuntime
  -> AgentToolCallSanitizer
  -> AgentPermissionToolExecutor
  -> delegate ToolExecutor
```

也就是说：

- `AgentToolRegistry` 决定模型能看见哪些工具。
- `AgentToolCallSanitizer` 只检查工具调用结构是否合法。
- `AgentPermissionPolicy` 决定合法调用是否允许执行。
- `ToolExecutor` 真正执行工具。

不要把业务授权逻辑塞进 sanitizer。sanitizer 只应该回答“这个调用像不像一个可执行调用”，不应该回答“业务上允不允许”。

## 7. 与插件、Guardrail、SubAgent 的关系

当前 `AgentBuilder` 的执行器装配顺序可以理解为：

```text
base executor
  -> extension tool routing
  -> subagent executor
  -> extension guardrails
  -> permission policy wrapper
```

因为 `AgentPermissionToolExecutor` 是外层包装器，所以普通工具、扩展工具和 subagent 工具都会先经过 permission policy，再进入 delegate 执行链。

需要注意：Team runtime 中存在动态替换 member executor 的路径。如果团队编排需要强制继承同一套工具审批策略，应单独增加 team 场景测试或在 team task 中补强。

## 8. 与 Sandbox 的关系

P0-D 不是 Sandbox SPI。

| 能力 | P0-D 是否提供 |
| --- | --- |
| 判断工具是否允许执行 | 提供 |
| 表达需要审批 | 提供 |
| 标记执行环境是 `LOCAL` / `SANDBOX` / `REMOTE_SANDBOX` | 提供，且只是 metadata |
| 创建 VM / 容器 / microVM | 不提供 |
| 上传 workspace 到远端环境 | 不提供 |
| 在远端执行 shell/file/git/browser | 不提供 |
| 收集 sandbox artifact / screenshot | 不提供 |

真实 sandbox 后续应由类似下面的合同承担：

```text
SandboxProvider
SandboxSession
SandboxSpec
SandboxCommand
SandboxResult
SandboxArtifact
```

权限策略和 sandbox 的关系是：

```text
permission policy 决定能不能执行
sandbox provider 决定在哪里执行以及怎么执行
```

进入 sandbox 不等于自动放开权限。即使工具运行在远端环境，也仍然应该经过 approval / permission policy。

## 9. 常见策略

### 9.1 只允许白名单工具

```java
.permissionPolicy(AgentPermissionPolicies.allowTools(
        new java.util.LinkedHashSet<String>(java.util.Arrays.asList("weather", "search"))))
```

白名单外工具会被拒绝。

### 9.2 禁止一组危险工具

```java
.permissionPolicy(AgentPermissionPolicies.denyTools(
        Collections.singleton("bash"),
        "shell command is disabled"))
```

未命中的工具会放行。

### 9.3 对写操作要求审批

```java
.permissionPolicy(AgentPermissionPolicies.requireApprovalForTools(
        Collections.singleton("write_file"),
        "file write needs user approval"))
```

当前 runtime 会把它变成可观察工具错误；后续 CLI/TUI 可以把它变成真正的用户确认流程。

## 10. 排查问题

### 工具没有被 policy 拦截

先检查工具调用是否通过了结构校验。比如 `bash` 必须有合法参数：

```json
{"command":"echo hi"}
```

如果参数是 `{}`，它会先被 `AgentToolCallSanitizer` 拦下，还没到 permission policy。

### 配置了 `executionEnvironment(REMOTE_SANDBOX)`，但工具仍在本地执行

这是正常的。`executionEnvironment` 只是 policy metadata，不负责路由工具。真实远端执行要等 Sandbox SPI 和 coding tool routing。

### 想要交互式审批

P0-D 只返回 `REQUIRE_APPROVAL` 和异常类型。交互式审批属于 CLI/TUI 或宿主应用的职责。

宿主应用可以选择：

- 当前轮直接返回错误给模型。
- 在自己的 UI 中拦截异常并展示确认。
- 把 pending approval 写入业务队列。
- 审批通过后重新执行对应工具调用。

## 11. 下一步

如果你正在做更完整的 Agent 产品，建议按这个顺序继续：

1. 用 `AgentPermissionPolicy` 固定工具执行前的权限语义。
2. 在 Blueprint 中把工具 approval 字段映射成 policy。
3. 在 CLI/TUI 中把 `REQUIRE_APPROVAL` 渲染成可确认的交互。
4. 在 Sandbox SPI 中把 `AgentExecutionEnvironment` 替换为真实 sandbox binding 摘要。
5. 在 `ai4j-coding` 中让 file / shell / git / browser 工具根据 sandbox binding 路由执行。
