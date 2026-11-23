package io.github.lnyocly.ai4j.ambient.heartbeat;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supervisory agenda and quiet-hours digest queue.
 *
 * <p>Projects two supervisory views ({@link Status#AWAITING_RULING} and
 * {@link Status#ANSWERED_PENDING_FOLLOWUP}) plus a quiet-hours digest buffer
 * ({@link Status#DIGEST_PENDING}) flushed when quiet hours end.</p>
 */
public class SupervisoryAgenda {

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    public enum Status {
        AWAITING_RULING,
        DIGEST_PENDING,
        ANSWERED_PENDING_FOLLOWUP,
        RESOLVED
    }

    private final Map<String, AgendaItem> items = new LinkedHashMap<String, AgendaItem>();

    /**
     * Routes an agenda item according to {@link QuietHoursPolicy} at {@code now}.
     * Non-critical items during quiet hours enter {@link Status#DIGEST_PENDING}.
     */
    public synchronized AgendaItem route(AgendaItem item, QuietHoursPolicy quietHours, Instant now) {
        if (item == null) {
            throw new IllegalArgumentException("agenda item must not be null");
        }
        String itemId = AmbientSupport.trimToNull(item.getItemId());
        if (itemId == null) {
            itemId = "agenda_" + UUID.randomUUID().toString().replace("-", "");
        }
        String scopeKey = AmbientSupport.trimToNull(item.getScopeKey());
        if (scopeKey == null) {
            scopeKey = "default";
        }
        Priority priority = item.getPriority() == null ? Priority.NORMAL : item.getPriority();
        boolean quiet = quietHours != null && quietHours.isQuietAt(now == null ? Instant.now() : now);
        boolean bypass = priority == Priority.CRITICAL && quietHours != null && quietHours.isBypassForCritical();

        Status targetStatus;
        if (quiet && !bypass) {
            targetStatus = Status.DIGEST_PENDING;
        } else if (item.getStatus() != null) {
            targetStatus = item.getStatus();
        } else {
            targetStatus = item.isInformationalOnly() ? Status.DIGEST_PENDING : Status.AWAITING_RULING;
        }

        long createdAt = item.getCreatedAtEpochMs() > 0L
                ? item.getCreatedAtEpochMs()
                : (now == null ? System.currentTimeMillis() : now.toEpochMilli());

        AgendaItem stored = item.toBuilder()
                .itemId(itemId)
                .scopeKey(scopeKey)
                .priority(priority)
                .status(targetStatus)
                .createdAtEpochMs(createdAt)
                .options(item.getOptions() == null
                        ? Collections.<String>emptyList()
                        : new ArrayList<String>(item.getOptions()))
                .metadata(item.getMetadata() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<String, Object>(item.getMetadata()))
                .build();
        items.put(itemId, stored);
        return stored.toBuilder().build();
    }

    /**
     * Records an owner ruling on an agenda item and moves it to
     * {@link Status#ANSWERED_PENDING_FOLLOWUP}.
     */
    public synchronized AgendaItem recordAnswer(String itemId,
                                                String chosenOption,
                                                String notes,
                                                String answeredBy,
                                                Instant answeredAt) {
        String id = AmbientSupport.requireNonBlank(itemId, "agenda item id");
        AgendaItem existing = items.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("agenda item not found: " + id);
        }
        long ts = answeredAt == null ? System.currentTimeMillis() : answeredAt.toEpochMilli();
        AgendaItem updated = existing.toBuilder()
                .status(Status.ANSWERED_PENDING_FOLLOWUP)
                .chosenOption(chosenOption)
                .answerNotes(notes)
                .answeredBy(answeredBy)
                .answeredAtEpochMs(ts)
                .build();
        items.put(id, updated);
        return updated.toBuilder().build();
    }

    public synchronized AgendaItem markFollowUpCompleted(String itemId) {
        String id = AmbientSupport.requireNonBlank(itemId, "agenda item id");
        AgendaItem existing = items.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("agenda item not found: " + id);
        }
        AgendaItem updated = existing.toBuilder()
                .status(Status.RESOLVED)
                .build();
        items.put(id, updated);
        return updated.toBuilder().build();
    }

    /**
     * Flushes all {@link Status#DIGEST_PENDING} items for {@code scopeKey} (or all scopes
     * when {@code scopeKey} is null) into a single digest bundle. Actionable items move
     * to {@link Status#AWAITING_RULING}; informational items move to {@link Status#RESOLVED}.
     */
    public synchronized AgendaDigest flushDigest(String scopeKey, Instant now) {
        String normalizedScope = AmbientSupport.trimToNull(scopeKey);
        List<AgendaItem> flushed = new ArrayList<AgendaItem>();
        for (Map.Entry<String, AgendaItem> entry : items.entrySet()) {
            AgendaItem item = entry.getValue();
            if (item.getStatus() != Status.DIGEST_PENDING) {
                continue;
            }
            if (normalizedScope != null && !normalizedScope.equals(item.getScopeKey())) {
                continue;
            }
            Status nextStatus = item.isInformationalOnly() ? Status.RESOLVED : Status.AWAITING_RULING;
            AgendaItem promoted = item.toBuilder().status(nextStatus).build();
            entry.setValue(promoted);
            flushed.add(promoted.toBuilder().build());
        }
        StringBuilder summary = new StringBuilder();
        summary.append("Digest (").append(flushed.size()).append(" item(s))");
        for (AgendaItem item : flushed) {
            summary.append("\n- [").append(item.getPriority()).append("] ")
                    .append(item.getTitle() != null ? item.getTitle() : item.getItemId())
                    .append(": ").append(item.getSummary());
        }
        return AgendaDigest.builder()
                .digestId("digest_" + UUID.randomUUID().toString().replace("-", ""))
                .scopeKey(normalizedScope == null ? "all" : normalizedScope)
                .generatedAtEpochMs(now == null ? System.currentTimeMillis() : now.toEpochMilli())
                .items(Collections.unmodifiableList(flushed))
                .summaryText(summary.toString())
                .build();
    }

    public synchronized AgendaItem getItem(String itemId) {
        AgendaItem item = items.get(itemId);
        return item == null ? null : item.toBuilder().build();
    }

    public synchronized List<AgendaItem> awaitingRuling(String scopeKey) {
        return filterByStatus(scopeKey, Status.AWAITING_RULING);
    }

    public synchronized List<AgendaItem> pendingDigest(String scopeKey) {
        return filterByStatus(scopeKey, Status.DIGEST_PENDING);
    }

    public synchronized List<AgendaItem> answeredPendingFollowUp(String scopeKey) {
        return filterByStatus(scopeKey, Status.ANSWERED_PENDING_FOLLOWUP);
    }

    private List<AgendaItem> filterByStatus(String scopeKey, Status status) {
        String normalizedScope = AmbientSupport.trimToNull(scopeKey);
        List<AgendaItem> result = new ArrayList<AgendaItem>();
        for (AgendaItem item : items.values()) {
            if (item.getStatus() == status
                    && (normalizedScope == null || normalizedScope.equals(item.getScopeKey()))) {
                result.add(item.toBuilder().build());
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgendaItem {
        private String itemId;
        private String scopeKey;
        private String waitId;
        private String taskId;
        private String executionId;
        private String source;
        private String title;
        private String summary;
        @Builder.Default
        private Priority priority = Priority.NORMAL;
        private boolean informationalOnly;
        @Builder.Default
        private List<String> options = new ArrayList<String>();
        private String recommendedOption;
        private Status status;
        private long createdAtEpochMs;
        private long answeredAtEpochMs;
        private String chosenOption;
        private String answerNotes;
        private String answeredBy;
        @Builder.Default
        private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgendaDigest {
        private String digestId;
        private String scopeKey;
        private long generatedAtEpochMs;
        @Builder.Default
        private List<AgendaItem> items = new ArrayList<AgendaItem>();
        private String summaryText;
    }
}
