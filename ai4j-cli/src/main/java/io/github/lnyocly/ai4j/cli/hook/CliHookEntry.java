package io.github.lnyocly.ai4j.cli.hook;

/**
 * One external hook command declared by the user. {@code command} is run as a shell command
 * (Claude-Code-style); {@code match} is the tool name to match, or null/empty/"*" for all tools.
 */
public class CliHookEntry {

    private String command;
    private String match;

    public CliHookEntry() {
    }

    public CliHookEntry(String command, String match) {
        this.command = command;
        this.match = match;
    }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getMatch() { return match; }
    public void setMatch(String match) { this.match = match; }

    /** True if this entry applies to the given tool name (null/empty/"*" matches everything). */
    public boolean matches(String toolName) {
        if (match == null || match.trim().isEmpty() || "*".equals(match.trim())) {
            return true;
        }
        return match.trim().equals(toolName);
    }
}
