package io.github.lnyocly.ai4j.rag;

import com.knuddels.jtokkit.api.EncodingType;
import io.github.lnyocly.ai4j.token.TikTokensUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TokenAwareRagContextAssemblerTest {

    @Test
    public void shouldKeepContextWithinTokenBudget() {
        TokenAwareRagContextAssembler assembler = new TokenAwareRagContextAssembler(16);

        RagContext context = assembler.assemble(RagQuery.builder().build(), Arrays.asList(
                RagHit.builder().content("alpha beta gamma").sourceName("a.md").build(),
                RagHit.builder().content("delta epsilon zeta eta theta iota kappa").sourceName("b.md").build()
        ));

        Assert.assertTrue(TikTokensUtil.tokens(EncodingType.CL100K_BASE, context.getText()) <= 16);
        Assert.assertEquals(1, context.getCitations().size());
        Assert.assertTrue(context.getText().contains("alpha beta gamma"));
        Assert.assertFalse(context.getText().contains("delta epsilon"));
    }

    @Test
    public void shouldTruncateFirstOversizedHit() {
        TokenAwareRagContextAssembler assembler = new TokenAwareRagContextAssembler("unknown-model", 6);

        RagContext context = assembler.assemble(RagQuery.builder().includeCitations(false).build(), Arrays.asList(
                RagHit.builder().content("one two three four five six seven eight nine ten eleven twelve").sourceName("long.md").build()
        ));

        Assert.assertTrue(TikTokensUtil.tokens(EncodingType.CL100K_BASE, context.getText()) <= 6);
        Assert.assertEquals(1, context.getCitations().size());
        Assert.assertTrue(context.getText().startsWith("one two"));
        Assert.assertFalse(context.getText().contains("seven"));
        Assert.assertEquals(context.getText(), context.getCitations().get(0).getSnippet());
    }

    @Test
    public void shouldUseExplicitEncodingWhenProvided() {
        TokenAwareRagContextAssembler assembler = TokenAwareRagContextAssembler.withEncoding(EncodingType.O200K_BASE, 16);

        RagContext context = assembler.assemble(RagQuery.builder().build(), Arrays.asList(
                RagHit.builder().content("alpha beta gamma").sourceName("a.md").build(),
                RagHit.builder().content("delta epsilon zeta eta theta iota kappa").sourceName("b.md").build()
        ));

        Assert.assertTrue(TikTokensUtil.tokens(EncodingType.O200K_BASE, context.getText()) <= 16);
        Assert.assertEquals(1, context.getCitations().size());
        Assert.assertTrue(context.getText().contains("alpha beta gamma"));
        Assert.assertFalse(context.getText().contains("delta epsilon"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullExplicitEncoding() {
        TokenAwareRagContextAssembler.withEncoding(null, 16);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonPositiveBudget() {
        new TokenAwareRagContextAssembler(0);
    }
}

