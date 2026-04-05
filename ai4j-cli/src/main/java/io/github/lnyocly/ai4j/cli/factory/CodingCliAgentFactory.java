package io.github.lnyocly.ai4j.cli.factory;

import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;

import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;

import java.util.Collection;

public interface CodingCliAgentFactory {

    PreparedCodingAgent prepare(CodeCommandOptions options) throws Exception;

    default PreparedCodingAgent prepare(CodeCommandOptions options, TerminalIO terminal) throws Exception {
        return prepare(options);
    }

    default PreparedCodingAgent prepare(CodeCommandOptions options,
                                        TerminalIO terminal,
                                        TuiInteractionState interactionState) throws Exception {
        return prepare(options, terminal);
    }

    default PreparedCodingAgent prepare(CodeCommandOptions options,
                                        TerminalIO terminal,
                                        TuiInteractionState interactionState,
                                        Collection<String> pausedMcpServers) throws Exception {
        return prepare(options, terminal, interactionState);
    }

    final class PreparedCodingAgent {

        private final CodingAgent agent;
        private final CliProtocol protocol;
        private final CliMcpRuntimeManager mcpRuntimeManager;

        public PreparedCodingAgent(CodingAgent agent, CliProtocol protocol) {
            this(agent, protocol, null);
        }

        public PreparedCodingAgent(CodingAgent agent,
                                   CliProtocol protocol,
                                   CliMcpRuntimeManager mcpRuntimeManager) {
            this.agent = agent;
            this.protocol = protocol;
            this.mcpRuntimeManager = mcpRuntimeManager;
        }

        public CodingAgent getAgent() {
            return agent;
        }

        public CliProtocol getProtocol() {
            return protocol;
        }

        public CliMcpRuntimeManager getMcpRuntimeManager() {
            return mcpRuntimeManager;
        }
    }
}

