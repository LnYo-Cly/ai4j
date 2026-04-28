---
sidebar_position: 22
---

# Chat vs Responses 选型

这页给一个工程化结论，而不是概念对比。

## 1. 快速结论

- 追求兼容存量、迁移平滑：优先 **Chat**。
- 追求事件结构化、可观测性、Agent runtime：优先 **Responses**。

## 2. 对比维度

| 维度 | Chat | Responses |
| --- | --- | --- |
| 存量兼容 | 高 | 中 |
| 文本直出易用性 | 高 | 中 |
| 事件结构化 | 中 | 高 |
| 推理/函数参数事件可观测 | 中 | 高 |
| 迁移成本 | 低 | 中 |
| 适合作为 Agent 底层 | 中 | 高 |

## 3. Chat 更合适的场景

- 你已有大量 `chatCompletion` 代码
- 需求是“稳定文本回答 + 工具调用”
- 团队优先低改造成本

## 4. Responses 更合适的场景

- 你要做 trace/审计/事件回放
- 你要区分 reasoning、message、function arguments
- 你要构建新的 agent runtime / workflow

## 5. 关于“流式是否 token 级”

两个接口都不能保证 token 级分片。

- Chat 常见更细粒度文本片段
- Responses 常见事件片段（可能按句）

这不是 SDK 错误，而是上游流式切片策略。

## 6. 推荐迁移路径

### 阶段 1

先保留 Chat 作为主链路，补齐：

- 统一工具注册
- 统一日志字段
- 流式回调规范

### 阶段 2

在新业务/新 Agent 中优先 Responses：

- 基于事件做 trace
- 把工具循环放到 runtime

### 阶段 3

双栈共存，按场景路由：

- 普通问答 -> Chat
- 智能体编排 -> Responses

## 7. 一个实践建议

不要做“全量切换”。

最优雅做法是：

- 抽象统一的 `ModelClient` 接口
- Chat/Responses 作为实现
- 在 AgentBuilder 或业务配置中按场景注入

这样切换成本最低，也最符合开源组件可扩展性。

## 8. 关键对象

如果你要继续从文档进入代码，优先关注：

- `ChatCompletion`
- `ResponseRequest`
- `SseListener`
- `ResponseSseListener`

这组对象能帮助你把“请求实体差异”和“流式消费心智差异”一起对上。

## 9. 迁移时最容易判断错的点

- 以为 `Responses` 只是 `Chat` 的字段重命名版本
- 以为只要能输出文本，两条链的 runtime 价值就完全相同
- 以为迁移必须一次性全量替换

实际上，两条链最大的差异在于事件语义、可观测性和是否适合作为更复杂 runtime 的底座。

## 10. 更稳的使用方式

很多项目的更合适做法是双栈共存：

- 普通文本问答继续走 `Chat`
- 新的 agentic 场景和事件化链路优先走 `Responses`

这样既能保留存量稳定性，也能逐步引入新的事件模型。
