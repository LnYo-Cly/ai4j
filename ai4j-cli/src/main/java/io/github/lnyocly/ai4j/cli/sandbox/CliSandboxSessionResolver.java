package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxConfig;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxConfig;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CLI-side bridge from slash-command state into live sandbox providers.
 */
public class CliSandboxSessionResolver {

    public OpenedSandboxSession open(CliSandboxCommand command, Map<String, String> env) throws SandboxException {
        if (command == null || command.getAction() == CliSandboxCommand.Action.STATUS
                || command.getAction() == CliSandboxCommand.Action.DISABLE) {
            throw new SandboxException("sandbox command does not create a session");
        }
        String providerId = normalizeProviderId(command.getProviderId());
        SandboxSpec spec = toSpec(command);
        if (CubeSandboxConfig.DEFAULT_PROVIDER_ID.equalsIgnoreCase(providerId) || "cube".equalsIgnoreCase(providerId)) {
            CubeSandboxConfig config = CubeSandboxConfig.fromEnvironment(spec, env == null ? Collections.<String, String>emptyMap() : env);
            CubeSandboxProvider provider = new CubeSandboxProvider(config);
            SandboxSession session;
            if (command.getAction() == CliSandboxCommand.Action.ATTACH && !isBlank(command.getSandboxIdOrName())) {
                session = provider.connect(command.getSandboxIdOrName(), spec);
            } else {
                session = provider.createSession(spec);
            }
            return new OpenedSandboxSession(session, CliSandboxBinding.from(session, command));
        }
        if (DaytonaSandboxConfig.DEFAULT_PROVIDER_ID.equalsIgnoreCase(providerId)) {
            DaytonaSandboxConfig config = DaytonaSandboxConfig.fromEnvironment(spec, env == null ? Collections.<String, String>emptyMap() : env);
            DaytonaSandboxProvider provider = new DaytonaSandboxProvider(config);
            SandboxSession session = provider.createSession(spec);
            return new OpenedSandboxSession(session, CliSandboxBinding.from(session, command));
        }
        throw new SandboxException("Unsupported sandbox provider: " + providerId + ". Supported providers: daytona, cubesandbox (or cube).");
    }

    private SandboxSpec toSpec(CliSandboxCommand command) {
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("createIfMissing", Boolean.valueOf(command.isCreateIfMissing()));
        config.put("deleteOnClose", Boolean.valueOf(command.isDeleteOnClose()));
        if (!isBlank(command.getSandboxIdOrName())) {
            config.put("sandboxId", command.getSandboxIdOrName());
        }
        if (!isBlank(command.getWorkspaceId())) {
            config.put("sandboxName", command.getWorkspaceId());
        }
        return SandboxSpec.builder()
                .providerId(normalizeProviderId(command.getProviderId()))
                .workspaceId(command.getWorkspaceId())
                .image(command.getImage())
                .config(config)
                .build();
    }

    private String normalizeProviderId(String providerId) {
        return isBlank(providerId)
                ? DaytonaSandboxConfig.DEFAULT_PROVIDER_ID
                : providerId.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class OpenedSandboxSession {
        private final SandboxSession session;
        private final CliSandboxBinding binding;

        public OpenedSandboxSession(SandboxSession session, CliSandboxBinding binding) {
            this.session = session;
            this.binding = binding;
        }

        public SandboxSession getSession() {
            return session;
        }

        public CliSandboxBinding getBinding() {
            return binding;
        }
    }
}
