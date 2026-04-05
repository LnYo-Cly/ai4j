package io.github.lnyocly.ai4j.cli.acp;

import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.factory.DefaultCodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig;
import io.github.lnyocly.ai4j.coding.tool.ToolExecutorDecorator;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;

import java.util.Collection;

final class AcpCodingCliAgentFactory extends DefaultCodingCliAgentFactory {

    private final AcpToolApprovalDecorator.PermissionGateway permissionGateway;
    private final CliResolvedMcpConfig resolvedMcpConfig;

    AcpCodingCliAgentFactory(AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                             CliResolvedMcpConfig resolvedMcpConfig) {
        this.permissionGateway = permissionGateway;
        this.resolvedMcpConfig = resolvedMcpConfig;
    }

    @Override
    protected CliMcpRuntimeManager prepareMcpRuntime(CodeCommandOptions options,
                                                     Collection<String> pausedMcpServers,
                                                     TerminalIO terminal) {
        if (resolvedMcpConfig == null) {
            return super.prepareMcpRuntime(options, pausedMcpServers, terminal);
        }
        try {
            return CliMcpRuntimeManager.initialize(resolvedMcpConfig);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    protected ToolExecutorDecorator createToolExecutorDecorator(CodeCommandOptions options,
                                                                TerminalIO terminal,
                                                                TuiInteractionState interactionState) {
        return new AcpToolApprovalDecorator(
                options == null ? ApprovalMode.AUTO : options.getApprovalMode(),
                permissionGateway
        );
    }
}
