package io.github.lnyocly.ai4j.cli.render;

/**
 * Sanitises and renders command text for user-facing approval prompts.
 *
 * <p>Security rationale: an attacker (or prompt injection) can embed ANSI escape sequences and
 * carriage-return characters in a tool-call command. Without sanitisation the terminal may
 * execute control sequences or visually hide parts of the command, so the user approves something
 * different from what they see. This class:
 * <ol>
 *   <li>Strips CSI/OSC ANSI escape sequences entirely.</li>
 *   <li>Removes {@code \r} so it cannot overwrite earlier characters on the same line.</li>
 *   <li>Preserves the full command text without truncation — the user must see exactly what they
 *       are approving.</li>
 *   <li>Wraps long lines by terminal width using {@link CliDisplayWidth}, so multi-line or very
 *       long commands remain readable but are never cut short.</li>
 * </ol>
 */
public final class SafeApprovalText {

    private static final char ESC = 0x1B;
    private static final char BEL = 0x07;

    private SafeApprovalText() {
    }

    /**
     * Remove ANSI escape sequences and the carriage-return character from the given text.
     *
     * <p>This handles:
     * <ul>
     *   <li>CSI sequences: {@code ESC [} followed by parameter bytes and a final byte (0x40-0x7E).</li>
     *   <li>OSC sequences: {@code ESC ]} terminated by BEL ({@code \007}) or ST ({@code ESC \}).</li>
     *   <li>Single-character ESC sequences (e.g. {@code ESC c}).</li>
     *   <li>Carriage returns ({@code \r}), which can visually overwrite earlier output.</li>
     * </ul>
     *
     * @param text raw text that may contain ANSI escapes
     * @return sanitised text with all escape sequences and {@code \r} removed
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        int length = text.length();
        int i = 0;
        while (i < length) {
            char ch = text.charAt(i);
            if (ch == '\r') {
                i++;
                continue;
            }
            if (ch == ESC) {
                i = consumeEscape(text, i, length);
                continue;
            }
            builder.append(ch);
            i++;
        }
        return builder.toString();
    }

    /**
     * Render the given command for an approval prompt: sanitise ANSI/CR, then wrap to the given
     * terminal width without truncation.
     *
     * @param command        the raw command string from the tool call
     * @param terminalWidth  available column width (<= 0 disables wrapping)
     * @return the safe, wrapped display string
     */
    public static String renderForApproval(String command, int terminalWidth) {
        String safe = sanitize(command);
        if (terminalWidth <= 0 || safe.isEmpty()) {
            return safe;
        }
        return wrapPreservingNewlines(safe, terminalWidth);
    }

    /**
     * Wrap text to fit within {@code terminalWidth} columns, preserving explicit newlines and
     * never truncating. Uses {@link CliDisplayWidth} for CJK-aware column counting.
     */
    static String wrapPreservingNewlines(String text, int terminalWidth) {
        if (text == null || text.isEmpty() || terminalWidth <= 0) {
            return text == null ? "" : text;
        }
        StringBuilder result = new StringBuilder(text.length() + 16);
        int start = 0;
        while (start <= text.length()) {
            int newlineIndex = text.indexOf('\n', start);
            String line = newlineIndex >= 0
                    ? text.substring(start, newlineIndex)
                    : text.substring(start);
            wrapSingleLine(result, line, terminalWidth);
            if (newlineIndex < 0) {
                break;
            }
            result.append('\n');
            start = newlineIndex + 1;
        }
        return result.toString();
    }

    private static void wrapSingleLine(StringBuilder result, String line, int terminalWidth) {
        if (CliDisplayWidth.displayWidth(line) <= terminalWidth) {
            result.append(line);
            return;
        }
        int offset = 0;
        while (offset < line.length()) {
            String fragment = CliDisplayWidth.sliceByColumns(line.substring(offset), terminalWidth);
            if (fragment.isEmpty()) {
                break;
            }
            result.append(fragment);
            offset += fragment.length();
            if (offset < line.length()) {
                result.append('\n');
            }
        }
    }

    /**
     * Consume a single ANSI escape sequence starting at {@code escIndex} (the {@code ESC} character)
     * and return the index immediately after the sequence.
     */
    private static int consumeEscape(String text, int escIndex, int length) {
        int next = escIndex + 1;
        if (next >= length) {
            return next;
        }
        char afterEsc = text.charAt(next);
        // CSI: ESC [ ... final-byte (0x40-0x7E)
        if (afterEsc == '[') {
            int i = next + 1;
            while (i < length) {
                char c = text.charAt(i);
                if (c >= 0x40 && c <= 0x7E) {
                    return i + 1;
                }
                i++;
            }
            return length;
        }
        // OSC: ESC ] ... BEL (\007) or ST (ESC \)
        if (afterEsc == ']') {
            int i = next + 1;
            while (i < length) {
                char c = text.charAt(i);
                if (c == BEL) {
                    return i + 1;
                }
                if (c == ESC && i + 1 < length && text.charAt(i + 1) == '\\') {
                    return i + 2;
                }
                i++;
            }
            return length;
        }
        // Single-char ESC sequence: ESC + one byte
        return next + 1;
    }
}
