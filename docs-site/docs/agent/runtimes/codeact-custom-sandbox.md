# CodeAct Custom Sandbox

当你已经决定使用 `CodeActRuntime`，下一步最容易变成的工程问题就是：执行环境怎么控。

## 1. 这页关注什么

- 代码能读什么、写什么、执行什么
- 进程、网络、文件系统权限怎么约束
- 错误、超时、审计和回归怎么做

## 2. 为什么它重要

`CodeAct` 的价值来自“代码可执行”，但风险也来自这里。

所以沙箱不是补充项，而是这类 runtime 能不能进生产的关键边界。

## 3. 推荐连读

1. [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Trace](/docs/agent/observability/trace)
