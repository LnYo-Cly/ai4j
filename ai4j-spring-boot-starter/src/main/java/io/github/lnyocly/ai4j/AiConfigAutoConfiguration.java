package io.github.lnyocly.ai4j;

import cn.hutool.core.bean.BeanUtil;
import io.github.lnyocly.ai4j.agentflow.AgentFlow;
import io.github.lnyocly.ai4j.agentflow.AgentFlowConfig;
import io.github.lnyocly.ai4j.config.*;
import io.github.lnyocly.ai4j.interceptor.ContentTypeInterceptor;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.network.ConnectionPoolProvider;
import io.github.lnyocly.ai4j.network.DispatcherProvider;
import io.github.lnyocly.ai4j.service.AiConfig;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.service.factory.AiServiceFactory;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import io.github.lnyocly.ai4j.service.factory.DefaultAiServiceFactory;
import io.github.lnyocly.ai4j.service.factory.DefaultAiServiceRegistry;
import io.github.lnyocly.ai4j.service.factory.FreeAiService;
import io.github.lnyocly.ai4j.network.OkHttpUtil;
import io.github.lnyocly.ai4j.rag.DefaultRagContextAssembler;
import io.github.lnyocly.ai4j.rag.NoopReranker;
import io.github.lnyocly.ai4j.rag.RagContextAssembler;
import io.github.lnyocly.ai4j.rag.Reranker;
import io.github.lnyocly.ai4j.service.spi.ServiceLoaderUtil;
import io.github.lnyocly.ai4j.vector.service.PineconeService;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.milvus.MilvusVectorStore;
import io.github.lnyocly.ai4j.vector.store.pgvector.PgVectorStore;
import io.github.lnyocly.ai4j.vector.store.pinecone.PineconeVectorStore;
import io.github.lnyocly.ai4j.vector.store.qdrant.QdrantVectorStore;
import io.github.lnyocly.ai4j.websearch.searxng.SearXNGConfig;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/9 23:22
 */
@Configuration
@EnableConfigurationProperties({
        AiConfigProperties.class,
        OpenAiConfigProperties.class,
        OkHttpConfigProperties.class,
        PineconeConfigProperties.class,
        QdrantConfigProperties.class,
        MilvusConfigProperties.class,
        PgVectorConfigProperties.class,
        ZhipuConfigProperties.class,
        DeepSeekConfigProperties.class,
        MoonshotConfigProperties.class,
        HunyuanConfigProperties.class,
        LingyiConfigProperties.class,
        OllamaConfigProperties.class,
        MinimaxConfigProperties.class,
        BaichuanConfigProperties.class,
        SearXNGConfigProperties.class,
        DashScopeConfigProperties.class,
        DoubaoConfigProperties.class,
        JinaConfigProperties.class,
        AgentFlowProperties.class
})

public class AiConfigAutoConfiguration {

    // okhttp閰嶇疆
    private final OkHttpConfigProperties okHttpConfigProperties;

    // 鍚戦噺鏁版嵁搴撻厤缃?
    private final PineconeConfigProperties pineconeConfigProperties;
    private final QdrantConfigProperties qdrantConfigProperties;
    private final MilvusConfigProperties milvusConfigProperties;
    private final PgVectorConfigProperties pgVectorConfigProperties;

    // searxng閰嶇疆
    private final SearXNGConfigProperties searXNGConfigProperties;

    // AI骞冲彴閰嶇疆
    private final AiConfigProperties aiConfigProperties;
    private final OpenAiConfigProperties openAiConfigProperties;
    private final ZhipuConfigProperties zhipuConfigProperties;
    private final DeepSeekConfigProperties deepSeekConfigProperties;
    private final MoonshotConfigProperties moonshotConfigProperties;
    private final HunyuanConfigProperties hunyuanConfigProperties;
    private final LingyiConfigProperties lingyiConfigProperties;
    private final OllamaConfigProperties ollamaConfigProperties;
    private final MinimaxConfigProperties minimaxConfigProperties;
    private final BaichuanConfigProperties baichuanConfigProperties;
    private final DashScopeConfigProperties dashScopeConfigProperties;
    private final DoubaoConfigProperties doubaoConfigProperties;
    private final JinaConfigProperties jinaConfigProperties;
    private final AgentFlowProperties agentFlowProperties;

