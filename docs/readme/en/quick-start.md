[Back to English README](../../../README-EN.md) · [中文 README](../../../README.md)

# Quick start
## Import
### Module selection
+ Use `ai4j` for the core LLM / Tool Call / MCP / RAG capabilities
+ Use `ai4j-agent` for the general agent runtime
+ Use `ai4j-coding` for coding agent, workspace tools, and outer loop
+ Use `ai4j-cli` for the local CLI / TUI / ACP host
+ Use `ai4j-spring-boot-starter` for Spring Boot auto-configuration
+ Use `ai4j-flowgram-spring-boot-starter` for FlowGram workflow integration
+ Use `ai4j-bom` when you want version alignment across multiple modules

### Gradle
```groovy
implementation platform("io.github.lnyo-cly:ai4j-bom:${project.version}")
implementation "io.github.lnyo-cly:ai4j"
implementation "io.github.lnyo-cly:ai4j-agent"
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
<!-- Recommended for multi-module usage -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-agent</artifactId>
</dependency>

<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-coding</artifactId>
</dependency>
```

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
## Domestic access usually requires a proxy by default.
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
