package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.network.OkHttpUtil;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * MiniMax live smoke tests.
 */
@Slf4j
@Category(LiveProviderTest.class)
public class MinimaxTest {

    private IChatService chatService;

    @Before
    public void test_init() throws NoSuchAlgorithmException, KeyManagementException {
        MinimaxConfig minimaxConfig = new MinimaxConfig();
        minimaxConfig.setApiKey(LiveProviderTestSupport.requireEnv(
                "Skip because the MiniMax API key is not configured",
                "MINIMAX_API_KEY"));
        String baseUrl = System.getenv("MINIMAX_BASE_URL");
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            minimaxConfig.setApiHost(baseUrl);
        }

        Configuration configuration = new Configuration();
        configuration.setMinimaxConfig(minimaxConfig);


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
                .build();
        configuration.setOkHttpClient(okHttpClient);

        AiService aiService = new AiService(configuration);

        chatService = aiService.getChatService(PlatformType.MINIMAX);

    }


    @Test
    public void test_chatCompletions_common() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("MiniMax-M3")
                .message(ChatMessage.withUser("Why did Lu Xun hit Zhou Shuren?"))
                .build();

        System.out.println("Request parameters");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("Request succeeded");
        System.out.println(chatCompletionResponse);

    }

    @Test
    public void test_chatCompletions_multimodal() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("yi-vision")
                .message(ChatMessage.withUser("What animals are in these images, and what breed are they?",
                        "https://tse2-mm.cn.bing.net/th/id/OIP-C.SVxZtXIcz3LbcE4ZeS6jEgHaE7?w=231&h=180&c=7&r=0&o=5&dpr=1.3&pid=1.7",
                        "https://ts3.cn.mm.bing.net/th?id=OIP-C.BYyILFgs3ATnTEQ-B5ApFQHaFj&w=288&h=216&c=8&rs=1&qlt=90&o=6&dpr=1.3&pid=3.1&rm=2"))
                .build();

        System.out.println("Request parameters");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("Request succeeded");
        System.out.println(chatCompletionResponse);
    }


    @Test
    public void test_chatCompletions_stream() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("MiniMax-M3")
                .message(ChatMessage.withUser("Why did Lu Xun hit Zhou Shuren?"))
                .build();


        System.out.println("Request parameters");
        System.out.println(chatCompletion);

        // Build a streaming listener.
        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                System.out.println(this.getCurrStr());
            }
        };

        chatService.chatCompletionStream(chatCompletion, sseListener);

        System.out.println("Request succeeded");
        System.out.println(sseListener.getOutput());
        System.out.println(sseListener.getUsage());

    }

    @Test
    public void test_chatCompletions_function() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("Check tomorrow's weather in Luoyang and tell me whether the train is departing."))
                .functions("queryWeather", "queryTrainInfo")
                .build();

        System.out.println("Request parameters");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("Request succeeded");
        System.out.println(chatCompletionResponse);

        System.out.println(chatCompletion);

    }

    @Test
    public void test_chatCompletions_stream_function() throws Exception {

        // Build the request parameters.
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("yi-large-fc")
                .message(ChatMessage.withUser("Check tomorrow's weather in Luoyang."))
                .functions("queryWeather", "queryTrainInfo")
                .build();


        // Build a streaming listener.
        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                System.out.println(this.getCurrStr());
            }
        };
        // Show tool arguments for the streamed function call.
        sseListener.setShowToolArgs(true);

        // Send the SSE request.
        chatService.chatCompletionStream(chatCompletion, sseListener);
        System.out.println("Full content: ");
        System.out.println(sseListener.getOutput());
        System.out.println("Usage: ");
        System.out.println(sseListener.getUsage());
    }
}
