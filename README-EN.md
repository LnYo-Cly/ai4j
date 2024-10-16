![Maven Central](https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=blue)
# ai4j
Since SpringAI requires JDK 17 and Spring Boot 3,  but many applications still use JDK 8 version at present, so AI4J that can support JDK 8 is used to access large models such as OpenAI.  
An Java SDK for quickly integrating AI large model applications. It integrates multiple platform large models such as OpenAI, Ollama, Zhipu (ChatGLM), DeepSeek, Moonshot (Kimi), Tencent Hunyuan, Lingyi (01), etc. It provides a unified input and output (aligned with OpenAI), eliminates differences, optimizes function calls (Tool Call), optimizes RAG calls, and supports vector databases (Pinecone). It also supports JDK 1.8, providing users with the ability to quickly integrate AI.

## Supported platforms
+ OpenAi
+ Zhipu
+ DeepSeek
+ Moonshot
+ Tencent Hunyuan
+ Lingyi AI
+ Ollama
+ To be added(Qwen Llama MiniMax...)

## Supported services
+ Chat Completions（streaming and non-streaming）
+ Embedding
+ To be added

## Features
+ Supports Spring and ordinary Java applications. Supports applications above Java 8.
+ Multi-platform and multi-service.
+ Unified input and output.
+ Unified error handling.
+ Supports streaming output. Supports streaming output of function call parameters.
+ Easily use Tool Calls.
+ Supports simultaneous calls of multiple functions (Zhipu does not support this).
+ Supports stream_options, and directly obtains statistical token usage through streaming output.
+ Supports RAG. Built-in vector database support: Pinecone.
+ Uses Tika to read files.
+ Token statistics`TikTokensUtil.java`


## Tutorial documents
+ [Quick access to Spring Boot, access to streaming and non-streaming and function calls.](http://t.csdnimg.cn/iuIAW)
+ [Quick access to open source large models such as qwen2.5 and llama3.1 on the Ollama platform in Java.](https://blog.csdn.net/qq_35650513/article/details/142408092?spm=1001.2014.3001.5501)
+ [Build a legal AI assistant in Java and quickly implement RAG applications.](https://blog.csdn.net/qq_35650513/article/details/142568177?fromshare=blogdetail&sharetype=blogdetail&sharerId=142568177&sharerefer=PC&sharesource=qq_35650513&sharefrom=from_link)

## Other support
+ [[Low-cost transit platform] Low-cost ApiKey - Limited-time special offer 0.7:1 - Supports the latest o1 model.](https://api.trovebox.online/)

# Quick start
## Import
### Gradle
```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j', version: '${project.version}'
```

```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j-spring-boot-starter', version: '${project.version}'
```


### Maven
```xml
<!-- Non-Spring application -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
    <version>${project.version}</version>
</dependency>

```
```xml
<!-- Spring application -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Obtain AI service instance

### Obtaining without Spring
```java
    public void test_init(){
        OpenAiConfig openAiConfig = new OpenAiConfig();

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);

        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient okHttpClient = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1",10809)))
                .build();
        configuration.setOkHttpClient(okHttpClient);

        AiService aiService = new AiService(configuration);

        embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);
        chatService = aiService.getChatService(PlatformType.getPlatform("OPENAI"));

    }
```
### Obtaining with Spring
```yml
# Domestic access usually requires a proxy by default.
ai:
  openai:
    api-key: "api-key"
  okhttp:
    proxy-port: 10809
    proxy-url: "127.0.0.1"
  zhipu:
    api-key: "xxx"
  #other...
```

```java
// Inject Ai service
@Autowired
private AiService aiService;

// Obtain the required service instance
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);
// ......
```

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

## Embedding service

```java
public void test_embed() throws Exception {
    // Obtain embedding service instance
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // Build request parameters
    Embedding embeddingReq = Embedding.builder().input("1+1").build();

    // Send embedding request
    EmbeddingResponse embeddingResp = embeddingService.embedding(embeddingReq);

    System.out.println(embeddingResp);
}
```

## RAG
### Configure vector database
```yml
ai:
  vector:
    pinecone:
      url: ""
      key: ""
```
### Obtain instance
```java
@Autowired
private PineconeService pineconeService;
```
### Insert into vector database
```java
public void test_insert_vector_store() throws Exception {
    // Obtain embedding service instance
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // Read file content using Tika
    String fileContent = TikaUtil.parseFile(new File("D:\\data\\test\\test.txt"));

    // Split text content
    RecursiveCharacterTextSplitter recursiveCharacterTextSplitter = new RecursiveCharacterTextSplitter(1000, 200);
    List<String> contentList = recursiveCharacterTextSplitter.splitText(fileContent);

    // Convert to vector
    Embedding build = Embedding.builder()
            .input(contentList)
            .model("text-embedding-3-small")
            .build();
    EmbeddingResponse embedding = embeddingService.embedding(build);
    List<List<Float>> vectors = embedding.getData().stream().map(EmbeddingObject::getEmbedding).collect(Collectors.toList());
    VertorDataEntity vertorDataEntity = new VertorDataEntity();
    vertorDataEntity.setVector(vectors);
    vertorDataEntity.setContent(contentList);

    // Vector storage
    Integer count = pineconeService.insert(vertorDataEntity, "userId");

}
```
### Query from vector database
```java
public void test_query_vector_store() throws Exception {
    // // Obtain embedding service instance
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // Build the question to be queried and convert it to a vector
    Embedding build = Embedding.builder()
            .input("question")
            .model("text-embedding-3-small")
            .build();
    EmbeddingResponse embedding = embeddingService.embedding(build);
    List<Float> question = embedding.getData().get(0).getEmbedding();

    // Build the query object for the vector database
    PineconeQuery pineconeQueryReq = PineconeQuery.builder()
            .namespace("userId")
            .vector(question)
            .build();

    String result = pineconeService.query(pineconeQueryReq, " ");
    
    // Carry the result and have a conversation with the chat service.
    // ......
}
```

### Delete data from vector database
```java
public void test_delete_vector_store() throws Exception {
    // Build parameters
    PineconeDelete pineconeDelete = PineconeDelete.builder()
                                    .deleteAll(true)
                                    .namespace("userId")
                                    .build();
    // Delete
    Boolean res = pineconeService.delete(pineconeDelete);
}
```



# Contribute to ai4j
You are welcome to provide suggestions, report issues, or contribute code to ai4j. You can contribute to ai4j in the following ways:

## Issue feedback
Please use the GitHub Issue page to report issues. Describe as specifically as possible how to reproduce your issue, including detailed information such as the operating system, Java version, and any relevant log traces.
## PR
1. Fork this repository and create your branch.
2. Write your code and test it.
3. Ensure that your code conforms to the existing style.
4. Write clear log information when submitting. For small changes, a single line of information is sufficient, but for larger changes, there should be a detailed description.
5. Complete the pull request form and ensure that changes are made on the `dev` branch and link to the issue that your PR addresses.

# Support
If you find this project helpful to you, please give it a star⭐。
