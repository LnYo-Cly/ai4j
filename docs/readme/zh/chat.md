# Chat 服务

[返回中文 README](../../../README.md) · [English README](../../../README-EN.md)

## Chat服务

### 同步请求调用
```java

public void test_chat() throws Exception {
    // 获取chat服务实例
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // 构建请求参数
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("鲁迅为什么打周树人"))
            .build();

    // 发送对话请求
    ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

    System.out.println(response);
}

```

### 流式调用
```java
public void test_chat_stream() throws Exception {
    // 获取chat服务实例
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // 构造请求参数
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("查询北京明天的天气"))
            .functions("queryWeather")
            .build();


    // 构造监听器
    SseListener sseListener = new SseListener() {
        @Override
        protected void send() {
            System.out.println(this.getCurrStr());
        }
    };
    // 显示函数参数，默认不显示
    sseListener.setShowToolArgs(true);

    // 发送SSE请求
    chatService.chatCompletionStream(chatCompletion, sseListener);

    System.out.println(sseListener.getOutput());

}
```

### 图片识别

```java
public void test_chat_image() throws Exception {
    // 获取chat服务实例
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // 构建请求参数
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("图片中有什么东西", "https://cn.bing.com/images/search?view=detailV2&ccid=r0OnuYkv&id=9A07DE578F6ED50DB59DFEA5C675AC71845A6FC9&thid=OIP.r0OnuYkvsbqBrYk3kUT53AHaKX&mediaurl=https%3a%2f%2fimg.zcool.cn%2fcommunity%2f0104c15cd45b49a80121416816f1ec.jpg%401280w_1l_2o_100sh.jpg&exph=1792&expw=1280&q=%e5%b0%8f%e7%8c%ab%e5%9b%be%e7%89%87&simid=607987191780608963&FORM=IRPRST&ck=12127C1696CF374CB9D0F09AE99AFE69&selectedIndex=2&itb=0&qpvt=%e5%b0%8f%e7%8c%ab%e5%9b%be%e7%89%87"))
            .build();

    // 发送对话请求
    ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

    System.out.println(response);
}
```

### 函数调用

```java
public void test_chat_tool_call() throws Exception {
    // 获取chat服务实例
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // 构建请求参数
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("今天北京天气怎么样"))
            .functions("queryWeather")
            .build();

    // 发送对话请求
    ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

    System.out.println(response);
}
```

### 内置 ChatMemory

如果你只是做基础多轮对话，不想自己每轮维护完整上下文，可以直接使用 `ChatMemory`：

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

ChatMemory memory = new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(12));
memory.addSystem("你是一个简洁的 Java 助手");
memory.addUser("请用三点介绍 AI4J");

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
String answer = response.getChoices().get(0).getMessage().getContent().getText();

memory.addAssistant(answer);
```

同一份 `memory` 也可以直接给 `Responses`：

```java
IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input(memory.toResponsesInput())
        .build();
```
#### 定义函数
```java
@FunctionCall(name = "queryWeather", description = "查询目标地点的天气预报")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    @Data
    @FunctionRequest
    public static class Request{
        @FunctionParameter(description = "需要查询天气的目标位置, 可以是城市中文名、城市拼音/英文名、省市名称组合、IP 地址、经纬度")
        private String location;
        @FunctionParameter(description = "需要查询未来天气的天数, 最多15日")
        private int days = 15;
        @FunctionParameter(description = "预报的天气类型，daily表示预报多天天气、hourly表示预测当天24天气、now为当前天气实况")
        private Type type;
    }

    public enum Type{
        daily,
        hourly,
        now
    }

    @Override
    public String apply(Request request) {
        final String key = "";

        String url = String.format("https://api.seniverse.com/v3/weather/%s.json?key=%s&location=%s&days=%d",
                request.type.name(),
                key,
                request.location,
                request.days);


        OkHttpClient client = new OkHttpClient();

        okhttp3.Request http = new okhttp3.Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(http).execute()) {
            if (response.isSuccessful()) {
                // 解析响应体
                return response.body() != null ? response.body().string() : "";
            } else {
                return "获取天气失败 当前天气未知";
            }
        } catch (Exception e) {
            // 处理异常
            e.printStackTrace();
            return "获取天气失败 当前天气未知";
        }
    }

}
```
