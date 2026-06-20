# Service Extension

`service extension` 解决的是：**AI4J 是否要新增一条新的顶层能力契约**。  
这比 model extension 更重，因为你不是在补某个平台，而是在扩大整个 SDK 的公共能力面。

## 1. 当前已经存在的顶层能力面

在 `ai4j/src/main/java/io/github/lnyocly/ai4j/service/` 下，当前已经存在这些正式接口：

- `IChatService`
- `IResponsesService`
- `IEmbeddingService`
- `IImageService`
- `IAudioService`
- `IRealtimeService`
- `IRerankService`

这些接口不是随便拆的。它们决定了：

- `AiService` 暴露哪些正式入口
- provider 支持矩阵按什么维度维护
- 上层模块如何形成稳定心智

所以新增 service 不是“想起一个名字就加一个接口”，而是要重新定义 SDK 的能力版图。

## 2. 真正需要改的不是一个点，而是一整条链

如果你新增 `IXxxService`，真实上要经过下面几层：

1. 新接口本身，以及请求/返回对象
2. 至少一个 provider 的实现类
3. `AiService` 的 `getXxxService(...)` 和 `createXxxService(...)`
4. `AiServiceRegistry` 的便利访问方法
5. `FreeAiService` 的兼容静态入口
6. 对应文档、示例和回归测试

这里最容易漏的是第 4 和第 5 点。

很多扩展在 `AiService` 层已经能跑，但：

- 多实例场景拿不到新 service
- 旧版 `FreeAiService` 兼容入口没有同步

这会造成“核心能力存在，但正式访问面不完整”的不一致。

## 3. 什么时候才值得新增 service

只有当下面几条同时接近成立时，才应该认真考虑：

- 现有 `Chat / Responses / Embedding / Image / Audio / Realtime / Rerank` 都无法自然承载
- 输入输出语义已经不是现有能力面的变体
- 调用者的运行时心智已经明显变化
- 用“给现有请求对象再加几个字段”的方式会让抽象变形

反过来说，如果只是：

- 同一个交互模式下多几个选项
- provider 某些字段不一样
- 多一种结果格式

那往往还没有到新增 service 的程度。

## 4. 为什么这层代价最高

### 4.1 它直接扩大公共 API

只要新增顶层 service，你就在扩大 `ai4j` 模块对外承诺的接口面。这会影响：

- Java API 稳定性
- 文档结构
- 上层模块的调用模式
- starter 和 demo 的理解成本

这类改动天然比“多一个 provider 分支”更难回收。

### 4.2 每个 provider 都要被重新定义支持边界

service extension 不要求每个 provider 都马上支持，但你必须明确：

- 哪些 provider 已支持
- 哪些 provider 还不支持
- 不支持时是否继续抛显式异常

当前 `AiService` 的所有能力面都是这样做的。比如 `Responses` 只对少数 provider 暴露，而 `Chat` 覆盖更广。

所以新增 service 时，最重要的不是追求“看起来全平台统一”，而是维持清晰的支持矩阵。

### 4.3 它会波及 registry 和兼容层

`AiServiceRegistry` 不是简单的 id->AiService map，它还把常见能力做成了按 id 访问的默认方法。  
`FreeAiService` 则继续保留静态兼容入口。

如果新增 service 后不把这两层补齐，调用链就会断在多实例或兼容模式上。

## 5. `AiServiceFactory` 不是 service extension 的捷径

仓库里确实有 `AiServiceFactory` 抽象，但它的职责只是“如何创建一个 `AiService`”，不是“如何动态发现新的 service 类型”。

更实际一点说：

- provider/service 能力矩阵依然定义在 `AiService` 本身
- starter 默认直接注册 `DefaultAiServiceFactory`
- 这层并没有形成“新增 service 后自动进主线”的能力

所以不要把 `AiServiceFactory` 误解成 service 插件总线。

## 6. 一个更稳的判断方式

新增 service 前，先反问三次：

1. 这真的是新能力，还是 `Chat`/`Responses` 的一个运行模式
2. 我是在逃避 provider 侧字段映射复杂度，还是抽象真的不够
3. 如果把它做成新 service，上层文档和心智会更清晰，还是会更碎片化

如果第三个问题的答案不明确，通常说明还不该新增 service。

## 7. 调试与验收时看什么

### `AiService` 能返回新 service，但 registry 不行

说明 `AiServiceRegistry` 默认方法没有补齐，或者注册表调用链没有接入新能力。

### registry 可以，`FreeAiService` 不行

说明兼容壳没有同步静态入口。这会影响旧用法和历史示例。

### 只有某个 provider 可以，其他 provider 报错

这不一定是 bug。先判断是不是你有意保留的不对称支持矩阵。  
只要异常清晰、文档明确，这种不对称本身是允许存在的。

## 8. 什么时候应该回退为 model extension

如果你发现改动主要集中在：

- `ChatCompletion`
- `ResponseRequest`
- 某个 provider 的请求序列化
- 某个 provider 的事件/返回解析

那更可能还是 model extension，而不是 service extension。

把本该留在 provider 内部的变化过早提升到顶层 service，最后常常会得到一个名字很新、语义却和旧接口高度重叠的能力面。

## 9. 这一页的结论

> 在 AI4J 里，service extension 改的是 SDK 的正式能力版图，而不是某个平台的局部实现。只有当现有顶层契约已经无法自然承载新语义时，才值得新增一条 service 面；否则更稳的做法是把变化吸收在现有 service 契约和 provider 适配层内部。
