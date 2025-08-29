package io.github.lnyocly.ai4j;

import cn.hutool.core.bean.BeanUtil;
import io.github.lnyocly.ai4j.config.*;
import io.github.lnyocly.ai4j.interceptor.ContentTypeInterceptor;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.network.ConnectionPoolProvider;
import io.github.lnyocly.ai4j.network.DispatcherProvider;
import io.github.lnyocly.ai4j.service.AiConfig;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.service.factor.FreeAiService;
import io.github.lnyocly.ai4j.utils.OkHttpUtil;
import io.github.lnyocly.ai4j.utils.ServiceLoaderUtil;
import io.github.lnyocly.ai4j.vector.service.PineconeService;
import io.github.lnyocly.ai4j.websearch.searxng.SearXNGConfig;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

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
})

public class AiConfigAutoConfiguration {

    // okhttp配置
    private final OkHttpConfigProperties okHttpConfigProperties;

    // 向量数据库配置
    private final PineconeConfigProperties pineconeConfigProperties;

    // searxng配置
    private final SearXNGConfigProperties searXNGConfigProperties;

    // AI平台配置
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

    private io.github.lnyocly.ai4j.service.Configuration configuration = new io.github.lnyocly.ai4j.service.Configuration();

    public AiConfigAutoConfiguration(OkHttpConfigProperties okHttpConfigProperties, OpenAiConfigProperties openAiConfigProperties, PineconeConfigProperties pineconeConfigProperties, SearXNGConfigProperties searXNGConfigProperties, AiConfigProperties aiConfigProperties, ZhipuConfigProperties zhipuConfigProperties, DeepSeekConfigProperties deepSeekConfigProperties, MoonshotConfigProperties moonshotConfigProperties, HunyuanConfigProperties hunyuanConfigProperties, LingyiConfigProperties lingyiConfigProperties, OllamaConfigProperties ollamaConfigProperties, MinimaxConfigProperties minimaxConfigProperties, BaichuanConfigProperties baichuanConfigProperties, DashScopeConfigProperties dashScopeConfigProperties) {
        this.okHttpConfigProperties = okHttpConfigProperties;
        this.openAiConfigProperties = openAiConfigProperties;
        this.pineconeConfigProperties = pineconeConfigProperties;
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
    }

    @Bean
    public AiService aiService() {
        return new AiService(configuration);
    }

    @Bean
    public FreeAiService getFreeAiService() {
        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(BeanUtil.copyToList(aiConfigProperties.getPlatforms(), AiPlatform.class));
        return new FreeAiService(configuration, aiConfig);
    }

    @Bean
    public PineconeService pineconeService() {
        return new PineconeService(configuration);
    }

    @PostConstruct
    private void init() {
        initOkHttp();

        initPineconeConfig();

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
    }



    private void initOkHttp() {
        //configuration.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1",10809)));

        // 日志配置
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(okHttpConfigProperties.getLog());

        // SPI加载dispatcher和connectionPool
        DispatcherProvider dispatcherProvider = ServiceLoaderUtil.load(DispatcherProvider.class);
        ConnectionPoolProvider connectionPoolProvider = ServiceLoaderUtil.load(ConnectionPoolProvider.class);

        // 开启 Http 客户端
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

        // 是否开启Proxy代理
        if(StringUtils.isNotBlank(okHttpConfigProperties.getProxyUrl())){
            Proxy proxy = new Proxy(okHttpConfigProperties.getProxyType(), new InetSocketAddress(okHttpConfigProperties.getProxyUrl(), okHttpConfigProperties.getProxyPort()));
            okHttpBuilder.proxy(proxy);
        }

        // 忽略SSL证书验证, 默认开启
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
     * 初始化Openai 配置信息
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

        configuration.setOpenAiConfig(openAiConfig);
    }

    /**
     * 初始化Zhipu 配置信息
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
     * 初始化向量数据库 pinecone
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

    /**
     * 初始化DeepSeek 配置信息
     */
    private void initDeepSeekConfig(){
        DeepSeekConfig deepSeekConfig = new DeepSeekConfig();
        deepSeekConfig.setApiHost(deepSeekConfigProperties.getApiHost());
        deepSeekConfig.setApiKey(deepSeekConfigProperties.getApiKey());
        deepSeekConfig.setChatCompletionUrl(deepSeekConfigProperties.getChatCompletionUrl());

        configuration.setDeepSeekConfig(deepSeekConfig);
    }

