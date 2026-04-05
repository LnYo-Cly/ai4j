---
sidebar_position: 6
---

# 前端自定义节点开发

如果你要做自己的 Agentic 工作流平台，后端只会写 `FlowGramNodeExecutor` 还不够。

前端还必须同时定义：

- 节点类型
- 节点注册器
- 节点默认数据
- 表单元数据
- 前后端类型映射

这页专门讲前端这半边。

---

## 1. 前端节点的最小组成

在 `ai4j-flowgram-webapp-demo` 里，一个节点通常至少有这几层：

- `src/nodes/constants.ts`
- `src/nodes/<type>/index.tsx`
- 可选的 `form-meta.tsx`
- `src/nodes/index.ts`

对应关系可以理解成：

- `constants.ts`：定义前端节点 type
- `index.tsx`：定义 node registry 和 `onAdd`
- `form-meta.tsx`：定义右侧表单渲染、校验、effects
- `nodes/index.ts`：把节点注册到编辑器

---

## 2. 先定义前端节点类型

当前 demo 里的前端节点类型都集中在：

- `ai4j-flowgram-webapp-demo/src/nodes/constants.ts`

例如：

```ts
export enum WorkflowNodeType {
  Start = 'start',
  End = 'end',
  LLM = 'llm',
  HTTP = 'http',
  Code = 'code',
  Tool = 'tool',
  Knowledge = 'knowledge',
  Variable = 'variable',
}
```

如果你要新增自定义节点，第一步通常是先补一个 type。

例如：

```ts
export enum WorkflowNodeType {
  // ...
  Transform = 'transform',
}
```

---

## 3. 节点注册器怎么写

前端节点注册器的真实类型是：

- `FlowNodeRegistry`

它在 demo 中至少包含这些关键字段：

- `type`
- `info`
- `meta`
- `onAdd`
- `formMeta`

一个最小 `TRANSFORM` 节点前端注册器可以写成：

```tsx
import { nanoid } from 'nanoid';
import { FlowNodeRegistry } from '../../typings';
import { WorkflowNodeType } from '../constants';
import { defaultFormMeta } from '../default-form-meta';

let index = 0;

export const TransformNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Transform,
  info: {
    icon: '/icons/transform.svg',
    description: 'Normalize text and return a transformed result.',
  },
  meta: {
    size: {
      width: 360,
      height: 320,
    },
  },
  onAdd() {
    return {
      id: `transform_${nanoid(5)}`,
      type: WorkflowNodeType.Transform,
      data: {
        title: `Transform_${++index}`,
        inputsValues: {
          text: {
            type: 'template',
            content: '',
          },
          mode: {
            type: 'constant',
            content: 'upper',
          },
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

这个例子故意保持简单，因为它已经覆盖了最关键的几层：

- 节点类型
- 初始表单值
- 输入 schema
- 输出 schema
- 默认表单面板

---

## 4. 为什么很多节点先复用 `defaultFormMeta`

当前 demo 里，`Tool`、`Knowledge` 这些节点都直接复用了：

- `src/nodes/default-form-meta.tsx`

它已经内置了几类很重要的能力：

- 标题编辑
- `inputsValues.*` 校验
- 输出 schema 展示
- 变量引用校验与同步
- 输出 schema 派生

因此如果你只是想先把新节点跑起来，通常不需要一开始就单独写复杂的 `form-meta.tsx`。

先复用 `defaultFormMeta`，等交互真的有差异时再拆独立面板，成本最低。

---

## 5. 记得把节点注册到编辑器

定义完 registry 之后，还要把它加入：

- `ai4j-flowgram-webapp-demo/src/nodes/index.ts`

例如：

```ts
export const nodeRegistries: FlowNodeRegistry[] = [
  ConditionNodeRegistry,
  StartNodeRegistry,
  EndNodeRegistry,
  LLMNodeRegistry,
  TransformNodeRegistry,
];
```

不注册的话：

- 节点面板不会出现
- 画布也不会识别这个 type

---

## 6. 前后端类型不一致时怎么办

这是最容易漏掉的一步。

前端画布最终不会直接把原始 schema 原封不动发给后端，而是会走：

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`

这里当前会做类型映射，例如：

- `tool -> TOOL`
- `knowledge -> KNOWLEDGE`
- `llm -> LLM`

所以新增节点时你有两个选择。

### 6.1 前后端直接用同一个 type

例如前端和后端都用：

- `TRANSFORM`

这样不需要额外映射。

### 6.2 前端用小写，后端用大写

例如：

- 前端：`transform`
- 后端：`TRANSFORM`

那你必须在 `BACKEND_TYPE_MAP` 里补一条：

```ts
const BACKEND_TYPE_MAP: Record<string, string> = {
  transform: 'TRANSFORM',
};
```

如果忘了这一步，后端 runtime 会收到一个它不认识的节点类型。

---

## 7. 前端节点 schema 要和后端执行器对齐

前端真正要对齐的不是“长得像不像”，而是：

- `inputs.required`
- `inputs.properties`
- `inputsValues`
- `outputs.properties`

比如如果后端执行器要求：

- `text`
- `mode`

那前端节点就必须稳定产出这两个输入。

否则你会遇到的不是“节点渲染问题”，而是运行时报：

- 缺少必填参数
- 输出字段不匹配
- report 里 inputs/outputs 结构和 UI 预期不一致

---

## 8. 一个完整的前端新增路径

推荐顺序：

1. 在 `constants.ts` 定义节点 type
2. 新建 `src/nodes/transform/index.tsx`
3. 先复用 `defaultFormMeta`
4. 把 registry 加进 `nodes/index.ts`
5. 在 `backend-workflow.ts` 处理前后端 type 映射
6. 再去写后端 `FlowGramNodeExecutor`

这个顺序比先写后端更稳，因为你会先把输入输出 schema 想清楚。

---

## 9. 继续阅读

1. [自定义节点扩展](/docs/flowgram/custom-node-extension)
2. [前端工作流如何在后端执行](/docs/flowgram/workflow-execution-pipeline)
3. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)

