package io.github.lnyocly.ai4j.ambient.memory;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.harness.EntityKind;
import io.github.lnyocly.ai4j.harness.FactRecord;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.HarnessFactSpec;
import io.github.lnyocly.ai4j.harness.HarnessRelationSpec;
import io.github.lnyocly.ai4j.harness.RelationRecord;
import io.github.lnyocly.ai4j.harness.RelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sleep-time memory consolidation ("Dreaming") that prunes episodic noise,
 * extracts durable facts gated by {@link MemoryProvenanceGate}, resolves semantic
 * conflicts against existing {@code ai4j-harness} facts, invalidates superseded
 * facts, and persists explicit {@link RelationType#DERIVED_FROM} lineage links.
 */
public class DreamingConsolidator {

    public static final String META_SUBJECT_KEY = "subjectKey";
    public static final String META_TRUST_LEVEL = "trustLevel";
    public static final String META_EPISODE_ID = "sourceEpisodeId";
    public static final String META_SUPERSEDED_FACT_IDS = "supersededFactIds";

    private static final Set<String> NOISE_PHRASES = new LinkedHashSet<String>(Arrays.asList(
            "hi", "hello", "hey", "ok", "okay", "k", "thx", "thanks", "thank you",
            "got it", "ping", "pong", "noop", "heartbeat_noop", "ack",
            "你好", "您好", "谢谢", "收到", "好的", "嗯", "哦", "明白了"
    ));

    private final MemoryProvenanceGate provenanceGate;
    private final FactExtractor factExtractor;
    private final SemanticConflictDetector conflictDetector;

    public DreamingConsolidator() {
        this(new MemoryProvenanceGate(), new HeuristicFactExtractor(), new DefaultConflictDetector());
    }

    public DreamingConsolidator(MemoryProvenanceGate provenanceGate) {
        this(provenanceGate, new HeuristicFactExtractor(), new DefaultConflictDetector());
    }

    public DreamingConsolidator(MemoryProvenanceGate provenanceGate,
                                FactExtractor factExtractor,
                                SemanticConflictDetector conflictDetector) {
        if (provenanceGate == null) {
            throw new IllegalArgumentException("provenanceGate must not be null");
        }
        this.provenanceGate = provenanceGate;
        this.factExtractor = factExtractor == null ? new HeuristicFactExtractor() : factExtractor;
        this.conflictDetector = conflictDetector == null ? new DefaultConflictDetector() : conflictDetector;
    }

    public MemoryProvenanceGate getProvenanceGate() {
        return provenanceGate;
    }

    /**
     * Runs one sleep-time consolidation cycle over episodic records for {@code scopeKey}
     * and persists authoritative facts and lineage into {@code gateway}.
     */
    public ConsolidationReport consolidate(String scopeKey,
                                             List<EpisodicRecord> episodes,
                                             HarnessCommandGateway gateway,
                                             HarnessActor actor) {
        String normalizedScope = AmbientSupport.trimToNull(scopeKey);
        if (normalizedScope == null) {
            normalizedScope = "default";
        }
        HarnessActor effectiveActor = actor == null ? HarnessActor.system("dreaming-consolidator") : actor;
        List<EpisodicRecord> input = episodes == null
                ? Collections.<EpisodicRecord>emptyList()
                : episodes;

        List<String> prunedNoiseIds = new ArrayList<String>();
        List<EpisodicRecord> retained = new ArrayList<EpisodicRecord>();
        Set<String> seenFingerprints = new LinkedHashSet<String>();

        for (int i = 0; i < input.size(); i++) {
            EpisodicRecord episode = input.get(i);
            if (episode == null) {
                continue;
            }
            String episodeId = AmbientSupport.trimToNull(episode.getEpisodeId());
            if (episodeId == null) {
                episodeId = "ep_" + i;
            }
            if (isNoiseEpisode(episode, seenFingerprints)) {
                prunedNoiseIds.add(episodeId);
            } else {
                retained.add(episode);
            }
        }

        List<FactCandidate> candidates = factExtractor.extract(normalizedScope, retained);
        if (candidates == null) {
            candidates = Collections.emptyList();
        }

        List<String> recordedFactIds = new ArrayList<String>();
        List<String> invalidatedFactIds = new ArrayList<String>();
        List<String> quarantinedIds = new ArrayList<String>();
        List<String> rejectedIds = new ArrayList<String>();
        List<LineageLink> lineageLinks = new ArrayList<LineageLink>();

        List<FactRecord> existingFacts = gateway == null
                ? Collections.<FactRecord>emptyList()
                : new ArrayList<FactRecord>(gateway.listFactsInScope(normalizedScope));

        for (FactCandidate candidate : candidates) {
            if (candidate == null || AmbientSupport.trimToNull(candidate.getStatement()) == null) {
                continue;
            }
            String subjectKey = resolveSubjectKey(candidate);
            TrustLevel candidateTrust = candidate.getTrustLevel() == null
                    ? TrustLevel.EXTERNAL_UNTRUSTED
                    : candidate.getTrustLevel();

            List<FactRecord> conflictingFacts = findConflictingValidFacts(existingFacts, candidate, subjectKey);
            if (candidateTrust != TrustLevel.EXTERNAL_UNTRUSTED
                    && hasOutrankingHarnessFact(conflictingFacts, candidateTrust)) {
                rejectedIds.add(subjectKey + ":outranked-in-harness");
                continue;
            }

            MemoryObservation observation = MemoryObservation.builder()
                    .observationId(candidate.getCandidateId())
                    .scopeKey(normalizedScope)
                    .subjectKey(subjectKey)
                    .statement(candidate.getStatement())
                    .trustLevel(candidateTrust)
                    .targetLayer(MemoryTargetLayer.DURABLE_FACT)
                    .sourceKind(candidate.getSourceKind())
                    .sourceId(candidate.getSourceId())
                    .observedAtEpochMs(candidate.getObservedAtEpochMs())
                    .metadata(candidate.getMetadata())
                    .build();
            ProvenanceDecision decision = provenanceGate.evaluateAndApply(observation);
            if (decision.isQuarantined()) {
                quarantinedIds.add(decision.getQuarantineId());
                continue;
            }
            if (decision.isRejected()) {
                rejectedIds.add(subjectKey);
                continue;
            }

            if (gateway == null) {
                continue;
            }

            Map<String, Object> metadata = new LinkedHashMap<String, Object>();
            if (candidate.getMetadata() != null) {
                metadata.putAll(candidate.getMetadata());
            }
            metadata.put(META_SUBJECT_KEY, subjectKey);
            metadata.put(META_TRUST_LEVEL, candidateTrust.name());
            if (candidate.getSourceEpisodeId() != null) {
                metadata.put(META_EPISODE_ID, candidate.getSourceEpisodeId());
            }
            List<String> supersededIds = new ArrayList<String>();
            for (FactRecord conflict : conflictingFacts) {
                supersededIds.add(conflict.getFactId());
            }
            if (!supersededIds.isEmpty()) {
                metadata.put(META_SUPERSEDED_FACT_IDS, supersededIds);
            }

            FactRecord recorded = gateway.recordFact(HarnessFactSpec.builder()
                    .factId(candidate.getFactId())
                    .scopeKey(normalizedScope)
                    .taskId(candidate.getTaskId())
                    .statement(candidate.getStatement())
                    .source(candidate.getSourceKind() != null ? candidate.getSourceKind() : "dreaming")
                    .confidence(candidate.getConfidence())
                    .evidenceIds(candidate.getEvidenceIds() == null
                            ? Collections.<String>emptyList()
                            : candidate.getEvidenceIds())
                    .metadata(metadata)
                    .build(), effectiveActor);
            recordedFactIds.add(recorded.getFactId());
            existingFacts.add(recorded);

            for (FactRecord oldFact : conflictingFacts) {
                if (!oldFact.isValid()) {
                    continue;
                }
                String reason = "superseded by " + recorded.getFactId() + " (" + subjectKey + ")";
                FactRecord invalidated = gateway.invalidateFact(oldFact.getFactId(), reason, effectiveActor);
                oldFact.setValid(false);
                oldFact.setInvalidatedBy(invalidated.getInvalidatedBy());
                invalidatedFactIds.add(oldFact.getFactId());

                RelationRecord relation = gateway.addRelation(HarnessRelationSpec.builder()
                        .type(RelationType.DERIVED_FROM)
                        .scopeKey(normalizedScope)
                        .fromKind(EntityKind.FACT)
                        .fromId(recorded.getFactId())
                        .toKind(EntityKind.FACT)
                        .toId(oldFact.getFactId())
                        .metadata(Collections.<String, Object>singletonMap(META_SUBJECT_KEY, subjectKey))
                        .build(), effectiveActor);

                lineageLinks.add(LineageLink.builder()
                        .newFactId(recorded.getFactId())
                        .supersededFactId(oldFact.getFactId())
                        .subjectKey(subjectKey)
                        .relationId(relation.getRelationId())
                        .reason(reason)
                        .build());
            }
        }

        return ConsolidationReport.builder()
                .scopeKey(normalizedScope)
                .totalEpisodes(input.size())
                .prunedNoiseCount(prunedNoiseIds.size())
                .prunedEpisodeIds(prunedNoiseIds)
                .retainedEpisodeCount(retained.size())
                .extractedFactCount(candidates.size())
                .recordedFactIds(recordedFactIds)
                .invalidatedFactIds(invalidatedFactIds)
                .quarantinedIds(quarantinedIds)
                .rejectedIds(rejectedIds)
                .lineageLinks(lineageLinks)
                .build();
    }

    private boolean isNoiseEpisode(EpisodicRecord episode, Set<String> seenFingerprints) {
        if (episode.isNoiseHint() || episode.isRetryNoise()) {
            return true;
        }
        String text = AmbientSupport.trimToNull(episode.getContent());
        if (text == null) {
            return true;
        }
        String normalized = normalizeNoiseCheck(text);
        if (NOISE_PHRASES.contains(normalized)) {
            return true;
        }
        // Repeated transient error retries or duplicate chatter are noise unless carrying explicit durable metadata.
        boolean hasExplicitFact = episode.getMetadata() != null
                && (episode.getMetadata().containsKey(META_SUBJECT_KEY)
                || episode.getMetadata().containsKey("factKey"));
        if (!hasExplicitFact && normalized.startsWith("retry ") && normalized.contains("failed")) {
            return true;
        }
        String fingerprint = normalized + "::" + (episode.getTrustLevel() == null ? "" : episode.getTrustLevel().name());
        return !seenFingerprints.add(fingerprint) && !hasExplicitFact;
    }

    private static String normalizeNoiseCheck(String text) {
        String lower = text.trim().toLowerCase(Locale.ROOT);
        while (!lower.isEmpty()) {
            char last = lower.charAt(lower.length() - 1);
            if (last == '.' || last == '!' || last == '?' || last == '。' || last == '！') {
                lower = lower.substring(0, lower.length() - 1).trim();
            } else {
                break;
            }
        }
        return lower;
    }

    private List<FactRecord> findConflictingValidFacts(List<FactRecord> existingFacts,
                                                       FactCandidate candidate,
                                                       String subjectKey) {
        List<FactRecord> conflicts = new ArrayList<FactRecord>();
        for (FactRecord existing : existingFacts) {
            if (existing == null || !existing.isValid()) {
                continue;
            }
            if (conflictDetector.isConflict(existing, candidate, subjectKey)) {
                conflicts.add(existing);
            }
        }
        return conflicts;
    }

    private boolean hasOutrankingHarnessFact(List<FactRecord> conflictingFacts, TrustLevel candidateTrust) {
        for (FactRecord existing : conflictingFacts) {
            TrustLevel existingTrust = parseTrustLevel(existing);
            if (!provenanceGate.canSupersede(existingTrust, candidateTrust)) {
                return true;
            }
        }
        return false;
    }

    private static TrustLevel parseTrustLevel(FactRecord fact) {
        if (fact == null || fact.getMetadata() == null) {
            return TrustLevel.VERIFIED_TOOL;
        }
        Object raw = fact.getMetadata().get(META_TRUST_LEVEL);
        if (raw == null) {
            return TrustLevel.VERIFIED_TOOL;
        }
        try {
            return TrustLevel.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TrustLevel.VERIFIED_TOOL;
        }
    }

    private static String resolveSubjectKey(FactCandidate candidate) {
        String key = AmbientSupport.trimToNull(candidate.getSubjectKey());
        if (key != null) {
            return key.toLowerCase(Locale.ROOT);
        }
        String inferred = inferSubjectKey(candidate.getStatement());
        if (inferred != null) {
            return inferred;
        }
        return "fact_" + AmbientSupport.sha256Hex(candidate.getStatement()).substring(0, 12);
    }

    static String inferSubjectKey(String statement) {
        if (statement == null) {
            return null;
        }
        int eq = statement.indexOf('=');
        int colon = statement.indexOf(':');
        int sep = -1;
        if (eq > 0 && colon > 0) {
            sep = Math.min(eq, colon);
        } else if (eq > 0) {
            sep = eq;
        } else if (colon > 0) {
            sep = colon;
        }
        if (sep > 0 && sep <= 64) {
            String prefix = statement.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            if (!prefix.isEmpty() && !prefix.contains("\n")) {
                return prefix;
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface FactExtractor {
        List<FactCandidate> extract(String scopeKey, List<EpisodicRecord> retainedEpisodes);
    }

    @FunctionalInterface
    public interface SemanticConflictDetector {
        boolean isConflict(FactRecord existing, FactCandidate candidate, String normalizedSubjectKey);
    }

    /**
     * Default deterministic extractor recognizing explicit fact metadata and structured
     * declarative lines ({@code fact:}, {@code config:}, {@code preference:}, or {@code key=value}).
     */
    public static class HeuristicFactExtractor implements FactExtractor {
        @Override
        public List<FactCandidate> extract(String scopeKey, List<EpisodicRecord> retainedEpisodes) {
            List<FactCandidate> extracted = new ArrayList<FactCandidate>();
            if (retainedEpisodes == null) {
                return extracted;
            }
            for (EpisodicRecord ep : retainedEpisodes) {
                if (ep == null || AmbientSupport.trimToNull(ep.getContent()) == null) {
                    continue;
                }
                String content = ep.getContent().trim();
                String subject = null;
                String statement = content;
                if (ep.getMetadata() != null) {
                    Object metaKey = ep.getMetadata().get(META_SUBJECT_KEY);
                    if (metaKey == null) {
                        metaKey = ep.getMetadata().get("factKey");
                    }
                    if (metaKey != null) {
                        subject = AmbientSupport.trimToNull(String.valueOf(metaKey));
                    }
                    Object metaStmt = ep.getMetadata().get("factStatement");
                    if (metaStmt != null && AmbientSupport.trimToNull(String.valueOf(metaStmt)) != null) {
                        statement = String.valueOf(metaStmt).trim();
                    }
                }
                if (subject == null) {
                    String lower = content.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("fact:")) {
                        statement = content.substring(5).trim();
                        subject = inferSubjectKey(statement);
                    } else if (lower.startsWith("config:")) {
                        statement = content.substring(7).trim();
                        subject = inferSubjectKey(statement);
                    } else if (lower.startsWith("preference:")) {
                        statement = content.substring(11).trim();
                        subject = inferSubjectKey(statement);
                    } else {
                        subject = inferSubjectKey(statement);
                    }
                }
                if (AmbientSupport.trimToNull(statement) == null) {
                    continue;
                }
                extracted.add(FactCandidate.builder()
                        .candidateId("cand_" + (ep.getEpisodeId() == null ? extracted.size() : ep.getEpisodeId()))
                        .sourceEpisodeId(ep.getEpisodeId())
                        .taskId(ep.getTaskId())
                        .subjectKey(subject)
                        .statement(statement)
                        .trustLevel(ep.getTrustLevel() == null ? TrustLevel.EXTERNAL_UNTRUSTED : ep.getTrustLevel())
                        .sourceKind(ep.getSourceKind() == null ? "episode" : ep.getSourceKind())
                        .sourceId(ep.getSourceId())
                        .confidence("high")
                        .observedAtEpochMs(ep.getTimestampEpochMs())
                        .metadata(ep.getMetadata())
                        .build());
            }
            return extracted;
        }
    }

    /**
     * Detects semantic conflict by explicit {@code supersedesFactId}, matching
     * {@code subjectKey}/{@code factKey} metadata, or matching structured {@code key=value} prefixes.
     */
    public static class DefaultConflictDetector implements SemanticConflictDetector {
        @Override
        public boolean isConflict(FactRecord existing, FactCandidate candidate, String normalizedSubjectKey) {
            if (existing == null || candidate == null) {
                return false;
            }
            if (candidate.getSupersedesFactId() != null
                    && candidate.getSupersedesFactId().equals(existing.getFactId())) {
                return true;
            }
            String existingSubject = null;
            if (existing.getMetadata() != null) {
                Object raw = existing.getMetadata().get(META_SUBJECT_KEY);
                if (raw == null) {
                    raw = existing.getMetadata().get("factKey");
                }
                if (raw != null) {
                    existingSubject = AmbientSupport.trimToNull(String.valueOf(raw));
                }
            }
            if (existingSubject == null) {
                existingSubject = inferSubjectKey(existing.getStatement());
            }
            if (existingSubject != null && normalizedSubjectKey != null
                    && existingSubject.toLowerCase(Locale.ROOT).equals(normalizedSubjectKey.toLowerCase(Locale.ROOT))) {
                return !existing.getStatement().trim().equals(candidate.getStatement().trim());
            }
            return false;
        }
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EpisodicRecord {
        private String episodeId;
        private String scopeKey;
        private String taskId;
        private String content;
        private String sourceKind;
        private String sourceId;
        @Builder.Default
        private TrustLevel trustLevel = TrustLevel.VERIFIED_TOOL;
        private boolean noiseHint;
        private boolean retryNoise;
        private long timestampEpochMs;
        @Builder.Default
        private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactCandidate {
        private String candidateId;
        private String factId;
        private String sourceEpisodeId;
        private String taskId;
        private String subjectKey;
        private String statement;
        private String supersedesFactId;
        private TrustLevel trustLevel;
        private String sourceKind;
        private String sourceId;
        private String confidence;
        private long observedAtEpochMs;
        @Builder.Default
        private List<String> evidenceIds = new ArrayList<String>();
        @Builder.Default
        private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineageLink {
        private String newFactId;
        private String supersededFactId;
        private String subjectKey;
        private String relationId;
        private String reason;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsolidationReport {
        private String scopeKey;
        private int totalEpisodes;
        private int prunedNoiseCount;
        @Builder.Default
        private List<String> prunedEpisodeIds = new ArrayList<String>();
        private int retainedEpisodeCount;
        private int extractedFactCount;
        @Builder.Default
        private List<String> recordedFactIds = new ArrayList<String>();
        @Builder.Default
        private List<String> invalidatedFactIds = new ArrayList<String>();
        @Builder.Default
        private List<String> quarantinedIds = new ArrayList<String>();
        @Builder.Default
        private List<String> rejectedIds = new ArrayList<String>();
        @Builder.Default
        private List<LineageLink> lineageLinks = new ArrayList<LineageLink>();
    }
}
