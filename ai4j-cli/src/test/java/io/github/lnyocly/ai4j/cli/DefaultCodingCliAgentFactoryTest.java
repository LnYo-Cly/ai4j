package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Assert;
import org.junit.Test;

public class DefaultCodingCliAgentFactoryTest {

    private final DefaultCodingCliAgentFactory factory = new DefaultCodingCliAgentFactory();

    @Test
    public void test_default_protocol_prefers_chat_for_openai_compatible_base_url() {
        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                null,
                "deepseek-chat",
                null,
                "https://api.deepseek.com",
                ".",
                null,
                null,
                null,
                null,
                12,
                32,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        Assert.assertEquals(CliProtocol.CHAT, factory.resolveProtocol(options));
    }

    @Test
    public void test_default_protocol_prefers_responses_for_official_openai() {
        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                null,
                "gpt-5-mini",
                null,
                "https://api.openai.com",
                ".",
                null,
                null,
                null,
                null,
                12,
                32,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        Assert.assertEquals(CliProtocol.RESPONSES, factory.resolveProtocol(options));
    }

    @Test
    public void test_normalize_zhipu_coding_plan_base_url() {
        Assert.assertEquals(
                "https://open.bigmodel.cn/api/coding/paas/",
                factory.normalizeZhipuBaseUrl("https://open.bigmodel.cn/api/coding/paas/v4")
        );
        Assert.assertEquals(
                "https://open.bigmodel.cn/api/coding/paas/",
                factory.normalizeZhipuBaseUrl("https://open.bigmodel.cn/api/coding/paas/v4/chat/completions")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_explicit_responses_rejected_for_zhipu() {
        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.ZHIPU,
                CliProtocol.RESPONSES,
                "GLM-4.5-Flash",
                null,
                null,
                ".",
                null,
                null,
                null,
                null,
                12,
                32,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        factory.resolveProtocol(options);
    }
}
