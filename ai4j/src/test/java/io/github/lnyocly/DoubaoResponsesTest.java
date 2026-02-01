package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.utils.OkHttpUtil;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;


public class DoubaoResponsesTest {

    private IResponsesService responsesService;

    @Before
    public void test_init() throws NoSuchAlgorithmException, KeyManagementException {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DOUBAO_API_KEY");
        }
        Assume.assumeTrue(apiKey != null && !apiKey.isEmpty());

        DoubaoConfig doubaoConfig = new DoubaoConfig();
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

        AiService aiService = new AiService(configuration);
        responsesService = aiService.getResponsesService(PlatformType.DOUBAO);
    }

    @Test
    public void test_responses_create() throws Exception {
        ResponseRequest request = ResponseRequest.builder()
                .model("doubao-seed-1-8-251228")
                .input("Summarize the Responses API in one sentence")
                .build();

        Response response = responsesService.create(request);
        System.out.println(response);
    }

    @Test
    public void test_responses_stream() throws Exception {
        ResponseRequest request = ResponseRequest.builder()
                .model("doubao-seed-1-8-251228")
                .input("Describe the Responses API in one sentence")
                .build();

        ResponseSseListener listener = new ResponseSseListener() {
            @Override
            protected void onEvent() {
                if (!getCurrText().isEmpty()) {
                    System.out.print(getCurrText());
                }
            }
        };

        responsesService.createStream(request, listener);
        System.out.println();
        System.out.println("stream finished");
        System.out.println(listener.getResponse());
    }
}
