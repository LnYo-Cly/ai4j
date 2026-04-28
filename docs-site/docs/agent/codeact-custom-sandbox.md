---
sidebar_position: 8
---

# CodeAct：自定义代码沙箱执行器

这页讲的不是“怎么把代码跑起来”，而是 CodeAct 在 AI4J 里的真实执行边界，以及你应该在哪一层替换成自己的沙箱。

如果这个边界没看清，最容易出现两类误判：

- 以为默认 `CodeExecutor` 已经是强隔离沙箱
- 以为自定义执行器只是在“换个解释器”，不会影响 Agent 收口语义

这两种理解都不对。

## 1. 先抓住 6 个关键设计决策

### 1.1 CodeAct 的唯一代码执行扩展点就是 `CodeExecutor`

当前接口非常窄：

```java
public interface CodeExecutor {
    CodeExecutionResult execute(CodeExecutionRequest request) throws Exception;
}
```

`CodeActRuntime` 并不关心你是：

- 本地解释器
- 容器
- 远程沙箱服务
- 外部作业系统

它只关心两件事：

1. 你拿到 `CodeExecutionRequest`
2. 你返回标准 `CodeExecutionResult`

### 1.2 默认执行器不是“强安全沙箱”，只是默认宿主实现

`AgentBuilder` 的默认 `CodeExecutor` 选择是：

- Java 8 -> `NashornCodeExecutor`
- 更高版本 -> `GraalVmCodeExecutor`

这层默认实现的定位是：

- 让 CodeAct 开箱可跑
- 给模型提供代码执行 + 工具桥接能力

它不是：

- 容器级隔离
- syscall 级隔离
- 多租户强安全执行环境

### 1.3 默认语言能力和 Java 版本强绑定

当前默认执行器的语言边界非常具体：

- `NashornCodeExecutor` 只接受 JavaScript
- `GraalVmCodeExecutor` 当前只接受 Python

如果语言不匹配，会直接返回错误结果，而不是自动切换。

所以“CodeAct 支持 Python 和 JS”这句话本身不完整，必须补上前提：

- 取决于当前注入的是哪一个 `CodeExecutor`

### 1.4 工具调用桥接发生在执行器内部，不在 runtime 外面

`CodeActRuntime` 会把：

- `toolNames`
- `toolExecutor`
- `user`

打进 `CodeExecutionRequest`。

默认执行器再把它们桥接成：

- `callTool(...)`
- 每个工具名对应的 helper function

也就是说，CodeAct 的“代码里调工具”不是 runtime 直接解释 Python/JS，而是执行器自己把工具桥接埋进执行环境。

### 1.5 `CodeExecutionResult` 的合同会直接改变 Agent 收口语义

`CodeExecutionResult` 只有 3 个字段：

- `stdout`
- `result`
- `error`

但这 3 个字段并不只是给你记录日志，它们会直接影响：

- 这次执行被判定成功还是失败
- runtime 最终返回什么
- `reAct=true` 时下一轮模型看到什么

### 1.6 `CodeActOptions.reAct` 改变的不是执行器接口，而是执行后收口路径

`CodeActOptions.reAct` 默认值是：

- `false`

这意味着默认 CodeAct 更偏“执行即收口”。

只有当：

- `reAct = true`

时，执行结果才会继续回到模型，让模型再产出最终自然语言回答。

## 2. 当前执行链到底怎么走

理解自定义沙箱，先看默认链路：

```text
CodeActRuntime
  -> model 产出 {"type":"code", ...} 或 {"type":"final", ...}
  -> 构造 CodeExecutionRequest
  -> CodeExecutor.execute(...)
  -> CodeExecutionResult
  -> runtime 决定直接收口 or 再回模型
```

关键步骤是：

1. 模型先输出 code/final JSON 协议
2. `CodeActRuntime` 解析成 `CodeActMessage`
3. 如果是 `type=code`
4. runtime 构造一条名为 `code` 的 `AgentToolCall`
5. 再把代码执行真正委托给 `CodeExecutor`
6. 执行结果转成 `CODE_RESULT` 或 `CODE_ERROR`
7. 决定是否直接结束，或继续一轮

所以你替换 `CodeExecutor`，本质上是在替换这条链的“代码执行引擎”，不是替换整个 CodeAct runtime。

