package io.github.lnyocly.ai4j.coding.shell;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShellCommandSupportTest {

    @Test
    public void shouldBuildWindowsShellCommandAndGuidance() {
        assertEquals(Arrays.asList("cmd.exe", "/c", "dir"),
                ShellCommandSupport.buildShellCommand("dir", "Windows 11"));

        String guidance = ShellCommandSupport.buildShellUsageGuidance("Windows 11");
        assertTrue(guidance.contains("cmd.exe /c"));
        assertTrue(guidance.contains("cat <<EOF"));
        assertTrue(guidance.contains("action=start"));
    }

    @Test
    public void shouldBuildPosixShellCommandAndGuidance() {
        assertEquals(Arrays.asList("sh", "-lc", "pwd"),
                ShellCommandSupport.buildShellCommand("pwd", "Linux"));

        String guidance = ShellCommandSupport.buildShellUsageGuidance("Linux");
        assertTrue(guidance.contains("sh -lc"));
        assertTrue(guidance.contains("type nul > file"));
        assertTrue(guidance.contains("Get-Content"));
        assertTrue(guidance.contains("action=start"));
    }

    @Test
    public void shouldResolveWindowsShellCharsetFromNativeEncoding() {
        Charset charset = ShellCommandSupport.resolveShellCharset(
                "Windows 11",
                new String[]{null, ""},
                new String[]{"GBK", "UTF-8"}
        );

        assertEquals(Charset.forName("GBK"), charset);
    }

    @Test
    public void shouldPreferExplicitShellCharsetOverride() {
        Charset charset = ShellCommandSupport.resolveShellCharset(
                "Windows 11",
                new String[]{"UTF-8"},
                new String[]{"GBK"}
        );

        assertEquals(StandardCharsets.UTF_8, charset);
    }
}
