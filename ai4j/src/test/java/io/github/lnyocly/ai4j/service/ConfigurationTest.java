package io.github.lnyocly.ai4j.service;

import okhttp3.OkHttpClient;
import okhttp3.sse.EventSource;
import org.junit.Assert;
import org.junit.Test;

public class ConfigurationTest {

    @Test
    public void shouldProvideDefaultOkHttpClientForPlainJavaUsage() {
        Configuration configuration = new Configuration();

        Assert.assertNotNull(configuration.getOkHttpClient());
    }

    @Test
    public void shouldCreateEventSourceFactoryWithDefaultClient() {
        Configuration configuration = new Configuration();

        EventSource.Factory factory = configuration.createRequestFactory();

        Assert.assertNotNull(factory);
    }

    @Test
    public void shouldAllowCustomOkHttpClientOverride() {
        Configuration configuration = new Configuration();
        OkHttpClient customClient = new OkHttpClient();

        configuration.setOkHttpClient(customClient);

        Assert.assertSame(customClient, configuration.getOkHttpClient());
    }
}
