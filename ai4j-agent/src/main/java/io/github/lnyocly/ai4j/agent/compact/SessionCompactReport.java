package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.context.ContextReport;

/**
 * Diagnostic result returned by AgentSession compact helper APIs.
 */
public class SessionCompactReport {

    private final String sessionId;
    private final boolean compacted;
    private final CompactResult compactResult;
    private final String summary;
    private final ContextReport contextReport;

    public SessionCompactReport(String sessionId, boolean compacted, CompactResult compactResult) {
        this.sessionId = sessionId;
        this.compacted = compacted;
        this.compactResult = compactResult == null ? null : compactResult.copy();
        this.summary = compactResult == null ? null : compactResult.getSummary();
        this.contextReport = compactResult == null ? null : compactResult.getContextReport();
    }

    public static SessionCompactReport skipped(String sessionId) {
        return new SessionCompactReport(sessionId, false, null);
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isCompacted() {
        return compacted;
    }

    public CompactResult getCompactResult() {
        return compactResult == null ? null : compactResult.copy();
    }

    public String getSummary() {
        return summary;
    }

    public ContextReport getContextReport() {
        return contextReport == null ? null : contextReport.copy();
    }

    public int getSourceItemCount() {
        return contextReport == null ? 0 : contextReport.getSourceItemCount();
    }

    public int getProjectedItemCount() {
        return contextReport == null ? 0 : contextReport.getProjectedItemCount();
    }

    public int getDroppedItemCount() {
        return contextReport == null ? 0 : contextReport.getDroppedItemCount();
    }

    public boolean hasDroppedItems() {
        return getDroppedItemCount() > 0;
    }
}
