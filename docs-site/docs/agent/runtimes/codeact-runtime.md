# CodeAct Runtime

`CodeActRuntime` 适合那些“单纯多轮文本推理不够稳定，最好先产出代码再执行”的任务。

## 1. 什么时候该考虑它

- 工具链很多
- 需要中间代码做批处理
- 需要更稳定的数据整理或结构化变换

它的重点不是“更高级”，而是把一部分任务复杂度转移到可执行代码上。

## 2. 和 ReAct 的区别

- `ReAct` 更像边想边调工具
- `CodeAct` 更像先形成一个临时执行程序，再由程序调工具完成任务

当任务里“代码本身”就是稳定中间表示时，`CodeAct` 往往更合适。

## 3. 推荐下一步

1. [CodeAct Custom Sandbox](/docs/agent/runtimes/codeact-custom-sandbox)
2. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
3. [Tools and Registry](/docs/agent/tools-and-registry)
