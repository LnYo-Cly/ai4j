# Custom Nodes

`Custom Nodes` 的核心不是“再加一个节点卡片”，而是把一个能力正式接入 Flowgram 的前后端执行契约。

如果只做前端节点而没有后端 executor，它只是一个画布元素；如果只有后端 executor 而前端没有稳定 schema，它也很难成为真正可用的平台节点。

## 1. 真正的后端扩展点是什么

后端最核心的扩展点只有一个：

```java
public interface FlowGramNodeExecutor {
    String getType();
    FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception;
}
```

这说明自定义节点在后端的正式定义就是：

- 一个稳定的 `type`
- 一段基于 `FlowGramNodeExecutionContext` 的执行逻辑
- 一个 `outputs` map 作为结果

## 2. Runtime 会怎样识别你的节点

`FlowGramRuntimeService` 只原生理解：

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

除此之外的类型，必须通过注册式 executor 被 runtime 识别。

### 2.1 校验阶段就会检查类型

如果节点类型既不是 runtime 内建类型，也没有注册到 `customExecutors`，`validate` 阶段就会直接报：

- `Unsupported FlowGram node type ...`

这意味着自定义节点不是“运行到那里再说”，而是 schema contract 的一部分。

### 2.2 在 Spring Boot 下怎样注册

最常见的方式是：

- 实现一个 `FlowGramNodeExecutor`
- 把它注册成 Spring Bean
- 让 `FlowGramAutoConfiguration` 的 executor registrar 把它注入到 runtime

如果你不走 starter，也可以直接对 `FlowGramRuntimeService.registerNodeExecutor(...)` 编程式注册。

## 3. `FlowGramNodeExecutionContext` 里到底有什么

很多文档只说“拿到上下文”，但不说上下文里有什么。这里要讲具体。

当前上下文字段包括：

- `taskId`
- `node`
- `inputs`
- `taskInputs`
- `nodeOutputs`
- `locals`

这几个对象分别回答了不同问题：

- 当前跑的是哪个任务
- 当前节点的 schema 是什么
- 当前节点已经解析好的输入是什么
- 根任务输入是什么
- 之前节点产出了什么
- 当前局部变量是什么

因此一个设计良好的 custom node，通常不需要自己重新做全局状态管理。

## 4. 输出 contract 也要刻意设计

executor 返回的是：

```java
FlowGramNodeExecutionResult.builder()
    .outputs(...)
    .build();
```

也就是说，自定义节点真正暴露给下游的，是一个 `Map<String, Object>`。

这会直接影响下游如何引用你的结果，因此输出结构必须稳定。

### 不推荐

- 今天返回字符串，明天改成 map
- 让同一个字段有时是数组，有时是对象
- 把调试信息和正式业务输出混在一个字段里

### 更推荐

- 用固定 key 返回正式结果
- 把原始响应单独放在 `raw*` 字段
- 把统计信息单独放在 `metrics` / `meta` 字段

## 5. 自定义节点不是只写后端

要让节点真的可用，至少要同时完成 3 件事。

### 5.1 前端节点定义

你需要在画布侧定义：

- 节点 type
- 表单 schema
- 默认 data
- 输入输出呈现方式

### 5.2 前后端协议对齐

你要确保前端发给后端的类型、字段和执行器读取的字段是一致的。

尤其要检查：

- `backend-workflow.ts` 是否需要类型映射
- 这个节点是否会被误当成 UI-only 节点过滤掉
- 前端字段名和后端读取字段名是否一致

### 5.3 后端 executor

最后才是实际执行逻辑：

- 解析输入
- 调内部服务或外部能力
- 返回稳定 outputs

缺任何一环，节点都只是“半接入”。

## 6. 一个最小自定义节点实现示例

下面这个例子只演示 contract，不代表最终业务设计。

```java
public class EchoNodeExecutor implements FlowGramNodeExecutor {

    @Override
    public String getType() {
        return "ECHO";
    }

    @Override
    public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) {
        Map<String, Object> inputs = context == null || context.getInputs() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(context.getInputs());

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("message", inputs.get("message"));
        outputs.put("taskId", context == null ? null : context.getTaskId());
        return FlowGramNodeExecutionResult.builder()
                .outputs(outputs)
                .build();
    }
}
```

这个例子说明了 3 件事：

- `type` 是稳定协议名
- 节点读取的是已解析输入，不一定要自己处理原始 schema
- 下游拿到的是一个稳定 outputs map

## 7. 如果你想要内置节点同样的引用能力

starter 里的多个 executor 都用了 `FlowGramNodeValueResolver`。它支持：

- `REF`
- `CONSTANT`
- `TEMPLATE`
- `EXPRESSION`

如果你的自定义节点也需要：

- 读取上游节点结果
- 在模板里拼变量
- 解释轻量表达式

你就要考虑复用同样的解析逻辑，而不是把输入当成普通静态 JSON。

否则会出现一个很典型的问题：

- 内置节点能引用 `${nodeA.result}`
- 你的自定义节点却只能拿到字面量字符串

## 8. 设计自定义节点时最该守住的原则

### 8.1 `type` 必须稳定

不要把展示名当成协议名。`type` 一旦被前端 schema、后端 executor、历史流程图同时依赖，就已经成为兼容性边界。

### 8.2 一个节点只做一类事情

不要把“拉数据 + 处理规则 + 调模型 + 发通知”全塞进一个节点。那会让节点既难调试，又难复用。

### 8.3 输入输出比实现细节更重要

节点的长期维护成本，更多来自 contract 漂移，而不是来自 `execute(...)` 里那几十行代码。

### 8.4 错误要清楚

好的节点失败时，应该让前端和平台能快速知道：

- 缺了什么输入
- 调了哪个外部服务
- 为什么失败
- 是否值得重试

### 8.5 尽量保持确定性

如果节点承担的是平台业务能力，它应该更像稳定函数，而不是随机行为体。

## 9. 什么时候不要写自定义节点

有些情况写节点只是把系统复杂化。

不建议优先写自定义节点的情况：

- 其实只是简单字段拼装，`VARIABLE` 足够
- 其实只是调用现成 HTTP 服务，`HTTP` 足够
- 其实只是短脚本转换，`CODE` 足够
- 其实只是模型单步加工，`LLM` 足够

只有当现有节点无法表达你的边界时，custom node 才值得出现。

## 10. 常见错误

### 10.1 只改前端，不改后端

结果通常是画布能拖出来，但 `validate` 就失败。

### 10.2 只改后端，不改前端 schema

结果通常是 executor 有了，但没有人能稳定地把正确字段送进来。

### 10.3 用节点替代工作流

一个节点如果开始自己维护复杂状态机，通常说明这段逻辑本应留在工作流图里，而不是塞进单节点里。

### 10.4 输出结构不稳定

这是最常见也最隐蔽的问题。下游一旦依赖你的字段路径，输出 contract 就不该随意变化。

## 11. 最后一条判断标准

一个自定义节点是否设计得好，不是看它代码写得多快，而是看它是否满足这 4 点：

- 前后端 type 对齐
- 输入输出 contract 稳定
- 执行逻辑边界清楚
- 错误和结果足够可观测

满足这 4 点，它才是平台节点；否则它只是临时拼出来的一段执行代码。
