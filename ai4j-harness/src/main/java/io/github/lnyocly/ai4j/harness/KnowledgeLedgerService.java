package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collaborator managing causal {@link ReadSetEntry} tracking & staleness enforcement,
 * fact supersession, falsifiable decisions & reckoning, versioned agent blueprints,
 * and typed domain artifacts / immutable fact types.
 */
public final class KnowledgeLedgerService {

    public static final String ERROR_STALE_READ_SET = "STALE_READ_SET";

    private final HarnessStore store;
    private final HarnessContract contract;
    private final HarnessEventJournal eventJournal;
    private final HarnessActor defaultActor;
    private final VerticalKindRegistry verticalKinds;
    private final FactTypeRegistry factTypes;

    public KnowledgeLedgerService(HarnessStore store,
                                  HarnessContract contract,
                                  HarnessEventJournal eventJournal,
                                  HarnessActor defaultActor) {
        this.store = store;
        this.contract = contract == null ? HarnessContract.builder().build() : contract;
        this.eventJournal = eventJournal;
        this.defaultActor = HarnessSupport.normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
        this.verticalKinds = new VerticalKindRegistry();
        this.factTypes = new FactTypeRegistry();
    }

    public VerticalKindRegistry getVerticalKinds() {
        return verticalKinds;
    }

    public FactTypeRegistry getFactTypes() {
        return factTypes;
    }

    // ---- ReadSet Causal Freshness ----

