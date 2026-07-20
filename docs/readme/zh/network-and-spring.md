# 内置联网与 Spring 应用

[返回中文 README](../../../README.md) · [English README](../../../README-EN.md)

## 内置联网

### SearXNG

#### 配置
```java
// 非spring应用
SearXNGConfig searXNGConfig = new SearXNGConfig();
searXNGConfig.setUrl("http://127.0.0.1:8080/search");

Configuration configuration = new Configuration();
configuration.setSearXNGConfig(searXNGConfig);
```

```YML
## spring应用
ai:
  websearch:
    searxng:
      url: http://127.0.0.1:8080/search

```

#### 使用

```java

// ...

webEnhance = aiService.webSearchEnhance(chatService);

// ...


@Test
public void test_chatCompletions_common_websearch_enhance() throws Exception {
    ChatCompletion chatCompletion = ChatCompletion.builder()
            .model("qwen2.5:7b")
            .message(ChatMessage.withUser("鸡你太美是什么梗"))
            .build();

    System.out.println("请求参数");
    System.out.println(chatCompletion);

    ChatCompletionResponse chatCompletionResponse = webEnhance.chatCompletion(chatCompletion);

    System.out.println("请求成功");
    System.out.println(chatCompletionResponse);

}
```
