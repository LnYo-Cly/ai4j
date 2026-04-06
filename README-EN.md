<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" />
</p>

<p align="center">
  <a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j">
    <img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" />
  </a>
  <a href="https://lnyo-cly.github.io/ai4j/">
    <img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" />
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">
    <img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" />
  </a>
  <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" />
  <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" />
  <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" />
  <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" />
  <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" />
</p>

# ai4j
A Java AI Agentic development toolkit for JDK 8+, combining foundational AI capabilities with higher-level agent development capabilities.  
It covers multi-provider model access, unified I/O, Tool Calling, MCP, RAG, unified `VectorStore`, ChatMemory, agent runtime, coding agent, CLI / TUI / ACP, FlowGram integration, and integration with published AgentFlow endpoints such as Dify, Coze, and n8n, helping Java applications grow from basic model integration to more complete agentic application development.

This repository has evolved into a multi-module SDK. In addition to the core `ai4j` module, it now provides `ai4j-agent`, `ai4j-coding`, `ai4j-cli`, `ai4j-spring-boot-starter`, `ai4j-flowgram-spring-boot-starter`, and `ai4j-bom`. If you only need the basic LLM integration layer, start with `ai4j`. If you need agent runtime, coding agent, CLI / ACP, Spring Boot, or FlowGram integration, add the corresponding modules.

## Positioning Compared with Common Java AI Options

| Option | Java baseline | Application style | Primary focus |
| --- | --- | --- | --- |
| `ai4j` | `JDK 8+` | Plain Java / Spring | Unified model access, Tool / MCP / RAG, agent runtime, coding agent, CLI / TUI / ACP |
| `Spring AI` | `Java 17+` | `Spring Boot 3.x` | Spring-native AI integration, model access, Tool Calling, MCP, and RAG |
| `Spring AI Alibaba` | `Java 17+` | `Spring Boot 3.x` | Spring and Alibaba Cloud AI ecosystem integration |
| `LangChain4j` | `Java 17+` | Plain Java / Spring / Quarkus and more | General Java abstractions for LLM, agent, and RAG integration, plus AI Services |

## Supported platforms
+ OpenAi
+ Jina (Rerank / Jina-compatible Rerank)
+ Zhipu
+ DeepSeek
+ Moonshot
+ Tencent Hunyuan
+ Lingyi AI
+ Ollama
+ MiniMax
+ Baichuan

## Supported services
+ Chat Completions（streaming and non-streaming）
+ Responses
+ Embedding
+ Rerank
+ Audio
+ Image
+ Realtime

## Supported AgentFlow / hosted workflow platforms
+ Dify (chat / workflow)
+ Coze (chat / workflow)
+ n8n (webhook workflow)

## Features
+ Supports Spring and ordinary Java applications. Supports applications above Java 8.
+ Multi-platform and multi-service.
+ Provides `AgentFlow` support for integrating published Agent / Workflow endpoints from Dify, Coze, and n8n.
+ Provides `ai4j-agent` as the general agent runtime, with ReAct, subagents, agent teams, memory, tracing, and tool loop support.
+ Built-in Coding Agent CLI / TUI with interactive repository sessions, provider profiles, workspace model override, and session/process management.
+ Provides `ai4j-coding` as the coding agent runtime, with workspace-aware tools, outer loop, checkpoint compaction, subagent, and team collaboration support.
+ Provides `ai4j-flowgram-spring-boot-starter` for integrating FlowGram workflows and trace in Spring Boot applications.
+ Provides `ai4j-bom` for version alignment across multiple ai4j modules.
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

## Coding Agent CLI / TUI

AI4J now includes `ai4j-cli`, which can be used directly as a local coding agent. Current capabilities include:

+ one-shot and persistent sessions
+ CLI and TUI interaction modes
+ provider profile persistence
+ workspace-level model override
+ subagent and agent team collaboration
+ session persistence, resume, fork, history, tree, events, replay
+ team board, team messages, and team resume for collaboration visibility
+ process management and buffered logs

### Install

```bash
curl -fsSL https://lnyo-cly.github.io/ai4j/install.sh | sh
```

```powershell
irm https://lnyo-cly.github.io/ai4j/install.ps1 | iex
```

The installer downloads `ai4j-cli` from Maven Central and creates the `ai4j` command. Java 8+ must already be installed on the machine.

### one-shot example

```powershell
ai4j code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

### interactive CLI example

```powershell
ai4j code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### TUI example

```powershell
ai4j tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### ACP example

```powershell
ai4j acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

### Build from source (optional)

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

Artifact:

```text
ai4j-cli/target/ai4j-cli-<version>-jar-with-dependencies.jar
```

If you want to run the locally built artifact directly:

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-<version>-jar-with-dependencies.jar code --help
```

### Current protocol rules

The CLI currently exposes only two protocol families:

+ `chat`
+ `responses`

If `--protocol` is omitted, the CLI resolves a default locally from provider/baseUrl:

+ `openai` + official OpenAI host -> `responses`
+ `openai` + custom compatible `baseUrl` -> `chat`
+ `doubao` / `dashscope` -> `responses`
+ other providers -> `chat`

Notes:

+ `auto` is no longer exposed to users
+ legacy `auto` values in existing config files are normalized to explicit protocols on load

### provider profile locations

+ global config: `~/.ai4j/providers.json`
+ workspace config: `<workspace>/.ai4j/workspace.json`

Recommended workflow:

+ keep reusable long-term runtime profiles in the global config
+ let each workspace reference one `activeProfile`
+ use workspace `modelOverride` for temporary model switching

### Common commands

+ `/providers`
+ `/provider`
+ `/provider use <name>`
+ `/provider save <name>`
+ `/provider add <name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]`
+ `/provider edit <name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]`
+ `/provider default <name|clear>`
+ `/provider remove <name>`
+ `/model`
+ `/model <name>`
+ `/model reset`
+ `/stream [on|off]`
+ `/processes`
+ `/process status|follow|logs|write|stop ...`
+ `/resume <id>` / `/load <id>` / `/fork ...`

### Documentation entry points

+ [Coding Agent CLI Quickstart](docs-site/docs/getting-started/coding-agent-cli-quickstart.md)
+ [Coding Agent CLI and TUI](docs-site/docs/agent/coding-agent-cli.md)
+ [Multi-Provider Profiles](docs-site/docs/agent/multi-provider-profiles.md)
+ [Coding Agent Command Reference](docs-site/docs/agent/coding-agent-command-reference.md)
+ [Provider Configuration Examples](docs-site/docs/agent/provider-config-examples.md)

## Other support
+ [[Low-cost transit platform] Low-cost ApiKey - Limited-time special offer 0.7:1 - Supports the latest o1 model.](https://api.trovebox.online/)

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

