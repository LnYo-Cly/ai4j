package io.github.lnyocly.ai4j.cli.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CliResolvedMcpConfig {

    private final Map<String, CliResolvedMcpServer> servers;
    private final List<String> enabledServerNames;
    private final List<String> pausedServerNames;
    private final List<String> unknownEnabledServerNames;

    public CliResolvedMcpConfig(Map<String, CliResolvedMcpServer> servers,
                                List<String> enabledServerNames,
                                List<String> pausedServerNames,
                                List<String> unknownEnabledServerNames) {
        this.servers = servers == null
                ? Collections.<String, CliResolvedMcpServer>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, CliResolvedMcpServer>(servers));
        this.enabledServerNames = enabledServerNames == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(enabledServerNames));
        this.pausedServerNames = pausedServerNames == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(pausedServerNames));
        this.unknownEnabledServerNames = unknownEnabledServerNames == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(unknownEnabledServerNames));
    }

    public Map<String, CliResolvedMcpServer> getServers() {
        return servers;
    }

    public List<String> getEnabledServerNames() {
        return enabledServerNames;
    }

    public List<String> getPausedServerNames() {
        return pausedServerNames;
    }

    public List<String> getUnknownEnabledServerNames() {
        return unknownEnabledServerNames;
    }
}

