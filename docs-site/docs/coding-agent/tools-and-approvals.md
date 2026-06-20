---
sidebar_position: 6
---

# Tools 与审批机制

如果说 `Coding Agent` 和普通 Agent 最大的外显差异是什么，答案通常不是 prompt，而是 tools。  
但这页真正要讲清楚的，不是“有 4 个内置工具”这么简单，而是：

- 这些工具怎么被装配进去
- 谁真正执行它们
- 当前 session 可见工具面为什么不止 4 个
- 审批到底拦在什么位置
- 审批和工作区边界为什么不是一回事

## 1. 固定内置 Tool 只有四个，但运行面不止四个

`CodingToolRegistryFactory.createBuiltInRegistry()` 当前固定挂进去的本地工具确实只有：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

这四个来自：

- `BuiltInTools.codingTools()`

但模型在某个 session 里最终能看到的工具集合，未必只有这四个。  
`CodingAgentBuilder` 后面还可能继续合并：

- custom tool registry
- `delegate_*` 工具
- `subagent_*` 工具
- `/experimental` 注入的 subagent/team 工具
- MCP tools

所以最准确的说法是：

- 固定内置本地执行工具 = 4 个
- 当前 session 可见工具面 = 固定内置工具 + 运行时注入工具

## 2. 真正把四个工具接上执行器的是哪一层

关键入口不是 registry，而是：

- `CodingAgentBuilder.createBuiltInToolExecutor(...)`

它会把 4 个工具分别路由到：

- `ReadFileToolExecutor`
- `WriteFileToolExecutor`
- `ApplyPatchToolExecutor`
- `BashToolExecutor`

最后用：

- `RoutingToolExecutor`

按 tool name 做分发。

这点很重要，因为它说明 AI4J 这套 coding tools 不是简单注解函数，而是一组专用执行器。

## 3. `read_file` 的真实边界是什么

`ReadFileToolExecutor` 当前会把请求交给：

- `WorkspaceFileService.readFile(...)`

并支持：

- `path`
- `startLine`
- `endLine`
- `maxChars`

其中 `maxChars` 如果没传，会回退到：

- `CodingAgentOptions.defaultReadMaxChars`

也就是说 `read_file` 不是“读全文件”固定语义，而是一个：

- 带范围
- 带长度上限
- 走 workspace file service

的受控读取接口。

再结合 `WorkspaceContext.resolveReadablePath(...)`，它的核心边界是：

- 工作区内可读
- skills 之类的额外只读根目录也可读
- 默认不能随便越过 workspace 去读任意路径

## 4. `write_file` 的真实语义是什么

`WriteFileToolExecutor` 当前支持三种 mode：

- `create`
- `overwrite`
- `append`

它会返回：

- `resolvedPath`
- `mode`
- `created`
- `appended`
- `bytesWritten`

但这里有一个必须写清楚的实现事实：

**它自己的 `resolvePath(...)` 并没有调用 `WorkspaceContext.resolveWorkspacePath(...)`。**

当前行为是：

- 相对路径会落到 workspace root 下
- 绝对路径会被直接标准化后使用

这意味着它和 `read_file` 的边界并不完全对称。  
如果调用方传了绝对路径，当前实现确实可能写到工作区之外。

这也是为什么：

- approval 不能被误写成沙箱
- workspace 约束也不能被误写成所有工具都完全一致

## 5. `apply_patch` 为什么比 `write_file` 更像 coding-native 工具

`ApplyPatchToolExecutor` 做的不是简单“写一段文本”，而是：

1. 校验 patch envelope：
   - `*** Begin Patch`
   - `*** End Patch`
2. 解析：
   - `*** Add File:`
   - `*** Update File:`
   - `*** Delete File:`
3. 对 update hunk 做 anchor 匹配
4. 按文件逐个应用修改
5. 返回结构化 `ApplyPatchResult`

更关键的是，它在文件定位时会走：

