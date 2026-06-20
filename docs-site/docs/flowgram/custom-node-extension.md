---
sidebar_position: 13
---

# Flowgram 自定义节点扩展

这一页只讲后端 executor 这一半。

如果你已经知道前端节点怎么注册，现在真正要解决的问题是：

- runtime 怎样识别你的节点
- executor 实际拿到什么上下文
- 输入是不是已经被解析过
- 输出、异常、状态怎样进入 report / result

## 1. 后端正式扩展点只有一个

最核心的接口就是：

```java
public interface FlowGramNodeExecutor {

    String getType();

    FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception;
}
```

这意味着后端自定义节点的正式定义非常清楚：

- `type`
- `execute(...)`
- `outputs`

没有额外的“神秘注册机制”。

## 2. Runtime 怎样识别你的节点

`FlowGramRuntimeService` 只原生理解：

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

其它类型必须通过 `customExecutors` 注册后才会被识别。

### 2.1 注册发生在什么时候

最常见路径是：

- 你把 `FlowGramNodeExecutor` 声明成 Spring Bean
- `FlowGramAutoConfiguration` 的 registrar 把它注册到 runtime

如果你不走 Spring，也可以手动：

- `runtimeService.registerNodeExecutor(...)`

### 2.2 校验阶段就会拦截不支持的类型

如果 type 没注册，`validate` 阶段就可能直接报：

- `Unsupported FlowGram node type ...`

这很好，因为它把错误暴露在提交前，而不是等运行到节点时才爆炸。

## 3. `getType()` 不只是返回一个字符串

很多人低估了 `getType()`。

实际上它是：

- 前端映射
- workflow schema
- 后端 executor dispatch
- 历史流程兼容性

共同依赖的协议名。

### 推荐做法

- 后端 `getType()` 用稳定大写协议名，例如 `TRANSFORM`
- 前端展示名和协议名分开

### 不推荐

- 用会频繁变化的业务文案当 `type`
- 今天 `Transform`，明天 `TextTransform`

## 4. executor 调用前，runtime 已经做了什么

这一点最关键，也最容易写错文档。

`executeCustomNode(...)` 在真正调用 executor 之前，已经先做了这些工作：

1. 根据节点 `inputsValues` 解析输入
2. 支持 `REF` / `CONSTANT` / `TEMPLATE` / `EXPRESSION`
3. 应用输入 schema 默认值
4. 记录 node inputs
5. 构造 `FlowGramNodeExecutionContext`

因此：

- `context.inputs` 通常已经是解析完成的执行态输入
- 它不是原始前端表单 JSON

这会极大简化自定义 executor 的实现。

## 5. `FlowGramNodeExecutionContext` 里到底有什么

当前上下文对象包括：

- `taskId`
- `node`
- `inputs`
- `taskInputs`
- `nodeOutputs`
- `locals`

### `inputs`

当前节点已经解析完成的输入。

### `taskInputs`

整条任务最初的 root inputs。

### `nodeOutputs`

之前已完成节点的输出快照。

### `locals`

当前局部上下文，尤其在 loop 等场景里很重要。

这说明 executor 一般不需要自己维护全局状态；它要做的是消费当前上下文并产出稳定 outputs。

## 6. 最小后端节点长什么样

一个最小 `TRANSFORM` 节点可以这样写：

```java
@Bean
public FlowGramNodeExecutor transformNodeExecutor() {
    return new FlowGramNodeExecutor() {
        @Override
        public String getType() {
            return "TRANSFORM";
        }

        @Override
        public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) {
            Map<String, Object> inputs = context == null || context.getInputs() == null
                    ? new LinkedHashMap<String, Object>()
                    : context.getInputs();

            String text = String.valueOf(inputs.get("text"));
            String mode = String.valueOf(inputs.get("mode"));
            String result = "upper".equalsIgnoreCase(mode)
                    ? text.toUpperCase(java.util.Locale.ROOT)
                    : text.toLowerCase(java.util.Locale.ROOT);

            Map<String, Object> outputs = new LinkedHashMap<String, Object>();
            outputs.put("result", result);
            return FlowGramNodeExecutionResult.builder()
                    .outputs(outputs)
                    .build();
        }
    };
}
```

这段代码的重点不在“变大写”本身，而在它体现了 runtime contract：

- 读取已解析输入
- 返回稳定 outputs map
- 不关心 controller / facade 细节

## 7. 输出不仅影响下游，也影响 report / result

executor 返回的 `outputs` 会被 runtime 自动记录下来。

这会进入：

- 节点级 report
- workflow outputs 聚合
- 最终 result

因此输出设计必须稳定。

### 推荐

- 业务结果放固定 key，例如 `result`
- 原始外部响应放 `rawResponse`
- 指标放 `metrics`

### 不推荐

- 让同一字段在不同运行里类型变化
- 把日志、业务输出、调试信息全部塞进一个字符串

## 8. 异常如何被系统消费

executor 抛出的异常不会只是打印日志，它会直接影响节点和任务状态。

当前语义大致是：

- 节点状态标记为 `failed`
- 节点 error 被记录
- workflow 状态可能进入 `failed`
- 对应 trace 里会出现失败事件

所以自定义节点的异常信息应该足够清楚，至少能回答：

- 缺了什么输入
- 调了哪个外部依赖
- 是逻辑错误还是超时错误

## 9. 如果你需要更复杂的值解析怎么办

对于大多数普通 custom node，`context.inputs` 已经够用了。

但如果你的节点内部还想解析更复杂的配置对象，例如嵌套模板或自定义结构体，就要清楚一点：

- runtime 只会帮你把节点标准输入解析到 `context.inputs`

如果你自己的配置结构里还埋了模板或引用逻辑，就要自己显式处理，或者复用类似 `FlowGramNodeValueResolver` 的思路。

## 10. 一个好 executor 的设计原则

### 10.1 单一职责

一个节点最好只做一类事，不要在一个 executor 里同时做：

- 拉数据
- 调模型
- 规则处理
- 发通知

### 10.2 输入输出稳定

长期维护成本主要来自 contract 漂移，而不是 `execute(...)` 的代码量。

### 10.3 尽量确定性

平台节点更像稳定函数，不应该像不可预期的小工作流。

### 10.4 明确失败语义

调用方要能从 error 里看出：

- 值缺失
- 参数非法
- 远程服务失败
- 超时 / 重试耗尽

## 11. 什么情况下应该写自定义 executor

更适合：

- 逻辑是稳定规则
- 要接企业内部系统
- 需要强约束输入输出
- 这个能力会被多个流程复用

不一定适合：

- 只是一次性试验
- 只是简单字符串拼接
- 实际更适合放进 `HTTP`、`CODE`、`TOOL` 或 `LLM`

## 12. 这一页和其它页面的边界

- [Custom Nodes](/docs/flowgram/custom-nodes)
  讲前后端整体契约
- [前端自定义节点开发](/docs/flowgram/frontend-custom-node-development)
  讲前端 registry、表单和 type map
- 这一页
  讲 runtime 如何调你写的后端 executor

如果只记一句话：

后端自定义节点的本质，是把一个稳定能力封装成 runtime 可调度、report 可观测、result 可复用的 executor。
