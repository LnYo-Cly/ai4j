package io.github.lnyocly.ai4j.coding.tool;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BashToolExecutorOutputCapTest {

    @Test
    public void shouldTruncateLargeOutputWithHeadAndTail() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            huge.append("line-").append(i).append("-padding-padding-padding\n");
        }
        String output = huge.toString();
        long smallLimit = 4096L;
        String capped = BashToolExecutor.capOutput(output, smallLimit);

        assertTrue("truncation marker must be present", capped.contains("[... truncated"));
        assertTrue("head content must survive", capped.contains("line-0-"));
        assertTrue("tail content must survive", capped.contains("line-99999-"));
        assertTrue("capped output must be smaller than original", capped.length() < output.length());
    }

    @Test
    public void shouldNotTruncateSmallOutput() {
        String output = "just a small output line";
        assertEquals(output, BashToolExecutor.capOutput(output, 4096L));
    }

    @Test
    public void shouldHandleNullOutput() {
        assertEquals(null, BashToolExecutor.capOutput(null));
    }

    @Test
    public void shouldHandleEmptyOutput() {
        assertEquals("", BashToolExecutor.capOutput(""));
    }

    @Test
    public void shouldSimulate50MbTruncation() {
        StringBuilder huge = new StringBuilder();
        String chunk = "0123456789ABCDEF";
        for (int i = 0; i < 3500000; i++) {
            huge.append(chunk);
        }
        String output = huge.toString();
        assertTrue("test data must be at least 50MB", output.getBytes().length > 50 * 1024 * 1024);

        String capped = BashToolExecutor.capOutput(output, 10L * 1024 * 1024);
        assertTrue("truncation marker must be present", capped.contains("[... truncated"));
        assertTrue("head must be preserved", capped.startsWith(chunk.substring(0, 8)));
    }
}