## 3. 默认执行器到底做了什么

### 3.1 `NashornCodeExecutor`

主要特征：

- 仅支持 JavaScript
- 用 JDK `ScriptEngine` 跑 Nashorn
- 开单线程池 + `Future.get(timeout)` 做超时
- 把工具桥接注入成：
  - `callTool(name, args)`
  - 每个工具名对应的 JS helper function
- 支持通过 `return` 或 `__codeact_result` 返回最终结果

它是：

- 解释器内执行
- 宿主线程级超时

不是：

- 进程级隔离
- 文件系统 / 网络 / 系统调用隔离

### 3.2 `GraalVmCodeExecutor`

主要特征：

- 当前只支持 Python
- 用 GraalPy `Context` 执行
- 同样用单线程池 + timeout
- 注入 `tools.call(...)` 桥接
- 自动生成每个工具名对应的 Python helper function
- 最终从 `__codeact_result` 或函数返回值里提取结果

它的安全边界同样是：

- 宿主内执行
- 不是强安全沙箱

### 3.3 默认执行器一个容易忽略的细节

两个默认执行器都会根据 `user` 做工具名重写：

- `user_<user>_tool_<name>`

前提是 `user` 非空。

这说明当前 CodeAct 工具桥接不仅传参数，也把用户上下文掺进了工具名解析逻辑。

如果你自定义执行器，最好明确自己要不要保留这层语义。

## 4. `CodeExecutionRequest` 里你真正拿到什么

当前字段包括：

- `language`
- `code`
- `toolNames`
- `toolExecutor`
- `user`
- `timeoutMs`

这些字段分别回答的是：

| 字段 | 真正意义 |
| --- | --- |
| `language` | 模型声称要执行什么语言 |
| `code` | 模型生成的代码文本 |
| `toolNames` | 当前允许暴露给代码环境的工具名集合 |
| `toolExecutor` | 宿主实际工具执行入口 |
| `user` | 当前用户上下文 |
| `timeoutMs` | 这轮执行期望的超时预算 |

最重要的两个字段其实是：

- `toolNames`
- `toolExecutor`

因为它们决定你是做“纯计算沙箱”，还是“带工具桥接的宿主型执行器”。

## 5. `CodeExecutionResult` 合同为什么这么关键

### 5.1 成功与否不是靠异常，而是靠 `error`

`CodeExecutionResult.isSuccess()` 的定义是：

```java
return error == null || error.isEmpty();
```

也就是说，runtime 判定成功失败的第一依据不是有没有抛异常，而是你返回的 `error`。

如果你吃掉异常但忘了填 `error`，runtime 会把这次执行误判为成功。

### 5.2 `stdout` 和 `result` 不是一回事

这两个字段的语义应该分清：

- `stdout`
  - 过程输出
- `result`
  - 最终想交给 runtime 消费的值

如果你把所有结果都塞进 `stdout`，runtime 在 `reAct=false` 的时候可能只能退回到更弱的 fallback 行为。

### 5.3 `error` 不只是给日志看

当执行失败时，`CodeActRuntime` 会构造：

- `CODE_ERROR: ...`

并写回 memory。

这意味着 `error` 会直接影响下一轮模型对执行失败的理解。

## 6. `reAct=false` 和 `reAct=true` 的真实差异

### 6.1 `reAct=false`

这是默认模式。

执行器返回后，runtime 会尽可能直接结束。

优先级大致是：

1. 先尝试取成功执行的 `result`
2. 否则退回 `stdout`
3. 再不行退回工具输出 JSON
4. 失败时退回 `CODE_ERROR`

所以在 `reAct=false` 下，你的执行器返回值质量会直接决定最终用户答案质量。

### 6.2 `reAct=true`

执行器返回后，runtime 不会立即把结果当最终答案。

它会把：

- `CODE_RESULT: ...`
  或
- `CODE_ERROR: ...`

以 system message 的形式写回 memory，再让模型继续一轮，把结果整理成：

```json
{"type":"final","output":"..."}
```

所以 `reAct=true` 的收益是：

- 答案更自然
- 模型能对执行结果再解释

代价是：

- 多一轮 token
- 多一轮延迟

## 7. 什么时候应该自定义 `CodeExecutor`

你通常在下面几种情况下该换掉默认执行器：

### 7.1 需要真正的隔离边界

