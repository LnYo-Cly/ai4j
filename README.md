![Maven Central](https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=blue)
# ai4j
由于SpringAI需要使用JDK17和Spring Boot3， 但是目前很多应用依旧使用的JDK8版本，所以使用可以支持JDK8的AI4J来接入OpenAI等大模型。  
一款JavaSDK用于快速接入AI大模型应用，整合多平台大模型，如OpenAi、Ollama、智谱Zhipu(ChatGLM)、深度求索DeepSeek、月之暗面Moonshot(Kimi)、腾讯混元Hunyuan、零一万物(01)、MiniMax、百川Baichuan等等，提供统一的输入输出(对齐OpenAi)消除差异化，优化函数调用(Tool Call)，优化RAG调用、支持统一 `VectorStore` 抽象与多种向量数据库（Pinecone、Qdrant、pgvector、Milvus），并且支持JDK1.8，为用户提供快速整合AI的能力。
支持MCP协议，支持STDIO,SSE,Streamable HTTP; 支持MCP Server与MCP Client; 支持MCP网关; 支持自定义MCP数据源; 支持MCP自动重连

## 计划列表
- [x] 对接MCP，支持STDIO,SSE,Streamable HTTP;
- [ ] 对接 flowgram.ai 工作流组件
- [ ] 对接dify平台
- [ ] 对接coze平台

## 支持的平台
+ OpenAi(包含与OpenAi请求格式相同/兼容的平台)
+ Jina（Rerank / Jina-compatible Rerank）
+ Zhipu(智谱)
+ DeepSeek(深度求索)
+ Moonshot(月之暗面)
+ Hunyuan(腾讯混元)
+ Lingyi(零一万物)
+ Ollama
+ MiniMax
+ Baichuan

## 待添加
+ LLM(Qwen、Llama、Mistral...)
+ MLLM(Gemini、InternVL...)
+ t2i(stable diffusion、imagen...)

## 支持的服务
+ Chat Completions（流式与非流式）
+ Responses
+ Embedding
+ Rerank
+ Audio
+ Image
+ Realtime

## 特性
+ 支持MCP服务，内置MCP网关，支持建立动态MCP数据源。
+ 支持Spring以及普通Java应用、支持Java 8以上的应用
+ 多平台、多服务
+ 内置 Coding Agent CLI / TUI，支持本地代码仓交互式会话、provider profile、workspace model override、session/process 管理
+ 统一的输入输出
+ 统一的错误处理
+ 支持SPI机制，可自定义Dispatcher和ConnectPool
+ 支持服务增强，例如增加websearch服务
+ 支持流式输出。支持函数调用参数流式输出.
+ 简洁的多模态调用方式，例如vision识图
+ 轻松使用Tool Calls
+ 支持多个函数同时调用（智谱不支持）
+ 支持stream_options，流式输出直接获取统计token usage
+ 内置 `ChatMemory`，支持基础多轮会话上下文维护，可同时适配 Chat / Responses
+ 支持RAG，内置统一 `VectorStore` 抽象，当前支持: Pinecone、Qdrant、pgvector、Milvus
+ 内置 `IngestionPipeline`，统一串联 `DocumentLoader -> Chunker -> MetadataEnricher -> Embedding -> VectorStore.upsert`
+ 内置 `DenseRetriever`、`Bm25Retriever`、`HybridRetriever`，可按语义检索、关键词检索、混合检索方式组合知识库召回
+ `HybridRetriever` 支持 `RrfFusionStrategy`、`RsfFusionStrategy`、`DbsfFusionStrategy`，默认使用 RRF；融合排序与 `Reranker` 语义精排解耦
+ 支持统一 `IRerankService`，当前可接 Jina / Jina-compatible、Ollama、Doubao(方舟知识库重排)；可通过 `ModelReranker` 无缝接入 RAG 精排
+ RAG 运行时可直接拿到 `rank/retrieverSource/retrievalScore/fusionScore/rerankScore/scoreDetails/trace`，并可通过 `RagEvaluator` 计算 `Precision@K/Recall@K/F1@K/MRR/NDCG`
+ 使用Tika读取文件
+ Token统计`TikTokensUtil.java`

## 官方文档站
+ 在线文档站：`https://docs.ai4j.dev`
+ 文档站源码位于 `docs-site/`
+ 适合直接使用者的入口：`docs-site/docs/coding-agent/`
+ 适合 SDK 接入的入口：`docs-site/docs/getting-started/` 与 `docs-site/docs/ai-basics/`
+ 适合协议与扩展集成的入口：`docs-site/docs/mcp/`、`docs-site/docs/agent/`

推荐阅读顺序：

