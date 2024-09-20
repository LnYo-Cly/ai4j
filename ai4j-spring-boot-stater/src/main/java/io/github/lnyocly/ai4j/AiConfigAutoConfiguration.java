package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.config.*;
import io.github.lnyocly.ai4j.interceptor.ContentTypeInterceptor;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.utils.OkHttpUtil;
import io.github.lnyocly.ai4j.vector.service.PineconeService;
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
        OpenAiConfigProperties.class,
        OkHttpConfigProperties.class,
        PineconeConfigProperties.class,
        ZhipuConfigProperties.class,
        DeepSeekConfigProperties.class,
        MoonshotConfigProperties.class,
        HunyuanConfigProperties.class,
        LingyiConfigProperties.class,
        OllamaConfigProperties.class})
public class AiConfigAutoConfiguration {

    // okhttp配置
    private final OkHttpConfigProperties okHttpConfigProperties;

    // 向量数据库配置
    private final PineconeConfigProperties pineconeConfigProperties;

    // AI平台配置
    private final OpenAiConfigProperties openAiConfigProperties;
    private final ZhipuConfigProperties zhipuConfigProperties;
    private final DeepSeekConfigProperties deepSeekConfigProperties;
    private final MoonshotConfigProperties moonshotConfigProperties;
    private final HunyuanConfigProperties hunyuanConfigProperties;
    private final LingyiConfigProperties lingyiConfigProperties;
    private final OllamaConfigProperties ollamaConfigProperties;

    private io.github.lnyocly.ai4j.service.Configuration configuration = new io.github.lnyocly.ai4j.service.Configuration();

    public AiConfigAutoConfiguration(OkHttpConfigProperties okHttpConfigProperties, OpenAiConfigProperties openAiConfigProperties, PineconeConfigProperties pineconeConfigProperties, ZhipuConfigProperties zhipuConfigProperties, DeepSeekConfigProperties deepSeekConfigProperties, MoonshotConfigProperties moonshotConfigProperties, HunyuanConfigProperties hunyuanConfigProperties, LingyiConfigProperties lingyiConfigProperties, OllamaConfigProperties ollamaConfigProperties) {
        this.okHttpConfigProperties = okHttpConfigProperties;
        this.openAiConfigProperties = openAiConfigProperties;
        this.pineconeConfigProperties = pineconeConfigProperties;
        this.zhipuConfigProperties = zhipuConfigProperties;
        this.deepSeekConfigProperties = deepSeekConfigProperties;
        this.moonshotConfigProperties = moonshotConfigProperties;
        this.hunyuanConfigProperties = hunyuanConfigProperties;
        this.lingyiConfigProperties = lingyiConfigProperties;
        this.ollamaConfigProperties = ollamaConfigProperties;
    }

    @Bean
    public AiService aiService() {
        return new AiService(configuration);
    }

    @Bean
    public PineconeService pineconeService() {
        return new PineconeService(configuration);
    }

    @PostConstruct
    private void init() {
        initOkHttp();

        initPineconeConfig();

        initOpenAiConfig();
        initZhipuConfig();
        initDeepSeekConfig();
        initMoonshotConfig();
        initHunyuanConfig();
        initLingyiConfig();
        initOllamaConfig();
    }

    private void initOkHttp() {
        //configuration.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1",10809)));

        // 日志配置
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(okHttpConfigProperties.getLog());

        // 开启 Http 客户端
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .addInterceptor(new ContentTypeInterceptor())
                .connectTimeout(okHttpConfigProperties.getConnectTimeout(), okHttpConfigProperties.getTimeUnit())
                .writeTimeout(okHttpConfigProperties.getWriteTimeout(), okHttpConfigProperties.getTimeUnit())
                .readTimeout(okHttpConfigProperties.getReadTimeout(), okHttpConfigProperties.getTimeUnit());

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
        pineconeConfig.setUrl(pineconeConfigProperties.getUrl());
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
        ollamaConfig.setChatCompletionUrl(ollamaConfigProperties.getChatCompletionUrl());
        ollamaConfig.setEmbeddingUrl(ollamaConfigProperties.getEmbeddingUrl());

        configuration.setOllamaConfig(ollamaConfig);
    }

}
