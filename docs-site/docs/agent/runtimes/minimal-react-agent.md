# Minimal ReAct Agent

`Minimal ReAct Agent` 是 `Agent` 章节里最适合起步的 runtime 入口。

## 1. 适合什么场景

- 少量工具调用
- 没有代码执行沙箱
- 不需要显式 workflow 或 team 协作

如果你的任务只是“让模型按需决定是否调用工具并给出结果”，通常先从这里开始最稳。

## 2. 这页应该帮你建立什么心智

先记住三点：

- `ReAct` 是默认通用 runtime，不等于玩具示例
- 重点先把 model、tool registry、memory 边界跑通
- 大部分业务 Agent 都应该先在这一层验证，再决定要不要升级到 `CodeAct` 或编排层

## 3. 推荐下一步

1. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Trace](/docs/agent/observability/trace)
