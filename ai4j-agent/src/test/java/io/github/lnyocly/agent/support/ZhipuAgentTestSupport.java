package io.github.lnyocly.agent.support;

import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.network.OkHttpUtil;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Assume;
import org.junit.Before;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

public abstract class ZhipuAgentTestSupport {

    protected static final String DEFAULT_API_KEY = "1cbd1960cdc7e9144ded698a9763569b.seHlVxdOq3eTnY9m";
    protected static final String DEFAULT_MODEL = "GLM-4.5-Flash";

    protected AiService aiService;
    protected String model;

    @Before
    public void setupZhipuAiService() throws NoSuchAlgorithmException, KeyManagementException {
        String apiKey = System.getenv("ZHIPU_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getProperty("zhipu.api.key");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = DEFAULT_API_KEY;
        }

        model = System.getenv("ZHIPU_MODEL");
        if (model == null || model.isEmpty()) {
            model = System.getProperty("zhipu.model");
        }
        if (model == null || model.isEmpty()) {
            model = DEFAULT_MODEL;
        }

        ZhipuConfig zhipuConfig = new ZhipuConfig();
        zhipuConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setZhipuConfig(zhipuConfig);

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .sslSocketFactory(OkHttpUtil.getIgnoreInitedSslContext().getSocketFactory(), OkHttpUtil.IGNORE_SSL_TRUST_MANAGER_X509)
                .hostnameVerifier(OkHttpUtil.getIgnoreSslHostnameVerifier())
                .build();

        configuration.setOkHttpClient(okHttpClient);
        aiService = new AiService(configuration);
    }

    protected ChatModelClient chatModelClient() {
        return new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU));
    }

    protected <T> T callWithProviderGuard(ThrowingSupplier<T> supplier) throws Exception {
        try {
            return supplier.get();
        } catch (Exception ex) {
            skipIfProviderUnavailable(ex);
            throw ex;
        }
    }

    protected void skipIfProviderUnavailable(Throwable throwable) {
        if (isProviderUnavailable(throwable)) {
            String reason = extractRootMessage(throwable);
            Assume.assumeTrue("Skip due provider limit/unavailable: " + reason, false);
        }
    }

    private boolean isProviderUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout")
                        || lower.contains("rate limit")
                        || lower.contains("too many requests")
                        || lower.contains("quota")
                        || lower.contains("inference limit")
                        || message.contains("频次")
                        || message.contains("限流")
                        || message.contains("额度")
                        || message.contains("配额")
                        || message.contains("模型服务已暂停")
                        || message.contains("账户已达到")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable last = throwable;
        while (current != null) {
            last = current;
            current = current.getCause();
        }
        String message = last == null ? null : last.getMessage();
        return message == null || message.trim().isEmpty() ? "unknown error" : message;
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}


