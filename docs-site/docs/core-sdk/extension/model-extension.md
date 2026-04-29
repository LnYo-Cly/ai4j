# Model Extension

`model extension` 解决的是：**在不新增 `PlatformType`、不新增顶层 service 的前提下，把新的模型能力吸收到现有 provider 与现有契约中。**

这是 AI4J 里最常见、也最容易被误判的一类扩展。

## 1. 先明确它和 provider / service extension 的边界

只要下面两件事还成立，优先按 model extension 处理：

- 平台边界没有变，仍然属于同一个 provider
- 调用心智仍然属于现有 `Chat`、`Responses`、`Embedding`、`Image`、`Audio`、`Realtime` 或 `Rerank`

一旦你必须新增 `PlatformType`，那已经更像 provider extension。  
一旦你发现现有能力面根本装不下新的交互语义，那就要重新考虑 service extension。

## 2. 当前代码里，这类变化通常落在哪里

model extension 最常落在这几类对象上：

- 统一请求对象，例如 `ChatCompletion`、`ResponseRequest`
- provider 侧请求映射与结果解析
- provider 配置对象中的模型相关字段

也就是说，它的主战场通常在：

- 请求对象能不能表达新能力
- provider service 会不会把这些字段真正发出去
- 返回结果会不会被现有 listener / converter 正确吸收

而不是 `PlatformType` 或 `AiService` 的总分发层。

## 3. 三种典型的 model extension 场景

### 3.1 只是新增模型名

这是最轻的一类。

如果某个 provider 的现有 service 已经支持目标协议，而变化只是：

- 模型字符串变了
- 默认 `apiHost` 变了
- 某个调用方要切到新模型

那通常不需要改基座抽象，只需要正确配置并走现有 service。

### 3.2 请求或返回多了有限的 provider 差异

例如：

- 新模型需要新的推理参数
- 新模型支持新的输出格式控制
- 新模型在流式事件里多了某些字段

这时真正要检查的是：

- 统一请求对象是否需要新增字段
- 对应 provider service 是否有序列化/反序列化更新

如果只改请求对象、不改 provider 映射，实现上最常见的结果就是“字段看起来加了，但平台端根本没收到”。

### 3.3 名义上是新模型，实际上已经冲破旧契约

如果你发现：

- 输入不再像现有请求模型
- 返回不再像现有事件或结果对象
- 调用方对状态消费方式已经完全变了

那这往往不再是单纯的 model extension。继续把它硬塞进现有 service，通常只会让抽象逐渐变形。

## 4. 先看 provider 支持矩阵，再决定改哪里

AI4J 当前不同 service 面的 provider 覆盖并不对称。

例如：

- `Chat` 覆盖的 provider 明显更多
- `Responses` 目前只对少数 provider 暴露

这带来一个很实际的判断：

- 如果新增模型仍然属于现有 `Chat` 能力，通常优先留在 `Chat` 路径内
- 如果目标能力只在 `Responses` 语义下成立，那要先确认该 provider 是否已进入 `createResponsesService(...)`

所以 model extension 不是“加个 model 字符串”这么简单，而是要先看它落在现有哪条 service 主线里。

## 5. 这类扩展最应该保持的原则

### provider 差异尽量收敛在 provider service 内部

上层调用方最好继续只依赖统一请求对象和统一 service 接口。

如果每新增一个模型，业务代码都要写一批：

- `if openai`
- `if deepseek`
- `if dashscope`

那说明 model extension 没有被收敛好，而是在向业务层泄漏 provider 差异。

### 现有契约还能成立，就不要升级抽象层级

这是 AI4J 里很重要的一条经验线。

只要现有 `Chat`、`Responses` 等契约仍然成立，优先把变化留在：

- provider 请求映射
- provider 返回解析
- provider 配置字段

而不是立刻膨胀成新的 provider 或新的 service。

## 6. 当前实现里容易忽略的几个后果

### 并不是所有字段都会自动发到 provider

统一请求对象存在的意义，是给 SDK 提供一个稳定建模面，而不是保证所有字段都自动穿透到外部平台。

所以当你为新模型补字段时，必须同时验证：

- provider service 是否读取了该字段
- provider payload builder 是否把它真正写入请求
- 流式或非流式返回路径是否也需要同步调整

### 现有工厂分支不一定需要变化

很多 model extension 都不该去碰 `AiService`。  
如果你没有新增平台，也没有新增顶层能力面，但却开始改 `PlatformType` 或 `create*Service(...)`，通常说明扩展层级判断错了。

### service 对象默认按次创建

`AiService` 当前没有启用 service 缓存，获取具体 service 时会按次创建实例。  
这意味着模型扩展中的状态，不应偷偷依赖某个 provider service 被长时间复用。

## 7. 排障时先看哪一段

### 新模型名生效了，但新字段像没效果

先看 provider service 的请求映射，而不是先怪配置层。  
这类问题往往是统一请求对象已经补了字段，但 provider 侧序列化没有跟上。

### 业务侧被迫加很多 provider 分支

先反思是不是把本该留在 provider service 内部的差异暴露出来了。  
model extension 的目标本来就是吸收差异，而不是传播差异。

### 你已经开始改 `PlatformType`

这通常是一个警告信号：你也许已经从 model extension 滑向了 provider extension。

## 8. 一条实用判断线

如果这次改动主要集中在：

- 请求/返回对象
- provider service
- provider 配置字段

而不用改：

- `PlatformType`
- `DefaultAiServiceRegistry.applyPlatformConfig(...)`
- starter 的整套 provider 属性装配

那它大概率就是 model extension。

## 9. 这一页的结论

> 在 AI4J 里，model extension 的核心不是“能不能换一个 model 字符串”，而是现有 provider 与现有顶层契约能否继续承载这项变化。只要平台边界和能力语义没变，最稳的做法就是把差异收敛在请求对象和 provider 适配层内部，而不是过早抬升到 provider 或 service 级扩展。
