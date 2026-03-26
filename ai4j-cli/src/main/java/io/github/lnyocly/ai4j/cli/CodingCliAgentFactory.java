package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;

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

    final class PreparedCodingAgent {

        private final CodingAgent agent;
        private final CliProtocol protocol;

        public PreparedCodingAgent(CodingAgent agent, CliProtocol protocol) {
            this.agent = agent;
            this.protocol = protocol;
        }

        public CodingAgent getAgent() {
            return agent;
        }

        public CliProtocol getProtocol() {
            return protocol;
        }
    }
}
