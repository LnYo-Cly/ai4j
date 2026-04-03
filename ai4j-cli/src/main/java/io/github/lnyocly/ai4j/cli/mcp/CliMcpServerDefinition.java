package io.github.lnyocly.ai4j.cli.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CliMcpServerDefinition {

    private String type;
    private String url;
    private String command;
    private List<String> args;
    private Map<String, String> env;
    private String cwd;
    private Map<String, String> headers;

    public CliMcpServerDefinition() {
    }

    public CliMcpServerDefinition(String type,
                                  String url,
                                  String command,
                                  List<String> args,
                                  Map<String, String> env,
                                  String cwd,
                                  Map<String, String> headers) {
        this.type = type;
        this.url = url;
        this.command = command;
        this.args = copyList(args);
        this.env = copyMap(env);
        this.cwd = cwd;
        this.headers = copyMap(headers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .type(type)
                .url(url)
                .command(command)
                .args(args)
                .env(env)
                .cwd(cwd)
                .headers(headers);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return copyList(args);
    }

    public void setArgs(List<String> args) {
        this.args = copyList(args);
    }

    public Map<String, String> getEnv() {
        return copyMap(env);
    }

    public void setEnv(Map<String, String> env) {
        this.env = copyMap(env);
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public Map<String, String> getHeaders() {
        return copyMap(headers);
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = copyMap(headers);
    }

    private List<String> copyList(List<String> values) {
        return values == null ? null : new ArrayList<String>(values);
    }

    private Map<String, String> copyMap(Map<String, String> values) {
        return values == null ? null : new LinkedHashMap<String, String>(values);
    }

    public static final class Builder {

        private String type;
        private String url;
        private String command;
        private List<String> args;
        private Map<String, String> env;
        private String cwd;
        private Map<String, String> headers;

        private Builder() {
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder args(List<String> args) {
            this.args = args == null ? null : new ArrayList<String>(args);
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env == null ? null : new LinkedHashMap<String, String>(env);
            return this;
        }

        public Builder cwd(String cwd) {
            this.cwd = cwd;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers == null ? null : new LinkedHashMap<String, String>(headers);
            return this;
        }

        public CliMcpServerDefinition build() {
            return new CliMcpServerDefinition(type, url, command, args, env, cwd, headers);
        }
    }
}