    private io.github.lnyocly.ai4j.service.Configuration configuration = new io.github.lnyocly.ai4j.service.Configuration();

    public AiConfigAutoConfiguration(OkHttpConfigProperties okHttpConfigProperties, OpenAiConfigProperties openAiConfigProperties, PineconeConfigProperties pineconeConfigProperties, QdrantConfigProperties qdrantConfigProperties, MilvusConfigProperties milvusConfigProperties, PgVectorConfigProperties pgVectorConfigProperties, SearXNGConfigProperties searXNGConfigProperties, AiConfigProperties aiConfigProperties, ZhipuConfigProperties zhipuConfigProperties, DeepSeekConfigProperties deepSeekConfigProperties, MoonshotConfigProperties moonshotConfigProperties, HunyuanConfigProperties hunyuanConfigProperties, LingyiConfigProperties lingyiConfigProperties, OllamaConfigProperties ollamaConfigProperties, MinimaxConfigProperties minimaxConfigProperties, BaichuanConfigProperties baichuanConfigProperties, DashScopeConfigProperties dashScopeConfigProperties, DoubaoConfigProperties doubaoConfigProperties, JinaConfigProperties jinaConfigProperties, AgentFlowProperties agentFlowProperties) {
        this.okHttpConfigProperties = okHttpConfigProperties;
        this.openAiConfigProperties = openAiConfigProperties;
        this.pineconeConfigProperties = pineconeConfigProperties;
        this.qdrantConfigProperties = qdrantConfigProperties;
        this.milvusConfigProperties = milvusConfigProperties;
        this.pgVectorConfigProperties = pgVectorConfigProperties;
        this.searXNGConfigProperties = searXNGConfigProperties;
        this.aiConfigProperties = aiConfigProperties;
        this.zhipuConfigProperties = zhipuConfigProperties;
        this.deepSeekConfigProperties = deepSeekConfigProperties;
        this.moonshotConfigProperties = moonshotConfigProperties;
        this.hunyuanConfigProperties = hunyuanConfigProperties;
        this.lingyiConfigProperties = lingyiConfigProperties;
        this.ollamaConfigProperties = ollamaConfigProperties;
        this.minimaxConfigProperties = minimaxConfigProperties;
        this.baichuanConfigProperties = baichuanConfigProperties;
        this.dashScopeConfigProperties = dashScopeConfigProperties;
        this.doubaoConfigProperties = doubaoConfigProperties;
        this.jinaConfigProperties = jinaConfigProperties;
        this.agentFlowProperties = agentFlowProperties;
    }

    @Bean
    public AiService aiService() {
        return new AiService(configuration);
    }

    @Bean
    public AiServiceFactory aiServiceFactory() {
        return new DefaultAiServiceFactory();
    }

    @Bean
    public AiServiceRegistry aiServiceRegistry(AiServiceFactory aiServiceFactory) {
        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(BeanUtil.copyToList(aiConfigProperties.getPlatforms(), AiPlatform.class));
        return DefaultAiServiceRegistry.from(configuration, aiConfig, aiServiceFactory);
    }