- `workspaceContext.resolveWorkspacePath(path)`

也就是说，`apply_patch` 目前比 `write_file` 更严格地受 workspace 边界约束。

这也是为什么 coding 场景里，`apply_patch` 不是“换一种写文件方式”，而是：

**带结构约束、带上下文匹配、带工作区边界的代码编辑工具。**

## 6. `bash` 为什么不能只理解成“跑命令”

`BashToolExecutor` 当前支持动作：

- `exec`
- `start`
- `status`
- `logs`
- `write`
- `stop`
- `list`

这说明 `bash` 在 Coding Agent 里是两套语义的并集：

- 一次性命令执行：`exec`
- 长进程管理：`start/status/logs/write/stop/list`

而且它背后直接连着：

- `LocalShellCommandExecutor`
- `SessionProcessRegistry`

所以 `bash` 不是普通 function tool，而是 session 级进程面入口。

## 7. 当前 session 为什么可能还会出现 `delegate_*`、`subagent_*`、MCP tools

除了固定本地工具，`CodingAgentBuilder` 还会合并：

- `CodingDelegateToolRegistry`
- `SubAgentRegistry`
- custom registry
- MCP registry

并且 `DefaultCodingRuntime` 在派生 child session 时，还会通过：

- `CodingToolPolicyResolver`

按 agent definition 过滤 allowed tool names。

这意味着 tools 在 Coding Agent 里不是一张静态清单，而是一个：

- 基础本地工具面
- 运行时扩展工具面
- 按 session / definition 过滤后的有效工具面

所以阅读工具相关代码时，要区分：

- 注册面
- 执行面
- 策略面

## 8. 审批拦截到底发生在哪一层

审批当前不是：

- 操作系统 hook
- shell wrapper
- JVM agent
- “命令已经执行后再确认”

而是一个非常明确的执行器装饰器模型。

链路是：

1. CLI/ACP 先决定 `ApprovalMode`
2. `DefaultCodingCliAgentFactory` 把 decorator 挂进 `CodingAgentOptions`
3. `CodingAgentBuilder` 在创建内置执行器时调用 `decorate(...)`
4. 真正执行工具前，由 decorator 先判断是否要求审批

所以审批的拦截点是：

- ToolExecutor 组装阶段
- ToolExecutor 调用入口

这个分层很干净，因为它允许：

- 同一套 runtime
- 不同宿主交互方式

共用一套审批语义。

## 9. `CliToolApprovalDecorator` 当前到底拦什么

从实现看，当前规则是：

- `manual`：所有工具调用都审批
- `safe`：
  - `apply_patch` 总是审批
  - `bash` 的 `exec/start/stop/write` 审批
  - `read_file` 默认不审批
  - `write_file` 默认不审批
- `auto`：默认直接放行

这点要按“当前代码”理解，而不是按名字想象。  
很多人会以为 `safe` 等于“所有写操作都拦”，但现阶段并不是这样。

如果你想扩审批范围，正确入口是：

- 换 decorator
- 改 decorator 规则

而不是改 prompt。

## 10. 被拒绝的审批是怎样传回 runtime 的

`CliToolApprovalDecorator` 在审批拒绝时不会静默吞掉，而是抛出带：

- `[approval-rejected]`

前缀的拒绝信息。

然后 `CodingAgentLoopController` 会把这种 tool result 识别为：

- `BLOCKED_BY_APPROVAL`

这就把“宿主交互拒绝执行”和“runtime 停止继续推进”连成了完整语义链。

所以 approval 在 AI4J 里不是 UX 小细节，而是 stop reason 的一部分。

## 11. ACP 的审批为什么又是另一条路径

CLI/TUI 下，审批是终端交互：

- 打印 approval block
- 读 `y/yes`

ACP 下，则是：

- `AcpToolApprovalDecorator`
- `PermissionGateway`
- `session/request_permission`

也就是说 ACP 里的审批本质上是协议往返，不是本地 stdin 交互。

