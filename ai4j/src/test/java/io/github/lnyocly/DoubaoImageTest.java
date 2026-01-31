package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGeneration;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGenerationResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IImageService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.utils.OkHttpUtil;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * @Author cly
 * @Description 豆包图片生成测试
 * @Date 2026/1/31
 */
public class DoubaoImageTest {

    private IImageService imageService;

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
        imageService = aiService.getImageService(PlatformType.DOUBAO);
    }

    @Test
    public void test_image_generate() throws Exception {
        ImageGeneration request = ImageGeneration.builder()
                .model("doubao-seedream-4-5-251128")
                .prompt("一只戴着飞行员护目镜的小猫，卡通风格，明亮配色")
                .size("2K")
                .responseFormat("url")
                .build();

        ImageGenerationResponse response = imageService.generate(request);
        System.out.println(response);
    }

    @Test
    public void test_image_generate_stream() throws Exception {
        ImageGeneration request = ImageGeneration.builder()
                .model("doubao-seedream-4-5-251128")
                .prompt("一只戴着飞行员护目镜的小猫，卡通风格，明亮配色")
                .size("2K")
                .responseFormat("url")
                .stream(true)
                .build();

        imageService.generateStream(request, new io.github.lnyocly.ai4j.listener.ImageSseListener() {
            @Override
            protected void onEvent() {
                System.out.println(getCurrEvent());
            }
        });

        System.out.println("stream finished");
    }
}
