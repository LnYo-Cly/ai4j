---
sidebar_position: 2
---

# JDK8 + OpenAI 最小示例（Spring Boot）

历史主题来源：Spring Boot + OpenAI + JDK8 快速实践。

本页目标：在 JDK8 工程里完整打通 **同步 + 流式 + Tool** 三条链路。

## 1. 配置文件

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
  okhttp:
    proxy-url: 127.0.0.1
    proxy-port: 10809
```

> 如果没有代理，可移除 `okhttp.proxy-*`。

## 2. 获取服务入口

```java
@Autowired
private AiService aiService;

public IChatService chatService() {
    return aiService.getChatService(PlatformType.OPENAI);
}
```

## 3. 同步调用（先打通）

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse resp = chatService().chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent();
System.out.println(text);
```

## 4. 流式调用（确认增量输出）

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请分 3 点介绍 AI4J"))
        .build();

SseListener listener = new SseListener() {
    @Override
    protected void send() {
        if (!getCurrStr().isEmpty()) {
            System.out.print(getCurrStr());
        }
    }
};

chatService().chatCompletionStream(req, listener);
System.out.println("\nstream finished");
```

## 5. Tool 调用（天气示例）

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气并给出建议"))
        .functions("queryWeather")
        .build();

ChatCompletionResponse resp = chatService().chatCompletion(req);
System.out.println(resp.getChoices().get(0).getMessage().getContent());
```

## 6. 质量基线（建议写成测试）

在你的 `*Test` 中至少断言：

- 返回对象非空
- 输出文本非空
- 流式能接收到中间增量
- Tool 调用路径确实发生（可看日志或 toolResults）

## 7. 常见坑

### 7.1 模型可用但 Tool 不触发

- 检查 `functions("queryWeather")` 是否传入
- 检查工具名是否与注解/注册名一致
- 检查提示词是否明确“先调用工具再回答”

### 7.2 流式感觉“很慢”

- 先区分：是模型首 token 慢，还是控制台没实时 flush
- 测试模式下建议输出短文本，避免误判

## 8. 迁移到 Agent 的建议

当你完成本页后，下一步不要直接堆业务逻辑在 Controller 里，建议迁移到：

- `Agent + Runtime`（管理推理与工具循环）
- `Workflow`（管理多节点编排）
- `Trace`（排障与审计）

详见：`Agent / Agent 架构总览`。
