package io.github.lnyocly.ai4j.cli.sandbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parsed form of the interactive {@code /sandbox} command.
 */
public final class CliSandboxCommand {

    public enum Action {
        STATUS,
        ENABLE,
        ATTACH,
        DISABLE
    }

    private final Action action;
    private final String providerId;
    private final String sandboxIdOrName;
    private final String workspaceId;
    private final String image;
    private final boolean deleteOnClose;
    private final boolean createIfMissing;

    private CliSandboxCommand(Builder builder) {
        this.action = builder.action;
        this.providerId = trimToNull(builder.providerId);
        this.sandboxIdOrName = trimToNull(builder.sandboxIdOrName);
        this.workspaceId = trimToNull(builder.workspaceId);
        this.image = trimToNull(builder.image);
        this.deleteOnClose = builder.deleteOnClose;
        this.createIfMissing = builder.createIfMissing;
    }

    public static CliSandboxCommand status() {
        return builder(Action.STATUS).build();
    }

    public static CliSandboxCommand disable() {
        return builder(Action.DISABLE).build();
    }

    public static CliSandboxCommand parse(String rawArgument) {
        List<String> tokens = splitShellLike(rawArgument);
        if (tokens.isEmpty()) {
            return status();
        }
        String action = lower(tokens.get(0));
        if ("status".equals(action)) {
            ensureNoExtra(tokens, "/sandbox status");
            return status();
        }
        if ("disable".equals(action) || "off".equals(action)) {
            ensureNoExtra(tokens, "/sandbox disable");
            return disable();
        }
        if ("enable".equals(action) || "create".equals(action)) {
            return parseOpen(Action.ENABLE, tokens, 1);
        }
        if ("attach".equals(action)) {
            return parseOpen(Action.ATTACH, tokens, 1);
        }
        throw new IllegalArgumentException("Unknown /sandbox action: " + tokens.get(0)
                + ". Use /sandbox status, /sandbox enable daytona, /sandbox attach daytona <id>, or /sandbox disable.");
    }

    private static CliSandboxCommand parseOpen(Action action, List<String> tokens, int offset) {
        if (tokens.size() <= offset) {
            throw new IllegalArgumentException("Usage: " + usage(action));
        }
        Builder builder = builder(action)
                .providerId(tokens.get(offset))
                .createIfMissing(action == Action.ENABLE);
        int index = offset + 1;
        if (action == Action.ATTACH) {
            if (index >= tokens.size() || isOption(tokens.get(index))) {
                throw new IllegalArgumentException("Usage: " + usage(action));
            }
            builder.sandboxIdOrName(tokens.get(index));
            builder.createIfMissing(false);
            index++;
        }
        while (index < tokens.size()) {
            String token = tokens.get(index);
            String normalized = lower(token);
            if ("--workspace".equals(normalized) || "--sandbox-name".equals(normalized)) {
                builder.workspaceId(requireValue(tokens, ++index, token));
            } else if ("--sandbox-id".equals(normalized)) {
                builder.sandboxIdOrName(requireValue(tokens, ++index, token));
            } else if ("--image".equals(normalized) || "--snapshot".equals(normalized)) {
                builder.image(requireValue(tokens, ++index, token));
            } else if ("--delete-on-close".equals(normalized)) {
                builder.deleteOnClose(true);
            } else if ("--keep-on-close".equals(normalized)) {
                builder.deleteOnClose(false);
            } else if ("--create-if-missing".equals(normalized)) {
                builder.createIfMissing(true);
            } else if ("--no-create-if-missing".equals(normalized)) {
                builder.createIfMissing(false);
            } else {
                throw new IllegalArgumentException("Unknown /sandbox option: " + token
                        + ". Credentials must come from environment or local config, not slash-command arguments.");
            }
            index++;
        }
        return builder.build();
    }

    private static String usage(Action action) {
        if (action == Action.ATTACH) {
            return "/sandbox attach daytona <sandbox-id-or-name> [--workspace <name>] [--delete-on-close] [--create-if-missing]";
        }
        return "/sandbox enable daytona [--workspace <name>] [--image <snapshot>] [--delete-on-close] [--no-create-if-missing]";
    }

    private static void ensureNoExtra(List<String> tokens, String usage) {
        if (tokens.size() > 1) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static String requireValue(List<String> tokens, int index, String option) {
        if (index >= tokens.size() || isOption(tokens.get(index))) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return tokens.get(index);
    }

    private static boolean isOption(String token) {
        return token != null && token.startsWith("--");
    }

    static List<String> splitShellLike(String value) {
        if (trimToNull(value) == null) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        boolean escaping = false;
        String input = value.trim();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                if (inQuote || shouldEscape(input, i)) {
                    escaping = true;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (inQuote) {
                if (ch == quote) {
                    inQuote = false;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                inQuote = true;
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (escaping) {
            current.append('\\');
        }
        if (inQuote) {
            throw new IllegalArgumentException("Unclosed quote in /sandbox arguments");
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static boolean shouldEscape(String input, int index) {
        if (index + 1 >= input.length()) {
            return false;
        }
        char next = input.charAt(index + 1);
        return next == '"' || next == '\'' || next == '\\' || Character.isWhitespace(next);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static Builder builder(Action action) {
        return new Builder(action);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Action getAction() {
        return action;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getSandboxIdOrName() {
        return sandboxIdOrName;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getImage() {
        return image;
    }

    public boolean isDeleteOnClose() {
        return deleteOnClose;
    }

    public boolean isCreateIfMissing() {
        return createIfMissing;
    }

    public static final class Builder {
        private final Action action;
        private String providerId;
        private String sandboxIdOrName;
        private String workspaceId;
        private String image;
        private boolean deleteOnClose;
        private boolean createIfMissing;

        private Builder(Action action) {
            this.action = action;
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder sandboxIdOrName(String sandboxIdOrName) {
            this.sandboxIdOrName = sandboxIdOrName;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder deleteOnClose(boolean deleteOnClose) {
            this.deleteOnClose = deleteOnClose;
            return this;
        }

        public Builder createIfMissing(boolean createIfMissing) {
            this.createIfMissing = createIfMissing;
            return this;
        }

        public CliSandboxCommand build() {
            if (action == null) {
                throw new IllegalStateException("sandbox action is required");
            }
            return new CliSandboxCommand(this);
        }
    }
}
