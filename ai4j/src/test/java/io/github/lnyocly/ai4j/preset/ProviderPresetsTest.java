package io.github.lnyocly.ai4j.preset;

import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.service.Platform;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for open {@link Platform}, {@link ProviderPreset}, {@link ProtocolQuirks},
 * and {@link ProviderPresets}.
 */
public class ProviderPresetsTest {

    @Test
    public void openPlatformResolvesBuiltInAndCustomGateways() {
        Assert.assertEquals(PlatformType.OPENAI, Platform.of("  OpenAI "));
        Assert.assertEquals(PlatformType.GEMINI, Platform.of("gemini"));
        Assert.assertEquals("gemini", PlatformType.GEMINI.id());

        Platform custom1 = Platform.of(" Corp-Gateway ");
        Platform custom2 = Platform.of("corp-gateway");
        Assert.assertEquals("corp-gateway", custom1.id());
        Assert.assertEquals(custom1, custom2);
        Assert.assertEquals(custom1.hashCode(), custom2.hashCode());
    }

    @Test
    public void allBuiltInPresetsAndEndpointBuildingWork() {
        Assert.assertEquals(9, ProviderPresets.all().size());
        Assert.assertSame(ProviderPresets.DEEPSEEK, ProviderPresets.require("DeepSeek"));
        Assert.assertSame(ProviderPresets.GEMINI, ProviderPresets.find(PlatformType.GEMINI));

        Endpoint gemini = ProviderPresets.GEMINI.toEndpoint("goog-key");
        Assert.assertEquals(Protocol.GEMINI, gemini.getProtocol());
        Assert.assertEquals(Credentials.Type.HEADER, gemini.getCredentials().getType());
        Assert.assertEquals("x-goog-api-key", gemini.getCredentials().getHeaderName());
        Assert.assertEquals("/v1beta/models/{model}:generateContent", gemini.getEndpointPath());

        Endpoint geminiProxy = ProviderPresets.GEMINI.toEndpoint("goog-key", "https://proxy.example/v1beta/");
        Assert.assertEquals("/models/{model}:generateContent", geminiProxy.getEndpointPath());

        Endpoint dashscope = ProviderPresets.DASHSCOPE.toEndpoint("ds-key");
        Assert.assertEquals(Protocol.CHAT_COMPLETIONS, dashscope.getProtocol());
        Assert.assertEquals(Credentials.Type.BEARER, dashscope.getCredentials().getType());
        Assert.assertEquals("/chat/completions", dashscope.getEndpointPath());

        Endpoint anthropic = ProviderPresets.ANTHROPIC.toEndpoint("ant-key");
        Assert.assertEquals(Protocol.MESSAGES, anthropic.getProtocol());
        Assert.assertEquals("x-api-key", anthropic.getCredentials().getHeaderName());
        Assert.assertTrue(ProviderPresets.ANTHROPIC.quirks().isSupportsPromptCaching());
        Assert.assertTrue(ProviderPresets.GEMINI.quirks().isRequiresThoughtSignatureRoundTrip());
    }

    @Test
    public void parsesProviderColonModelAndInfersKnownModels() {
        ProviderPresets.ModelSpec deepseek = ProviderPresets.parseModelSpec("deepseek:deepseek-reasoner");
        Assert.assertEquals("deepseek", deepseek.getProviderId());
        Assert.assertEquals("deepseek-reasoner", deepseek.getModel());
        Assert.assertSame(ProviderPresets.DEEPSEEK, deepseek.getPreset());

        ProviderPresets.ModelSpec ollama = ProviderPresets.parseModelSpec("ollama:qwen2.5:7b");
        Assert.assertEquals("ollama", ollama.getProviderId());
        Assert.assertEquals("qwen2.5:7b", ollama.getModel());
        Assert.assertEquals(PlatformType.OLLAMA, ollama.getPlatform());

        ProviderPresets.ModelSpec inferred = ProviderPresets.parseModelSpec("gemini-2.5-pro");
        Assert.assertEquals("gemini", inferred.getProviderId());
        Assert.assertSame(ProviderPresets.GEMINI, inferred.getPreset());

        ProviderPresets.ModelSpec customBare = ProviderPresets.parseModelSpec("my-local-model");
        Assert.assertNull(customBare.getProviderId());
        Assert.assertEquals("my-local-model", customBare.getModel());
    }
}
