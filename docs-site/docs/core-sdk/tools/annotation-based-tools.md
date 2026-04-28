# Annotation-based Tools

AI4J 推荐用注解声明本地工具，不是因为“写起来省几行”，而是因为这能把 Java 类型、参数说明和模型 tool schema 固定到同一条链上。

如果只写“有三个注解”，其实没有讲出来这套设计为什么成立。

## 1. 三个核心注解分别负责什么

- `@FunctionCall`：定义工具名和工具描述
- `@FunctionRequest`：标记参数载体
- `@FunctionParameter`：定义字段说明和 required 语义

这三层分别对应模型真正关心的三件事：

- 这个工具叫什么
- 输入参数长什么样
- 每个字段是什么意思

## 2. 源码入口

最关键的实现都在 `ToolUtil`：

- `scanFunctionTools()`
- `getFunctionEntity(...)`
- `setFunctionParameters(...)`
- `createPropertyFromType(...)`

从这里可以直接看出，AI4J 的注解不是“装饰”，而是 schema 生成协议的一部分。

## 3. 为什么不是手写 schema

如果完全手写 tool schema，通常会出现：

- Java 字段变了，schema 忘了改
- 字段描述散落在多个地方
- `Chat`、`Responses`、Agent runtime 各自维护一份工具定义

注解方案的价值就在于：

- Java 类型系统和工具 schema 绑定
- 参数说明贴着实现
- 一套定义可以复用到多个 runtime

## 4. 参数 schema 是怎么生成的

`ToolUtil` 会：

1. 扫描所有 `@FunctionCall`
2. 找到对应内部 `@FunctionRequest`
3. 读取字段上的 `@FunctionParameter`
4. 根据字段类型生成 provider 可接受的参数定义

这里尤其重要的是 `createPropertyFromType(...)`，它把 Java 类型映射成模型能理解的 schema 语义。

也就是说，AI4J 这套方案本质上是“**用 Java 类型系统生成模型工具契约**”。

## 5. 什么时候特别适合用注解工具

- 本地 JVM 内部工具
- 参数结构稳定
- 希望 `Chat` / `Responses` / Agent 复用同一套定义
- 需要把工具契约长期维护下来

如果是远端能力接入，就更应该看 MCP，而不是硬往本地注解工具里塞。

## 6. 设计摘要

AI4J 的注解工具体系本质上是在用 Java 类型和注解生成模型 tool schema。`@FunctionCall` 定义 identity，`@FunctionRequest` 定义参数对象，`@FunctionParameter` 定义字段约束，最后由 `ToolUtil` 在发送前统一展开，因此工具契约可以长期稳定维护。

## 7. 关键对象

这页真正要对照源码看的对象其实很集中：

- `annotation/FunctionCall.java`
- `annotation/FunctionRequest.java`
- `annotation/FunctionParameter.java`
- `tool/ToolUtil.java`

它们共同构成了 AI4J 注解工具的声明与展开体系。

## 8. 这套方案不负责什么

注解工具负责的是“怎么把本地 Java 能力稳定暴露给模型”，但它不负责：

- 远端服务生命周期管理
- 多服务协议接入
- 模型外权限审批

这些问题如果已经出现，通常就该继续看 `MCP` 或上层 runtime，而不是继续往注解工具里堆逻辑。

## 9. 继续阅读

- [Tools / Function Calling](/docs/core-sdk/tools/function-calling)
- [Tools / Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
