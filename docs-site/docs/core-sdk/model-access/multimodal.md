# Multimodal

`Multimodal` 讲的是文本之外的输入片段如何进入统一模型请求。

## 1. 最常见场景

- 文本 + 图片理解
- 视觉问答
- 多张图片比较

## 2. 在 AI4J 里的位置

多模态属于 `Model Access`，不是工具能力，也不是 MCP 能力。

它回答的是：

- 请求体如何描述图文混合输入
- provider 差异如何被统一

## 3. 继续阅读

如果你要继续按当前 canonical 主线往下读，建议看：

- [Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
- [Tools / Function Calling](/docs/core-sdk/tools/function-calling)
