package io.github.lnyocly.ai4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.web.codec.AguiSseCodec;
import io.github.lnyocly.ai4j.web.codec.OpenAiSseCodec;
import io.github.lnyocly.ai4j.web.spring.AiWebStreamOptions;
import io.github.lnyocly.ai4j.web.spring.AiWebStreams;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class AiWebAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiWebAutoConfiguration.class));

    @Test
    public void autoConfiguresStreamOptionsAndSseCodecsWithProperties() {
        contextRunner
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "ai.web.stream.timeout-ms=45000",
                        "ai.web.stream.heartbeat-interval-ms=5000",
                        "ai.web.stream.heartbeat-comment=keepalive",
                        "ai4j.unused=ignored",
                        "ai.web.stream.include-error-event=true",
                        "ai.web.agui.include-event-name=false",
                        "ai.web.openai.id=chatcmpl-custom",
                        "ai.web.openai.model=gpt-5-mini",
                        "ai.web.openai.system-fingerprint=fp_ai4j"
                )
                .run(context -> {
                    Assert.assertTrue(context.containsBean("aiWebStreamOptions"));
                    Assert.assertTrue(context.containsBean("aguiSseCodec"));
                    Assert.assertTrue(context.containsBean("openAiSseCodec"));

                    AiWebStreamOptions options = context.getBean(AiWebStreamOptions.class);
                    Assert.assertEquals(Long.valueOf(45000L), options.getTimeoutMs());
                    Assert.assertEquals(5000L, options.getHeartbeatIntervalMs());
                    Assert.assertEquals("keepalive", options.getHeartbeatComment());
                    Assert.assertTrue(options.isIncludeErrorEvent());

                    AguiSseCodec aguiCodec = context.getBean(AguiSseCodec.class);
                    Assert.assertNull(aguiCodec.runStarted("t1", "r1").getEvent());

                    OpenAiSseCodec openAiCodec = context.getBean(OpenAiSseCodec.class);
                    Assert.assertEquals("chatcmpl-custom", openAiCodec.getId());
                    Assert.assertEquals("gpt-5-mini", openAiCodec.getModel());
                    Assert.assertEquals("fp_ai4j", openAiCodec.getSystemFingerprint());
                });
    }

    @Test
    public void backsOffWhenAiWebStreamsMissingOrDisabledOrCustomBeansProvided() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(AiWebStreams.class))
                .run(context -> {
                    Assert.assertEquals(0, context.getBeansOfType(AiWebStreamOptions.class).size());
                    Assert.assertEquals(0, context.getBeansOfType(AguiSseCodec.class).size());
                    Assert.assertEquals(0, context.getBeansOfType(OpenAiSseCodec.class).size());
                });

        contextRunner
                .withPropertyValues("ai.web.enabled=false")
                .run(context -> Assert.assertEquals(0, context.getBeansOfType(AiWebStreamOptions.class).size()));

        AiWebStreamOptions customOptions = AiWebStreamOptions.builder().timeoutMs(1234L).build();
        contextRunner
                .withBean(AiWebStreamOptions.class, () -> customOptions)
                .run(context -> Assert.assertSame(customOptions, context.getBean(AiWebStreamOptions.class)));
    }
}
