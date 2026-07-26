package io.github.lnyocly.ai4j.cli.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SafeApprovalTextTest {

    @Test
    public void shouldStripCsiColorEscape() {
        String input = "[31mecho hello[0m";
        String result = SafeApprovalText.sanitize(input);
        assertEquals("echo hello", result);
    }

    @Test
    public void shouldStripCsiCursorMovement() {
        String input = "[2Aecho[2K";
        String result = SafeApprovalText.sanitize(input);
        assertEquals("echo", result);
    }

    @Test
    public void shouldStripOscSequenceTerminatedByBel() {
        String input = "]0;evil-titlels -la";
        String result = SafeApprovalText.sanitize(input);
        assertEquals("ls -la", result);
    }

    @Test
    public void shouldStripOscSequenceTerminatedBySt() {
        String input = "]0;evil-title\\rm -rf /";
        String result = SafeApprovalText.sanitize(input);
        assertEquals("rm -rf /", result);
    }

    @Test
    public void shouldRemoveCarriageReturn() {
        String input = "echo visible\recho hidden_evil";
        String result = SafeApprovalText.sanitize(input);
        assertFalse("carriage return must be removed", result.contains("\r"));
        assertTrue("visible part must survive", result.contains("echo visible"));
        assertTrue("hidden evil part must be visible after sanitising", result.contains("echo hidden_evil"));
    }

    @Test
    public void shouldNotTruncateLongCommand() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            builder.append("arg").append(i).append(' ');
        }
        String longCommand = builder.toString().trim();
        String result = SafeApprovalText.renderForApproval(longCommand, 80);
        assertFalse("long command must not be truncated", result.contains("..."));
        assertTrue("full content must survive", result.contains("arg499"));
        assertTrue("full content must survive", result.contains("arg0"));
    }

    @Test
    public void shouldWrapLongLineToTerminalWidth() {
        String longLine = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String result = SafeApprovalText.renderForApproval(longLine, 10);
        assertFalse("must not truncate", result.contains("..."));
        assertTrue("must wrap by inserting newlines", result.contains("\n"));
        assertTrue("last character must survive wrapping", result.endsWith("Z"));
        assertTrue("head must survive", result.startsWith("abc"));
    }

    @Test
    public void shouldPreserveExplicitNewlines() {
        String input = "line1\nline2\nline3";
        String result = SafeApprovalText.renderForApproval(input, 80);
        assertTrue("explicit newlines must be preserved", result.contains("line1\nline2"));
    }

    @Test
    public void shouldHandleCombinedAnsiCarriageReturnAndInjection() {
        // Simulates an attack: ANSI red, CR overwrite, hidden command
        String attack = "[32mSafe looking command\r[31mrm -rf /[0m";
        String result = SafeApprovalText.sanitize(attack);
        assertFalse("no ESC bytes allowed", result.contains(""));
        assertFalse("no CR allowed", result.contains("\r"));
        assertTrue("hidden command must be visible to the user", result.contains("rm -rf /"));
    }

    @Test
    public void shouldHandleNullInput() {
        assertEquals("", SafeApprovalText.sanitize(null));
        assertEquals("", SafeApprovalText.renderForApproval(null, 80));
    }

    @Test
    public void shouldHandleEmptyInput() {
        assertEquals("", SafeApprovalText.sanitize(""));
        assertEquals("", SafeApprovalText.renderForApproval("", 80));
    }

    @Test
    public void shouldHandleShortCommandWithoutWrapping() {
        assertEquals("ls -la", SafeApprovalText.renderForApproval("ls -la", 80));
    }
}