+ `docs-site/docs/intro.md`
+ `docs-site/docs/getting-started/installation.md`
+ `docs-site/docs/coding-agent/overview.md`
+ `docs-site/docs/ai-basics/overview.md`
+ `docs-site/docs/mcp/overview.md`

基础会话上下文新增入口：

+ `docs-site/docs/ai-basics/chat/chat-memory.md`
+ `docs-site/docs/ai-basics/services/rerank.md`
+ `docs-site/docs/ai-basics/rag/ingestion-pipeline.md`

本地运行文档站：

```powershell
cd .\docs-site
npm install
npm run start
```

```powershell
cd .\docs-site
npm run build
```

## 更新日志
+ [2026-03-28] 修复 Coding Agent ACP 流式场景下纯空白 chunk 被 runtime 过滤的问题；ACP 保持透传原始 delta，不做 chunk 聚合；补充 CLI/文档中的流式语义说明
+ [2026-03-26] 新增 Coding Agent CLI / TUI 文档与能力说明，覆盖交互式会话、provider profile、workspace model override、命令参考与配置样例
+ [2025-08-19] 修复传递有验证参数的sse-url时，key丢失问题
+ [2025-08-08] OpenAi: max_tokens字段现已废弃，推荐使用max_completion_tokens(GPT-5已经不支持max_tokens字段)
+ [2025-08-08] 支持MCP协议，支持STDIO,SSE,Streamable HTTP; 支持MCP Server与MCP Client; 支持MCP网关; 支持自定义MCP数据源; 支持MCP自动重连
+ [2025-06-23] 修复ollama的流式错误；修复ollama函数调用的错误；修复moonshot请求时错误；修复ollama embedding错误；修复思考无内容；修复日志冲突；新增自定义异常方法。
+ [2025-02-28] 新增对Ollama平台的embedding接口的支持。
+ [2025-02-17] 新增对DeepSeek平台推理模型的适配。
+ [2025-02-12] 为Ollama平台添加Authorization
+ [2025-02-11] 实现自定义的Jackson序列化，解决OpenAi已经无法通过Json String来直接实现多模态接口的问题。
+ [2024-12-12] 使用装饰器模式增强Chat服务，支持SearXNG网络搜索增强，无需模型支持内置搜索以及function_call。
+ [2024-10-17] 支持SPI机制，可自定义Dispatcher和ConnectPool。新增百川Baichuan平台Chat接口支持。
+ [2024-10-16] 增加MiniMax平台Chat接口对接
+ [2024-10-15] 增加realtime服务
+ [2024-10-12] 修复早期遗忘的小bug; 修复错误拦截器导致的音频字节流异常错误问题; 增加OpenAi Audio服务。
+ [2024-10-10] 增强对SSE输出的获取，新加入`currData`属性，记录当前消息的整个对象。而原先的`currStr`为当前消息的content内容，保留不变。
+ [2024-09-26] 修复有关Pinecone向量数据库的一些问题。发布0.6.3版本
+ [2024-09-20] 增加对Ollama平台的支持，并修复一些bug。发布0.6.2版本
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
+ [Java快速接入qwen2.5、llama3.1等Ollama平台开源大模型](https://blog.csdn.net/qq_35650513/article/details/142408092?spm=1001.2014.3001.5501)
+ [Java搭建法律AI助手，快速实现RAG应用](https://blog.csdn.net/qq_35650513/article/details/142568177?fromshare=blogdetail&sharetype=blogdetail&sharerId=142568177&sharerefer=PC&sharesource=qq_35650513&sharefrom=from_link)
+ [大模型不支持联网搜索？为Deepseek、Qwen、llama等本地模型添加网络搜索](https://blog.csdn.net/qq_35650513/article/details/144572824)
+ [java快速接入mcp以及结合mysql动态管理](https://blog.csdn.net/qq_35650513/article/details/150532784?fromshare=blogdetail&sharetype=blogdetail&sharerId=150532784&sharerefer=PC&sharesource=qq_35650513&sharefrom=from_link)

## Coding Agent CLI / TUI

AI4J 目前已经内置 `ai4j-cli`，可以直接作为本地 coding agent 使用，支持：

+ one-shot 与持续会话
+ CLI / TUI 两种交互模式
+ provider profile 持久化
+ workspace 级 model override
+ session 持久化、resume、fork、history、tree、events、replay
+ process 管理与日志查看

### 打包

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

产物：

```text
ai4j-cli/target/ai4j-cli-2.0.0-jar-with-dependencies.jar
```

### one-shot 示例

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

### 交互式 CLI 示例

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### TUI 示例

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### ACP 示例

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

### 当前协议规则

当前 CLI 对用户只暴露两种协议：

+ `chat`
+ `responses`

如果省略 `--protocol`，会按 provider/baseUrl 在本地推导默认值：

+ `openai` + 官方 OpenAI host -> `responses`
+ `openai` + 自定义兼容 `baseUrl` -> `chat`
+ `doubao` / `dashscope` -> `responses`
+ 其他 provider -> `chat`

注意：

+ 不再对用户暴露 `auto`
+ 旧配置中的 `auto` 会在读取时自动归一化为显式协议

### provider profile 配置位置

+ 全局配置：`~/.ai4j/providers.json`
+ 工作区配置：`<workspace>/.ai4j/workspace.json`

推荐工作流：

+ 全局保存长期可复用 profile
+ workspace 只引用当前 activeProfile
+ 临时切模型时使用 workspace 的 `modelOverride`

`workspace.json` 也可以显式挂载额外 skill 目录：

```json
{
  "activeProfile": "openai-main",
  "modelOverride": "gpt-5-mini",
  "enabledMcpServers": ["fetch"],
  "skillDirectories": [
    ".ai4j/skills",
    "C:/skills/team",
    "../shared-skills"
  ]
}
```

skill 发现规则：

+ 默认扫描 `<workspace>/.ai4j/skills`
+ 默认扫描 `~/.ai4j/skills`
+ `skillDirectories` 中的相对路径按 workspace 根目录解析
+ 进入 CLI 后可用 `/skills` 查看当前发现到的 skill
+ 可用 `/skills <name>` 查看某个 skill 的路径、来源、描述和扫描 roots，不打印 `SKILL.md` 正文

### `/stream`、`Esc` 与状态提示

当前 `/stream` 的语义是“当前 CLI 会话里的模型请求是否启用 `stream`”，不是单纯的 transcript 渲染开关：

+ 作用域是当前 CLI 会话
+ `/stream on|off` 会切换请求级 `stream=true|false`，并立即重建当前 session runtime
+ `on` 时 provider 响应按增量到达，assistant 文本也按增量呈现
+ `off` 时等待完整响应后再输出整理后的完成块
+ 流式 event 粒度由上游 provider/SSE 决定，不保证“一个 event = 一个 token”
+ 如果通过 ACP/IDE 接入，宿主应按收到的 chunk 顺序渲染，并保留换行与空白

当前交互壳层里：

+ `Esc` 在活跃 turn 中断当前任务；空闲时关闭 palette 或清空输入
+ 状态栏会显示 `Thinking`、`Connecting`、`Responding`、`Working`、`Retrying`
+ 一段时间没有新进展会升级为 `Waiting`
+ 更久没有新进展会显示 `Stalled`，并提示 `press Esc to interrupt`

### 常用命令

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
+ `/skills`
+ `/skills <name>`
+ `/stream [on|off]`
+ `/processes`
+ `/process status|follow|logs|write|stop ...`
+ `/resume <id>` / `/load <id>` / `/fork ...`

### 文档入口

+ [Coding Agent 总览](docs-site/docs/coding-agent/overview.md)
+ [Coding Agent 快速开始](docs-site/docs/coding-agent/quickstart.md)
+ [CLI / TUI 使用指南](docs-site/docs/coding-agent/cli-and-tui.md)
+ [会话、流式与进程](docs-site/docs/coding-agent/session-runtime.md)
+ [配置体系](docs-site/docs/coding-agent/configuration.md)
+ [Tools 与审批机制](docs-site/docs/coding-agent/tools-and-approvals.md)
+ [Skills 使用与组织](docs-site/docs/coding-agent/skills.md)
+ [MCP 对接](docs-site/docs/coding-agent/mcp-integration.md)
+ [ACP 集成](docs-site/docs/coding-agent/acp-integration.md)
+ [TUI 定制与主题](docs-site/docs/coding-agent/tui-customization.md)
+ [命令参考](docs-site/docs/coding-agent/command-reference.md)

## 其它支持
+ [[低价中转平台] 低价ApiKey—限时特惠 ](https://api.trovebox.online/)
+ [[在线平台] 每日白嫖额度-所有模型均可使用 ](https://chat.trovebox.online/)

# 快速开始
## 导入
### Gradle
```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j', version: '${project.version}'
```

```groovy
implementation group: 'io.github.lnyo-cly', name: 'ai4j-spring-boot-starter', version: '${project.version}'
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
    <artifactId>ai4j-spring-boot-starter</artifactId>
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

## Rerank服务

### 直接调用统一重排服务

```java
IRerankService rerankService = aiService.getRerankService(PlatformType.JINA);

RerankRequest request = RerankRequest.builder()
        .model("jina-reranker-v2-base-multilingual")
        .query("哪段最适合回答 Java 8 为什么仍然常见")
        .documents(Arrays.asList(
                RerankDocument.builder().id("doc-1").text("Java 8 仍是很多传统系统的默认运行时").build(),
                RerankDocument.builder().id("doc-2").text("AI4J 提供统一 Chat、Responses 和 RAG 接口").build(),
                RerankDocument.builder().id("doc-3").text("历史中间件和升级成本让很多企业延后 JDK 升级").build()
        ))
        .topN(2)
        .build();

RerankResponse response = rerankService.rerank(request);
System.out.println(response.getResults());
```

### 作为 RAG 精排器接入

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先保留制度原文、版本说明和编号明确的片段"
);
```

## RAG
### 推荐：使用统一 IngestionPipeline 入库

```java
VectorStore vectorStore = aiService.getQdrantVectorStore();

IngestionPipeline ingestionPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        vectorStore
);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/employee-handbook.md")
                .tenant("acme")
                .biz("hr")
                .version("2026.03")
                .build())
        .source(IngestionSource.text("第一章 假期政策。第二章 报销政策。"))
        .build());

System.out.println(ingestResult.getUpsertedCount());
```

如果你已经走 Pinecone，也可以直接：

```java
IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);
```

推荐主线是：

1. `IngestionPipeline` 负责文档入库
2. `VectorStore` 负责底层向量存储
3. `DenseRetriever / HybridRetriever / ModelReranker / RagService` 负责查询阶段

完整说明见：

+ `docs-site/docs/ai-basics/rag/ingestion-pipeline.md`
+ `docs-site/docs/ai-basics/rag/overview.md`

### 配置向量数据库
```yml
ai:
  vector:
    pinecone:
      host: ""
      key: ""
```
### 推荐：Pinecone 也走统一 `VectorStore + IngestionPipeline`

```java
VectorStore vectorStore = aiService.getPineconeVectorStore();

IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("tenant_a_hr_v202603")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/employee-handbook.pdf")
                .tenant("tenant_a")
                .biz("hr")
                .version("2026.03")
                .build())
        .source(IngestionSource.file(new File("D:/data/employee-handbook.pdf")))
        .build());

System.out.println("upserted=" + ingestResult.getUpsertedCount());
```

### 查询阶段：直接走统一 `RagService`

```java
RagService ragService = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore
);

RagQuery ragQuery = RagQuery.builder()
        .query("年假如何计算")
        .dataset("tenant_a_hr_v202603")
        .embeddingModel("text-embedding-3-small")
        .topK(5)
        .build();

RagResult ragResult = ragService.search(ragQuery);

System.out.println(ragResult.getContext());
System.out.println(ragResult.getCitations());
```

### 如果需要更高精度，再接 Rerank

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先制度原文、章节标题和编号明确的片段"
);

RagService ragService = new DefaultRagService(
        new DenseRetriever(
                aiService.getEmbeddingService(PlatformType.OPENAI),
                vectorStore
        ),
        reranker,
        new DefaultRagContextAssembler()
);
```

### 什么时候还需要直接用已废弃的 `PineconeService`（Deprecated）

`PineconeService` 目前在文档层已视为 Deprecated。只有在你明确需要 Pinecone 特有的底层控制时，才建议继续直接用：

+ namespace 级底层操作
+ 兼容旧项目里已经写死的 `PineconeQuery / PineconeDelete`
+ 你就是在做 Pinecone 专用封装，而不是面向统一 RAG 抽象开发

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
# spring应用
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


# 为AI4J提供贡献
欢迎您对AI4J提出建议、报告问题或贡献代码。您可以按照以下的方式为AI4J提供贡献: 

## 问题反馈
请使用GitHub Issue页面报告问题。尽可能具体地说明如何重现您的问题，包括操作系统、Java版本和任何相关日志跟踪等详细信息。

## PR
1. Fork 本仓库并创建您的分支（建议命名：feature/功能名、fix/问题名 或 docs/文档优化）。
2. 编写代码或修改内容（如更新文档），并完成测试（确保功能正常或文档无误）。
3. 确保您的代码符合现有的样式。
4. 提交时编写清晰的日志信息。对于小的改动，单行信息就可以了，但较大的改动应该有详细的描述。
5. 完成拉取请求表单，确保在`dev`分支进行改动，链接到您的 PR 解决的问题。

# 支持
如果您觉得这个项目对您有帮助，请点一个star⭐。

# Buy Me a Coffee
您的支持是我更新的最大的动力。

![新图片](https://cdn.jsdelivr.net/gh/lnyo-cly/blogImg/pics/新图片.jpg)

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


# ⭐️ Star History
<a href="https://star-history.com/#LnYo-Cly/ai4j&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=LnYo-Cly/ai4j&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=LnYo-Cly/ai4j&type=Date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=LnYo-Cly/ai4j&type=Date" />
 </picture>
</a>
