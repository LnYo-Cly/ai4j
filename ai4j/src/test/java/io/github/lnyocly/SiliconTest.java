package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.BaichuanConfig;
import io.github.lnyocly.ai4j.config.SiliconFlowConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.utils.OkHttpUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SiliconTest {

    private IChatService chatService;

    @Before
    public void test_init() throws NoSuchAlgorithmException, KeyManagementException {
        SiliconFlowConfig siliconFlowConfig = new SiliconFlowConfig();
        siliconFlowConfig.setApiKey("sk-pbwlqdaebkpoxrzhgqmvtmdlgahsqnfcikzabbmoceqbfmfl");

        Configuration configuration = new Configuration();
        configuration.setSiliconFlowConfig(siliconFlowConfig);


        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient okHttpClient = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .sslSocketFactory(OkHttpUtil.getIgnoreInitedSslContext().getSocketFactory(), OkHttpUtil.IGNORE_SSL_TRUST_MANAGER_X509)
                .hostnameVerifier(OkHttpUtil.getIgnoreSslHostnameVerifier())
                //.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809)))
                .build();
        configuration.setOkHttpClient(okHttpClient);

        AiService aiService = new AiService(configuration);

        chatService = aiService.getChatService(PlatformType.SILICONFLOW);
    }


    @Test
    public void test_chatCompletions_common() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("deepseek-ai/DeepSeek-V3")
                .message(ChatMessage.withUser("你叫什么名字？"))
                .build();

        System.out.println("请求参数");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("请求成功");
        System.out.println(chatCompletionResponse);

    }



}
