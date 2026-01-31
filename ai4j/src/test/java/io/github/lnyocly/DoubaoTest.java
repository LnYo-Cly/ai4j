package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.doubao.chat.DoubaoChatService;
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
public class DoubaoTest {

    private IChatService chatService;

    @Before
    public void test_init() throws NoSuchAlgorithmException, KeyManagementException {
        DoubaoConfig doubaoConfig = new DoubaoConfig();
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DOUBAO_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "************";
        }
        doubaoConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setDoubaoConfig(doubaoConfig);

        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient okHttpClient = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .sslSocketFactory(OkHttpUtil.getIgnoreInitedSslContext().getSocketFactory(), OkHttpUtil.IGNORE_SSL_TRUST_MANAGER_X509)
                .hostnameVerifier(OkHttpUtil.getIgnoreSslHostnameVerifier())
                .build();
        configuration.setOkHttpClient(okHttpClient);

        chatService = new DoubaoChatService(configuration);
    }

    @Test
    public void test_chatCompletions_common() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("doubao-seed-1-6-250615")
                .message(ChatMessage.withUser("你好，请介绍一下你自己"))
                .build();

        System.out.println("请求参数");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("请求成功");
        System.out.println(chatCompletionResponse);
    }

    @Test
    public void test_chatCompletions_stream() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("doubao-seed-1-6-250615")
                .message(ChatMessage.withUser("先有鸡还是先有蛋？"))
                .build();

        System.out.println("请求参数");
        System.out.println(chatCompletion);

        // 构造监听器
        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {

                // 当前流式输出的data的数据，即当前输出的token的内容，可能为content，reasoning_content， function call
                log.info(this.getCurrStr());

            }
        };

        chatService.chatCompletionStream(chatCompletion, sseListener);

        System.out.println("\n请求成功");
        System.out.println("完整回答内容：");
        System.out.println(sseListener.getOutput());
        System.out.println("Token使用：");
        System.out.println(sseListener.getUsage());

    }

    @Test
    public void test_chatCompletions_multimodal_stream() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("doubao-seed-1-6-250615")
                .message(ChatMessage.withUser("这几张图片，分别有什么动物, 并且是什么品种",
                        "https://tse2-mm.cn.bing.net/th/id/OIP-C.SVxZtXIcz3LbcE4ZeS6jEgHaE7?w=231&h=180&c=7&r=0&o=5&dpr=1.3&pid=1.7",
                        "https://ts3.cn.mm.bing.net/th?id=OIP-C.BYyILFgs3ATnTEQ-B5ApFQHaFj&w=288&h=216&c=8&rs=1&qlt=90&o=6&dpr=1.3&pid=3.1&rm=2"))
                .build();


        System.out.println("请求参数");
        System.out.println(chatCompletion);

        // 构造监听器
        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                log.info(this.getCurrStr());
            }
        };

        chatService.chatCompletionStream(chatCompletion, sseListener);

        System.out.println("请求成功");
        System.out.println(sseListener.getOutput());
        System.out.println(sseListener.getUsage());

    }
}
