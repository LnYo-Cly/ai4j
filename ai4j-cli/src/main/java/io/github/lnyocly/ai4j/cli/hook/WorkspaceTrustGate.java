package io.github.lnyocly.ai4j.cli.hook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Trust gate for {@code .ai4j/workspace.json} hooks. Before the CLI loads and executes
 * workspace hooks (which run arbitrary shell commands), this gate verifies that the user
 * has explicitly trusted the workspace directory.
 *
 * <p><strong>Flow:</strong></p>
 * <ol>
 *   <li>If the directory is already in {@code ~/.ai4j/trusted-dirs.txt} → trusted, proceed.</li>
 *   <li>Otherwise, display the full hooks configuration (ANSI escapes stripped, no truncation)
 *       and prompt the user for y/n confirmation.</li>
 *   <li>On {@code y}: persist the directory to trusted-dirs.txt, return {@code TRUSTED}.</li>
 *   <li>On {@code n}: return {@code UNTRUSTED} — hooks must not be loaded.</li>
 *   <li>On EOF/read error: return {@code UNTRUSTED} (fail-closed).</li>
 * </ol>
 */
public class WorkspaceTrustGate {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\[[0-9;]*[a-zA-Z]|\\][^]*|[()][0-9A-Za-z]");

    private final TrustedDirsStore store;

    public WorkspaceTrustGate() {
        this(new TrustedDirsStore());
    }

    public WorkspaceTrustGate(TrustedDirsStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    /**
     * The outcome of a trust check.
     */
    public enum TrustResult {
        /** Directory was already trusted or user confirmed — hooks may proceed. */
        TRUSTED,
        /** User declined or input was unavailable — hooks must NOT be loaded. */
        UNTRUSTED,
        /** No hooks configured — no trust check needed. */
        NO_HOOKS
    }

    /**
     * Checks whether hooks from the given workspace directory should be loaded.
     *
     * @param workspaceDir the workspace root directory containing {@code .ai4j/workspace.json}
     * @param hooksConfig  the parsed hooks configuration (may be null or empty)
     * @param in           stdin for reading the y/n response
     * @param out          stdout/stderr for displaying the prompt
     * @return the trust result
     */
    public TrustResult checkTrust(Path workspaceDir, CliHooksConfig hooksConfig,
                                  InputStream in, PrintStream out) {
        if (hooksConfig == null || hooksConfig.isEmpty()) {
            return TrustResult.NO_HOOKS;
        }

        if (workspaceDir != null && store.isTrusted(workspaceDir)) {
            return TrustResult.TRUSTED;
        }

        // Untrusted directory with hooks — show the config and ask.
        displayHooksForReview(workspaceDir, hooksConfig, out);
        out.println();
        out.print("Do you trust this workspace and want to enable these hooks? (y/n): ");
        out.flush();

        String response = readLine(in);
        if (response != null && response.trim().equalsIgnoreCase("y")) {
            try {
                if (workspaceDir != null) {
                    store.trust(workspaceDir);
                }
                out.println("Workspace trusted. Hooks will be loaded.");
                out.flush();
                return TrustResult.TRUSTED;
            } catch (IOException e) {
                out.println("Warning: could not persist trust decision (" + e.getMessage()
                        + "). Hooks will be loaded for this session only.");
                out.flush();
                return TrustResult.TRUSTED;
            }
        }

        out.println("Workspace not trusted. Hooks will be DISABLED for this session.");
        out.flush();
        return TrustResult.UNTRUSTED;
    }

    /**
     * Trusts a directory without prompting (for {@code ai4j cli trust --dir <path>}).
     */
    public void trustDirectory(Path dir) throws IOException {
        store.trust(dir);
    }

    /**
     * Revokes trust for a directory (for {@code ai4j cli trust --revoke <path>}).
     *
     * @return true if the directory was previously trusted
     */
    public boolean revokeDirectory(Path dir) throws IOException {
        return store.revoke(dir);
    }

    /**
     * Checks whether a directory is already trusted.
     */
    public boolean isTrusted(Path dir) {
        return store.isTrusted(dir);
    }

    // ---- display ----

    private void displayHooksForReview(Path workspaceDir, CliHooksConfig config, PrintStream out) {
        out.println();
        out.println("========================================================");
        out.println("  Workspace Hook Trust Review");
        out.println("========================================================");
        if (workspaceDir != null) {
            out.println("Directory: " + workspaceDir.toAbsolutePath().normalize());
        }
        out.println("This workspace declares the following hooks that will run");
        out.println("arbitrary shell commands. Review them carefully.");
        out.println("--------------------------------------------------------");

        printHookSection("preToolUse", config.getPreToolUse(), out);
        printHookSection("postToolUse", config.getPostToolUse(), out);
        printHookSection("userPromptSubmit", config.getUserPromptSubmit(), out);
        printHookSection("stop", config.getStop(), out);
        printHookSection("preCompact", config.getPreCompact(), out);
        printHookSection("sessionStart", config.getSessionStart(), out);
        printHookSection("sessionEnd", config.getSessionEnd(), out);

        out.println("========================================================");
    }

    private void printHookSection(String name, List<CliHookEntry> hooks, PrintStream out) {
        if (hooks == null || hooks.isEmpty()) {
            return;
        }
        out.println();
        out.println("[" + name + "] (" + hooks.size() + " hook" + (hooks.size() > 1 ? "s" : "") + ")");
        int i = 1;
        for (CliHookEntry hook : hooks) {
            if (hook == null) {
                continue;
            }
            out.println("  " + i + ". command: " + sanitizeForDisplay(hook.getCommand()));
            if (hook.getMatch() != null && !hook.getMatch().trim().isEmpty()) {
                out.println("     match:   " + sanitizeForDisplay(hook.getMatch()));
            }
            i++;
        }
    }

    /**
     * Strips ANSI escape sequences from the given text so malicious configs can't
     * hide commands with terminal control codes. Also removes the OSC title-set sequence.
     *
     * @param text the raw text that may contain ANSI escapes
     * @return the sanitized text with all escape sequences removed
     */
    public static String sanitizeForDisplay(String text) {
        if (text == null) {
            return "";
        }
        return ANSI_ESCAPE.matcher(text).replaceAll("");
    }

    private static String readLine(InputStream in) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
