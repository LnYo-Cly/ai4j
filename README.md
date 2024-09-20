![Maven Central](https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=blue)
# ai4j
一款JavaSDK用于快速接入AI大模型应用，整合多平台大模型，如OpenAi、Ollama、智谱Zhipu(ChatGLM)、深度求索DeepSeek、月之暗面Moonshot(Kimi)、腾讯混元Hunyuan、零一万物(01)等等，提供统一的输入输出(对齐OpenAi)消除差异化，优化函数调用(Tool Call)，优化RAG调用、支持向量数据库(Pinecone)，并且支持JDK1.8，为用户提供快速整合AI的能力。


## 支持的平台
+ OpenAi
+ Zhipu(智谱)
+ DeepSeek(深度求索)
+ Moonshot(月之暗面)
+ Hunyuan(腾讯混元)
+ Lingyi(零一万物)
+ Ollama
+ 待添加(Qwen Llama MiniMax...)

## 支持的服务
+ Chat Completions（流式与非流式）
+ Embedding
+ 待添加

## 特性
+ 支持Spring以及普通Java应用、支持Java 8以上的应用
+ 多平台、多服务
+ 统一的输入输出
+ 统一的错误处理
+ 支持流式输出。支持函数调用参数流式输出
+ 轻松使用Tool Calls
+ 支持多个函数同时调用（智谱不支持）
+ 支持stream_options，流式输出直接获取统计token usage
+ 内置向量数据库支持: Pinecone
+ 使用Tika读取文件
+ Token统计`TikTokensUtil.java`

## 更新日志
+ [2024-09-20] 增加对Ollama平台的支持，并修复一些bug。发布0.6.1版本
+ [2024-09-19] 增加错误处理链，统一处理为openai错误类型; 修复部分情况下URL拼接问题，修复拦截器中response重复调用而导致的关闭问题。发布0.5.3版本
+ [2024-09-12] 修复上个问题OpenAi参数导致错误的遗漏，发布0.5.2版本
+ [2024-09-12] 修复SpringBoot 2.6以下导致OkHttp变为3.14版本的报错问题；修复OpenAi参数`parallel_tool_calls`在tools为null时的异常问题。发布0.5.1版本。
+ [2024-09-09] 新增零一万物大模型支持、发布0.5.0版本。
+ [2024-09-02] 新增腾讯混元Hunyuan平台支持（注意：所需apiKey 属于SecretId与SecretKey的拼接，格式为 {SecretId}.{SecretKey}），发布0.4.0版本。
+ [2024-08-30] 新增对Moonshot(Kimi)平台的支持，增加`OkHttpUtil.java`实现忽略SSL证书的校验。
+ [2024-08-29] 新增对DeepSeek平台的支持、新增stream_options可以直接统计usage、新增错误拦截器`ErrorInterceptor.java`、发布0.3.0版本。
+ [2024-08-29] 修改SseListener以兼容智谱函数调用。
+ [2024-08-28] 添加token统计、添加智谱AI的Chat服务、优化函数调用可以支持多轮多函数。
+ [2024-08-17] 增强SseListener监听器功能。发布0.2.0版本。

## 教程文档
+ [快速接入SpringBoot、接入流式与非流式以及函数调用](http://t.csdnimg.cn/iuIAW)

## 其它
+ [低价中转平台-低价ApiKey](https://api.trovebox.online/)
+ [临时GPT网站-免费|重构中](https://api.fukaluosi.online/)

# 快速开始
## 导入
### Gradle
```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j', version: '${project.version}'
```

```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j-spring-boot-stater', version: '${project.version}'
```


### Maven
```xml
<!-- 非Spring应用 -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
    <version>${project.version}</version>
</dependency>

```
```xml
<!-- Spring应用 -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-stater</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 获取AI服务实例

### 非Spring获取
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
### Spring获取
```yml
# 国内访问默认需要代理
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
// 注入Ai服务
@Autowired
private AiService aiService;

// 获取需要的服务实例
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);
// ......
```

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

## Embedding服务

```java
public void test_embed() throws Exception {
    // 获取embedding服务实例
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // 构建请求参数
    Embedding embeddingReq = Embedding.builder().input("1+1").build();

    // 发送embedding请求
    EmbeddingResponse embeddingResp = embeddingService.embedding(embeddingReq);

    System.out.println(embeddingResp);
}
```

## RAG
### 配置向量数据库
```yml
ai:
  vector:
    pinecone:
      url: ""
      key: ""
```
### 获取实例
```java
@Autowired
private PineconeService pineconeService;
```
### 插入向量数据库
```java
public void test_insert_vector_store() throws Exception {
    // 获取embedding服务实例
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // Tika读取file文件内容
    String fileContent = TikaUtil.parseFile(new File("D:\\data\\test\\test.txt"));

    // 分割文本内容
    RecursiveCharacterTextSplitter recursiveCharacterTextSplitter = new RecursiveCharacterTextSplitter(1000, 200);
    List<String> contentList = recursiveCharacterTextSplitter.splitText(fileContent);

    // 转为向量
    Embedding build = Embedding.builder()
            .input(contentList)
            .model("text-embedding-3-small")
            .build();
    EmbeddingResponse embedding = embeddingService.embedding(build);
    List<List<Float>> vectors = embedding.getData().stream().map(EmbeddingObject::getEmbedding).collect(Collectors.toList());
    VertorDataEntity vertorDataEntity = new VertorDataEntity();
    vertorDataEntity.setVector(vectors);
    vertorDataEntity.setContent(contentList);
    
    // 向量存储
    Integer count = pineconeService.insert(vertorDataEntity, "userId");

}
```
### 从向量数据库查询
```java
public void test_query_vector_store() throws Exception {
    // 获取embedding服务实例
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // 构建要查询的问题，转为向量
    Embedding build = Embedding.builder()
            .input("question")
            .model("text-embedding-3-small")
            .build();
    EmbeddingResponse embedding = embeddingService.embedding(build);
    List<Float> question = embedding.getData().get(0).getEmbedding();

    // 构建向量数据库的查询对象
    PineconeQuery pineconeQueryReq = PineconeQuery.builder()
            .namespace("userId")
            .vector(question)
            .build();

    String result = pineconeService.query(pineconeQueryReq, " ");
    
    // 携带result，与chat服务进行对话
    // ......
}
```

### 删除向量数据库数据
```java
public void test_delete_vector_store() throws Exception {
    // 构建参数
    PineconeDelete pineconeDelete = PineconeDelete.builder()
                                    .deleteAll(true)
                                    .namespace("userId")
                                    .build();
    // 删除
    Boolean res = pineconeService.delete(pineconeDelete);
}
```



# 为AI4J提供贡献
欢迎您对AI4J提出建议、报告问题或贡献代码。您可以按照以下的方式为AI4J提供贡献: 

## 问题反馈
请使用GitHub Issue页面报告问题。尽可能具体地说明如何重现您的问题，包括操作系统、Java版本和任何相关日志跟踪等详细信息。

## PR
1. Fork 本仓库并创建您的分支。
2. 编写您的代码，并进行测试。
3. 确保您的代码符合现有的样式。
4. 提交时编写清晰的日志信息。对于小的改动，单行信息就可以了，但较大的改动应该有详细的描述。
5. 完成拉取请求表单，确保在`dev`分支进行改动，链接到您的 PR 解决的问题。

# 支持
如果您觉得这个项目对您有帮助，请点一个star⭐。


# 贡献者

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

<a href="https://github.com/LnYo-Cly/ai4j/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=LnYo-Cly/ai4j" />
</a>

