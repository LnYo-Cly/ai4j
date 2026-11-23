package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
import io.github.lnyocly.ai4j.harness.HarnessJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic {@code base / local / center} three-way entity merger and
 * field-level conflict detector for offline/weak-network edge sync.
 */
public final class ThreeWayEntityMerger {

    private static final Set<String> IGNORED_SYNC_FIELDS = createIgnoredFields();

    private ThreeWayEntityMerger() {
    }

    private static Set<String> createIgnoredFields() {
        Set<String> set = new LinkedHashSet<String>();
        set.add("version");
        set.add("updatedAtEpochMs");
        return set;
    }

    public static MergeOutcome merge(HarnessEntityRecord base,
                                     HarnessEntityRecord local,
                                     HarnessEntityRecord center,
                                     EdgeSyncConflictPolicy policy) {
        EdgeSyncConflictPolicy effectivePolicy = policy == null
                ? EdgeSyncConflictPolicy.DETECT_AND_REPORT : policy;
        String entityType = pickNonNull(center == null ? null : center.getEntityType(),
                local == null ? null : local.getEntityType(),
                base == null ? null : base.getEntityType());
        String entityId = pickNonNull(center == null ? null : center.getEntityId(),
                local == null ? null : local.getEntityId(),
                base == null ? null : base.getEntityId());
        String harnessId = pickNonNull(center == null ? null : center.getHarnessId(),
                local == null ? null : local.getHarnessId(),
                base == null ? null : base.getHarnessId());

        if (local == null && center == null) {
            return new MergeOutcome(EdgeSyncResolution.Action.UNCHANGED, null,
                    new ArrayList<EdgeSyncConflict>());
        }
        if (local != null && center == null && base == null) {
            return new MergeOutcome(EdgeSyncResolution.Action.PUSHED_TO_CENTER, local.copy(),
                    new ArrayList<EdgeSyncConflict>());
        }
        if (local == null && center != null && base == null) {
            return new MergeOutcome(EdgeSyncResolution.Action.PULLED_FROM_CENTER, center.copy(),
                    new ArrayList<EdgeSyncConflict>());
        }

        boolean localChanged = !equivalentEntities(base, local);
        boolean centerChanged = !equivalentEntities(base, center);

        if (!localChanged && !centerChanged) {
            return new MergeOutcome(EdgeSyncResolution.Action.UNCHANGED,
                    center != null ? center.copy() : (local == null ? null : local.copy()),
                    new ArrayList<EdgeSyncConflict>());
        }
        if (!localChanged) {
            return new MergeOutcome(EdgeSyncResolution.Action.PULLED_FROM_CENTER,
                    center == null ? null : center.copy(),
                    new ArrayList<EdgeSyncConflict>());
        }
        if (!centerChanged) {
            HarnessEntityRecord pushed = local == null ? null : local.copy();
            if (pushed != null && center != null) {
                pushed.setEntityVersion(Math.max(pushed.getEntityVersion(), center.getEntityVersion() + 1L));
            }
            return new MergeOutcome(EdgeSyncResolution.Action.PUSHED_TO_CENTER,
                    pushed, new ArrayList<EdgeSyncConflict>());
        }
        if (equivalentEntities(local, center)) {
            return new MergeOutcome(EdgeSyncResolution.Action.UNCHANGED,
                    center == null ? null : center.copy(),
                    new ArrayList<EdgeSyncConflict>());
        }

        // Both local and center changed relative to base and differ from each other:
        // attempt deterministic field-level 3-way merge.
        List<EdgeSyncConflict> conflicts = new ArrayList<EdgeSyncConflict>();
        long baseVer = base == null ? 0L : base.getEntityVersion();
        long localVer = local == null ? 0L : local.getEntityVersion();
        long centerVer = center == null ? 0L : center.getEntityVersion();

        String baseStatus = base == null ? null : base.getStatus();
        String localStatus = local == null ? null : local.getStatus();
        String centerStatus = center == null ? null : center.getStatus();
        String mergedStatus;
        if (Objects.equals(localStatus, baseStatus)) {
            mergedStatus = centerStatus;
        } else if (Objects.equals(centerStatus, baseStatus)) {
            mergedStatus = localStatus;
        } else if (Objects.equals(localStatus, centerStatus)) {
            mergedStatus = centerStatus;
        } else {
            if (effectivePolicy == EdgeSyncConflictPolicy.CENTER_WINS) {
                mergedStatus = centerStatus;
            } else if (effectivePolicy == EdgeSyncConflictPolicy.LOCAL_WINS) {
                mergedStatus = localStatus;
            } else {
                mergedStatus = centerStatus;
                conflicts.add(EdgeSyncConflict.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .fieldPath("status")
                        .baseValue(baseStatus)
                        .localValue(localStatus)
                        .centerValue(centerStatus)
                        .baseVersion(baseVer)
                        .localVersion(localVer)
                        .centerVersion(centerVer)
                        .reason("status diverged between local and center")
                        .resolution("UNRESOLVED")
                        .build());
            }
        }

        Map<String, Object> baseMap = parsePayload(base);
        Map<String, Object> localMap = parsePayload(local);
        Map<String, Object> centerMap = parsePayload(center);

        Set<String> allFields = new LinkedHashSet<String>();
        allFields.addAll(baseMap.keySet());
        allFields.addAll(localMap.keySet());
        allFields.addAll(centerMap.keySet());

        Map<String, Object> mergedMap = new LinkedHashMap<String, Object>();
        for (String field : allFields) {
            Object bVal = baseMap.get(field);
            Object lVal = localMap.get(field);
            Object cVal = centerMap.get(field);

            if (IGNORED_SYNC_FIELDS.contains(field)) {
                Object preferred = cVal != null ? cVal : lVal;
                if (preferred != null) {
                    mergedMap.put(field, preferred);
                }
                continue;
            }

            if (Objects.equals(lVal, bVal)) {
                if (cVal != null) {
                    mergedMap.put(field, cVal);
                }
            } else if (Objects.equals(cVal, bVal)) {
                if (lVal != null) {
                    mergedMap.put(field, lVal);
                }
            } else if (Objects.equals(lVal, cVal)) {
                if (cVal != null) {
                    mergedMap.put(field, cVal);
                }
            } else {
                if (effectivePolicy == EdgeSyncConflictPolicy.CENTER_WINS) {
                    if (cVal != null) {
                        mergedMap.put(field, cVal);
                    }
                } else if (effectivePolicy == EdgeSyncConflictPolicy.LOCAL_WINS) {
                    if (lVal != null) {
                        mergedMap.put(field, lVal);
                    }
                } else {
                    if (cVal != null) {
                        mergedMap.put(field, cVal);
                    }
                    conflicts.add(EdgeSyncConflict.builder()
                            .entityType(entityType)
                            .entityId(entityId)
                            .fieldPath(field)
                            .baseValue(bVal)
                            .localValue(lVal)
                            .centerValue(cVal)
                            .baseVersion(baseVer)
                            .localVersion(localVer)
                            .centerVersion(centerVer)
                            .reason("concurrent modification on field '" + field + "'")
                            .resolution("UNRESOLVED")
                            .build());
                }
            }
        }
        if (mergedStatus != null && mergedMap.containsKey("status")) {
            mergedMap.put("status", mergedStatus);
        }

        if (!conflicts.isEmpty()) {
            return new MergeOutcome(EdgeSyncResolution.Action.CONFLICT,
                    center == null ? null : center.copy(), conflicts);
        }

        long nextVer = Math.max(centerVer, localVer) + 1L;
        if (mergedMap.containsKey("version")) {
            mergedMap.put("version", Long.valueOf(nextVer));
        }
        HarnessEntityRecord merged = HarnessEntityRecord.builder()
                .harnessId(harnessId)
                .entityType(entityType)
                .entityId(entityId)
                .entityVersion(nextVer)
                .status(mergedStatus)
                .updatedAtEpochMs(System.currentTimeMillis())
                .payloadJson(HarnessJson.toJson(mergedMap))
                .build();
        return new MergeOutcome(EdgeSyncResolution.Action.THREE_WAY_MERGED, merged, conflicts);
    }

