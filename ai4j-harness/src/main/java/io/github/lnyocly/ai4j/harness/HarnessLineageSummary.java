package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Generic, benchmark-neutral summary of one execution repair lineage. */
public final class HarnessLineageSummary {
    private final List<ExecutionRecord> executions;
    private final List<AcceptanceRecord> acceptances;
    private final int repairCount;
    /** Latest record across the lineage, retained for compatibility. */
    private final HarnessAcceptanceStatus finalAcceptanceStatus;
    /** Aggregate status for required checks, or the legacy final status when none are declared. */
    private final HarnessAcceptanceStatus aggregateAcceptanceStatus;
    private final boolean passed;
    private final Map<String, AcceptanceRecord> latestAcceptancesByCheck;
    private final Set<String> requiredCheckIds;
    private final Set<String> missingRequiredCheckIds;
    private final boolean requiredChecksPassed;

    public HarnessLineageSummary(List<ExecutionRecord> executions,
                                 List<AcceptanceRecord> acceptances,
                                 int repairCount,
                                 HarnessAcceptanceStatus finalAcceptanceStatus,
                                 boolean passed) {
        this(executions, acceptances, repairCount, finalAcceptanceStatus,
                finalAcceptanceStatus, passed, Collections.<String, AcceptanceRecord>emptyMap(),
                Collections.<String>emptySet(), Collections.<String>emptySet(), passed);
    }

    public HarnessLineageSummary(List<ExecutionRecord> executions,
                                 List<AcceptanceRecord> acceptances,
                                 int repairCount,
                                 HarnessAcceptanceStatus finalAcceptanceStatus,
                                 HarnessAcceptanceStatus aggregateAcceptanceStatus,
                                 boolean passed,
                                 Map<String, AcceptanceRecord> latestAcceptancesByCheck,
                                 Set<String> requiredCheckIds,
                                 Set<String> missingRequiredCheckIds,
                                 boolean requiredChecksPassed) {
        this.executions = immutableExecutions(executions);
        this.acceptances = immutableAcceptances(acceptances);
        this.repairCount = repairCount;
        this.finalAcceptanceStatus = finalAcceptanceStatus;
        this.aggregateAcceptanceStatus = aggregateAcceptanceStatus;
        this.passed = passed;
        this.latestAcceptancesByCheck = immutableAcceptanceMap(latestAcceptancesByCheck);
        this.requiredCheckIds = immutableStrings(requiredCheckIds);
        this.missingRequiredCheckIds = immutableStrings(missingRequiredCheckIds);
        this.requiredChecksPassed = requiredChecksPassed;
    }

    public List<ExecutionRecord> getExecutions() { return executions; }
    public List<AcceptanceRecord> getAcceptances() { return acceptances; }
    public int getRepairCount() { return repairCount; }
    public HarnessAcceptanceStatus getFinalAcceptanceStatus() { return finalAcceptanceStatus; }
    public HarnessAcceptanceStatus getAggregateAcceptanceStatus() { return aggregateAcceptanceStatus; }
    public boolean isPassed() { return passed; }
    public Map<String, AcceptanceRecord> getLatestAcceptancesByCheck() { return latestAcceptancesByCheck; }
    public Set<String> getRequiredCheckIds() { return requiredCheckIds; }
    public Set<String> getMissingRequiredCheckIds() { return missingRequiredCheckIds; }
    public boolean isRequiredChecksPassed() { return requiredChecksPassed; }

    private static List<ExecutionRecord> immutableExecutions(List<ExecutionRecord> source) {
        List<ExecutionRecord> copy = new ArrayList<ExecutionRecord>();
        if (source != null) for (ExecutionRecord item : source) if (item != null) copy.add(item.copy());
        return Collections.unmodifiableList(copy);
    }

    private static List<AcceptanceRecord> immutableAcceptances(List<AcceptanceRecord> source) {
        List<AcceptanceRecord> copy = new ArrayList<AcceptanceRecord>();
        if (source != null) for (AcceptanceRecord item : source) if (item != null) copy.add(item.copy());
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, AcceptanceRecord> immutableAcceptanceMap(Map<String, AcceptanceRecord> source) {
        Map<String, AcceptanceRecord> copy = new LinkedHashMap<String, AcceptanceRecord>();
        if (source != null) for (Map.Entry<String, AcceptanceRecord> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) copy.put(entry.getKey(), entry.getValue().copy());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableStrings(Set<String> source) {
        Set<String> copy = new LinkedHashSet<String>();
        if (source != null) for (String value : source) if (value != null && !value.trim().isEmpty()) copy.add(value);
        return Collections.unmodifiableSet(copy);
    }
}