但两条路径仍然共享同一个核心思想：

- 都是 ToolExecutor decorator
- 都在执行前拦截
- 都能把拒绝传回 runtime

## 12. 为什么“审批”与“工作区沙箱”必须分开理解

这是当前文档最容易写错的地方。

审批回答的是：

- “要不要执行这次调用”

工作区边界回答的是：

- “即使执行，这次调用允许访问哪里”

两者不是同一层控制。

比如当前实现里：

- `apply_patch` 严格走 `resolveWorkspacePath(...)`
- `read_file` 走 `resolveReadablePath(...)`
- `write_file` 则有自己更宽松的 `resolvePath(...)`

所以你不能简单说：

- “开了审批就安全”
- “所有工具都被同一套 workspace 沙箱保护”

更准确的说法是：

- 不同工具的路径边界实现并不完全对称
- approval 只是执行前控制，不是文件系统隔离层


## 13. Sandbox routing 当前做到哪一步

`ai4j-coding` 已经有了 P3 首切片：当宿主通过 `CodingAgentBuilder.sandbox(SandboxSession)` 绑定 live sandbox 后，`bash action=exec` 会通过 `SandboxShellCommandExecutor` 调用 `SandboxSession.execute(SandboxCommand)`。

这条链路只改变 foreground shell 命令的执行位置：

```text
无 sandbox -> LocalShellCommandExecutor -> host shell
有 sandbox -> SandboxShellCommandExecutor -> SandboxSession.execute(...)
```

返回结果会带上 `executionEnvironment`、`sandboxSessionId`、`sandboxProviderId`，方便 CLI/TUI、日志或上层宿主展示“这次命令到底在哪执行”。

注意：这不等于所有工具都已经远端化。`read_file`、`write_file`、`apply_patch`、后台进程、browser、git/project run 等仍需要后续切片逐步接入。完整边界见 [Sandbox Routing](/docs/coding-agent/sandbox-routing)。

## 14. 当前最稳的扩展位置在哪里

如果你要把 Coding Agent 接进企业环境，最稳的扩展入口通常是：

- 自定义 `toolRegistry`
- 自定义 `toolExecutor`
- 自定义 `ToolExecutorDecorator`

它们分别适合：

- 控制模型可见工具面
- 改写工具实际执行逻辑
- 统一挂审批、审计、限流、鉴权

其中一个很重要的约束是：

- 传了 custom `toolRegistry`，就必须同时提供匹配的 `toolExecutor`

否则工具面和执行面会脱节。

## 15. 最容易踩坑的 5 个点

### 15.1 把“固定内置工具”理解成“全部可见工具”

当前 session 工具面可能还包含 delegate、subagent、MCP 工具。

### 15.2 把审批理解成操作系统级 hook

它本质上只是 ToolExecutor decorator。

### 15.3 把 `safe` 模式想得比当前实现更严格

当前并不是所有写操作都会自动审批。

### 15.4 把 `write_file` 和 `apply_patch` 的路径边界想成一致

当前实现并不完全对称。

### 15.5 只设计 registry，不设计 executor

工具暴露面和执行面必须同时考虑。

## 16. 这页最该记住的结论

AI4J 当前的 Coding Agent tools 机制，不是“4 个函数 + 一个确认框”，而是一整套运行面：

- 用 registry 决定模型看见什么
- 用专用 executor 决定本地怎么执行
- 用 decorator 决定执行前怎样审批
- 用 policy resolver 决定某个子 session 允许用哪些工具

而 approval 与 workspace 边界又是两套不同控制。  
把这几层分清，才能真正理解 Coding Agent 的可控性来自哪里。

## 17. 继续阅读

1. [会话、流式与进程](/docs/coding-agent/session-runtime)
2. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
3. [Sandbox Routing](/docs/coding-agent/sandbox-routing)
4. [Runtime 架构](/docs/coding-agent/runtime-architecture)
