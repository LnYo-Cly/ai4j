package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;

public final class JlineCodeCommandRunner {

    private final CodingAgent agent;
    private final CliProtocol protocol;
    private final CodeCommandOptions options;
    private final TerminalIO terminal;
    private final CodingSessionManager sessionManager;
    private final TuiInteractionState interactionState;
    private final JlineShellContext shellContext;
    private final SlashCommandController slashCommandController;
    private final CodingCliAgentFactory agentFactory;
    private final java.util.Map<String, String> env;
    private final java.util.Properties properties;

    public JlineCodeCommandRunner(CodingAgent agent,
                                  CliProtocol protocol,
                                  CodeCommandOptions options,
                                  TerminalIO terminal,
                                  CodingSessionManager sessionManager,
                                  TuiInteractionState interactionState,
                                  JlineShellContext shellContext,
                                  SlashCommandController slashCommandController,
                                  CodingCliAgentFactory agentFactory,
                                  java.util.Map<String, String> env,
                                  java.util.Properties properties) {
        this.agent = agent;
        this.protocol = protocol;
        this.options = options;
        this.terminal = terminal;
        this.sessionManager = sessionManager;
        this.interactionState = interactionState;
        this.shellContext = shellContext;
        this.slashCommandController = slashCommandController;
        this.agentFactory = agentFactory;
        this.env = env;
        this.properties = properties;
    }

    public int runCommand() throws Exception {
        try {
            if (slashCommandController != null) {
                slashCommandController.setSessionManager(sessionManager);
            }
            if (terminal instanceof JlineShellTerminalIO) {
                ((JlineShellTerminalIO) terminal).updateSessionContext(null, options.getModel(), options.getWorkspace());
                ((JlineShellTerminalIO) terminal).showIdle("Enter a prompt or /command");
            }
            return new CodingCliSessionRunner(
                    agent,
                    protocol,
                    options,
                    terminal,
                    sessionManager,
                    interactionState,
                    null,
                    slashCommandController,
                    agentFactory,
                    env,
                    properties
            ).run();
        } finally {
            if (terminal != null) {
                terminal.close();
            }
            if (shellContext != null) {
                shellContext.close();
            }
        }
    }
}
