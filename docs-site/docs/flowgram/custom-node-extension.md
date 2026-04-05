---
sidebar_position: 5
---

# Flowgram 自定义节点扩展

本页讲的是后端这一半：如何让一个自定义节点在 AI4J 的 Flowgram runtime 中真正执行。

如果你还没定义前端节点，先看：

- [前端自定义节点开发](/docs/flowgram/frontend-custom-node-development)

---

## 1. 扩展入口在哪里

当前最核心的扩展点是：

- `FlowGramNodeExecutor`

你只要实现这个接口，并把它注册进 Spring 容器，运行时就会把它纳入可执行节点集合。

---

## 2. 最小实现模式

从集成测试可以确认，一个最小后端自定义节点大致长这样：

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
            String text = String.valueOf(context.getInputs().get("text"));
            return FlowGramNodeExecutionResult.builder()
                    .outputs(java.util.Collections.<String, Object>singletonMap(
                            "result",
                            "custom:" + text.toUpperCase(java.util.Locale.ROOT)
                    ))
                    .build();
        }
    };
}
```

这意味着：

- 节点类型通过 `getType()` 决定；
- 节点真正运行逻辑写在 `execute(...)`；
- 输出字段交给 `FlowGramNodeExecutionResult.outputs(...)`。

---

## 3. 一套完整的前后端对应示例

只给后端执行器还不够，下面给一套最小的 `TRANSFORM` 对应关系。

### 3.1 前端节点类型

前端定义：

```ts
export enum WorkflowNodeType {
  Transform = 'transform',
}
```

### 3.2 前端节点注册

```tsx
export const TransformNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Transform,
  info: {
    icon: '/icons/transform.svg',
    description: 'Normalize text and return a transformed result.',
  },
  meta: {
    size: { width: 360, height: 320 },
  },
  onAdd() {
    return {
      id: 'transform_xxx',
      type: WorkflowNodeType.Transform,
      data: {
        title: 'Transform_1',
        inputsValues: {
          text: { type: 'template', content: '' },
          mode: { type: 'constant', content: 'upper' },
        },
        inputs: {
          type: 'object',
          required: ['text'],
          properties: {
            text: { type: 'string' },
            mode: { type: 'string' },
          },
        },
        outputs: {
          type: 'object',
          required: ['result'],
          properties: {
            result: { type: 'string' },
          },
        },
      },
    };
  },
  formMeta: defaultFormMeta,
};
```

### 3.3 前后端类型映射

如果前端 type 用的是 `transform`，后端执行器 type 用的是 `TRANSFORM`，你还要补：

```ts
const BACKEND_TYPE_MAP: Record<string, string> = {
  transform: 'TRANSFORM',
};
```

### 3.4 后端执行器

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
            String text = String.valueOf(context.getInputs().get("text"));
            String mode = String.valueOf(context.getInputs().get("mode"));
            String result = "upper".equalsIgnoreCase(mode)
                    ? text.toUpperCase(java.util.Locale.ROOT)
                    : text.toLowerCase(java.util.Locale.ROOT);
            return FlowGramNodeExecutionResult.builder()
                    .outputs(java.util.Collections.<String, Object>singletonMap("result", result))
                    .build();
        }
    };
}
```

这才是一套真正可运行的自定义节点。

---

## 4. 运行时会给你什么

`FlowGramNodeExecutionContext` 当前通常包含：

- `taskId`
- `node`
- `inputs`
- `taskInputs`
- `nodeOutputs`
- `locals`

常见用途：

- `inputs`：当前节点已经解析完成的输入
- `taskInputs`：整条任务最初输入
- `nodeOutputs`：已完成节点的输出快照

---

## 5. 如何让前端用你的节点

只注册后端执行器还不够，前端或调用方还必须知道：

- 节点 `type` 是什么；
- 节点接收哪些输入；
- 节点产出哪些输出；
- `inputsValues` 如何构造引用或常量。

建议至少约定三件事：

1. 节点类型命名规范，例如全大写 `TRANSFORM`
2. 节点 `data.inputs` / `data.outputs` schema
3. 节点 UI 表单和后端 schema 一一对应

---

## 6. 什么时候应该自定义节点，而不是硬塞进 LLM

以下情况更适合自定义节点：

- 逻辑是稳定规则，不需要模型推理；
- 需要强约束输入输出；
- 要访问企业内部系统；
- 要复用现有 Java 服务；
- 要单独监控节点执行结果和性能。

以下情况更适合保留在 `LLM` 节点：

- 主要是自然语言理解或生成；
- 输出不可严格结构化；
- 规则本身经常变化，靠提示词调整更快。

---

## 7. 推荐做法

- 先让节点只做一件事；
- 输出字段尽量稳定、可复用；
- 节点失败时给明确异常信息；
- 节点命名与前端名称分离，`type` 保持稳定；
- 对外部系统调用尽量放进专属节点，而不是写进 LLM 提示词。

---

## 8. 下一步

如果你已经能写后端节点，下一步通常会进入两条路线：

1. 给前端画布补节点 schema 与表单协议；
2. 给节点执行加权限、任务归属和结果持久化。

继续看：

1. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
2. [前端工作流如何在后端执行](/docs/flowgram/workflow-execution-pipeline)
2. [Agent、Tool、知识库与 MCP 接入](/docs/flowgram/agent-tool-knowledge-integration)
