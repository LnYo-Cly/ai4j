---
title: "Tool Whitelist and Security"
description: "讲清 AI4J 工具安全两层防线：请求级 functions/mcpServices 白名单与 BuiltInToolContext 工作区读写边界，剖析 bash 等高风险 built-in 与待补的审批沙箱治理。"
tags: [concept]
---

# Tool Whitelist and Security

工具安全里最重要的原则不是“模型能不能调”，而是“默认让模型看见什么、让它能碰到什么”。

AI4J 当前基座层采用的是两层防线：

1. 请求级白名单
2. built-in 工具上下文约束

这两层已经很关键，但还远远不是完整治理闭环。文档必须把这条边界写清楚。

## 1. 第一层安全：默认不暴露，显式白名单才暴露

请求里真正决定工具面的是：

- `functions(...)`
- `mcpServices(...)`

随后进入：

```java
ToolUtil.getAllTools(functionList, mcpServerIds)
```

它只会解析你显式传入的名字，不会因为 classpath 上扫描到了某个工具类，就自动把它交给模型。

这就是 AI4J 当前最核心的默认安全心智：

- 默认不开放
- 显式选择才开放

## 2. 为什么这一层白名单不能省

因为在真实工具面里，经常同时存在：

- 只读工具
- 写文件工具
- shell 工具
- 第三方 MCP 写操作工具

如果默认全量暴露，模型面对的就是一个过大的副作用面，而不是“完成当前任务的最小能力集”。

从工具治理角度看，最好的默认值从来不是“自动发现全开”，而是“最小必要暴露”。

## 3. 第二层安全：`BuiltInToolContext`

AI4J 对 built-in coding tools 还额外加了一层宿主上下文：

- `tool/BuiltInToolContext.java`

它当前最关键的字段有：

- `workspaceRoot`
- `allowOutsideWorkspace`
- `allowedReadRoots`
- `defaultReadMaxChars`
- `defaultCommandTimeoutMs`

这意味着 built-in 工具并不是在一个完全无边界的宿主里执行，而是依赖当前上下文对象决定：

- 工作区根目录
- 哪些目录可读
- 是否允许越出工作区
- 读文件和命令执行的默认限制

## 4. 读路径和写路径并不是同一套规则

这是最应该讲清楚的实现细节之一。

### 写路径

像：

- `write_file`
- `apply_patch`
- `bash` 的 `cwd`

最终都依赖：

```java
context.resolveWorkspacePath(path)
```

它的语义是：

- 相对路径以 workspace root 为基准
- 绝对路径也会被 normalize
- 如果 `allowOutsideWorkspace == false`，目标路径必须仍然落在 workspace root 内

也就是说，写相关 built-in 工具默认不允许越出工作区。

#### 写路径的第二道闸：`WorkspacePathGuard`

`BuiltInToolContext.resolveWorkspacePath(...)` 只是工作区边界检查。coding-agent 层（`ai4j-coding`）的写类执行器（`WriteFileToolExecutor` / `EditToolExecutor` / `ApplyPatchToolExecutor`）在落盘前还会过一遍 `WorkspacePathGuard.resolveForWrite(...)`，它在工作区边界之上叠了三层防御：

- **符号链接回路解析**：沿符号链接最多追 `MAX_SYMLINK_DEPTH = 8` 跳，并用 visited-set 检测环。解析出 canonical 路径后**再做一次**工作区边界检查——因为符号链接可能指向工作区外，单纯 normalize 拦不住。命中回路或超深则抛异常，杜绝用软链接逃逸工作区。
- **敏感目录黑名单**：canonical 路径任意一段为 `.ssh` 或 `.aws`、或落在 `.git/hooks/**` 下，一律拒绝写入（防 hook 后门、防凭据目录被改）。注意是精确拦 `.git/hooks`，不影响 `.gitignore` 等其它 `.git` 读写。
- **`excludedPaths` 拒写**：`WorkspaceContext.getExcludedPaths()`（典型如 `.git`、`target`）中的路径即使在工作区内也拒绝写入。