    @Bean
    public FreeAiService getFreeAiService(AiServiceRegistry aiServiceRegistry) {
        return new FreeAiService(aiServiceRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.agentflow", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public AgentFlowRegistry agentFlowRegistry(AiService aiService) {
        Map<String, AgentFlow> agentFlows = new LinkedHashMap<String, AgentFlow>();
        if (agentFlowProperties.getProfiles() != null) {
            for (Map.Entry<String, AgentFlowProperties.EndpointProperties> entry : agentFlowProperties.getProfiles().entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                agentFlows.put(entry.getKey(), aiService.getAgentFlow(toAgentFlowConfig(entry.getValue())));
            }
        }
        return new AgentFlowRegistry(agentFlows, agentFlowProperties.getDefaultName());
    }

    @Bean
    @ConditionalOnBean(AgentFlowRegistry.class)
    @ConditionalOnProperty(prefix = "ai.agentflow", name = "default-name")
    @ConditionalOnMissingBean(AgentFlow.class)
    public AgentFlow agentFlow(AgentFlowRegistry agentFlowRegistry) {
        return agentFlowRegistry.getDefault();
    }

    @Bean
    public PineconeService pineconeService() {
        return new PineconeService(configuration);
    }

    @Bean
    @ConditionalOnMissingBean(PineconeVectorStore.class)
    public PineconeVectorStore pineconeVectorStore(PineconeService pineconeService) {
        return new PineconeVectorStore(pineconeService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.vector.qdrant", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(QdrantVectorStore.class)
    public QdrantVectorStore qdrantVectorStore() {
        return new QdrantVectorStore(configuration);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.vector.milvus", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(MilvusVectorStore.class)
    public MilvusVectorStore milvusVectorStore() {
        return new MilvusVectorStore(configuration);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.vector.pgvector", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(PgVectorStore.class)
    public PgVectorStore pgVectorStore() {
        return new PgVectorStore(configuration);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagContextAssembler ragContextAssembler() {
        return new DefaultRagContextAssembler();
    }

    @Bean
    @ConditionalOnMissingBean
    public Reranker ragReranker() {
        return new NoopReranker();
    }

    @PostConstruct
    private void init() {
        initOkHttp();

        initPineconeConfig();
        initQdrantConfig();
        initMilvusConfig();
        initPgVectorConfig();

        initSearXNGConfig();

        initOpenAiConfig();
        initZhipuConfig();
        initDeepSeekConfig();
        initMoonshotConfig();
        initHunyuanConfig();
        initLingyiConfig();
        initOllamaConfig();
        initMinimaxConfig();
        initBaichuanConfig();
        initDashScopeConfig();
        initDoubaoConfig();
        initJinaConfig();
    }



    private void initOkHttp() {
        //configuration.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1",10809)));

        // 鏃ュ織閰嶇疆
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(okHttpConfigProperties.getLog());

        // SPI鍔犺浇dispatcher鍜宑onnectionPool
        DispatcherProvider dispatcherProvider = ServiceLoaderUtil.load(DispatcherProvider.class);
        ConnectionPoolProvider connectionPoolProvider = ServiceLoaderUtil.load(ConnectionPoolProvider.class);

        // 寮€鍚?Http 瀹㈡埛绔?
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .addInterceptor(new ContentTypeInterceptor())
                .connectTimeout(okHttpConfigProperties.getConnectTimeout(), okHttpConfigProperties.getTimeUnit())
                .writeTimeout(okHttpConfigProperties.getWriteTimeout(), okHttpConfigProperties.getTimeUnit())
                .readTimeout(okHttpConfigProperties.getReadTimeout(), okHttpConfigProperties.getTimeUnit())
                .dispatcher(dispatcherProvider.getDispatcher())
                .connectionPool(connectionPoolProvider.getConnectionPool());

        // 鏄惁寮€鍚疨roxy浠ｇ悊
        if(StringUtils.isNotBlank(okHttpConfigProperties.getProxyUrl())){
            Proxy proxy = new Proxy(okHttpConfigProperties.getProxyType(), new InetSocketAddress(okHttpConfigProperties.getProxyUrl(), okHttpConfigProperties.getProxyPort()));
            okHttpBuilder.proxy(proxy);
        }

        // 蹇界暐SSL璇佷功楠岃瘉, 榛樿寮€鍚?
        if(okHttpConfigProperties.isIgnoreSsl()){
            try {
                okHttpBuilder
                        .sslSocketFactory(OkHttpUtil.getIgnoreInitedSslContext().getSocketFactory(), OkHttpUtil.IGNORE_SSL_TRUST_MANAGER_X509)
                        .hostnameVerifier(OkHttpUtil.getIgnoreSslHostnameVerifier());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (KeyManagementException e) {
                throw new RuntimeException(e);
            }
        }

        OkHttpClient okHttpClient = okHttpBuilder.build();

        configuration.setOkHttpClient(okHttpClient);
    }

    /**
     * 鍒濆鍖朞penai 閰嶇疆淇℃伅
     */
    private void initOpenAiConfig() {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost(openAiConfigProperties.getApiHost());
        openAiConfig.setApiKey(openAiConfigProperties.getApiKey());
        openAiConfig.setChatCompletionUrl(openAiConfigProperties.getChatCompletionUrl());
        openAiConfig.setEmbeddingUrl(openAiConfigProperties.getEmbeddingUrl());
        openAiConfig.setSpeechUrl(openAiConfigProperties.getSpeechUrl());
        openAiConfig.setTranscriptionUrl(openAiConfigProperties.getTranscriptionUrl());
        openAiConfig.setTranslationUrl(openAiConfigProperties.getTranslationUrl());
        openAiConfig.setRealtimeUrl(openAiConfigProperties.getRealtimeUrl());
        openAiConfig.setImageGenerationUrl(openAiConfigProperties.getImageGenerationUrl());
        openAiConfig.setResponsesUrl(openAiConfigProperties.getResponsesUrl());

        configuration.setOpenAiConfig(openAiConfig);
    }

    /**
     * 鍒濆鍖朲hipu 閰嶇疆淇℃伅
     */
    private void initZhipuConfig() {
        ZhipuConfig zhipuConfig = new ZhipuConfig();
        zhipuConfig.setApiHost(zhipuConfigProperties.getApiHost());
        zhipuConfig.setApiKey(zhipuConfigProperties.getApiKey());
        zhipuConfig.setChatCompletionUrl(zhipuConfigProperties.getChatCompletionUrl());
        zhipuConfig.setEmbeddingUrl(zhipuConfigProperties.getEmbeddingUrl());

        configuration.setZhipuConfig(zhipuConfig);
    }

    /**
     * 鍒濆鍖栧悜閲忔暟鎹簱 pinecone
     */
    private void initPineconeConfig() {
        PineconeConfig pineconeConfig = new PineconeConfig();
        pineconeConfig.setHost(pineconeConfigProperties.getHost());
        pineconeConfig.setKey(pineconeConfigProperties.getKey());
        pineconeConfig.setUpsert(pineconeConfigProperties.getUpsert());
        pineconeConfig.setQuery(pineconeConfigProperties.getQuery());
        pineconeConfig.setDelete(pineconeConfigProperties.getDelete());

        configuration.setPineconeConfig(pineconeConfig);
    }

    private void initQdrantConfig() {
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setEnabled(qdrantConfigProperties.isEnabled());
        qdrantConfig.setHost(qdrantConfigProperties.getHost());
        qdrantConfig.setApiKey(qdrantConfigProperties.getApiKey());
        qdrantConfig.setVectorName(qdrantConfigProperties.getVectorName());
        qdrantConfig.setUpsert(qdrantConfigProperties.getUpsert());
        qdrantConfig.setQuery(qdrantConfigProperties.getQuery());
        qdrantConfig.setDelete(qdrantConfigProperties.getDelete());

        configuration.setQdrantConfig(qdrantConfig);
    }

    private void initMilvusConfig() {
        MilvusConfig milvusConfig = new MilvusConfig();
        milvusConfig.setEnabled(milvusConfigProperties.isEnabled());
        milvusConfig.setHost(milvusConfigProperties.getHost());
        milvusConfig.setToken(milvusConfigProperties.getToken());
        milvusConfig.setDbName(milvusConfigProperties.getDbName());
        milvusConfig.setPartitionName(milvusConfigProperties.getPartitionName());
        milvusConfig.setIdField(milvusConfigProperties.getIdField());
        milvusConfig.setVectorField(milvusConfigProperties.getVectorField());
        milvusConfig.setContentField(milvusConfigProperties.getContentField());
        milvusConfig.setOutputFields(milvusConfigProperties.getOutputFields());
        milvusConfig.setUpsert(milvusConfigProperties.getUpsert());
        milvusConfig.setSearch(milvusConfigProperties.getSearch());
        milvusConfig.setDelete(milvusConfigProperties.getDelete());

        configuration.setMilvusConfig(milvusConfig);
    }

    private void initPgVectorConfig() {
        PgVectorConfig pgVectorConfig = new PgVectorConfig();
        pgVectorConfig.setEnabled(pgVectorConfigProperties.isEnabled());
        pgVectorConfig.setJdbcUrl(pgVectorConfigProperties.getJdbcUrl());
        pgVectorConfig.setUsername(pgVectorConfigProperties.getUsername());
        pgVectorConfig.setPassword(pgVectorConfigProperties.getPassword());
        pgVectorConfig.setTableName(pgVectorConfigProperties.getTableName());
        pgVectorConfig.setIdColumn(pgVectorConfigProperties.getIdColumn());
        pgVectorConfig.setDatasetColumn(pgVectorConfigProperties.getDatasetColumn());
        pgVectorConfig.setVectorColumn(pgVectorConfigProperties.getVectorColumn());
        pgVectorConfig.setContentColumn(pgVectorConfigProperties.getContentColumn());
        pgVectorConfig.setMetadataColumn(pgVectorConfigProperties.getMetadataColumn());
        pgVectorConfig.setDistanceOperator(pgVectorConfigProperties.getDistanceOperator());

        configuration.setPgVectorConfig(pgVectorConfig);
    }

    /**
     * 鍒濆鍖朌eepSeek 閰嶇疆淇℃伅
     */
    private void initDeepSeekConfig(){
        DeepSeekConfig deepSeekConfig = new DeepSeekConfig();
        deepSeekConfig.setApiHost(deepSeekConfigProperties.getApiHost());
        deepSeekConfig.setApiKey(deepSeekConfigProperties.getApiKey());
        deepSeekConfig.setChatCompletionUrl(deepSeekConfigProperties.getChatCompletionUrl());

        configuration.setDeepSeekConfig(deepSeekConfig);
    }

    /**
     * 鍒濆鍖朚oonshot 閰嶇疆淇℃伅
     */
    private void initMoonshotConfig() {
        MoonshotConfig moonshotConfig = new MoonshotConfig();
        moonshotConfig.setApiHost(moonshotConfigProperties.getApiHost());
        moonshotConfig.setApiKey(moonshotConfigProperties.getApiKey());
        moonshotConfig.setChatCompletionUrl(moonshotConfigProperties.getChatCompletionUrl());

        configuration.setMoonshotConfig(moonshotConfig);
    }

    /**
     * 鍒濆鍖朒unyuan 閰嶇疆淇℃伅
     */
    private void initHunyuanConfig() {
        HunyuanConfig hunyuanConfig = new HunyuanConfig();
        hunyuanConfig.setApiHost(hunyuanConfigProperties.getApiHost());
        hunyuanConfig.setApiKey(hunyuanConfigProperties.getApiKey());

        configuration.setHunyuanConfig(hunyuanConfig);
    }

    /**
     * 鍒濆鍖杔ingyi 閰嶇疆淇℃伅
     */
    private void initLingyiConfig() {
        LingyiConfig lingyiConfig = new LingyiConfig();
        lingyiConfig.setApiHost(lingyiConfigProperties.getApiHost());
        lingyiConfig.setApiKey(lingyiConfigProperties.getApiKey());
        lingyiConfig.setChatCompletionUrl(lingyiConfigProperties.getChatCompletionUrl());

        configuration.setLingyiConfig(lingyiConfig);
    }

    /**
     * 鍒濆鍖朞llama 閰嶇疆淇℃伅
     */
    private void initOllamaConfig() {
        OllamaConfig ollamaConfig = new OllamaConfig();
        ollamaConfig.setApiHost(ollamaConfigProperties.getApiHost());
        ollamaConfig.setApiKey(ollamaConfigProperties.getApiKey());
        ollamaConfig.setChatCompletionUrl(ollamaConfigProperties.getChatCompletionUrl());
        ollamaConfig.setEmbeddingUrl(ollamaConfigProperties.getEmbeddingUrl());
        ollamaConfig.setRerankUrl(ollamaConfigProperties.getRerankUrl());

        configuration.setOllamaConfig(ollamaConfig);
    }

    /**
     * 鍒濆鍖朚inimax 閰嶇疆淇℃伅
     */
    private void initMinimaxConfig() {
        MinimaxConfig minimaxConfig = new MinimaxConfig();
        minimaxConfig.setApiHost(minimaxConfigProperties.getApiHost());
        minimaxConfig.setApiKey(minimaxConfigProperties.getApiKey());
        minimaxConfig.setChatCompletionUrl(minimaxConfigProperties.getChatCompletionUrl());

        configuration.setMinimaxConfig(minimaxConfig);
    }

    /**
     * 鍒濆鍖朆aichuan 閰嶇疆淇℃伅
     */
    private void initBaichuanConfig() {
        BaichuanConfig baichuanConfig = new BaichuanConfig();
        baichuanConfig.setApiHost(baichuanConfigProperties.getApiHost());
        baichuanConfig.setApiKey(baichuanConfigProperties.getApiKey());
        baichuanConfig.setChatCompletionUrl(baichuanConfigProperties.getChatCompletionUrl());

        configuration.setBaichuanConfig(baichuanConfig);
    }

    /**
     * 鍒濆鍖杝earxng 閰嶇疆淇℃伅
     */
    private void initSearXNGConfig() {
        SearXNGConfig searXNGConfig = new SearXNGConfig();
        searXNGConfig.setUrl(searXNGConfigProperties.getUrl());
        searXNGConfig.setEngines(searXNGConfigProperties.getEngines());
        searXNGConfig.setNums(searXNGConfigProperties.getNums());

        configuration.setSearXNGConfig(searXNGConfig);
    }

    /**
     * 鍒濆鍖朌ashscope 閰嶇疆淇℃伅
     */
    private void initDashScopeConfig() {
        DashScopeConfig dashScopeConfig = new DashScopeConfig();
        dashScopeConfig.setApiKey(dashScopeConfigProperties.getApiKey());
        dashScopeConfig.setApiHost(dashScopeConfigProperties.getApiHost());
        dashScopeConfig.setResponsesUrl(dashScopeConfigProperties.getResponsesUrl());

        configuration.setDashScopeConfig(dashScopeConfig);
    }

    /**
     * 鍒濆鍖朌oubao(鐏北寮曟搸鏂硅垷) 閰嶇疆淇℃伅
     */
    private void initDoubaoConfig() {
        DoubaoConfig doubaoConfig = new DoubaoConfig();
        doubaoConfig.setApiHost(doubaoConfigProperties.getApiHost());
        doubaoConfig.setApiKey(doubaoConfigProperties.getApiKey());
        doubaoConfig.setChatCompletionUrl(doubaoConfigProperties.getChatCompletionUrl());
        doubaoConfig.setImageGenerationUrl(doubaoConfigProperties.getImageGenerationUrl());
        doubaoConfig.setResponsesUrl(doubaoConfigProperties.getResponsesUrl());
        doubaoConfig.setRerankApiHost(doubaoConfigProperties.getRerankApiHost());
        doubaoConfig.setRerankUrl(doubaoConfigProperties.getRerankUrl());

        configuration.setDoubaoConfig(doubaoConfig);
    }

    private void initJinaConfig() {
        JinaConfig jinaConfig = new JinaConfig();
        jinaConfig.setApiHost(jinaConfigProperties.getApiHost());
        jinaConfig.setApiKey(jinaConfigProperties.getApiKey());
        jinaConfig.setRerankUrl(jinaConfigProperties.getRerankUrl());

        configuration.setJinaConfig(jinaConfig);
    }

    private AgentFlowConfig toAgentFlowConfig(AgentFlowProperties.EndpointProperties properties) {
        return AgentFlowConfig.builder()
                .type(properties.getType())
                .baseUrl(properties.getBaseUrl())
                .webhookUrl(properties.getWebhookUrl())
                .apiKey(properties.getApiKey())
                .botId(properties.getBotId())
                .workflowId(properties.getWorkflowId())
                .appId(properties.getAppId())
                .userId(properties.getUserId())
                .conversationId(properties.getConversationId())
                .pollIntervalMillis(properties.getPollIntervalMillis())
                .pollTimeoutMillis(properties.getPollTimeoutMillis())
                .headers(properties.getHeaders())
                .build();
    }
}