    /**
     * 初始化Moonshot 配置信息
     */
    private void initMoonshotConfig() {
        MoonshotConfig moonshotConfig = new MoonshotConfig();
        moonshotConfig.setApiHost(moonshotConfigProperties.getApiHost());
        moonshotConfig.setApiKey(moonshotConfigProperties.getApiKey());
        moonshotConfig.setChatCompletionUrl(moonshotConfigProperties.getChatCompletionUrl());

        configuration.setMoonshotConfig(moonshotConfig);
    }

    /**
     * 初始化Hunyuan 配置信息
     */
    private void initHunyuanConfig() {
        HunyuanConfig hunyuanConfig = new HunyuanConfig();
        hunyuanConfig.setApiHost(hunyuanConfigProperties.getApiHost());
        hunyuanConfig.setApiKey(hunyuanConfigProperties.getApiKey());

        configuration.setHunyuanConfig(hunyuanConfig);
    }

    /**
     * 初始化lingyi 配置信息
     */
    private void initLingyiConfig() {
        LingyiConfig lingyiConfig = new LingyiConfig();
        lingyiConfig.setApiHost(lingyiConfigProperties.getApiHost());
        lingyiConfig.setApiKey(lingyiConfigProperties.getApiKey());
        lingyiConfig.setChatCompletionUrl(lingyiConfigProperties.getChatCompletionUrl());

        configuration.setLingyiConfig(lingyiConfig);
    }

    /**
     * 初始化Ollama 配置信息
     */
    private void initOllamaConfig() {
        OllamaConfig ollamaConfig = new OllamaConfig();
        ollamaConfig.setApiHost(ollamaConfigProperties.getApiHost());
        ollamaConfig.setApiKey(ollamaConfigProperties.getApiKey());
        ollamaConfig.setChatCompletionUrl(ollamaConfigProperties.getChatCompletionUrl());
        ollamaConfig.setEmbeddingUrl(ollamaConfigProperties.getEmbeddingUrl());

        configuration.setOllamaConfig(ollamaConfig);
    }

    /**
     * 初始化Minimax 配置信息
     */
    private void initMinimaxConfig() {
        MinimaxConfig minimaxConfig = new MinimaxConfig();
        minimaxConfig.setApiHost(minimaxConfigProperties.getApiHost());
        minimaxConfig.setApiKey(minimaxConfigProperties.getApiKey());
        minimaxConfig.setChatCompletionUrl(minimaxConfigProperties.getChatCompletionUrl());

        configuration.setMinimaxConfig(minimaxConfig);
    }

    /**
     * 初始化Baichuan 配置信息
     */
    private void initBaichuanConfig() {
        BaichuanConfig baichuanConfig = new BaichuanConfig();
        baichuanConfig.setApiHost(baichuanConfigProperties.getApiHost());
        baichuanConfig.setApiKey(baichuanConfigProperties.getApiKey());
        baichuanConfig.setChatCompletionUrl(baichuanConfigProperties.getChatCompletionUrl());

        configuration.setBaichuanConfig(baichuanConfig);
    }

    /**
     * 初始化searxng 配置信息
     */
    private void initSearXNGConfig() {
        SearXNGConfig searXNGConfig = new SearXNGConfig();
        searXNGConfig.setUrl(searXNGConfigProperties.getUrl());
        searXNGConfig.setEngines(searXNGConfigProperties.getEngines());
        searXNGConfig.setNums(searXNGConfigProperties.getNums());

        configuration.setSearXNGConfig(searXNGConfig);
    }

    /**
     * 初始化Dashscope 配置信息
     */
    private void initDashScopeConfig() {
        DashScopeConfig dashScopeConfig = new DashScopeConfig();
        dashScopeConfig.setApiKey(dashScopeConfigProperties.getApiKey());

        configuration.setDashScopeConfig(dashScopeConfig);
    }
}
