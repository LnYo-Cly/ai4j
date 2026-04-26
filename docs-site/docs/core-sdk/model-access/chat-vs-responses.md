# Chat vs Responses

这是整个基座里最重要的选型问题之一。

## 1. 一句话区别

- `Chat`：消息式接口，迁移成本低，首调更直接
- `Responses`：事件式接口，结构化更强

## 2. 什么时候优先选 Chat

- 你第一次接入 AI4J
- 你已有大量 Chat 风格代码
- 你主要目标是文本生成 + function call

## 3. 什么时候优先选 Responses

- 你要细粒度流式事件
- 你要处理 reasoning / output item / function args
- 你在做更复杂的 runtime

## 4. 一条实用建议

如果你还不确定，先把 `Chat` 跑通，再决定要不要升级到 `Responses`。