    public ReadSetEntry recordFactRead(String executionId, String factId, HarnessActor actor) {
        final String execKey = HarnessSupport.requireText(executionId, "execution id");
        final String factKey = HarnessSupport.requireText(factId, "fact id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ReadSetEntry>() {
            @Override
            public ReadSetEntry apply(HarnessState state) {
                ExecutionRecord execution = requireActiveExecution(state, execKey);
                FactRecord fact = state.getFacts().get(factKey);
                if (fact == null) {
                    throw new HarnessValidationException("fact not found: " + factKey);
                }
                HarnessSupport.assertCompatibleScope(execution.getScopeKey(), fact.getScopeKey(),
                        "read-set fact and execution scopes must match");
                long now = HarnessSupport.now();
                String readId = readSetKey(execKey, EntityKind.FACT, factKey);
                ReadSetEntry entry = ReadSetEntry.builder()
                        .readId(readId)
                        .executionId(execKey)
                        .taskId(execution.getTaskId())
                        .scopeKey(execution.getScopeKey())
                        .entityKind(EntityKind.FACT)
                        .entityId(factKey)
                        .observedVersion(Math.max(1L, fact.getVersion()))
                        .observedValid(fact.isValid())
                        .observedSuperseded(fact.getSupersededByFactId() != null)
                        .observedStatus(fact.isValid() ? "VALID" : "INVALIDATED")
                        .observedDigest(HarnessDigests.computeFactDigest(fact))
                        .readAtEpochMs(now)
                        .build();
                state.getReadSets().put(readId, entry);
                eventJournal.addEvent(state, "readset.fact_recorded", readId, effectiveActor,
                        HarnessSupport.mapOf("executionId", execKey, "factId", factKey,
                                "observedVersion", entry.getObservedVersion(),
                                "observedValid", entry.isObservedValid()));
                return entry.copy();
            }
        });
    }

    public ReadSetEntry recordDecisionRead(String executionId, String decisionId, HarnessActor actor) {
        final String execKey = HarnessSupport.requireText(executionId, "execution id");
        final String decisionKey = HarnessSupport.requireText(decisionId, "decision id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ReadSetEntry>() {
            @Override
            public ReadSetEntry apply(HarnessState state) {
                ExecutionRecord execution = requireActiveExecution(state, execKey);
                DecisionRecord decision = state.getDecisions().get(decisionKey);
                if (decision == null) {
                    throw new HarnessValidationException("decision not found: " + decisionKey);
                }
                HarnessSupport.assertCompatibleScope(execution.getScopeKey(), decision.getScopeKey(),
                        "read-set decision and execution scopes must match");
                long now = HarnessSupport.now();
                String readId = readSetKey(execKey, EntityKind.DECISION, decisionKey);
                ReadSetEntry entry = ReadSetEntry.builder()
                        .readId(readId)
                        .executionId(execKey)
                        .taskId(execution.getTaskId())
                        .scopeKey(execution.getScopeKey())
                        .entityKind(EntityKind.DECISION)
                        .entityId(decisionKey)
                        .observedVersion(Math.max(1L, decision.getVersion()))
                        .observedValid(decision.getStatus() != DecisionStatus.FALSIFIED
                                && decision.getStatus() != DecisionStatus.REJECTED)
                        .observedSuperseded(decision.getStatus() == DecisionStatus.SUPERSEDED)
                        .observedStatus(decision.getStatus() == null ? null : decision.getStatus().name())
                        .observedDigest(HarnessDigests.computeDecisionDigest(decision))
                        .readAtEpochMs(now)
                        .build();
                state.getReadSets().put(readId, entry);
                eventJournal.addEvent(state, "readset.decision_recorded", readId, effectiveActor,
                        HarnessSupport.mapOf("executionId", execKey, "decisionId", decisionKey,
                                "observedVersion", entry.getObservedVersion(),
                                "observedStatus", entry.getObservedStatus()));
                return entry.copy();
            }
        });
    }

    public List<ReadSetEntry> reconfirmReadSet(String executionId,
                                               List<String> entityIds,
                                               String note,
                                               HarnessActor actor) {
        final String execKey = HarnessSupport.requireText(executionId, "execution id");
        final Set<String> targetIds = new LinkedHashSet<String>();
        if (entityIds != null) {
            for (String entityId : entityIds) {
                String normalized = HarnessSupport.trimToNull(entityId);
                if (normalized != null) {
                    targetIds.add(normalized);
                }
            }
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<List<ReadSetEntry>>() {
            @Override
            public List<ReadSetEntry> apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, execKey);
                if (ExecutionStatus.CANCELLED.equals(execution.getStatus())) {
                    throw new HarnessConflictException("cannot reconfirm read-set on cancelled execution: " + execKey);
                }
                long now = HarnessSupport.now();
                List<ReadSetEntry> updated = new ArrayList<ReadSetEntry>();
                for (ReadSetEntry entry : state.getReadSets().values()) {
                    if (entry == null || !execKey.equals(entry.getExecutionId())) {
                        continue;
                    }
                    if (!targetIds.isEmpty() && !targetIds.contains(entry.getEntityId())) {
                        continue;
                    }
                    if (entry.getEntityKind() == EntityKind.FACT) {
                        FactRecord fact = state.getFacts().get(entry.getEntityId());
                        if (fact == null) {
                            throw new HarnessValidationException("cannot reconfirm missing fact: " + entry.getEntityId());
                        }
                        long currentVersion = Math.max(1L, fact.getVersion());
                        entry.setObservedVersion(currentVersion);
                        entry.setObservedValid(fact.isValid());
                        entry.setObservedSuperseded(fact.getSupersededByFactId() != null);
                        entry.setObservedStatus(fact.isValid() ? "VALID" : "INVALIDATED");
                        entry.setObservedDigest(HarnessDigests.computeFactDigest(fact));
                        entry.setReconfirmedVersion(Long.valueOf(currentVersion));
                    } else if (entry.getEntityKind() == EntityKind.DECISION) {
                        DecisionRecord decision = state.getDecisions().get(entry.getEntityId());
                        if (decision == null) {
                            throw new HarnessValidationException("cannot reconfirm missing decision: " + entry.getEntityId());
                        }
                        long currentVersion = Math.max(1L, decision.getVersion());
                        entry.setObservedVersion(currentVersion);
                        entry.setObservedValid(decision.getStatus() != DecisionStatus.FALSIFIED
                                && decision.getStatus() != DecisionStatus.REJECTED);
                        entry.setObservedSuperseded(decision.getStatus() == DecisionStatus.SUPERSEDED);
                        entry.setObservedStatus(decision.getStatus() == null ? null : decision.getStatus().name());
                        entry.setObservedDigest(HarnessDigests.computeDecisionDigest(decision));
                        entry.setReconfirmedVersion(Long.valueOf(currentVersion));
                    }
                    entry.setReconfirmedAtEpochMs(now);
                    entry.setReconfirmedBy(HarnessSupport.copyActor(effectiveActor));
                    entry.setReconfirmNote(note);
                    updated.add(entry.copy());
                    eventJournal.addEvent(state, "readset.reconfirmed", entry.getReadId(), effectiveActor,
                            HarnessSupport.mapOf("executionId", execKey,
                                    "entityId", entry.getEntityId(),
                                    "reconfirmedVersion", entry.getReconfirmedVersion(),
                                    "note", note));
                }
                return updated;
            }
        });
    }

    public List<ReadSetEntry> listReadSet(String executionId) {
        String execKey = HarnessSupport.requireText(executionId, "execution id");
        HarnessState state = store.load();
        if (state == null || state.getReadSets() == null) {
            return Collections.emptyList();
        }
        List<ReadSetEntry> result = new ArrayList<ReadSetEntry>();
        for (ReadSetEntry entry : state.getReadSets().values()) {
            if (entry != null && execKey.equals(entry.getExecutionId())) {
                result.add(entry.copy());
            }
        }
        return result;
    }

    public static void assertReadSetFresh(HarnessState state, String executionId) {
        List<StaleReadDetail> stale = findStaleReadEntries(state, executionId);
        if (stale.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder(ERROR_STALE_READ_SET).append(": execution ")
                .append(executionId).append(" read stale causal state [");
        for (int i = 0; i < stale.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(stale.get(i).getDescription());
        }
        sb.append("]");
        throw new HarnessConflictException(ERROR_STALE_READ_SET, sb.toString());
    }

    public static List<StaleReadDetail> findStaleReadEntries(HarnessState state, String executionId) {
        if (state == null || executionId == null || state.getReadSets() == null) {
            return Collections.emptyList();
        }
        List<StaleReadDetail> stale = new ArrayList<StaleReadDetail>();
        for (ReadSetEntry entry : state.getReadSets().values()) {
            if (entry == null || !executionId.equals(entry.getExecutionId())) {
                continue;
            }
            String reason = stalenessReason(state, entry);
            if (reason != null) {
                stale.add(new StaleReadDetail(entry.copy(), reason));
            }
        }
        return stale;
    }

    private static String stalenessReason(HarnessState state, ReadSetEntry entry) {
        if (entry.getEntityKind() == EntityKind.FACT) {
            FactRecord current = state.getFacts().get(entry.getEntityId());
            if (current == null) {
                return "fact missing after read: " + entry.getEntityId();
            }
            long currentVersion = Math.max(1L, current.getVersion());
            if (currentVersion != entry.effectiveObservedVersion()) {
                return "fact " + entry.getEntityId() + " changed from v"
                        + entry.effectiveObservedVersion() + " to v" + currentVersion;
            }
            if (!current.isValid() && entry.isObservedValid()) {
                return "fact " + entry.getEntityId() + " was invalidated after read";
            }
            if (current.getSupersededByFactId() != null && !entry.isObservedSuperseded()) {
                return "fact " + entry.getEntityId() + " was superseded by " + current.getSupersededByFactId();
            }
            if (!HarnessSupport.safeEquals(entry.getObservedDigest(), HarnessDigests.computeFactDigest(current))) {
                return "fact " + entry.getEntityId() + " digest changed after read";
            }
            return null;
        }
        if (entry.getEntityKind() == EntityKind.DECISION) {
            DecisionRecord current = state.getDecisions().get(entry.getEntityId());
            if (current == null) {
                return "decision missing after read: " + entry.getEntityId();
            }
            long currentVersion = Math.max(1L, current.getVersion());
            if (currentVersion != entry.effectiveObservedVersion()) {
                return "decision " + entry.getEntityId() + " amended from v"
                        + entry.effectiveObservedVersion() + " to v" + currentVersion;
            }
            if (current.getAmendedAtEpochMs() > entry.effectiveObservedEpochMs()) {
                return "decision " + entry.getEntityId() + " was amended after read";
            }
            if (!HarnessSupport.safeEquals(entry.getObservedDigest(), HarnessDigests.computeDecisionDigest(current))) {
                return "decision " + entry.getEntityId() + " digest changed after read";
            }
            return null;
        }
        return null;
    }

    // ---- Fact Supersession & Typed Facts / Vertical Artifacts ----

    public FactRecord supersedeFact(String oldFactId,
                                    HarnessFactSpec newSpec,
                                    String reason,
                                    HarnessActor actor) {
        final String oldId = HarnessSupport.requireText(oldFactId, "old fact id");
        if (newSpec == null) {
            throw new HarnessValidationException("replacement fact specification is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent() && !contract.acceptsAgentFact(newSpec)) {
            throw new HarnessValidationException("the Harness contract rejected this Agent fact");
        }
        final String newId = HarnessSupport.valueOrGenerated(newSpec.getFactId(), "fact_");
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<FactRecord>() {
            @Override
            public FactRecord apply(HarnessState state) {
                FactRecord oldFact = state.getFacts().get(oldId);
                if (oldFact == null) {
                    throw new HarnessValidationException("fact not found: " + oldId);
                }
                if (state.getFacts().containsKey(newId)) {
                    throw new HarnessConflictException("replacement fact already exists: " + newId);
                }
                String targetScope = HarnessSupport.normalizeScope(newSpec.getScopeKey());
                HarnessSupport.assertCompatibleScope(targetScope, oldFact.getScopeKey(),
                        "superseding fact and original fact scopes must match");
                if (targetScope == null) {
                    targetScope = oldFact.getScopeKey();
                }
                String taskId = HarnessSupport.trimToNull(newSpec.getTaskId());
                if (taskId == null) {
                    taskId = oldFact.getTaskId();
                }
                long now = HarnessSupport.now();
                oldFact.setValid(false);
                oldFact.setSupersededByFactId(newId);
                oldFact.setInvalidatedAtEpochMs(now);
                oldFact.setInvalidatedBy(HarnessSupport.actorKey(effectiveActor, defaultActor)
                        + (reason == null ? ": superseded by " + newId : ": " + reason));
                oldFact.setVersion(Math.max(1L, oldFact.getVersion()) + 1L);

                FactRecord replacement = FactRecord.builder()
                        .factId(newId)
                        .scopeKey(targetScope)
                        .taskId(taskId)
                        .statement(HarnessSupport.requireText(newSpec.getStatement(), "fact statement"))
                        .source(newSpec.getSource())
                        .confidence(newSpec.getConfidence())
                        .valid(true)
                        .version(1L)
                        .supersedesFactId(oldId)
                        .factTypeId(newSpec.getFactTypeId())
                        .factTypeVersion(newSpec.getFactTypeVersion())
                        .typedPayloadJson(newSpec.getTypedPayloadJson())
                        .createdAtEpochMs(now)
                        .provenance(HarnessSupport.provenance(effectiveActor, defaultActor, null, null, "fact", newId))
                        .evidenceIds(HarnessSupport.copyList(newSpec.getEvidenceIds()))
                        .metadata(HarnessSupport.copyMap(newSpec.getMetadata()))
                        .build();
                state.getFacts().put(newId, replacement);

                String relId = HarnessSupport.valueOrGenerated(null, "rel_");
                RelationRecord rel = RelationRecord.builder()
                        .relationId(relId)
                        .scopeKey(targetScope)
                        .type(RelationType.DERIVED_FROM)
                        .fromKind(EntityKind.FACT)
                        .fromId(newId)
                        .toKind(EntityKind.FACT)
                        .toId(oldId)
                        .createdBy(HarnessSupport.actorKey(effectiveActor, defaultActor))
                        .createdAtEpochMs(now)
                        .metadata(HarnessSupport.mapOf("supersession", true, "reason", reason))
                        .build();
                state.getRelations().put(relId, rel);
                eventJournal.addEvent(state, "fact.superseded", oldId, effectiveActor,
                        HarnessSupport.mapOf("supersededByFactId", newId, "reason", reason));
                eventJournal.addEvent(state, "fact.recorded", newId, effectiveActor,
                        HarnessSupport.mapOf("supersedesFactId", oldId, "statement", replacement.getStatement()));
                return replacement.copy();
            }
        });
    }

    public <T> TypedArtifactRecord recordTypedArtifact(VerticalKind<T> kind,
                                                       String taskId,
                                                       String executionId,
                                                       String scopeKey,
                                                       T payload,
                                                       HarnessActor actor) {
        if (kind == null) {
            throw new HarnessValidationException("vertical kind is required");
        }
        verticalKinds.register(kind);
        kind.validate(payload);
        final String payloadJson = HarnessJson.toJson(payload);
        final String digest = HarnessDigests.sha256Hex(kind.schemaKey() + ":" + payloadJson);
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String artifactId = HarnessSupport.valueOrGenerated(null, "art_");
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<TypedArtifactRecord>() {
            @Override
            public TypedArtifactRecord apply(HarnessState state) {
                String resolvedScope = HarnessSupport.normalizeScope(scopeKey);
                String tId = HarnessSupport.trimToNull(taskId);
                String eId = HarnessSupport.trimToNull(executionId);
                if (tId != null) {
                    TaskRecord task = HarnessSupport.requireTask(state, tId);
                    HarnessSupport.assertCompatibleScope(resolvedScope, task.getScopeKey(),
                            "typed artifact and task scopes must match");
                    if (resolvedScope == null) {
                        resolvedScope = task.getScopeKey();
                    }
                }
                if (eId != null) {
                    ExecutionRecord exe = HarnessSupport.requireExecution(state, eId);
                    HarnessSupport.assertCompatibleScope(resolvedScope, exe.getScopeKey(),
                            "typed artifact and execution scopes must match");
                    if (resolvedScope == null) {
                        resolvedScope = exe.getScopeKey();
                    }
                }
                TypedArtifactRecord record = TypedArtifactRecord.builder()
                        .artifactId(artifactId)
                        .scopeKey(resolvedScope)
                        .taskId(tId)
                        .executionId(eId)
                        .kindId(kind.getKindId())
                        .schemaVersion(kind.getSchemaVersion())
                        .payloadClass(kind.getPayloadType().getName())
                        .payloadJson(payloadJson)
                        .digest(digest)
                        .createdBy(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(HarnessSupport.now())
                        .build();
                state.getTypedArtifacts().put(artifactId, record);
                eventJournal.addEvent(state, "artifact.recorded", artifactId, effectiveActor,
                        HarnessSupport.mapOf("kindId", kind.getKindId(), "schemaVersion", kind.getSchemaVersion(),
                                "digest", digest));
                return record.copy();
            }
        });
    }

    public <T> T readTypedArtifact(VerticalKind<T> kind, String artifactId) {
        if (kind == null) {
            throw new HarnessValidationException("vertical kind is required");
        }
        String id = HarnessSupport.requireText(artifactId, "artifact id");
        HarnessState state = store.load();
        TypedArtifactRecord record = state == null ? null : state.getTypedArtifacts().get(id);
        if (record == null) {
            throw new HarnessValidationException("typed artifact not found: " + id);
        }
        if (!kind.getKindId().equals(record.getKindId()) || kind.getSchemaVersion() != record.getSchemaVersion()) {
            throw new HarnessConflictException("typed artifact schema mismatch: expected "
                    + kind.schemaKey() + " but got " + record.getKindId() + "@v" + record.getSchemaVersion());
        }
        T decoded = record.decode(kind.getPayloadType());
        kind.validate(decoded);
        return decoded;
    }

    public <T> FactRecord recordTypedFact(FactType<T> factType,
                                          String taskId,
                                          String scopeKey,
                                          String statement,
                                          T payload,
                                          String source,
                                          List<String> evidenceIds,
                                          HarnessActor actor) {
        if (factType == null) {
            throw new HarnessValidationException("factType is required");
        }
        factTypes.register(factType);
        final String typedJson = factType.encodeAndValidate(payload);
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        HarnessFactSpec spec = HarnessFactSpec.builder()
                .scopeKey(scopeKey)
                .taskId(taskId)
                .statement(HarnessSupport.requireText(statement, "fact statement"))
                .source(source)
                .factTypeId(factType.getTypeId())
                .factTypeVersion(factType.getSchemaVersion())
                .typedPayloadJson(typedJson)
                .evidenceIds(HarnessSupport.copyList(evidenceIds))
                .build();
        if (effectiveActor.isAgent() && !contract.acceptsAgentFact(spec)) {
            throw new HarnessValidationException("the Harness contract rejected this Agent fact");
        }
        final String factId = HarnessSupport.valueOrGenerated(null, "fact_");
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<FactRecord>() {
            @Override
            public FactRecord apply(HarnessState state) {
                String resolvedScope = HarnessSupport.normalizeScope(spec.getScopeKey());
                String tId = HarnessSupport.trimToNull(spec.getTaskId());
                if (tId != null) {
                    TaskRecord task = HarnessSupport.requireTask(state, tId);
                    HarnessSupport.assertCompatibleScope(resolvedScope, task.getScopeKey(),
                            "fact and task scopes must match");
                    if (resolvedScope == null) {
                        resolvedScope = task.getScopeKey();
                    }
                }
                FactRecord fact = FactRecord.builder()
                        .factId(factId)
                        .scopeKey(resolvedScope)
                        .taskId(tId)
                        .statement(spec.getStatement())
                        .source(spec.getSource())
                        .valid(true)
                        .version(1L)
                        .factTypeId(factType.getTypeId())
                        .factTypeVersion(factType.getSchemaVersion())
                        .typedPayloadJson(typedJson)
                        .createdAtEpochMs(HarnessSupport.now())
                        .provenance(HarnessSupport.provenance(effectiveActor, defaultActor, null, null, "fact", factId))
                        .evidenceIds(HarnessSupport.copyList(spec.getEvidenceIds()))
                        .build();
                state.getFacts().put(factId, fact);
                eventJournal.addEvent(state, "fact.recorded", factId, effectiveActor,
                        HarnessSupport.mapOf("factType", factType.schemaKey(), "statement", fact.getStatement()));
                return fact.copy();
            }
        });
    }

    public <T> T readTypedFactPayload(FactType<T> factType, String factId) {
        if (factType == null) {
            throw new HarnessValidationException("factType is required");
        }
        String id = HarnessSupport.requireText(factId, "fact id");
        HarnessState state = store.load();
        FactRecord fact = state == null ? null : state.getFacts().get(id);
        if (fact == null) {
            throw new HarnessValidationException("fact not found: " + id);
        }
        if (!factType.getTypeId().equals(fact.getFactTypeId())
                || factType.getSchemaVersion() != fact.getFactTypeVersion()) {
            throw new HarnessConflictException("fact schema mismatch for " + id);
        }
        return HarnessJson.fromJson(fact.getTypedPayloadJson(), factType.getPayloadType());
    }

    // ---- Falsifiable Decisions & Reckoning ----

    public DecisionRecord amendDecision(String decisionId,
                                        HarnessDecisionSpec amendment,
                                        String reason,
                                        HarnessActor actor) {
        final String id = HarnessSupport.requireText(decisionId, "decision id");
        if (amendment == null) {
            throw new HarnessValidationException("decision amendment specification is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException("an Agent cannot amend an existing decision directly");
        }
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<DecisionRecord>() {
            @Override
            public DecisionRecord apply(HarnessState state) {
                DecisionRecord decision = state.getDecisions().get(id);
                if (decision == null) {
                    throw new HarnessValidationException("decision not found: " + id);
                }
                if (HarnessSupport.trimToNull(amendment.getChosenOption()) != null) {
                    decision.setChosenOption(amendment.getChosenOption().trim());
                }
                if (amendment.getRejected() != null && !amendment.getRejected().isEmpty()) {
                    decision.setRejected(HarnessSupport.copyList(amendment.getRejected()));
                }
                if (amendment.getClaims() != null && !amendment.getClaims().isEmpty()) {
                    decision.setClaims(normalizeClaims(amendment.getClaims()));
                }
                if (amendment.getRationale() != null) {
                    decision.setRationale(amendment.getRationale());
                }
                if (HarnessSupport.trimToNull(amendment.getQuestion()) != null) {
                    decision.setQuestion(amendment.getQuestion().trim());
                }
                long now = HarnessSupport.now();
                decision.setAmendedAtEpochMs(now);
                decision.setAmendedBy(HarnessSupport.copyActor(effectiveActor));
                decision.setVersion(Math.max(1L, decision.getVersion()) + 1L);
                eventJournal.addEvent(state, "decision.amended", id, effectiveActor,
                        HarnessSupport.mapOf("version", decision.getVersion(), "reason", reason));
                return decision.copy();
            }
        });
    }

    public DecisionReckonResult reckonDecision(String decisionId,
                                               List<DecisionFulfillment> fulfillments,
                                               boolean autoRevokeRules,
                                               String summary,
                                               HarnessActor actor) {
        final String id = HarnessSupport.requireText(decisionId, "decision id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException("an Agent cannot reckon a decision; non-Agent principal required");
        }
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<DecisionReckonResult>() {
            @Override
            public DecisionReckonResult apply(HarnessState state) {
                DecisionRecord decision = state.getDecisions().get(id);
                if (decision == null) {
                    throw new HarnessValidationException("decision not found: " + id);
                }
                long now = HarnessSupport.now();
                Map<String, DecisionFulfillment> byClaimId = new LinkedHashMap<String, DecisionFulfillment>();
                List<DecisionFulfillment> existingFulfillments = decision.getFulfillments() == null
                        ? new ArrayList<DecisionFulfillment>() : new ArrayList<DecisionFulfillment>(decision.getFulfillments());
                if (fulfillments != null) {
                    for (DecisionFulfillment raw : fulfillments) {
                        if (raw == null) {
                            continue;
                        }
                        String claimId = HarnessSupport.requireText(raw.getClaimId(), "fulfillment claimId");
                        DecisionFulfillment stored = raw.toBuilder()
                                .fulfillmentId(HarnessSupport.valueOrGenerated(raw.getFulfillmentId(), "ful_"))
                                .claimId(claimId)
                                .observer(raw.getObserver() == null
                                        ? HarnessSupport.copyActor(effectiveActor) : HarnessSupport.copyActor(raw.getObserver()))
                                .observedAtEpochMs(raw.getObservedAtEpochMs() > 0L ? raw.getObservedAtEpochMs() : now)
                                .build();
                        existingFulfillments.add(stored);
                        byClaimId.put(claimId, stored);
                    }
                }
                decision.setFulfillments(existingFulfillments);

                List<String> falsifiedClaimIds = new ArrayList<String>();
                List<String> fulfilledClaimIds = new ArrayList<String>();
                List<DecisionClaim> claims = decision.getClaims() == null
                        ? new ArrayList<DecisionClaim>() : new ArrayList<DecisionClaim>(decision.getClaims());
                for (DecisionClaim claim : claims) {
                    if (claim == null) {
                        continue;
                    }
                    DecisionFulfillment match = byClaimId.get(claim.getClaimId());
                    if (match != null) {
                        if (match.isSatisfied()) {
                            claim.setStatus(DecisionClaim.Status.FULFILLED);
                            fulfilledClaimIds.add(claim.getClaimId());
                        } else {
                            claim.setStatus(DecisionClaim.Status.FALSIFIED);
                            falsifiedClaimIds.add(claim.getClaimId());
                        }
                    } else if (claim.getStatus() == DecisionClaim.Status.FALSIFIED) {
                        falsifiedClaimIds.add(claim.getClaimId());
                    } else if (claim.getStatus() == DecisionClaim.Status.FULFILLED) {
                        fulfilledClaimIds.add(claim.getClaimId());
                    }
                }
                if (claims.isEmpty() && !byClaimId.isEmpty()) {
                    for (DecisionFulfillment fulfillment : byClaimId.values()) {
                        if (fulfillment.isSatisfied()) {
                            fulfilledClaimIds.add(fulfillment.getClaimId());
                        } else {
                            falsifiedClaimIds.add(fulfillment.getClaimId());
                        }
                    }
                }
                decision.setClaims(claims);

                boolean anyFalsified = !falsifiedClaimIds.isEmpty();
                ReckonStatus reckonStatus;
                if (anyFalsified) {
                    reckonStatus = fulfilledClaimIds.isEmpty()
                            ? ReckonStatus.FALSIFIED : ReckonStatus.PARTIALLY_FALSIFIED;
                    decision.setStatus(DecisionStatus.FALSIFIED);
                    decision.setAmendedAtEpochMs(now);
                    decision.setAmendedBy(HarnessSupport.copyActor(effectiveActor));
                } else {
                    reckonStatus = ReckonStatus.CONFIRMED;
                }
                decision.setReckonStatus(reckonStatus);
                decision.setReckonSummary(summary);
                decision.setReckonedBy(HarnessSupport.copyActor(effectiveActor));
                decision.setReckonedAtEpochMs(now);
                decision.setVersion(Math.max(1L, decision.getVersion()) + 1L);

                List<String> revokedRuleIds = new ArrayList<String>();
                if (anyFalsified && autoRevokeRules) {
                    for (HarnessRule rule : state.getLearnedRules().values()) {
                        if (rule != null && rule.isEnabled() && id.equals(rule.getDecisionId())) {
                            rule.setEnabled(false);
                            rule.setRevokedBy(HarnessSupport.copyActor(effectiveActor));
                            rule.setRevokedAtEpochMs(now);
                            revokedRuleIds.add(rule.getRuleId());
                            eventJournal.addEvent(state, "rule.revoked", rule.getRuleId(), effectiveActor,
                                    HarnessSupport.mapOf("decisionId", id, "reason", "decision_falsified"));
                        }
                    }
                }

                eventJournal.addEvent(state, "decision.reckoned", id, effectiveActor,
                        HarnessSupport.mapOf("reckonStatus", reckonStatus.name(),
                                "falsified", anyFalsified,
                                "revokedRules", revokedRuleIds));

                return DecisionReckonResult.builder()
                        .decision(decision.copy())
                        .reckonStatus(reckonStatus)
                        .falsified(anyFalsified)
                        .summary(summary)
                        .falsifiedClaimIds(falsifiedClaimIds)
                        .fulfilledClaimIds(fulfilledClaimIds)
                        .revokedRuleIds(revokedRuleIds)
                        .build();
            }
        });
    }

    public static List<DecisionClaim> normalizeClaims(List<DecisionClaim> rawClaims) {
        List<DecisionClaim> result = new ArrayList<DecisionClaim>();
        if (rawClaims == null) {
            return result;
        }
        int idx = 1;
        for (DecisionClaim claim : rawClaims) {
            if (claim == null) {
                continue;
            }
            String claimId = HarnessSupport.trimToNull(claim.getClaimId());
            if (claimId == null) {
                claimId = "claim-" + idx;
            }
            idx++;
            result.add(claim.toBuilder()
                    .claimId(claimId)
                    .status(claim.getStatus() == null ? DecisionClaim.Status.PENDING : claim.getStatus())
                    .build());
        }
        return result;
    }

    // ---- Versioned Agent Blueprints (register / diff / rollback) ----

    public AgentBlueprintRecord registerAgentBlueprint(AgentBlueprintSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("agent blueprint specification is required");
        }
        final String name = HarnessSupport.requireText(spec.getName(), "blueprint name");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<AgentBlueprintRecord>() {
            @Override
            public AgentBlueprintRecord apply(HarnessState state) {
                long maxVersion = 0L;
                for (AgentBlueprintRecord existing : state.getAgentBlueprints().values()) {
                    if (existing != null && name.equals(existing.getName())) {
                        existing.setActive(false);
                        maxVersion = Math.max(maxVersion, existing.getVersion());
                    }
                }
                long nextVersion = maxVersion + 1L;
                String blueprintId = name + "@v" + nextVersion;
                AgentBlueprintRecord record = AgentBlueprintRecord.builder()
                        .blueprintId(blueprintId)
                        .scopeKey(HarnessSupport.normalizeScope(spec.getScopeKey()))
                        .name(name)
                        .version(nextVersion)
                        .model(spec.getModel())
                        .systemPrompt(spec.getSystemPrompt())
                        .description(spec.getDescription())
                        .active(true)
                        .allowedTools(HarnessSupport.copyList(spec.getAllowedTools()))
                        .capabilities(HarnessSupport.copyList(spec.getCapabilities()))
                        .metadata(HarnessSupport.copyMap(spec.getMetadata()))
                        .createdBy(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(HarnessSupport.now())
                        .build();
                state.getAgentBlueprints().put(blueprintId, record);
                eventJournal.addEvent(state, "blueprint.registered", blueprintId, effectiveActor,
                        HarnessSupport.mapOf("name", name, "version", nextVersion, "model", spec.getModel()));
                return record.copy();
            }
        });
    }

    public List<AgentBlueprintRecord> listAgentBlueprintVersions(String name) {
        String key = HarnessSupport.requireText(name, "blueprint name");
        HarnessState state = store.load();
        if (state == null || state.getAgentBlueprints() == null) {
            return Collections.emptyList();
        }
        List<AgentBlueprintRecord> result = new ArrayList<AgentBlueprintRecord>();
        for (AgentBlueprintRecord bp : state.getAgentBlueprints().values()) {
            if (bp != null && key.equals(bp.getName())) {
                result.add(bp.copy());
            }
        }
        Collections.sort(result, new Comparator<AgentBlueprintRecord>() {
            @Override
            public int compare(AgentBlueprintRecord left, AgentBlueprintRecord right) {
                return Long.compare(left.getVersion(), right.getVersion());
            }
        });
        return result;
    }

    public AgentBlueprintRecord getActiveAgentBlueprint(String name) {
        String key = HarnessSupport.requireText(name, "blueprint name");
        HarnessState state = store.load();
        if (state == null || state.getAgentBlueprints() == null) {
            return null;
        }
        AgentBlueprintRecord active = null;
        for (AgentBlueprintRecord bp : state.getAgentBlueprints().values()) {
            if (bp != null && key.equals(bp.getName()) && bp.isActive()) {
                active = bp;
            }
        }
        return active == null ? null : active.copy();
    }

    public AgentBlueprintDiff diffAgentBlueprints(String name, long fromVersion, long toVersion) {
        String key = HarnessSupport.requireText(name, "blueprint name");
        HarnessState state = store.load();
        AgentBlueprintRecord from = findBlueprintVersion(state, key, fromVersion);
        AgentBlueprintRecord to = findBlueprintVersion(state, key, toVersion);
        if (from == null) {
            throw new HarnessValidationException("blueprint version not found: " + key + "@v" + fromVersion);
        }
        if (to == null) {
            throw new HarnessValidationException("blueprint version not found: " + key + "@v" + toVersion);
        }
        Set<String> fromTools = new LinkedHashSet<String>(HarnessSupport.copyList(from.getAllowedTools()));
        Set<String> toTools = new LinkedHashSet<String>(HarnessSupport.copyList(to.getAllowedTools()));
        List<String> addedTools = new ArrayList<String>();
        for (String t : toTools) {
            if (!fromTools.contains(t)) {
                addedTools.add(t);
            }
        }
        List<String> removedTools = new ArrayList<String>();
        for (String t : fromTools) {
            if (!toTools.contains(t)) {
                removedTools.add(t);
            }
        }
        Set<String> fromCaps = new LinkedHashSet<String>(HarnessSupport.copyList(from.getCapabilities()));
        Set<String> toCaps = new LinkedHashSet<String>(HarnessSupport.copyList(to.getCapabilities()));
        List<String> addedCaps = new ArrayList<String>();
        for (String c : toCaps) {
            if (!fromCaps.contains(c)) {
                addedCaps.add(c);
            }
        }
        List<String> removedCaps = new ArrayList<String>();
        for (String c : fromCaps) {
            if (!toCaps.contains(c)) {
                removedCaps.add(c);
            }
        }
        Map<String, Object> changedMeta = new LinkedHashMap<String, Object>();
        Map<String, Object> fromMeta = HarnessSupport.copyMap(from.getMetadata());
        Map<String, Object> toMeta = HarnessSupport.copyMap(to.getMetadata());
        Set<String> allKeys = new LinkedHashSet<String>(fromMeta.keySet());
        allKeys.addAll(toMeta.keySet());
        for (String metaKey : allKeys) {
            if (!HarnessSupport.safeEquals(fromMeta.get(metaKey), toMeta.get(metaKey))) {
                changedMeta.put(metaKey, HarnessSupport.mapOf("from", fromMeta.get(metaKey), "to", toMeta.get(metaKey)));
            }
        }
        return AgentBlueprintDiff.builder()
                .name(key)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .modelChanged(!HarnessSupport.safeEquals(from.getModel(), to.getModel()))
                .previousModel(from.getModel())
                .currentModel(to.getModel())
                .systemPromptChanged(!HarnessSupport.safeEquals(from.getSystemPrompt(), to.getSystemPrompt()))
                .previousSystemPrompt(from.getSystemPrompt())
                .currentSystemPrompt(to.getSystemPrompt())
                .addedTools(addedTools)
                .removedTools(removedTools)
                .addedCapabilities(addedCaps)
                .removedCapabilities(removedCaps)
                .changedMetadata(changedMeta)
                .build();
    }

    public AgentBlueprintRecord rollbackAgentBlueprint(String name,
                                                     long targetVersion,
                                                     String reason,
                                                     HarnessActor actor) {
        final String key = HarnessSupport.requireText(name, "blueprint name");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException("an Agent cannot rollback an AgentBlueprint; non-Agent principal required");
        }
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<AgentBlueprintRecord>() {
            @Override
            public AgentBlueprintRecord apply(HarnessState state) {
                AgentBlueprintRecord target = findBlueprintVersion(state, key, targetVersion);
                if (target == null) {
                    throw new HarnessValidationException("blueprint version not found: " + key + "@v" + targetVersion);
                }
                long maxVersion = 0L;
                for (AgentBlueprintRecord existing : state.getAgentBlueprints().values()) {
                    if (existing != null && key.equals(existing.getName())) {
                        existing.setActive(false);
                        maxVersion = Math.max(maxVersion, existing.getVersion());
                    }
                }
                long nextVersion = maxVersion + 1L;
                String blueprintId = key + "@v" + nextVersion;
                AgentBlueprintRecord restored = AgentBlueprintRecord.builder()
                        .blueprintId(blueprintId)
                        .scopeKey(target.getScopeKey())
                        .name(key)
                        .version(nextVersion)
                        .model(target.getModel())
                        .systemPrompt(target.getSystemPrompt())
                        .description(target.getDescription())
                        .active(true)
                        .rollbackFromVersion(Long.valueOf(targetVersion))
                        .rollbackReason(reason)
                        .allowedTools(HarnessSupport.copyList(target.getAllowedTools()))
                        .capabilities(HarnessSupport.copyList(target.getCapabilities()))
                        .metadata(HarnessSupport.copyMap(target.getMetadata()))
                        .createdBy(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(HarnessSupport.now())
                        .build();
                state.getAgentBlueprints().put(blueprintId, restored);
                eventJournal.addEvent(state, "blueprint.rolled_back", blueprintId, effectiveActor,
                        HarnessSupport.mapOf("name", key, "targetVersion", targetVersion,
                                "newVersion", nextVersion, "reason", reason));
                return restored.copy();
            }
        });
    }

    private static AgentBlueprintRecord findBlueprintVersion(HarnessState state, String name, long version) {
        if (state == null || state.getAgentBlueprints() == null) {
            return null;
        }
        for (AgentBlueprintRecord bp : state.getAgentBlueprints().values()) {
            if (bp != null && name.equals(bp.getName()) && bp.getVersion() == version) {
                return bp;
            }
        }
        return null;
    }

    private static ExecutionRecord requireActiveExecution(HarnessState state, String executionId) {
        ExecutionRecord execution = HarnessSupport.requireExecution(state, executionId);
        if (execution.getStatus() != ExecutionStatus.READY
                && execution.getStatus() != ExecutionStatus.RUNNING
                && execution.getStatus() != ExecutionStatus.WAITING) {
            throw new HarnessConflictException("read-set tracking requires an active execution, found "
                    + execution.getStatus() + ": " + executionId);
        }
        return execution;
    }

    private static String readSetKey(String executionId, EntityKind kind, String entityId) {
        return "rs:" + executionId + ":" + kind.name() + ":" + entityId;
    }

    public static final class StaleReadDetail {
        private final ReadSetEntry entry;
        private final String description;

        public StaleReadDetail(ReadSetEntry entry, String description) {
            this.entry = entry;
            this.description = description;
        }

        public ReadSetEntry getEntry() {
            return entry;
        }

        public String getDescription() {
            return description;
        }
    }
}
