# CodeAct Custom Sandbox

一旦你决定使用 `CodeActRuntime`，工程重点马上会从“能不能执行代码”变成“执行环境怎么控”。

## 1. 这页关注的不是 prompt，而是边界

`CodeAct` 的价值来自“代码可执行”，风险也同样来自这里。

你至少要回答：

- 代码能调用哪些工具
- 能不能访问文件系统
- 能不能发网络请求
- 超时和失败怎么处理
- 执行过程如何审计

如果这些边界没有设计清楚，`CodeAct` 很难进入生产。

## 2. 真实扩展点在哪里

关键接口：

- `CodeExecutor`

默认实现：

- `NashornCodeExecutor`
- `GraalVmCodeExecutor`

也就是说，真正的安全边界不在模型层，而在 `CodeExecutor`。

## 3. 你可以控制哪些东西

自定义 `CodeExecutor` 时，通常会控制：

- 允许的语言
- 允许的内置函数
- 是否允许任意 import / process / IO
- tool bridge 是否做 allow-list
- 执行超时
- stdout/stderr 和错误输出怎样返回

这比事后在模型输出上做字符串过滤可靠得多。

## 4. 为什么工具治理和沙箱是同一件事

在 `CodeAct` 里，代码往往会通过 tool bridge 调工具。

因此真正的权限面通常是两层叠加：

- 代码执行器本身允许做什么
- 代码里能调用的 tool names 有哪些

如果其中任何一层放得太宽，运行风险都会扩大。

## 5. 设计建议

- 默认 deny，而不是默认全开
- 限制语言和运行时能力，而不是只限制 prompt
- 把工具 allow-list 和执行器能力边界一起设计
- 让超时、错误、审计成为默认能力，而不是补丁
- 先在最小受控任务里验证，再逐步放大权限

## 6. 和 Coding Agent 审批的区别

这里讲的是 `Agent` 里的代码执行沙箱。

`Coding Agent` 里的 approvals 讲的是本地代码仓工具执行审批。

两者相关，但不等价：

- `CodeAct sandbox`：控制“代码能做什么”
- `Coding Agent approvals`：控制“宿主允许工具执行什么”

如果你准备做本地代码仓代理，还要继续看 `Coding Agent` 的 tools/approval 文档。

## 7. 推荐下一步

1. [Tools and Registry](/docs/agent/tools-and-registry)
2. [Trace](/docs/agent/observability/trace)
3. [Coding Agent / Tools and Approvals](/docs/coding-agent/tools-and-approvals)