```java
// io.github.lnyocly.ai4j:ai4j-coding:2.4.2
// 写执行器落盘前的统一校验：工作区边界 + 符号链接 + 黑名单 + excludedPaths
Path safe = WorkspacePathGuard.resolveForWrite(workspaceContext, rawPath);
Files.write(safe, content.getBytes(StandardCharsets.UTF_8));
```

这层防御只在写/补丁路径生效；`read_file` 走的是 `resolveReadablePath(...)` 的只读规则，不受黑名单约束。

### 读路径

`read_file` 用的是：

```java
context.resolveReadablePath(path)
```

它的语义更宽一点：

- 允许读 workspace 内路径
- 如果命中了 `allowedReadRoots`，也允许读这些额外只读根

这就是为什么 `read_file` 可以读某些 skill 目录，但 `write_file` 不可以。

## 5. Skill 只读根是怎样进入工具上下文的

这条链很多文档会漏掉，但它正是 skills 和工具安全衔接的地方。

`Skills.createToolContext(...)` 会：

1. 先做 skill discovery
2. 拿到 `DiscoveryResult.allowedReadRoots`
3. 构造 `BuiltInToolContext`
4. 把这些 skill roots 写进 `allowedReadRoots`

因此，skill 并不是简单“告诉模型这里有个 SKILL.md”，而是同时把这些目录注册成：

- 可按需读取
- 但默认只读

这也是 skill 体系能做懒加载而不破坏工作区写边界的关键设计。

## 6. Built-in tools 里哪些风险最大

### `read_file`

风险相对低，但仍然可能泄露：

- 工作区源码
- skill 目录内容
- 意外暴露的敏感文本

### `write_file` / `apply_patch`

风险在于：

- 改动工作区内容
- 产生破坏性修改

虽然默认受 workspace root 限制，但这不等于“业务上就安全”。

### `bash`

这是当前最需要保守对待的 built-in 工具。

:::danger bash 是宿主能力面最大的 built-in
它的 `cwd` 会被限制在 workspace 内，但命令本身仍然可以：

- 读写工作区文件
- 拉起子进程
- 发网络请求
- 产生长时间运行的后台进程

所以 `bash` 不是“轻量文件工具”，而是宿主能力面最大的 built-in 之一。
:::

#### bash 的多动作 API：前台 `exec` + 后台进程管理

`bash` 不是单参数命令执行器。它的 `action` 参数枚举为 `exec`/`start`/`status`/`logs`/`write`/`stop`/`list`，由 `BuiltInToolExecutor.runBash(...)` 路由，后台进程由 `BuiltInProcessRegistry` 托管。这套 API 让模型既能跑自终止命令，也能驱动交互式/长任务进程：

| action | 作用 | 关键参数 |
|--------|------|----------|
| `exec`（默认） | 同步执行一条自终止命令，超时即杀 | `command`、`cwd`、`timeoutMs` |
| `start` | 启动一个后台/交互式进程，返回 `processId` | `command`、`cwd` |
| `status` | 查询某进程快照（`status`/`pid`/`exitCode`/`startedAt`/`endedAt`） | `processId` |
| `logs` | 读取进程累计输出，支持 `offset` 游标分页 | `processId`、`offset`、`limit` |
| `write` | 向后台进程 stdin 写文本 | `processId`、`input` |
| `stop` | 停止后台进程（先 `destroy`，宽限 `processStopGraceMs` 后 `destroyForcibly`） | `processId` |
| `list` | 列出当前上下文托管的所有后台进程快照 | — |

后台进程的输出由 `BuiltInProcessRegistry` 内部的环形缓冲捕获（容量受 `BuiltInToolContext.maxProcessOutputChars` 约束，溢出从头部丢弃并推进 `startOffset`），`logs` 返回的 `nextOffset`/`truncated` 用于增量拉取。进程边界与 `cwd` 一致——`start` 的 `cwd` 同样经 `resolveWorkspacePath(...)` 校验，不会越出工作区。

从安全面看，这意味着 `bash` 的能力面比“跑一条命令”大得多：`start` 可以留下持续占用资源、长期读写工作区或外联网络的后台进程。给模型开放 `bash` 即等于同时开放了后台进程治理，宿主应把 `start`/`write`/`stop` 视作与 `exec` 同级的副作用动作纳入审批与审计。

