package io.github.lnyocly.ai4j.token;

import org.junit.Assert;
import org.junit.Test;

public class TikTokensUtilTest {

    @Test
    public void shouldResolveModelSnapshotNamesThroughJtokkitRegistry() {
        Assert.assertTrue(TikTokensUtil.tokens("gpt-4-0314", "hello world") > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnknownModelName() {
        TikTokensUtil.tokens("unknown-model-for-token-test", "hello world");
    }
}
