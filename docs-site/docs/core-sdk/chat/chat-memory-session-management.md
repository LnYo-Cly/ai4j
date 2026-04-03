---
sidebar_position: 16
---

# ChatMemory 与 sessionId 管理

上一页讲的是“单个 `ChatMemory` 怎么用”，这一页讲更接近真实业务的场景：

- 一个用户一个 `sessionId`
- 业务层自己管理 `sessionId -> ChatMemory`
- 用同一份上下文持续对话

这也是当前 `ai4j` 核心层推荐的做法。

## 1. 先明确边界

`ChatMemory` 当前是轻量基础设施，不自带：

- session manager
- 多实例同步
- 分布式锁

但现在已经有官方 JDBC 版 `ChatMemory`，所以“是否落库”这件事不再完全需要你从零实现。

所以“按 `sessionId` 管会话”这层，现在仍然由你的业务代码负责。

## 2. 最小做法：`ConcurrentHashMap<String, ChatMemory>`

单机场景下，最直接的写法就是：

```java
private final ConcurrentHashMap<String, ChatMemory> sessions = new ConcurrentHashMap<String, ChatMemory>();

private ChatMemory getMemory(String sessionId) {
    return sessions.computeIfAbsent(
            sessionId,
            id -> new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(12))
    );
}
```

这适合：

- 单体应用
- 内部工具
- 本地开发
- 中小规模单实例服务

## 3. 一个更完整的 Service 示例

```java
@Service
public class ChatSessionService {

    private final AiService aiService;
    private final ConcurrentHashMap<String, ChatMemory> sessions = new ConcurrentHashMap<String, ChatMemory>();

    public ChatSessionService(AiService aiService) {
        this.aiService = aiService;
    }

    public String chat(String sessionId, String userInput) throws Exception {
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
        ChatMemory memory = sessions.computeIfAbsent(
                sessionId,
                id -> {
                    ChatMemory created = new InMemoryChatMemory(
                            new MessageWindowChatMemoryPolicy(12)
                    );
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

    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
```

这里有两个关键点：

- `computeIfAbsent`：首次访问时创建 memory
- `synchronized (memory)`：避免同一个会话并发写入时顺序错乱

## 4. 为什么要做并发保护

如果同一个 `sessionId` 同时进来两次请求，而你不做串行化，很容易出现：

- 两次用户输入顺序打乱
- assistant 输出回写到错误轮次
- 同一会话上下文交叉污染

所以至少要保证：

- 同一 `sessionId` 内串行
- 不同 `sessionId` 间并行

最简单的做法就是：

- 对 `memory` 对象加锁

如果你后面要做更高并发，再考虑：

- 单独的 session lock map
- actor/queue 模型

## 5. Spring Boot Controller 示例

```java
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatSessionService chatSessionService;

    public ChatController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public String chat(@RequestParam String sessionId, @RequestBody String userInput) throws Exception {
        return chatSessionService.chat(sessionId, userInput);
    }

    @DeleteMapping
    public void clear(@RequestParam String sessionId) {
        chatSessionService.clear(sessionId);
    }
}
```

你也可以把 `sessionId` 放到：

- cookie
- header
- path variable
- 登录态用户 ID + 业务会话 ID

## 6. Responses 链路也一样

如果你走的是 `Responses`，思路不变，只是把：

- `memory.toChatMessages()`

换成：

- `memory.toResponsesInput()`

例如：

```java
ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input(memory.toResponsesInput())
        .build();
```

所以真正被 `sessionId` 管理的是：

- `ChatMemory` 本身

而不是某个特定协议对象。

## 7. 清理策略怎么做

最简单的方式是显式清理：

```java
sessions.remove(sessionId);
```

适合：

- 用户主动结束会话
- 一个任务跑完就销毁上下文

如果你想自动清理空闲会话，可以在业务层做：

- 最后访问时间
- 定时扫描
- 超时删除

这层逻辑目前不建议塞进 `ChatMemory` 本体。

## 8. 什么时候这套方案不够了

下面这些场景，说明你已经开始超出“核心层轻量 memory”边界：

- 服务重启后要保留会话
- 多实例之间共享上下文
- 需要 Redis / MySQL 持久化
- 需要可观测的 session lifecycle
- 需要和 tool/agent/workflow 状态统一存储

这时建议：

- 如果只是关系库存储，优先直接切到 `JdbcChatMemory`
- 如果你要 Redis / 自定义缓存分层 / 会话网关，再继续由业务层自己封装 session store
- 或直接升级到 `Agent` / 更高层 runtime

## 8.1 Spring Boot + MySQL 的最小落地方式

如果你已经有 `spring.datasource.*`，可以直接把 `sessionId -> JdbcChatMemory` 接起来：

```java
@Service
public class ChatSessionService {

    private final IChatService chatService;
    private final DataSource dataSource;
    private final ConcurrentHashMap<String, ChatMemory> sessions = new ConcurrentHashMap<String, ChatMemory>();

    public ChatSessionService(IChatService chatService, DataSource dataSource) {
        this.chatService = chatService;
        this.dataSource = dataSource;
    }

    public ChatMemory getMemory(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id ->
                new JdbcChatMemory(
                        JdbcChatMemoryConfig.builder()
                                .dataSource(dataSource)
                                .sessionId(id)
                                .policy(new MessageWindowChatMemoryPolicy(20))
                                .build()
                )
        );
    }
}
```

## 9. 推荐理解方式

把它理解成两层：

- `ChatMemory`
  - 负责“一个会话里有哪些上下文”
- 你的业务服务
  - 负责“这个会话对象归谁管、何时创建、何时删除、如何并发控制”

这样边界最清晰，也最符合 `ai4j` 当前轻量设计。
