# Chat service

[Back to English README](../../../README-EN.md) · [中文 README](../../../README.md)

## Chat service

### Synchronous request call
```java

public void test_chat() throws Exception {
    // Obtain chat service instance
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // Build request parameters
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("鲁迅为什么打周树人"))
            .build();

    // Send dialogue request
    ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

    System.out.println(response);
}

```

### Streaming call
```java
public void test_chat_stream() throws Exception {
    // Obtain chat service instance
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // Construct request parameters
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("查询北京明天的天气"))
            .functions("queryWeather")
            .build();


    // Construct listener
    SseListener sseListener = new SseListener() {
        @Override
        protected void send() {
            System.out.println(this.getCurrStr());
        }
    };
    // Display function parameters. Default is not to display.
    sseListener.setShowToolArgs(true);

    // Send SSE request
    chatService.chatCompletionStream(chatCompletion, sseListener);

    System.out.println(sseListener.getOutput());

}
```

### Image recognition

```java
public void test_chat_image() throws Exception {
    // Obtain chat service instance
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // Build request parameters
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("图片中有什么东西", "https://cn.bing.com/images/search?view=detailV2&ccid=r0OnuYkv&id=9A07DE578F6ED50DB59DFEA5C675AC71845A6FC9&thid=OIP.r0OnuYkvsbqBrYk3kUT53AHaKX&mediaurl=https%3a%2f%2fimg.zcool.cn%2fcommunity%2f0104c15cd45b49a80121416816f1ec.jpg%401280w_1l_2o_100sh.jpg&exph=1792&expw=1280&q=%e5%b0%8f%e7%8c%ab%e5%9b%be%e7%89%87&simid=607987191780608963&FORM=IRPRST&ck=12127C1696CF374CB9D0F09AE99AFE69&selectedIndex=2&itb=0&qpvt=%e5%b0%8f%e7%8c%ab%e5%9b%be%e7%89%87"))
            .build();

    // Send dialogue request
    ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

    System.out.println(response);
}
```

### Function call

```java
public void test_chat_tool_call() throws Exception {
    // Obtain chat service instance
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    // Build request parameters
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("今天北京天气怎么样"))
            .functions("queryWeather")
            .build();

    // Send dialogue request
    ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

    System.out.println(response);
}
```
#### Define function
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
