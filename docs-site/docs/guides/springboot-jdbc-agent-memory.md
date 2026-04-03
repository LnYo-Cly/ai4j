---
sidebar_position: 2
---

# Spring Boot + JDBC AgentMemory：持久化 Agent 会话

如果你已经不满足于“基础多轮聊天”，而是要让 `ReAct Agent` 在多次请求之间继续记住：

- 历史用户输入
- 上一轮模型输出
- 工具执行结果
- 压缩后的摘要

那就该从 `ChatMemory` 升级到 `AgentMemory` 了。

这页给一条最实用的落地方式：

- `ai4j-agent` 负责 Agent runtime
- `JdbcAgentMemory` 负责会话持久化
- Spring Boot 提供 `DataSource`

## 1. 什么时候该用它

适合：

- ReAct Agent
- 带工具调用的业务 Agent
- 多轮任务代理
- 需要跨实例恢复的 Agent session

不适合：

- 只是普通聊天
- 不需要工具结果回写
- 不需要 runtime state

如果只是普通聊天，请先用：

- [Spring Boot + MySQL：多轮聊天与 ChatMemory 持久化](/docs/guides/springboot-mysql-chat-memory)

## 2. 依赖

```xml
<dependencies>
  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-agent</artifactId>
    <version>2.0.0</version>
  </dependency>

  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.0.0</version>
  </dependency>

  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

## 3. `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ai4j?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456

ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://api.openai.com/
```

`JdbcAgentMemory` 默认会自动建表：

- `ai4j_agent_memory`

## 4. 构造一个按 sessionId 创建 Agent 的工厂

这里故意不把 `Agent` 做成单例，因为每个用户会话都应该绑定自己的 `sessionId`。

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.memory.JdbcAgentMemory;
import io.github.lnyocly.ai4j.agent.memory.JdbcAgentMemoryConfig;
import io.github.lnyocly.ai4j.agent.memory.WindowedMemoryCompressor;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;

@Component
public class PersistentAgentFactory {

    private final AiService aiService;
    private final DataSource dataSource;

    public PersistentAgentFactory(AiService aiService, DataSource dataSource) {
        this.aiService = aiService;
        this.dataSource = dataSource;
    }

    public Agent create(String sessionId) {
        return Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.OPENAI)))
                .model("gpt-4o-mini")
                .systemPrompt("你是一个严谨的企业知识助手。")
                .instructions("必要时再调用工具，最终回答保持简洁。")
                .toolRegistry(Arrays.asList("queryWeather"), null)
                .memorySupplier(() -> new JdbcAgentMemory(
                        JdbcAgentMemoryConfig.builder()
                                .dataSource(dataSource)
                                .sessionId(sessionId)
                                .compressor(new WindowedMemoryCompressor(30))
                                .build()
                ))
                .build();
    }
}
```

这段配置里最重要的是：

- `memorySupplier(...)`
- `sessionId(sessionId)`
- `compressor(new WindowedMemoryCompressor(30))`

它表示：

- 每个会话使用独立的 `JdbcAgentMemory`
- 每轮运行后的 memory 都持久化到数据库
- 历史 item 超过窗口后会自动裁剪

## 5. Agent 服务层

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import org.springframework.stereotype.Service;

@Service
public class AgentConversationService {

    private final PersistentAgentFactory persistentAgentFactory;

    public AgentConversationService(PersistentAgentFactory persistentAgentFactory) {
        this.persistentAgentFactory = persistentAgentFactory;
    }

    public String run(String sessionId, String input) throws Exception {
        Agent agent = persistentAgentFactory.create(sessionId);
        AgentResult result = agent.run(AgentRequest.builder().input(input).build());
        return result.getOutputText();
    }
}
```

这种写法的重点不是“每次重新 build Agent 会不会浪费”，而是：

- 每次请求都能从数据库恢复对应 `sessionId` 的 memory
- 不依赖单机 JVM 内部缓存
- 更适合 Web 服务和多实例部署

## 6. Controller 示例

```java
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentConversationService agentConversationService;

    public AgentController(AgentConversationService agentConversationService) {
        this.agentConversationService = agentConversationService;
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestHeader("X-Session-Id") String sessionId,
                                   @RequestBody Map<String, String> body) throws Exception {
        String output = agentConversationService.run(sessionId, body.get("input"));
        return Collections.<String, Object>singletonMap("output", output);
    }
}
```

## 7. 如何理解 AgentMemory 和 ChatMemory 的差别

### `ChatMemory`

更关注：

- 聊天消息历史
- `Chat / Responses` 输入拼装
- 用户消息与助手消息的连续上下文

### `AgentMemory`

更关注：

- Agent 每一轮 loop 的状态
- 工具调用结果
- `function_call_output`
- 摘要压缩后的 system 记忆

所以对带工具的 Agent 来说，`AgentMemory` 才是正确层级。

## 8. 如果你想显式使用 Session

如果你在单个请求中要持续运行一个会话对象，也可以这样写：

```java
Agent agent = persistentAgentFactory.create(sessionId);
String answer = agent.newSession()
        .run(AgentRequest.builder().input("请继续上一轮任务").build())
        .getOutputText();
```

但在典型 Web 服务里，更常见的仍然是：

- 根据请求里的 `sessionId` 重建 Agent
- 让 `JdbcAgentMemory` 负责恢复上下文

## 9. 你最终会得到什么

当 Agent 运行一轮后，数据库里会保留：

- 用户输入
- 模型输出 item
- 工具输出 item
- 可选 summary

这意味着下次再用同一个 `sessionId` 进入时，模型看到的不是空白上下文，而是上一轮运行后的真实状态。

## 10. 上线建议

- `sessionId` 应绑定用户、租户或业务单据，不要用公共常量
- `WindowedMemoryCompressor` 适合第一版，稳定且便宜
- 如果要长期项目协作式 Agent，再考虑“摘要 + 窗口”的自定义压缩器
- `JdbcAgentMemory` 解决的是持久化，不替你做任务并发控制、租户隔离和会话生命周期治理

## 11. 继续阅读

1. [Memory 记忆管理与压缩策略](/docs/agent/memory-management)
2. [最小 ReAct Agent](/docs/agent/minimal-react-agent)
3. [自定义 Agent 开发指南](/docs/agent/custom-agent-development)