例如：

- 多租户执行
- 不可信代码
- 生产合规要求

### 7.2 需要进程 / 容器 / K8s / 远程执行环境

默认宿主内解释器已经不够。

### 7.3 需要更严格的资源治理

例如：

- CPU
- 内存
- 文件系统
- 网络
- 子进程数量

### 7.4 需要更可控的审计链路

例如：

- 执行前静态检查
- 执行后产出审计记录
- 代码片段归档

## 8. 两种更靠谱的实现模式

### 8.1 模式 A：本地进程沙箱

适合：

- 单机部署
- 起步验证
- 比宿主内解释器更强一些的隔离

核心思路：

1. 校验 `language`
2. 生成临时工作目录
3. 把代码落盘
4. 用受限解释器进程执行
5. 超时杀进程
6. 收集 stdout/stderr
7. 构造标准 `CodeExecutionResult`

这条路线的优点是：

- 简单
- 可控
- 比宿主内执行更容易管超时和清理

缺点是：

- 仍然不是容器级隔离

### 8.2 模式 B：远程沙箱服务

适合：

- 平台化执行
- 多租户环境
- 需要统一审计和资源池

核心思路：

- `CodeExecutor` 本地只做 RPC client
- 远程服务负责真正执行
- 返回统一的 `CodeExecutionResult`

这条路线最大的好处是：

- SDK 不直接执行不可信代码
- 隔离与审计都能集中治理

## 9. 工具桥接在自定义沙箱里应该怎么做

这里有两条路线，必须自己选清楚。

### 9.1 纯计算模式

不开放工具调用。

这时你可以：

- 忽略 `toolExecutor`
- 只允许纯代码计算

优点是安全模型最简单。

### 9.2 宿主桥接模式

把 `toolExecutor` 暴露给代码环境中的桥接函数。

这时要明确处理：

- 工具白名单
- 参数序列化
- 用户上下文
- 超时与并发
- 越权调用

一个非常现实的建议是：

- 开源默认策略应偏保守
- 默认不开高风险工具
- 参数校验不能只靠模型自觉

## 10. 一个更稳的实现骨架应该考虑什么

无论本地还是远程，至少要把下面这些点写进实现：

### 10.1 语言校验

不要相信模型一定会遵守你希望的语言。

### 10.2 超时

`request.getTimeoutMs()` 不该被忽略。

### 10.3 编码

stdout / stderr / result 的编码必须稳定，否则中文输出极易乱码。

### 10.4 结果归一化

明确：

- 哪些值进 `result`
- 哪些值进 `stdout`
- 什么情况下填 `error`

### 10.5 工具调用治理

如果开放工具桥接，执行器本身就是安全边界的一部分。

## 11. 生产环境最少要补哪些安全约束

至少应明确：

1. 时间限制
2. CPU / 内存上限
3. 文件系统范围
4. 网络访问策略
5. 工具白名单
6. 参数校验
7. 审计日志
8. 清理策略

默认执行器只覆盖了其中非常小的一部分。

## 12. 观测时应该看哪几段

如果 trace 已打开，CodeAct 最值得分三段看：

1. 第一轮 `MODEL`
   看模型生成代码用了多久。
2. `TOOL(type=code)`
   看沙箱执行本身用了多久。
3. 下一轮 `MODEL`
   只在 `reAct=true` 时存在，用来看模型如何整理执行结果。

如果这三段混在一起看，很难知道慢点到底在模型还是执行器。

## 13. 最常见的坑

### 13.1 返回了错误，但没填 `error`

runtime 会误判成功。

### 13.2 忽略 `timeoutMs`

执行悬挂时，Agent 体验会非常差。

### 13.3 把 `stdout` 当最终结果

在 `reAct=false` 模式下，这会让最终收口变得不可控。

### 13.4 开了工具桥接，但没做白名单和参数治理

这相当于把宿主能力直接暴露给模型生成代码。

### 13.5 以为默认执行器已经是生产安全沙箱

它不是。

## 14. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutionRequest.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutionResult.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/NashornCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/GraalVmCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`

## 15. 继续阅读

1. [CodeAct Runtime](/docs/agent/codeact-runtime)
2. [Runtime Implementations](/docs/agent/runtime-implementations)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Trace 与可观测性](/docs/agent/trace-observability)
