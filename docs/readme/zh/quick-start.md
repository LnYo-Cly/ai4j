[返回中文 README](../../../README.md) · [English README](../../../README-EN.md)

# 快速开始
## 导入
### 模块选型
+ 只需要基础 LLM / Tool Call / MCP / RAG 能力：引入 `ai4j`
+ 需要通用 Agent 运行时：引入 `ai4j-agent`
+ 需要 Coding Agent、workspace tools、outer loop：引入 `ai4j-coding`
+ 需要本地 CLI / TUI / ACP 宿主：引入 `ai4j-cli`
+ 需要 Spring Boot 自动配置：引入 `ai4j-spring-boot-starter`
+ 需要 FlowGram 工作流集成：引入 `ai4j-flowgram-spring-boot-starter`
+ 需要开发第三方插件：引入 `ai4j-extension-api`
+ 需要让 Agent 结构化询问用户：引入 `ai4j-plugin-ask-user`
+ 同时引入多个模块：建议额外引入 `ai4j-bom`

### Gradle
```groovy
implementation platform("io.github.lnyo-cly:ai4j-bom:${project.version}")
implementation "io.github.lnyo-cly:ai4j"
implementation "io.github.lnyo-cly:ai4j-agent"
implementation "io.github.lnyo-cly:ai4j-plugin-ask-user"
```

```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j', version: '${project.version}'
```

```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j-spring-boot-starter', version: '${project.version}'
```


### Maven
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.lnyo-cly</groupId>
            <artifactId>ai4j-bom</artifactId>
            <version>${project.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<!-- 多模块项目推荐 -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-agent</artifactId>
</dependency>

<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-coding</artifactId>
</dependency>

<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-plugin-ask-user</artifactId>
</dependency>
```

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
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 获取AI服务实例

### 非Spring首聊推荐

如果只是先跑通第一条同步 Chat 请求，直接使用 AI4J 的核心对象链：

```java
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;

public class Ai4jFirstChat {
    public static void main(String[] args) throws Exception {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);

        AiService aiService = new AiService(configuration);
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("用一句话介绍 AI4J"))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(request);
        String text = response.getChoices().get(0).getMessage().getContent().getText();
        System.out.println(text);
    }
}
```

这条路径也是 AI4J 的真实主线：`Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse`。后续自定义 `OkHttpClient`、代理、超时、流式、多模态、Tool、MCP、RAG 或读取完整 `ChatCompletionResponse` 时，都沿着同一条对象链继续扩展。

### 非Spring进阶获取
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
## 国内访问默认需要代理
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
