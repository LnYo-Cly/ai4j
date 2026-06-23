package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daytona toolbox command execution request.
 */
public final class DaytonaExecuteRequest {

    private String command;
    private String cwd;
    private String stdin;
    private Map<String, String> envs;
    private Integer timeout;

    public DaytonaExecuteRequest() {
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }

    public Map<String, String> getEnvs() {
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (envs != null) {
            copy.putAll(envs);
        }
        return copy;
    }

    public void setEnvs(Map<String, String> envs) {
        this.envs = new LinkedHashMap<String, String>();
        if (envs != null) {
            this.envs.putAll(envs);
        }
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}
