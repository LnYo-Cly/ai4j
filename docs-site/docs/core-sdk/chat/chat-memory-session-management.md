---
sidebar_position: 16
---

# ChatMemory 与 sessionId 管理

上一页讲的是 `ChatMemory` 本体，这一页讲的是更贴近真实服务的边界问题：

- 谁创建 memory
- 谁根据 `sessionId` 取回 memory
- 谁负责并发、过期和失败补偿

结论先说在前面：这些都不属于 `ChatMemory` 本体，而属于你的业务会话层。

## 1. 为什么这个边界很重要

`ChatMemory` 当前只负责“会话事实怎么存、怎么投影”。

它不自带：

- session registry
- TTL
- 多实例协同
- 分布式锁
- 失败补偿策略

这不是能力缺失，而是分层选择。

如果这些东西全塞进 memory，本体就会从“协议无关的上下文层”变成“带业务假设的会话网关”。

## 2. 最小可行模型是什么

最简单的理解方式就是：

- `sessionId` 决定拿到哪一个 `ChatMemory`
- `ChatMemory` 决定本轮请求带哪些上下文

单机场景里，这通常就是一张：

- `sessionId -> ChatMemory`

的 map。

```java
private final ConcurrentHashMap<String, ChatMemory> sessions =
        new ConcurrentHashMap<String, ChatMemory>();
```

## 3. 为什么 `computeIfAbsent` 只是开始，不是全部

很多人会停在这一步：

```java
private ChatMemory getMemory(String sessionId) {
    return sessions.computeIfAbsent(
            sessionId,
            id -> new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(12))
    );
}
```

这确实解决了“首次访问创建 memory”的问题。

但它没有解决：

- 同一会话并发写入
- 用户输入已写入、模型请求失败后的半完成状态
- 空闲 session 回收

所以 `computeIfAbsent` 只是 session lookup，不是完整 session management。

## 4. 同一 `sessionId` 必须串行，是因为消息顺序就是协议本身

`Chat` 和 `Responses` 都依赖上下文顺序。

如果同一个 `sessionId` 同时进来两次请求，而你不做串行化，最容易出现：

- 两次 user message 交叉
- assistant 回复回写到错误轮次
- tool output 被并入错误上下文

因此最低限度要满足：

- 同一 `sessionId` 串行
- 不同 `sessionId` 并行

最简单的做法就是对 memory 或独立 session lock 加锁。

## 5. 一个更真实的 service 写法

```java
@Service
public class ChatSessionService {

    private final AiService aiService;
    private final ConcurrentHashMap<String, ChatMemory> sessions =
            new ConcurrentHashMap<String, ChatMemory>();

    public ChatSessionService(AiService aiService) {
        this.aiService = aiService;
    }

    public String chat(String sessionId, String userInput) throws Exception {
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
        ChatMemory memory = sessions.computeIfAbsent(
                sessionId,
                id -> {
                    ChatMemory created =
                            new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(12));
                    created.addSystem("你是一个简洁的中文助手");
                    return created;
                }
        );

        synchronized (memory) {
            memory.addUser(userInput);

            ChatCompletion request = ChatCompletion.builder()
                    .model("gpt-4o-mini")
                    .messages(memory.toChatMessages())
                    .build();

            ChatCompletionResponse response = chatService.chatCompletion(request);
            String answer = response.getChoices().get(0).getMessage().getContent().getText();

            memory.addAssistant(answer);
            return answer;
        }
    }
}
```

这里最重要的不是写法本身，而是这条顺序：

1. 找到 session 对应 memory
2. 串行追加 user message
3. 发请求
4. 回写 assistant message

## 6. 失败补偿才是很多系统真正漏掉的点

假设流程是：

1. `memory.addUser(userInput)`
2. 调模型
3. 模型超时或工具执行失败

这时会发生什么，要由你的业务来定义。

常见策略有三种：

- 保留这条 user message，允许用户重试并沿用上下文
- 回滚本轮 user message
- 保留 user message，但写入一条错误占位 assistant message

`ChatMemory` 不会替你做这个决定，因为这已经是业务恢复语义，不是上下文存储语义。

## 7. `JdbcChatMemory` 能让你落库，但不会自动给你“会话治理”

如果你切到：

- `JdbcChatMemory`

你得到的是：

- 会话持久化
- 服务重启可恢复
- `sessionId` 到会话记录的稳定映射

你没有自动得到的是：

- 多实例互斥
- session TTL
- 用户隔离策略
- 会话关闭规则

所以 JDBC 解决的是“上下文住哪里”，不是“会话怎么治理”。

## 8. `Responses` 链路完全复用同一套 session 模式

这一点经常被误判。

真正被 `sessionId` 管理的不是：

- `ChatCompletion`
- `ResponseRequest`

而是：

- `ChatMemory`

所以如果你切到 `Responses`，变化只是在投影阶段：

```java
ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input(memory.toResponsesInput())
        .build();
```

session registry、本轮串行化、过期清理这些设计完全不需要重写。

## 9. 清理策略应该放在哪一层

最简单的做法是显式清理：

```java
sessions.remove(sessionId);
```

更真实的生产场景通常还会叠加：

- 最后访问时间
- 定时清理空闲 session
- 用户主动结束会话
- 某类任务完成后自动回收

这些逻辑都应放在 session service，而不是塞进 `ChatMemory` 本体。

## 10. 什么时候单机 map 方案已经不够

下面这些信号说明你该升级了：

- 应用重启后还要保留上下文
- 多实例要共享会话
- 同一会话可能被不同节点处理
- 要观察 session 生命周期和内存占用
- 会话状态要与 workflow、tool 执行态统一管理

这时通常有三条路：

- 先切到 `JdbcChatMemory`
- 业务层自己封装 Redis / DB session store
- 直接升级到 `ai4j-agent` 或 `ai4j-coding` 的更高层 runtime

## 11. 推荐的边界理解

把整个问题拆成两层最清晰：

- `ChatMemory`
  负责一个会话内部有哪些事实，以及这些事实如何投影到模型协议
- `Session service`
  负责这个会话何时创建、谁能访问、是否串行、何时清理、失败后如何补偿

只要这两层不混，后续从 `Chat` 切到 `Responses`，或者从内存切到 JDBC，成本都会低很多。

## 12. 这一页的结论

> `ChatMemory` 与 `sessionId` 的关系，本质上是“业务层拿 `sessionId` 找到一份上下文事实集合”。AI4J 负责把这份事实投影给 `Chat` 或 `Responses`，但不会替你承担并发、TTL、多实例协调和失败补偿。把会话治理留在 session service，把协议投影留在 `ChatMemory`，边界才会长期稳定。
