# Request and Response Conventions

这一页统一解释“请求怎么构造，返回怎么读”。很多接入问题不是 provider 不可用，而是没有建立统一请求/返回约定。

## 1. AI4J 的基本策略

AI4J 的基座层倾向于：

- 用统一请求对象承载主线字段
- 把 provider 差异留在 service 适配层
- 通过 `extraBody` 等机制承接必要扩展

这类策略在：

- `ChatCompletion`
- `ResponseRequest`
- `Embedding`

上都能看到。

## 2. 为什么这层约定重要

如果团队没有统一约定，很容易出现：

- 同步和流式混读
- 辅助字段被误当作最终 provider payload
- 各模块自己解析原始 JSON

短期看只是“代码有点散”，长期看会直接影响：

- 日志一致性
- 调试效率
- 回归稳定性

## 3. 一个很实用的规则

- 先读统一实体
- 再看 provider 特有字段
- 尽量不要在业务层到处手搓 JSON 路径

这条规则的价值不是“写法优雅”，而是让 SDK 能长期演进。

## 4. 设计摘要

AI4J 在请求/返回层的核心策略是“统一对象承载主语义，provider 差异留在适配层”。这样 `Chat`、`Responses`、Embedding 等主线能保持一致心智，而不会每接一家 provider 就重写一套调用和解析逻辑。

## 5. 关键对象

这页最值得对照源码看的对象通常是：

- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/response/entity/ResponseRequest.java`
- `Embedding` 相关请求与返回实体
- `extraBody` 这类承接 provider 扩展字段的通道

它们共同说明了一件事：AI4J 的“统一约定”并不是把 provider 差异抹平，而是先把主语义固定下来，再给必要差异留出正式扩展位。

## 6. 实际接入时应统一什么

一套稳定的团队约定，至少应覆盖：

- 谁负责构造统一请求对象
- 谁可以读取 provider 特有字段
- 流式和非流式结果的消费方式是否分开
- 日志、trace 和错误处理是否都围绕统一实体展开

这些约定如果没有先建立，代码很快就会退化成“每个调用点各自适配一遍”。

## 7. 常见误区

这一层最常见的错误通常是：

- 直接把 provider 原始 JSON 结构泄漏到业务层
- 不同调用点各自定义一套返回解析路径
- 一边走统一实体，一边又在旁路维护手工字段映射

这些做法短期看像是“灵活”，长期会让 SDK 层和业务层同时承担适配成本。

## 8. 继续阅读

- [Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
