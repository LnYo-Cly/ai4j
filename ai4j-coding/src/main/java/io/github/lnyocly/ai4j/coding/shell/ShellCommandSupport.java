package io.github.lnyocly.ai4j.coding.shell;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ShellCommandSupport {

    private static final String SHELL_ENCODING_PROPERTY = "ai4j.shell.encoding";
    private static final String SHELL_ENCODING_ENV = "AI4J_SHELL_ENCODING";

    private ShellCommandSupport() {
    }

    public static List<String> buildShellCommand(String command) {
        return buildShellCommand(command, System.getProperty("os.name", ""));
    }

    static List<String> buildShellCommand(String command, String osName) {
        if (isWindows(osName)) {
            return Arrays.asList("cmd.exe", "/c", command);
        }
        return Arrays.asList("sh", "-lc", command);
    }

    public static String buildShellUsageGuidance() {
        return buildShellUsageGuidance(System.getProperty("os.name", ""));
    }

    static String buildShellUsageGuidance(String osName) {
        if (isWindows(osName)) {
            return "The bash tool runs through cmd.exe /c on this machine. Use Windows command syntax and redirection; avoid Unix-only shell features such as cat <<EOF or other POSIX heredocs. Use action=exec only for commands that finish on their own. For interactive or long-running commands, use action=start and then action=logs/status/write/stop.";
        }
        return "The bash tool runs through sh -lc on this machine. Use POSIX shell syntax; avoid cmd.exe or PowerShell-only commands such as type nul > file or Get-Content. Use action=exec only for commands that finish on their own. For interactive or long-running commands, use action=start and then action=logs/status/write/stop.";
    }

    public static Charset resolveShellCharset() {
        return resolveShellCharset(
                System.getProperty("os.name", ""),
                new String[]{
                        System.getProperty(SHELL_ENCODING_PROPERTY),
                        System.getenv(SHELL_ENCODING_ENV)
                },
                new String[]{
                        System.getProperty("native.encoding"),
                        System.getProperty("sun.jnu.encoding"),
                        System.getProperty("file.encoding"),
                        Charset.defaultCharset().name()
                }
        );
    }

    static Charset resolveShellCharset(String osName, String[] explicitCandidates, String[] systemCandidates) {
        Charset explicit = firstSupportedCharset(explicitCandidates);
        if (explicit != null) {
            return explicit;
        }
        if (!isWindows(osName)) {
            return StandardCharsets.UTF_8;
        }
        Charset platform = firstSupportedCharset(systemCandidates);
        return platform == null ? Charset.defaultCharset() : platform;
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private static Charset firstSupportedCharset(String[] candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            try {
                return Charset.forName(candidate.trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
