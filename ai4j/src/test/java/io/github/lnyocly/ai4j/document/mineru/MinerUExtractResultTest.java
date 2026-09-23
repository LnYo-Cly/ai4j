package io.github.lnyocly.ai4j.document.mineru;

import org.junit.Assert;
import org.junit.Test;

public class MinerUExtractResultTest {

    @Test
    public void fromZipExtractsMarkdownJsonAndImages() throws Exception {
        MinerUExtractResult result = MinerUExtractResult.fromZip(MinerUServiceTest.zipBytes());

        Assert.assertEquals("# Doc\n", result.getMarkdown());
        Assert.assertEquals("[{\"type\":\"text\"}]", result.getContentListJson());
        Assert.assertEquals(1, result.getImages().size());
        Assert.assertArrayEquals(new byte[]{1, 2, 3}, result.getImages().get("images/a.png"));
        Assert.assertEquals(3, result.getEntries().size());
    }
}