#### bash 输出的字符集解析（Windows GBK 兜底）

`bash` 的 stdout/stderr 在回流前要先按某个 `Charset` 解码成字符串。`BuiltInToolExecutor`（以及后台进程的 `BuiltInProcessRegistry`、`ai4j-coding` 的 `ShellCommandSupport.resolveShellCharset()`）按下面的顺序解析这个字符集：

1. **显式覆盖优先**：系统属性 `ai4j.shell.encoding`（或环境变量 `AI4J_SHELL_ENCODING`）只要指向一个 JVM 支持的字符集就立即采用，常用于把 Windows 控制台强制钉成 UTF-8。
2. **平台兜底**：未显式指定时，非 Windows 一律按 UTF-8；Windows 则依次尝试 `native.encoding` → `sun.jnu.encoding` → `file.encoding` → `Charset.defaultCharset()`——在中文 Windows 上这通常解析成 **GBK**。

所以同一个 `bash` 工具，在 Linux/macOS 默认返回 UTF-8 文本，在中文 Windows 默认按 GBK 解码。若模型看到乱码，几乎都是控制台实际编码与上述推断不一致，这时用 `-Dai4j.shell.encoding=UTF-8`（或 `AI4J_SHELL_ENCODING=UTF-8`）显式钉死即可，无需改代码。

## 7. `readOnlyCodingToolNames()` 的边界要说清楚

`BuiltInTools` 里当前有：

```java
readOnlyCodingToolNames()
```

它把：

- `bash`
- `read_file`
- `glob`
- `grep`

归到一个只读集合（对应源码 `READ_ONLY_CODING_TOOL_NAMES = {BASH, READ_FILE, GLOB, GREP}`）。

但要注意，这更像一个分类辅助，而不是完整的策略引擎。单靠这个集合本身，并不会自动阻止 `bash` 执行有副作用命令。

真正的副作用治理，仍然要由上层 runtime 决定：

- 这个工具要不要给模型
- 是否需要审批
- 是否允许当前会话执行

## 8. 本地 Tool 和 MCP Tool 的安全面为什么不同

### 本地 Tool 风险面

- 工作区文件系统
- 本地进程能力
- 当前宿主环境

### MCP Tool 风险面

- 外部账号权限
- 远端副作用 API
- 多服务、多租户可见性

所以工具安全不能只看本地注解函数。远程 MCP 同样必须按服务白名单控制，而且通常还需要外部认证和审计。

## 9. Core SDK 现在已经做到了什么

当前基座层已经提供了：

- 请求级工具白名单
- 请求级 MCP 服务白名单
- built-in 工具的 workspace / readable-root 边界
- skill 目录与只读根的联动

这些是第一层安全边界，已经非常有价值。

## 10. 它还没有替你做什么

当前 Core SDK 没有直接负责：

- 人机审批
- 每个用户的权限判定
- 命令级 allow/deny policy
- 第三方账号授权管理
- 高风险动作审计
- OS / container 级沙箱

:::warning
尤其是 `bash`，当前更接近“受 workspace 路径约束的宿主 shell”，不是隔离级执行沙箱。
:::

## 11. 最稳的使用建议

基于当前实现，比较稳的默认策略通常是：

- 优先暴露最小工具集
- 能不用 `bash` 就不用 `bash`
- skill 读取只开放只读根
- 写工具和远程副作用工具单独治理
- 多租户场景下把 MCP 白名单和用户身份一起绑定

## 12. 这页最该记住的结论

AI4J 当前的工具安全，不是“全量自动发现后再补救”，而是：

- 先用白名单收窄模型可见面
- 再用 `BuiltInToolContext` 收窄 built-in 宿主边界

这已经构成了基座层的第一道防线；但审批、鉴权、审计、真正的进程隔离，仍然属于更上层 runtime 和宿主治理问题。

## 继续阅读

- → [BuiltInTools API Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/tool/BuiltInTools.html)（`allCodingToolNames()` / `readOnlyCodingToolNames()` 等内建工具契约）
