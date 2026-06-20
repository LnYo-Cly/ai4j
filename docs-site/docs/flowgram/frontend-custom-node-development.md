---
sidebar_position: 12
---

# 前端自定义节点开发

这一页只讲前端这半边，不讲后端 executor。

如果 `custom-nodes.md` 讲的是前后端整体契约，这一页讲的是：在 `ai4j-flowgram-webapp-demo` 里，一个新节点怎样真正成为“可编辑、可校验、可序列化、可映射到后端”的前端节点。

## 1. 先明确前端节点真正由哪些部件组成

在当前 demo 里，一个节点通常至少涉及：

- `src/nodes/constants.ts`
- `src/nodes/<type>/index.tsx`
- 可选 `src/nodes/<type>/form-meta.tsx`
- `src/nodes/index.ts`
- `src/utils/backend-workflow.ts`

这几层分别解决不同问题：

- type 是什么
- 节点长什么样、初始数据是什么
- 右侧表单怎么渲染与校验
- 编辑器是否认识这个节点
- 发给后端时怎样映射

少任意一层，节点都只是半成品。

## 2. 第一步先定前端 type

当前前端枚举位于：

- `ai4j-flowgram-webapp-demo/src/nodes/constants.ts`

已有类型包括：

- `start`
- `end`
- `llm`
- `http`
- `code`
- `tool`
- `knowledge`
- `variable`
- `condition`
- `loop`

如果你要加自定义节点，第一步就是先把它变成正式前端 type，例如：

```ts
export enum WorkflowNodeType {
  // ...
  Transform = 'transform',
}
```

这一步看似简单，实际上它定义了编辑器侧的协议名。

## 3. 第二步写节点 registry

前端真正把节点交给编辑器识别的是 `FlowNodeRegistry`。

当前 registry 至少会关心：

- `type`
- `info`
- `meta`
- `onAdd`
- `formMeta`

### 3.1 `type`

决定这是哪类节点。

### 3.2 `info`

决定节点在节点面板里的图标和说明。

### 3.3 `meta`

决定节点默认尺寸等编辑器元信息。

### 3.4 `onAdd`

这是最关键的部分。它决定一个节点拖进画布时，默认会生成什么 JSON。

### 3.5 `formMeta`

决定右侧表单面板怎样渲染、校验和联动。

## 4. `onAdd()` 其实就是你的前端 schema 工厂

很多人会把 `onAdd()` 理解成“只是在画布里加个节点”。这不够准确。

更准确地说：

> `onAdd()` 负责生成这个节点的初始 schema、输入输出约束和默认绑定值。

例如一个最小 `TRANSFORM` 节点：

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
    size: { width: 360, height: 320 },
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

这个例子本质上已经定义了：

- 节点协议名
- 表单默认值
- 输入 schema
- 输出 schema

## 5. `defaultFormMeta` 为什么值得先复用

当前 demo 中多个节点都直接复用了：

- `src/nodes/default-form-meta.tsx`

这份默认 meta 比表面上更有用。

### 5.1 它已经自带基础校验

包括：

- `title` 必填
- `inputsValues.*` 按 required 字段做校验

### 5.2 它已经带了一组关键 effects

包括：

- `syncVariableTitle`
- `provideJsonSchemaOutputs`
- `autoRenameRefEffect`
- `validateWhenVariableSync`
- `listenRefSchemaChange`

这意味着默认表单面板并不只是“能输文本”，而是已经帮你处理了：

- 标题同步
- 输出 schema 派生
- 引用重命名联动
- 变量引用校验
- 引用 schema 变化监听

### 5.3 实际建议

如果你的新节点没有特别强的交互差异，先复用 `defaultFormMeta`，等确认确实需要专属交互时再拆自定义 `form-meta.tsx`。

## 6. 第三步要把节点注册到编辑器

只定义 registry 还不够，还要把它加入：

- `ai4j-flowgram-webapp-demo/src/nodes/index.ts`

当前 `nodeRegistries` 是编辑器识别节点的实际来源。

如果不注册，会出现非常典型的症状：

- 代码里明明有节点定义
- 但节点面板里看不到
- 已有 workflow JSON 也无法正常识别该 type

## 7. 第四步：前后端 type 映射必须处理

这一步经常漏。

前端最终不会把原始节点 type 原样交给后端，而是会经过：

- `backend-workflow.ts`

如果你前端 type 用的是：

- `transform`

而后端 executor type 用的是：

- `TRANSFORM`

那你必须补到：

```ts
const BACKEND_TYPE_MAP: Record<string, string> = {
  transform: 'TRANSFORM',
};
```

否则前端看起来一切正常，后端却会在校验阶段报：

- unsupported node type

## 8. 前端真正要对齐的不是外观，而是 schema

一个前端自定义节点是否合格，不取决于卡片做得是否好看，而取决于这几组数据是否稳定：

- `inputs.required`
- `inputs.properties`
- `inputsValues`
- `outputs.properties`

这些字段决定了：

- 表单怎么校验
- runtime 收到什么输入
- 下游节点能引用什么输出

如果这些字段定义得含糊，后端 executor 再强也没用。

## 9. 与内置节点保持一致时最值得学什么

看当前内置节点 registry，最值得复用的不是样式，而是它们的组织方式：

- `ToolNodeRegistry` 用默认 form meta 和清晰的输入输出 contract
- `KnowledgeNodeRegistry` 把 serviceId、embeddingModel、namespace、query 这些后端关键字段前置出来

这说明好的节点前端定义有一个共同特点：

- 它把后端真正需要的 contract 暴露为明确表单字段

而不是把复杂逻辑藏在前端内部。

## 10. 最常见的前端错误

### 10.1 只加了 type，没加 backend map

结果：

- 画布能拖出来
- 后端不认识

### 10.2 `inputs` 和 `inputsValues` 没对齐

结果：

- 表单看起来填了
- runtime 仍然会报 required missing

### 10.3 输出 schema 写得太随意

结果：

- 下游节点引用路径不稳定
- 表单和展示面板都不好做

### 10.4 一上来就写复杂专属 form meta

结果：

- UI 复杂度先失控
- 但节点 contract 还没稳定

## 11. 一个最重要的判断标准

前端自定义节点写得好不好，不看它 JSX 多炫，而看这 4 点：

- 拖进画布时能生成稳定初始 schema
- 表单校验和 required 字段一致
- 能正确映射成后端识别的 type
- 输出 schema 足够稳定，供下游引用

满足这 4 点，它才是正式平台节点的前端半边。
