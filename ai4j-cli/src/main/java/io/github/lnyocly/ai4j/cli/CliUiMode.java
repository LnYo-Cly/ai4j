package io.github.lnyocly.ai4j.cli;

public enum CliUiMode {
    CLI("cli"),
    TUI("tui");

    private final String value;

    CliUiMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CliUiMode parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return CLI;
        }
        String normalized = value.trim().toLowerCase();
        for (CliUiMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported ui mode: " + value + ". Expected: cli, tui");
    }
}
