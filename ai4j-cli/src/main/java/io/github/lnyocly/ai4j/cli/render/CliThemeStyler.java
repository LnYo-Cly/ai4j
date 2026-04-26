package io.github.lnyocly.ai4j.cli.render;

import io.github.lnyocly.ai4j.tui.TuiTheme;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CliThemeStyler {

    private static final String FALLBACK_BRAND = "#7cc6fe";
    private static final String FALLBACK_ACCENT = "#f5b14c";
    private static final String FALLBACK_SUCCESS = "#8fd694";
    private static final String FALLBACK_WARNING = "#f4d35e";
    private static final String FALLBACK_DANGER = "#ef6f6c";
    private static final String FALLBACK_TEXT = "#f3f4f6";
    private static final String FALLBACK_MUTED = "#9ca3af";
    private static final String FALLBACK_CODE_BACKGROUND = "#161b22";
    private static final String FALLBACK_CODE_TEXT = "#c9d1d9";
    private static final String FALLBACK_CODE_KEYWORD = "#ff7b72";
    private static final String FALLBACK_CODE_STRING = "#a5d6ff";
    private static final String FALLBACK_CODE_COMMENT = "#8b949e";
    private static final String FALLBACK_CODE_NUMBER = "#79c0ff";
    private static final Set<String> JAVA_LIKE_KEYWORDS = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "record", "return", "sealed", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "var", "void", "volatile", "while", "yield"
    ));
    private static final Set<String> SCRIPT_KEYWORDS = new HashSet<String>(Arrays.asList(
            "as", "async", "await", "break", "case", "catch", "class", "const", "continue", "def", "default",
            "del", "do", "elif", "else", "esac", "except", "export", "extends", "fi", "finally", "for",
            "from", "function", "if", "import", "in", "lambda", "let", "local", "pass", "raise", "return",
            "then", "try", "var", "while", "with", "yield"
    ));
    private static final Set<String> LITERAL_KEYWORDS = new HashSet<String>(Arrays.asList(
            "true", "false", "null", "none", "undefined"
    ));

    private final boolean ansi;
    private volatile TuiTheme theme;

    public CliThemeStyler(TuiTheme theme, boolean ansi) {
        this.theme = theme == null ? new TuiTheme() : theme;
        this.ansi = ansi;
    }

    public void updateTheme(TuiTheme theme) {
        this.theme = theme == null ? new TuiTheme() : theme;
    }

    public String styleTranscriptLine(String line) {
        return styleTranscriptLine(line, new TranscriptStyleState());
    }

    public String styleTranscriptLine(String line, TranscriptStyleState state) {
        if (line == null || line.isEmpty() || line.indexOf('\u001b') >= 0) {
            return line;
        }
        TranscriptStyleState renderState = state == null ? new TranscriptStyleState() : state;
        if (isCodeBlockHeaderLine(line)) {
            renderState.enterCodeBlock(codeBlockLanguage(line));
            return styleCodeFenceHeader(line);
        }
        if (renderState.isInsideCodeBlock()) {
            if (isCodeBlockLine(line)) {
                return styleCodeBlockLine(line, renderState.getCodeBlockLanguage());
            }
            renderState.exitCodeBlock();
        }
        if (line.startsWith("Thinking: ")) {
            return styleReasoningFragment(line);
        }
        if (line.startsWith("• ")) {
            return CliAnsi.colorize(line, transcriptBulletColor(line), ansi, true);
        }
        if (isMarkdownHeadingLine(line)) {
            return styleMarkdownHeadingLine(line);
        }
        if (isMarkdownQuoteLine(line)) {
            return styleMarkdownQuoteLine(line);
        }
        if (line.startsWith("  └ ") || line.startsWith("  │ ") || line.startsWith("    ")) {
            return styleMutedFragment(line);
        }
        return styleInlineMarkdown(line, text(), false);
    }

    public String buildPrimaryStatusLine(String statusLabel, boolean spinnerActive, String spinner, String statusDetail) {
        String label = firstNonBlank(statusLabel, "Idle");
        String color = statusColor(label);
        StringBuilder builder = new StringBuilder();
        builder.append(CliAnsi.colorize("• " + label, color, ansi, true));
        if (spinnerActive && !isBlank(spinner)) {
            builder.append(' ').append(CliAnsi.colorize(spinner, color, ansi, true));
        }
        if (!isBlank(statusDetail)) {
            builder.append("  ").append(CliAnsi.colorize(statusDetail, "idle".equalsIgnoreCase(label) ? muted() : text(), ansi, false));
        }
        return builder.toString();
    }

    public String buildCompactStatusLine(String statusLabel,
                                         boolean spinnerActive,
                                         String spinner,
                                         String statusDetail,
                                         String model,
                                         String workspace,
                                         String hint) {
        StringBuilder builder = new StringBuilder(buildPrimaryStatusLine(statusLabel, spinnerActive, spinner, statusDetail));
        if (!isBlank(model)) {
            builder.append("  ")
                    .append(styleMutedFragment("model"))
                    .append(' ')
                    .append(CliAnsi.colorize(firstNonBlank(model, "(unknown)"), accent(), ansi, false));
        }
        if (!isBlank(workspace)) {
            builder.append("  ")
                    .append(styleMutedFragment("workspace"))
                    .append(' ')
                    .append(styleAssistantFragment(firstNonBlank(workspace, ".")));
        }
        if ("idle".equalsIgnoreCase(firstNonBlank(statusLabel, "Idle")) && !isBlank(hint)) {
            builder.append("  ")
                    .append(styleMutedFragment("hint"))
                    .append(' ')
                    .append(styleMutedFragment(hint));
        }
        return builder.toString();
    }

    public String buildSessionLine(String sessionId, String model, String workspace) {
        return styleMutedFragment("session")
                + " " + CliAnsi.colorize(firstNonBlank(sessionId, "(new)"), brand(), ansi, true)
                + "  " + styleMutedFragment("model")
                + " " + CliAnsi.colorize(firstNonBlank(model, "(unknown)"), accent(), ansi, false)
                + "  " + styleMutedFragment("workspace")
                + " " + styleAssistantFragment(firstNonBlank(workspace, "."));
    }

    public String buildHintLine(String hint) {
        return styleMutedFragment("hint")
                + " " + styleMutedFragment(firstNonBlank(hint, "Enter a prompt or /command"));
    }

    public String styleAssistantFragment(String text) {
        return CliAnsi.colorize(text, text(), ansi, false);
    }

    public String styleReasoningFragment(String text) {
        return CliAnsi.colorize(text, muted(), ansi, false);
    }

    public String styleMutedFragment(String text) {
        return CliAnsi.colorize(text, muted(), ansi, false);
    }

    public String styleTranscriptCodeLine(String line, String language) {
        return styleCodeBlockLine(line == null ? "" : line, language);
    }

    private String styleCodeFenceHeader(String line) {
        return CliAnsi.style(line, codeComment(), codeBackground(), ansi, true);
    }

    private String styleCodeBlockLine(String line, String language) {
        String prefix = CodexStyleBlockFormatter.CODE_BLOCK_LINE_PREFIX;
        String body = line.length() <= prefix.length() ? "" : line.substring(prefix.length());
        if (!ansi) {
            return line;
        }
        return styleCodeTextFragment(prefix) + highlightCodeBody(body, language);
    }

    private String styleMarkdownHeadingLine(String line) {
        if (!ansi) {
            return stripInlineMarkdown(line);
        }
        return styleInlineMarkdown(line, brand(), true);
    }

    private String styleMarkdownQuoteLine(String line) {
        return styleInlineMarkdown(line, muted(), false);
    }

    private String styleInlineMarkdown(String line, String baseColor, boolean boldByDefault) {
        if (line == null) {
            return "";
        }
        String normalized = stripMarkdownLinks(line);
        if (!ansi) {
            return stripInlineMarkdown(normalized);
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder buffer = new StringBuilder();
        boolean bold = boldByDefault;
        boolean code = false;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if ((current == '*' || current == '_')
                    && index + 1 < normalized.length()
                    && normalized.charAt(index + 1) == current) {
                appendStyledMarkdownSegment(builder, buffer.toString(), baseColor, bold, code);
                buffer.setLength(0);
                bold = !bold;
                index++;
                continue;
            }
            if (current == '`') {
                appendStyledMarkdownSegment(builder, buffer.toString(), baseColor, bold, code);
                buffer.setLength(0);
                code = !code;
                continue;
            }
            if ((current == '*' || current == '_') && isSingleMarkerToggle(normalized, index, current)) {
                continue;
            }
            buffer.append(current);
        }
        appendStyledMarkdownSegment(builder, buffer.toString(), baseColor, bold, code);
        return builder.toString();
    }

    private String highlightCodeBody(String body, String language) {
        String normalizedLanguage = normalizeLanguage(language);
        if ("json".equals(normalizedLanguage)) {
            return highlightJson(body);
        }
        if ("xml".equals(normalizedLanguage) || "html".equals(normalizedLanguage)) {
            return highlightXml(body);
        }
        if ("bash".equals(normalizedLanguage)) {
            return highlightGenericCode(body, SCRIPT_KEYWORDS, true, true, false, true);
        }
        if ("python".equals(normalizedLanguage)) {
            return highlightGenericCode(body, SCRIPT_KEYWORDS, false, true, true, false);
        }
        if ("yaml".equals(normalizedLanguage)) {
            return highlightGenericCode(body, Collections.<String>emptySet(), false, true, false, false);
        }
        if ("java".equals(normalizedLanguage)
                || "kotlin".equals(normalizedLanguage)
                || "javascript".equals(normalizedLanguage)
                || "typescript".equals(normalizedLanguage)
                || "csharp".equals(normalizedLanguage)) {
            return highlightGenericCode(body, JAVA_LIKE_KEYWORDS, true, false, true, false);
        }
        return highlightGenericCode(body, JAVA_LIKE_KEYWORDS, true, true, true, true);
    }

    private String highlightGenericCode(String code,
                                        Set<String> keywords,
                                        boolean slashComment,
                                        boolean hashComment,
                                        boolean annotations,
                                        boolean variables) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        StringBuilder styled = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < code.length()) {
            char ch = code.charAt(index);
            if (slashComment && index + 1 < code.length() && ch == '/' && code.charAt(index + 1) == '/') {
                flushPlain(styled, plain);
                styled.append(styleCodeCommentFragment(code.substring(index)));
                return styled.toString();
            }
            if (slashComment && index + 1 < code.length() && ch == '/' && code.charAt(index + 1) == '*') {
                int end = code.indexOf("*/", index + 2);
                int stop = end >= 0 ? end + 2 : code.length();
                flushPlain(styled, plain);
                styled.append(styleCodeCommentFragment(code.substring(index, stop)));
                index = stop;
                continue;
            }
            if (hashComment && ch == '#') {
                flushPlain(styled, plain);
                styled.append(styleCodeCommentFragment(code.substring(index)));
                return styled.toString();
            }
            if (variables && ch == '$') {
                int stop = consumeVariable(code, index);
                if (stop > index + 1) {
                    flushPlain(styled, plain);
                    styled.append(styleCodeNumberFragment(code.substring(index, stop), false));
                    index = stop;
                    continue;
                }
            }
            if (annotations && ch == '@') {
                int stop = consumeIdentifier(code, index + 1);
                if (stop > index + 1) {
                    flushPlain(styled, plain);
                    styled.append(styleCodeNumberFragment(code.substring(index, stop), false));
                    index = stop;
                    continue;
                }
            }
            if (ch == '"' || ch == '\'') {
                int stop = consumeQuoted(code, index, ch);
                flushPlain(styled, plain);
                styled.append(styleCodeStringFragment(code.substring(index, stop)));
                index = stop;
                continue;
            }
            if (Character.isDigit(ch)) {
                int stop = consumeNumber(code, index);
                flushPlain(styled, plain);
                styled.append(styleCodeNumberFragment(code.substring(index, stop), false));
                index = stop;
                continue;
            }
            if (isIdentifierStart(ch)) {
                int stop = consumeIdentifier(code, index + 1);
                String word = code.substring(index, stop);
                if (keywords.contains(word)) {
                    flushPlain(styled, plain);
                    styled.append(styleCodeKeywordFragment(word, true));
                } else if (LITERAL_KEYWORDS.contains(word.toLowerCase(Locale.ROOT))) {
                    flushPlain(styled, plain);
                    styled.append(styleCodeNumberFragment(word, true));
                } else {
                    plain.append(word);
                }
                index = stop;
                continue;
            }
            if (isCodePunctuation(ch)) {
                flushPlain(styled, plain);
                styled.append(styleCodeTextFragment(String.valueOf(ch)));
                index++;
                continue;
            }
            plain.append(ch);
            index++;
        }
        flushPlain(styled, plain);
        return styled.toString();
    }

    private String highlightJson(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        StringBuilder styled = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < code.length()) {
            char ch = code.charAt(index);
            if (ch == '"' || ch == '\'') {
                int stop = consumeQuoted(code, index, ch);
                String token = code.substring(index, stop);
                int next = skipWhitespace(code, stop);
                flushPlain(styled, plain);
                styled.append(next < code.length() && code.charAt(next) == ':'
                        ? styleCodeKeywordFragment(token, true)
                        : styleCodeStringFragment(token));
                index = stop;
                continue;
            }
            if (Character.isDigit(ch) || (ch == '-' && index + 1 < code.length() && Character.isDigit(code.charAt(index + 1)))) {
                int stop = consumeNumber(code, index);
                flushPlain(styled, plain);
                styled.append(styleCodeNumberFragment(code.substring(index, stop), false));
                index = stop;
                continue;
            }
            if (isIdentifierStart(ch)) {
                int stop = consumeIdentifier(code, index + 1);
                String word = code.substring(index, stop);
                if (LITERAL_KEYWORDS.contains(word.toLowerCase(Locale.ROOT))) {
                    flushPlain(styled, plain);
                    styled.append(styleCodeNumberFragment(word, true));
                } else {
                    plain.append(word);
                }
                index = stop;
                continue;
            }
            if (isCodePunctuation(ch) || ch == ':' || ch == ',') {
                flushPlain(styled, plain);
                styled.append(styleCodeTextFragment(String.valueOf(ch)));
                index++;
                continue;
            }
            plain.append(ch);
            index++;
        }
        flushPlain(styled, plain);
        return styled.toString();
    }

    private String highlightXml(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        StringBuilder styled = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < code.length()) {
            if (code.startsWith("<!--", index)) {
                int end = code.indexOf("-->", index + 4);
                int stop = end >= 0 ? end + 3 : code.length();
                flushPlain(styled, plain);
                styled.append(styleCodeCommentFragment(code.substring(index, stop)));
                index = stop;
                continue;
            }
            if (code.charAt(index) == '<') {
                int end = code.indexOf('>', index + 1);
                int stop = end >= 0 ? end + 1 : code.length();
                flushPlain(styled, plain);
                styled.append(highlightXmlTag(code.substring(index, stop)));
                index = stop;
                continue;
            }
            plain.append(code.charAt(index));
            index++;
        }
        flushPlain(styled, plain);
        return styled.toString();
    }

    private String highlightXmlTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "";
        }
        StringBuilder styled = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < tag.length()) {
            char ch = tag.charAt(index);
            if (ch == '"' || ch == '\'') {
                int stop = consumeQuoted(tag, index, ch);
                flushPlain(styled, plain);
                styled.append(styleCodeStringFragment(tag.substring(index, stop)));
                index = stop;
                continue;
            }
            if (ch == '<' || ch == '>' || ch == '/' || ch == '=') {
                flushPlain(styled, plain);
                styled.append(styleCodeTextFragment(String.valueOf(ch)));
                index++;
                continue;
            }
            if (isIdentifierStart(ch) || ch == ':') {
                int stop = consumeXmlIdentifier(tag, index + 1);
                String word = tag.substring(index, stop);
                flushPlain(styled, plain);
                styled.append(styleCodeKeywordFragment(word, true));
                index = stop;
                continue;
            }
            plain.append(ch);
            index++;
        }
        flushPlain(styled, plain);
        return styled.toString();
    }

    private void flushPlain(StringBuilder styled, StringBuilder plain) {
        if (plain.length() == 0) {
            return;
        }
        styled.append(styleCodeTextFragment(plain.toString()));
        plain.setLength(0);
    }

    private boolean isCodeBlockHeaderLine(String line) {
        return line != null
                && line.startsWith(CodexStyleBlockFormatter.CODE_BLOCK_HEADER_PREFIX)
                && line.endsWith(CodexStyleBlockFormatter.CODE_BLOCK_HEADER_SUFFIX);
    }

    private boolean isCodeBlockLine(String line) {
        return line != null
                && (line.startsWith(CodexStyleBlockFormatter.CODE_BLOCK_LINE_PREFIX)
                || CodexStyleBlockFormatter.CODE_BLOCK_EMPTY_LINE.equals(line));
    }

    private String codeBlockLanguage(String line) {
        if (!isCodeBlockHeaderLine(line)) {
            return null;
        }
        return line.substring(
                CodexStyleBlockFormatter.CODE_BLOCK_HEADER_PREFIX.length(),
                Math.max(CodexStyleBlockFormatter.CODE_BLOCK_HEADER_PREFIX.length(), line.length() - CodexStyleBlockFormatter.CODE_BLOCK_HEADER_SUFFIX.length())
        ).trim();
    }

    private String styleCodeTextFragment(String text) {
        return CliAnsi.style(text, codeText(), codeBackground(), ansi, false);
    }

    private String styleCodeKeywordFragment(String text, boolean bold) {
        return CliAnsi.style(text, codeKeyword(), codeBackground(), ansi, bold);
    }

    private String styleCodeStringFragment(String text) {
        return CliAnsi.style(text, codeString(), codeBackground(), ansi, false);
    }

    private String styleCodeCommentFragment(String text) {
        return CliAnsi.style(text, codeComment(), codeBackground(), ansi, false);
    }

    private String styleCodeNumberFragment(String text, boolean bold) {
        return CliAnsi.style(text, codeNumber(), codeBackground(), ansi, bold);
    }

    private int consumeQuoted(String text, int start, char quote) {
        int index = start + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\' && index + 1 < text.length()) {
                index += 2;
                continue;
            }
            if (current == quote) {
                return index + 1;
            }
            index++;
        }
        return text.length();
    }

    private int consumeIdentifier(String text, int start) {
        int index = start;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '$') {
                break;
            }
            index++;
        }
        return index;
    }

    private int consumeXmlIdentifier(String text, int start) {
        int index = start;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '-' && current != ':' && current != '.') {
                break;
            }
            index++;
        }
        return index;
    }

    private int consumeNumber(String text, int start) {
        int index = start;
        if (index < text.length() && (text.charAt(index) == '-' || text.charAt(index) == '+')) {
            index++;
        }
        while (index < text.length()) {
            char current = text.charAt(index);
            if (!Character.isDigit(current) && current != '.' && current != '_' && current != 'x' && current != 'X'
                    && current != 'b' && current != 'B' && current != 'o' && current != 'O'
                    && current != 'e' && current != 'E' && current != '+' && current != '-') {
                break;
            }
            index++;
        }
        return index;
    }

    private int consumeVariable(String text, int start) {
        if (start + 1 >= text.length()) {
            return start + 1;
        }
        if (text.charAt(start + 1) == '{') {
            int end = text.indexOf('}', start + 2);
            return end >= 0 ? end + 1 : text.length();
        }
        return consumeIdentifier(text, start + 1);
    }

    private int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_' || ch == '$';
    }

    private boolean isCodePunctuation(char ch) {
        return "{}[]()<>".indexOf(ch) >= 0;
    }

    private boolean isMarkdownHeadingLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }
        return trimmed.length() == 1 || (trimmed.length() > 1 && trimmed.charAt(1) == '#') || Character.isWhitespace(trimmed.charAt(1));
    }

    private boolean isMarkdownQuoteLine(String line) {
        return line != null && line.trim().startsWith(">");
    }

    private boolean isSingleMarkerToggle(String text, int index, char marker) {
        if (index > 0 && text.charAt(index - 1) == marker) {
            return false;
        }
        if (index + 1 < text.length() && text.charAt(index + 1) == marker) {
            return false;
        }
        return true;
    }

    private String normalizeLanguage(String language) {
        if (isBlank(language)) {
            return "";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if ("sh".equals(normalized) || "shell".equals(normalized) || "zsh".equals(normalized) || "powershell".equals(normalized) || "ps1".equals(normalized)) {
            return "bash";
        }
        if ("js".equals(normalized)) {
            return "javascript";
        }
        if ("ts".equals(normalized)) {
            return "typescript";
        }
        if ("kt".equals(normalized)) {
            return "kotlin";
        }
        if ("py".equals(normalized)) {
            return "python";
        }
        if ("yml".equals(normalized)) {
            return "yaml";
        }
        if ("htm".equals(normalized)) {
            return "html";
        }
        if ("c#".equals(normalized) || "cs".equals(normalized)) {
            return "csharp";
        }
        return normalized;
    }

    private void appendStyledMarkdownSegment(StringBuilder builder,
                                             String segment,
                                             String baseColor,
                                             boolean bold,
                                             boolean code) {
        if (builder == null || segment == null || segment.isEmpty()) {
            return;
        }
        if (code) {
            builder.append(CliAnsi.style(segment, codeText(), codeBackground(), ansi, true));
            return;
        }
        builder.append(CliAnsi.style(segment, baseColor, null, ansi, bold));
    }

    private String stripMarkdownLinks(String text) {
        if (text == null || text.indexOf('[') < 0 || text.indexOf('(') < 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int openLabel = text.indexOf('[', index);
            if (openLabel < 0) {
                builder.append(text.substring(index));
                break;
            }
            int closeLabel = text.indexOf(']', openLabel + 1);
            int openUrl = closeLabel >= 0 && closeLabel + 1 < text.length() && text.charAt(closeLabel + 1) == '('
                    ? closeLabel + 1
                    : -1;
            int closeUrl = openUrl >= 0 ? text.indexOf(')', openUrl + 1) : -1;
            if (closeLabel < 0 || openUrl < 0 || closeUrl < 0) {
                builder.append(text.substring(index));
                break;
            }
            builder.append(text, index, openLabel);
            builder.append(text, openLabel + 1, closeLabel);
            index = closeUrl + 1;
        }
        return builder.toString();
    }

    private String stripInlineMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return stripMarkdownLinks(text)
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
    }

    private String transcriptBulletColor(String line) {
        String normalized = line.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("• error")
                || normalized.startsWith("• tool failed")
                || normalized.startsWith("• command failed")
                || normalized.startsWith("• rejected")) {
            return danger();
        }
        if (normalized.startsWith("• approved")) {
            return success();
        }
        if (normalized.startsWith("• approval required")
                || normalized.startsWith("• applying")
                || normalized.startsWith("• running")
                || normalized.startsWith("• reading")
                || normalized.startsWith("• writing")
                || normalized.startsWith("• checking")
                || normalized.startsWith("• stopping")) {
            return warning();
        }
        if (normalized.startsWith("• auto-compacted")
                || normalized.startsWith("• compacted")
                || normalized.startsWith("• thinking")
                || normalized.startsWith("• responding")
                || normalized.startsWith("• working")) {
            return brand();
        }
        return accent();
    }

    private String statusColor(String statusLabel) {
        String normalized = firstNonBlank(statusLabel, "Idle").trim().toLowerCase(Locale.ROOT);
        if ("thinking".equals(normalized)) {
            return accent();
        }
        if ("connecting".equals(normalized)) {
            return accent();
        }
        if ("responding".equals(normalized)) {
            return brand();
        }
        if ("retrying".equals(normalized)) {
            return warning();
        }
        if ("working".equals(normalized)) {
            return warning();
        }
        if ("waiting".equals(normalized)) {
            return accent();
        }
        if ("stalled".equals(normalized)) {
            return warning();
        }
        if ("error".equals(normalized)) {
            return danger();
        }
        return muted();
    }

    private String brand() {
        return firstNonBlank(theme == null ? null : theme.getBrand(), FALLBACK_BRAND);
    }

    private String accent() {
        return firstNonBlank(theme == null ? null : theme.getAccent(), FALLBACK_ACCENT);
    }

    private String success() {
        return firstNonBlank(theme == null ? null : theme.getSuccess(), FALLBACK_SUCCESS);
    }

    private String warning() {
        return firstNonBlank(theme == null ? null : theme.getWarning(), FALLBACK_WARNING);
    }

    private String danger() {
        return firstNonBlank(theme == null ? null : theme.getDanger(), FALLBACK_DANGER);
    }

    private String text() {
        return firstNonBlank(theme == null ? null : theme.getText(), FALLBACK_TEXT);
    }

    private String muted() {
        return firstNonBlank(theme == null ? null : theme.getMuted(), FALLBACK_MUTED);
    }

    private String codeBackground() {
        return firstNonBlank(theme == null ? null : theme.getCodeBackground(), FALLBACK_CODE_BACKGROUND);
    }

    private String codeText() {
        return firstNonBlank(theme == null ? null : theme.getCodeText(), FALLBACK_CODE_TEXT);
    }

    private String codeKeyword() {
        return firstNonBlank(theme == null ? null : theme.getCodeKeyword(), FALLBACK_CODE_KEYWORD);
    }

    private String codeString() {
        return firstNonBlank(theme == null ? null : theme.getCodeString(), FALLBACK_CODE_STRING);
    }

    private String codeComment() {
        return firstNonBlank(theme == null ? null : theme.getCodeComment(), FALLBACK_CODE_COMMENT);
    }

    private String codeNumber() {
        return firstNonBlank(theme == null ? null : theme.getCodeNumber(), FALLBACK_CODE_NUMBER);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class TranscriptStyleState {

        private String codeBlockLanguage;
        private boolean insideCodeBlock;

        public void enterCodeBlock(String language) {
            codeBlockLanguage = language;
            insideCodeBlock = true;
        }

        public void exitCodeBlock() {
            codeBlockLanguage = null;
            insideCodeBlock = false;
        }

        public void reset() {
            exitCodeBlock();
        }

        public String getCodeBlockLanguage() {
            return codeBlockLanguage;
        }

        public boolean isInsideCodeBlock() {
            return insideCodeBlock;
        }
    }
}

