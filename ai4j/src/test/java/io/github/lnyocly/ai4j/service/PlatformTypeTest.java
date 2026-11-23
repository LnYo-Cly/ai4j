package io.github.lnyocly.ai4j.service;

import org.junit.Assert;
import org.junit.Test;

public class PlatformTypeTest {

    @Test
    public void includesGeminiAndResolvesCaseInsensitivelyWithTrim() {
        Assert.assertEquals("gemini", PlatformType.GEMINI.getPlatform());
        Assert.assertEquals(PlatformType.GEMINI, PlatformType.fromPlatform("gemini"));
        Assert.assertEquals(PlatformType.GEMINI, PlatformType.fromPlatform("  GEMINI "));
        Assert.assertEquals(PlatformType.OPENAI, PlatformType.fromPlatform(" OpenAI "));
        Assert.assertEquals(PlatformType.TYPESAFE, PlatformType.fromPlatform("typesafe"));

        Assert.assertEquals(PlatformType.GEMINI, PlatformType.getPlatform("Gemini"));
        Assert.assertEquals(PlatformType.DEEPSEEK, PlatformType.getPlatform("  DEEPSEEK "));
    }

    @Test
    public void fromPlatformRejectsNullBlankAndUnknownPlatforms() {
        assertIllegalPlatform(null, true);
        assertIllegalPlatform("", true);
        assertIllegalPlatform("   ", true);
        assertIllegalPlatform("unknown-provider", true);
    }

    @Test
    public void getPlatformRejectsNullBlankAndUnknownPlatformsInsteadOfFallingBack() {
        assertIllegalPlatform(null, false);
        assertIllegalPlatform("", false);
        assertIllegalPlatform("   ", false);
        assertIllegalPlatform("unknown-provider", false);
    }

    private static void assertIllegalPlatform(String input, boolean useFromPlatform) {
        try {
            if (useFromPlatform) {
                PlatformType.fromPlatform(input);
            } else {
                PlatformType.getPlatform(input);
            }
            Assert.fail("Expected IllegalArgumentException for platform: " + input);
        } catch (IllegalArgumentException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }
}
