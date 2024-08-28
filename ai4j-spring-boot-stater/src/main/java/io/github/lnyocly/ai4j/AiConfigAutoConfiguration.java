package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.config.PineconeConfig;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.vector.service.PineconeService;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

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
        ZhipuConfigProperties.class})
public class AiConfigAutoConfiguration {

    private final OkHttpConfigProperties okHttpConfigProperties;
    private final OpenAiConfigProperties openAiConfigProperties;
    private final PineconeConfigProperties pineconeConfigProperties;
    private final ZhipuConfigProperties zhipuConfigProperties;

    private io.github.lnyocly.ai4j.service.Configuration configuration = new io.github.lnyocly.ai4j.service.Configuration();

    public AiConfigAutoConfiguration(OkHttpConfigProperties okHttpConfigProperties, OpenAiConfigProperties openAiConfigProperties, PineconeConfigProperties pineconeConfigProperties, ZhipuConfigProperties zhipuConfigProperties) {
        this.okHttpConfigProperties = okHttpConfigProperties;
        this.openAiConfigProperties = openAiConfigProperties;
        this.pineconeConfigProperties = pineconeConfigProperties;
        this.zhipuConfigProperties = zhipuConfigProperties;
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
        initOpenAiConfig();
        initPineconeConfig();
        initZhipuConfig();
    }

    private void initOkHttp() {
        //configuration.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1",10809)));

        Proxy proxy = new Proxy(okHttpConfigProperties.getProxyType(), new InetSocketAddress(okHttpConfigProperties.getProxyUrl(), okHttpConfigProperties.getProxyPort()));

        // 日志配置
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(okHttpConfigProperties.getLog());

        // 开启 Http 客户端
        OkHttpClient okHttpClient = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .connectTimeout(okHttpConfigProperties.getConnectTimeout(), okHttpConfigProperties.getTimeUnit())
                .writeTimeout(okHttpConfigProperties.getWriteTimeout(), okHttpConfigProperties.getTimeUnit())
                .readTimeout(okHttpConfigProperties.getReadTimeout(), okHttpConfigProperties.getTimeUnit())
                .proxy(proxy)
                .build();

        configuration.setOkHttpClient(okHttpClient);
    }

    private void initOpenAiConfig() {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost(openAiConfigProperties.getApiHost());
        openAiConfig.setApiKey(openAiConfigProperties.getApiKey());
        openAiConfig.setV1_chat_completions(openAiConfigProperties.getV1_chat_completions());
        openAiConfig.setV1_embeddings(openAiConfigProperties.getV1_embeddings());

        configuration.setOpenAiConfig(openAiConfig);
    }

    private void initZhipuConfig() {
        ZhipuConfig zhipuConfig = new ZhipuConfig();
        zhipuConfig.setApiHost(zhipuConfigProperties.getApiHost());
        zhipuConfig.setApiKey(zhipuConfigProperties.getApiKey());
        zhipuConfig.setChat_completion(zhipuConfigProperties.getChat_completion());
        zhipuConfig.setEmbedding(zhipuConfigProperties.getEmbedding());

        configuration.setZhipuConfig(zhipuConfig);
    }

    private void initPineconeConfig() {
        PineconeConfig pineconeConfig = new PineconeConfig();
        pineconeConfig.setUrl(pineconeConfigProperties.getUrl());
        pineconeConfig.setKey(pineconeConfigProperties.getKey());
        pineconeConfig.setUpsert(pineconeConfigProperties.getUpsert());
        pineconeConfig.setQuery(pineconeConfigProperties.getQuery());
        pineconeConfig.setDelete(pineconeConfigProperties.getDelete());

        configuration.setPineconeConfig(pineconeConfig);
    }



}
