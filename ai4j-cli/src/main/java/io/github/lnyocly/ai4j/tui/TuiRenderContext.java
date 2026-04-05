package io.github.lnyocly.ai4j.tui;

public class TuiRenderContext {

    private final String provider;
    private final String protocol;
    private final String model;
    private final String workspace;
    private final String sessionStore;
    private final String sessionMode;
    private final String approvalMode;
    private final int terminalRows;
    private final int terminalColumns;

    private TuiRenderContext(Builder builder) {
        this.provider = builder.provider;
        this.protocol = builder.protocol;
        this.model = builder.model;
        this.workspace = builder.workspace;
        this.sessionStore = builder.sessionStore;
        this.sessionMode = builder.sessionMode;
        this.approvalMode = builder.approvalMode;
        this.terminalRows = builder.terminalRows;
        this.terminalColumns = builder.terminalColumns;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProvider() {
        return provider;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getModel() {
        return model;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getSessionStore() {
        return sessionStore;
    }

    public String getSessionMode() {
        return sessionMode;
    }

    public String getApprovalMode() {
        return approvalMode;
    }

    public int getTerminalRows() {
        return terminalRows;
    }

    public int getTerminalColumns() {
        return terminalColumns;
    }

    public static final class Builder {

        private String provider;
        private String protocol;
        private String model;
        private String workspace;
        private String sessionStore;
        private String sessionMode;
        private String approvalMode;
        private int terminalRows;
        private int terminalColumns;

        private Builder() {
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder workspace(String workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder sessionStore(String sessionStore) {
            this.sessionStore = sessionStore;
            return this;
        }

        public Builder sessionMode(String sessionMode) {
            this.sessionMode = sessionMode;
            return this;
        }

        public Builder approvalMode(String approvalMode) {
            this.approvalMode = approvalMode;
            return this;
        }

        public Builder terminalRows(int terminalRows) {
            this.terminalRows = terminalRows;
            return this;
        }

        public Builder terminalColumns(int terminalColumns) {
            this.terminalColumns = terminalColumns;
            return this;
        }

        public TuiRenderContext build() {
            return new TuiRenderContext(this);
        }
    }
}