    private static boolean equivalentEntities(HarnessEntityRecord left, HarnessEntityRecord right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (!Objects.equals(left.getStatus(), right.getStatus())) {
            return false;
        }
        Map<String, Object> leftMap = new LinkedHashMap<String, Object>(parsePayload(left));
        Map<String, Object> rightMap = new LinkedHashMap<String, Object>(parsePayload(right));
        for (String ignored : IGNORED_SYNC_FIELDS) {
            leftMap.remove(ignored);
            rightMap.remove(ignored);
        }
        return Objects.equals(leftMap, rightMap);
    }

    private static Map<String, Object> parsePayload(HarnessEntityRecord record) {
        if (record == null || record.getPayloadJson() == null || record.getPayloadJson().trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> parsed = HarnessJson.parseMap(record.getPayloadJson());
        return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
    }

    private static String pickNonNull(String a, String b, String c) {
        if (a != null) return a;
        if (b != null) return b;
        return c;
    }

    public static final class MergeOutcome {
        private final EdgeSyncResolution.Action action;
        private final HarnessEntityRecord mergedEntity;
        private final List<EdgeSyncConflict> conflicts;

        public MergeOutcome(EdgeSyncResolution.Action action,
                            HarnessEntityRecord mergedEntity,
                            List<EdgeSyncConflict> conflicts) {
            this.action = action;
            this.mergedEntity = mergedEntity;
            this.conflicts = conflicts;
        }

        public EdgeSyncResolution.Action getAction() {
            return action;
        }

        public HarnessEntityRecord getMergedEntity() {
            return mergedEntity;
        }

        public List<EdgeSyncConflict> getConflicts() {
            return conflicts;
        }
    }
}
