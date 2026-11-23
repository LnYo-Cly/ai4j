package io.github.lnyocly.ai4j.ambient.evolve;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.harness.HarnessRuleSpec;
import io.github.lnyocly.ai4j.harness.RuleKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contrasts fast, clean successful trajectories against failed, slow, high-token,
 * or human-steered trajectories clustered by {@code taskType} to pinpoint actionable
 * root causes and emit L1/L2/L3 evolution candidates.
 */
public class TrajectoryContrastiveMiner {

    public enum RootCauseKind {
        MISSING_PRECHECK,
        REPEATED_TOOL_ERROR,
        EXCESSIVE_STEPS_AND_TOKENS,
        HUMAN_STEER_CORRECTION,
        REPETITIVE_TOOL_CHAIN
    }

    private final double slowDurationMultiplier;
    private final double highTokenMultiplier;
    private final int minChainStepsForCompile;

    public TrajectoryContrastiveMiner() {
        this(2.0d, 1.8d, 3);
    }

    public TrajectoryContrastiveMiner(double slowDurationMultiplier,
                                      double highTokenMultiplier,
                                      int minChainStepsForCompile) {
        this.slowDurationMultiplier = slowDurationMultiplier <= 1.0d ? 2.0d : slowDurationMultiplier;
        this.highTokenMultiplier = highTokenMultiplier <= 1.0d ? 1.8d : highTokenMultiplier;
        this.minChainStepsForCompile = Math.max(2, minChainStepsForCompile);
    }

    public MiningReport mine(List<TrajectoryRecord> trajectories) {
        return mine("default", trajectories, "meta-v1");
    }

    public MiningReport mine(String scopeKey, List<TrajectoryRecord> trajectories, String metaVersion) {
        String normalizedScope = AmbientSupport.trimToNull(scopeKey) == null ? "default" : scopeKey.trim();
        String effectiveMeta = AmbientSupport.trimToNull(metaVersion) == null ? "meta-v1" : metaVersion.trim();
        if (trajectories == null || trajectories.isEmpty()) {
            return MiningReport.builder()
                    .scopeKey(normalizedScope)
                    .metaVersion(effectiveMeta)
                    .build();
        }

        Map<String, List<TrajectoryRecord>> clusters = new LinkedHashMap<String, List<TrajectoryRecord>>();
        for (TrajectoryRecord trace : trajectories) {
            if (trace == null) {
                continue;
            }
            String taskType = AmbientSupport.trimToNull(trace.getTaskType());
            if (taskType == null) {
                taskType = "general";
            }
            List<TrajectoryRecord> bucket = clusters.get(taskType);
            if (bucket == null) {
                bucket = new ArrayList<TrajectoryRecord>();
                clusters.put(taskType, bucket);
            }
            bucket.add(trace);
        }

        List<ContrastiveDiagnosis> diagnoses = new ArrayList<ContrastiveDiagnosis>();
        List<HarnessRuleProposal> ruleProposals = new ArrayList<HarnessRuleProposal>();
        List<SkillPatchCandidate> skillPatches = new ArrayList<SkillPatchCandidate>();

        for (Map.Entry<String, List<TrajectoryRecord>> cluster : clusters.entrySet()) {
            String taskType = cluster.getKey();
            List<TrajectoryRecord> all = cluster.getValue();
            List<TrajectoryRecord> cleanSuccesses = new ArrayList<TrajectoryRecord>();
            List<TrajectoryRecord> failedOrSteered = new ArrayList<TrajectoryRecord>();

            for (TrajectoryRecord trace : all) {
                if (trace.isSuccess() && !trace.isSteeredByHuman()) {
                    cleanSuccesses.add(trace);
                } else {
                    failedOrSteered.add(trace);
                }
            }

            double baselineAvgSteps = avgSteps(cleanSuccesses);
            double baselineAvgTokens = avgTokens(cleanSuccesses);
            double baselineAvgDuration = avgDuration(cleanSuccesses);

            List<TrajectoryRecord> slowOrHeavy = new ArrayList<TrajectoryRecord>();
            if (!cleanSuccesses.isEmpty()) {
                for (TrajectoryRecord trace : cleanSuccesses) {
                    boolean slow = baselineAvgDuration > 0.0d
                            && trace.getDurationMillis() > baselineAvgDuration * slowDurationMultiplier;
                    boolean heavyTokens = baselineAvgTokens > 0.0d
                            && trace.getTotalTokens() > baselineAvgTokens * highTokenMultiplier;
                    if (slow || heavyTokens) {
                        slowOrHeavy.add(trace);
                    }
                }
            }

            // 1. Contrast missing pre-check / tool errors -> L1 HarnessRuleProposal
            if (!failedOrSteered.isEmpty()) {
                List<String> failedIds = collectIds(failedOrSteered);
                String missingPrecheckTool = detectMissingLeadingTool(cleanSuccesses, failedOrSteered);
                String targetTool = detectMostFrequentFailingTool(failedOrSteered);
                if (missingPrecheckTool != null) {
                    String diagId = newDiagnosisId();
                    diagnoses.add(ContrastiveDiagnosis.builder()
                            .diagnosisId(diagId)
                            .taskType(taskType)
                            .rootCause(RootCauseKind.MISSING_PRECHECK)
                            .recommendedTier(CandidateTier.L1_HARNESS_RULE)
                            .summary("Failed traces omitted leading pre-check tool '" + missingPrecheckTool
                                    + "' present in successful traces")
                            .baselineAvgSteps(baselineAvgSteps)
                            .baselineAvgTokens(baselineAvgTokens)
                            .problemAvgSteps(avgSteps(failedOrSteered))
                            .problemAvgTokens(avgTokens(failedOrSteered))
                            .affectedTrajectoryIds(failedIds)
                            .suggestedToolChain(extractFirstChain(cleanSuccesses))
                            .build());
                    ruleProposals.add(HarnessRuleProposal.builder()
                            .candidateId("l1_" + UUID.randomUUID().toString().replace("-", ""))
                            .scopeKey(normalizedScope)
                            .diagnosisId(diagId)
                            .metaVersion(effectiveMeta)
                            .ruleSpec(HarnessRuleSpec.builder()
                                    .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK)
                                    .payload("precheck_" + missingPrecheckTool)
                                    .build())
                            .rationale("Enforce pre-check " + missingPrecheckTool + " observed in successful traces")
                            .evidenceTraceIds(failedIds)
                            .build());
                } else if (targetTool != null) {
                    String diagId = newDiagnosisId();
                    diagnoses.add(ContrastiveDiagnosis.builder()
                            .diagnosisId(diagId)
                            .taskType(taskType)
                            .rootCause(RootCauseKind.REPEATED_TOOL_ERROR)
                            .recommendedTier(CandidateTier.L1_HARNESS_RULE)
                            .summary("Repeated tool failures on '" + targetTool + "'")
                            .baselineAvgSteps(baselineAvgSteps)
                            .baselineAvgTokens(baselineAvgTokens)
                            .problemAvgSteps(avgSteps(failedOrSteered))
                            .problemAvgTokens(avgTokens(failedOrSteered))
                            .affectedTrajectoryIds(failedIds)
                            .build());
                    ruleProposals.add(HarnessRuleProposal.builder()
                            .candidateId("l1_" + UUID.randomUUID().toString().replace("-", ""))
                            .scopeKey(normalizedScope)
                            .diagnosisId(diagId)
                            .metaVersion(effectiveMeta)
                            .ruleSpec(HarnessRuleSpec.builder()
                                    .kind(RuleKind.REQUIRE_APPROVAL_FOR_TOOL)
                                    .payload(targetTool)
                                    .build())
                            .rationale("Gate error-prone tool '" + targetTool + "' with explicit approval")
                            .evidenceTraceIds(failedIds)
                            .build());
                }

                // Human steer feedback -> L2 SkillPatchCandidate
                List<String> steerNotes = collectSteerFeedback(failedOrSteered);
                if (!steerNotes.isEmpty()) {
                    String diagId = newDiagnosisId();
                    diagnoses.add(ContrastiveDiagnosis.builder()
                            .diagnosisId(diagId)
                            .taskType(taskType)
                            .rootCause(RootCauseKind.HUMAN_STEER_CORRECTION)
                            .recommendedTier(CandidateTier.L2_SKILL_PATCH)
                            .summary("Human steer intervention detected: " + steerNotes.get(0))
                            .baselineAvgSteps(baselineAvgSteps)
                            .baselineAvgTokens(baselineAvgTokens)
                            .problemAvgSteps(avgSteps(failedOrSteered))
                            .problemAvgTokens(avgTokens(failedOrSteered))
                            .affectedTrajectoryIds(failedIds)
                            .build());
                    StringBuilder md = new StringBuilder();
                    md.append("# Skill Patch for ").append(taskType).append("\n\n");
                    md.append("## Steered Corrections\n");
                    for (String note : steerNotes) {
                        md.append("- ").append(note).append('\n');
                    }
                    skillPatches.add(SkillPatchCandidate.builder()
                            .candidateId("l2_" + UUID.randomUUID().toString().replace("-", ""))
                            .scopeKey(normalizedScope)
                            .diagnosisId(diagId)
                            .metaVersion(effectiveMeta)
                            .skillName(taskType)
                            .description("Patched skill instructions incorporating human steer feedback")
                            .markdownContent(md.toString())
                            .patchSummary("Incorporated " + steerNotes.size() + " human steer correction(s)")
                            .build());
                }
            }

            // 2. Slow / high-token or long repetitive tool chains -> L3 CompiledSkill candidate diagnosis
            List<String> repeatedChain = findDominantLongChain(cleanSuccesses, minChainStepsForCompile);
            if (!repeatedChain.isEmpty() || !slowOrHeavy.isEmpty()) {
                List<TrajectoryRecord> targets = slowOrHeavy.isEmpty() ? cleanSuccesses : slowOrHeavy;
                String diagId = newDiagnosisId();
                diagnoses.add(ContrastiveDiagnosis.builder()
                        .diagnosisId(diagId)
                        .taskType(taskType)
                        .rootCause(repeatedChain.isEmpty()
                                ? RootCauseKind.EXCESSIVE_STEPS_AND_TOKENS
                                : RootCauseKind.REPETITIVE_TOOL_CHAIN)
                        .recommendedTier(CandidateTier.L3_COMPILED_SKILL)
                        .summary("Detected deterministic multi-step chain (" + repeatedChain.size()
                                + " steps) eligible for L3 SkillCompiler consolidation")
                        .baselineAvgSteps(baselineAvgSteps)
                        .baselineAvgTokens(baselineAvgTokens)
                        .problemAvgSteps(avgSteps(targets))
                        .problemAvgTokens(avgTokens(targets))
                        .affectedTrajectoryIds(collectIds(targets))
                        .suggestedToolChain(repeatedChain)
                        .build());
            }
        }

        return MiningReport.builder()
                .scopeKey(normalizedScope)
                .metaVersion(effectiveMeta)
                .totalTrajectories(trajectories.size())
                .diagnoses(diagnoses)
                .ruleProposals(ruleProposals)
                .skillPatches(skillPatches)
                .build();
    }

    private static String detectMissingLeadingTool(List<TrajectoryRecord> successes,
                                                   List<TrajectoryRecord> problems) {
        if (successes.isEmpty() || problems.isEmpty()) {
            return null;
        }
        Set<String> leadingSuccessTools = new LinkedHashSet<String>();
        for (TrajectoryRecord s : successes) {
            if (s.getToolSteps() != null && !s.getToolSteps().isEmpty()) {
                leadingSuccessTools.add(s.getToolSteps().get(0).getToolName());
            }
        }
        if (leadingSuccessTools.size() != 1) {
            return null;
        }
        String commonLeading = leadingSuccessTools.iterator().next();
        if (commonLeading == null) {
            return null;
        }
        int missingCount = 0;
        for (TrajectoryRecord p : problems) {
            boolean found = false;
            if (p.getToolSteps() != null) {
                for (TrajectoryRecord.ToolStep step : p.getToolSteps()) {
                    if (commonLeading.equals(step.getToolName())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                missingCount++;
            }
        }
        return missingCount > 0 ? commonLeading : null;
    }

    private static String detectMostFrequentFailingTool(List<TrajectoryRecord> failed) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (TrajectoryRecord trace : failed) {
            if (trace.getToolSteps() == null) {
                continue;
            }
            for (TrajectoryRecord.ToolStep step : trace.getToolSteps()) {
                if (step != null && !step.isSuccess() && step.getToolName() != null) {
                    Integer current = counts.get(step.getToolName());
                    counts.put(step.getToolName(), Integer.valueOf(current == null ? 1 : current.intValue() + 1));
                }
            }
        }
        String topTool = null;
        int topCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue().intValue() > topCount) {
                topCount = entry.getValue().intValue();
                topTool = entry.getKey();
            }
        }
        return topTool;
    }

    private static List<String> collectSteerFeedback(List<TrajectoryRecord> failedOrSteered) {
        List<String> notes = new ArrayList<String>();
        for (TrajectoryRecord trace : failedOrSteered) {
            if (trace.isSteeredByHuman() && AmbientSupport.trimToNull(trace.getSteerFeedback()) != null) {
                notes.add(trace.getSteerFeedback().trim());
            }
        }
        return notes;
    }

    private static List<String> findDominantLongChain(List<TrajectoryRecord> successes, int minSteps) {
        Map<String, Integer> sigCounts = new LinkedHashMap<String, Integer>();
        Map<String, List<String>> sigChains = new LinkedHashMap<String, List<String>>();
        for (TrajectoryRecord s : successes) {
            if (s.getToolSteps() == null || s.getToolSteps().size() < minSteps) {
                continue;
            }
            String sig = s.toolSignature();
            Integer c = sigCounts.get(sig);
            sigCounts.put(sig, Integer.valueOf(c == null ? 1 : c.intValue() + 1));
            if (!sigChains.containsKey(sig)) {
                List<String> chain = new ArrayList<String>(s.getToolSteps().size());
                for (TrajectoryRecord.ToolStep step : s.getToolSteps()) {
                    chain.add(step.getToolName());
                }
                sigChains.put(sig, chain);
            }
        }
        String bestSig = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : sigCounts.entrySet()) {
            if (entry.getValue().intValue() > bestCount) {
                bestCount = entry.getValue().intValue();
                bestSig = entry.getKey();
            }
        }
        return bestSig == null ? Collections.<String>emptyList() : sigChains.get(bestSig);
    }

    private static List<String> extractFirstChain(List<TrajectoryRecord> traces) {
        if (traces.isEmpty() || traces.get(0).getToolSteps() == null) {
            return Collections.emptyList();
        }
        List<String> chain = new ArrayList<String>();
        for (TrajectoryRecord.ToolStep step : traces.get(0).getToolSteps()) {
            chain.add(step.getToolName());
        }
        return chain;
    }

    private static List<String> collectIds(List<TrajectoryRecord> traces) {
        List<String> ids = new ArrayList<String>(traces.size());
        for (TrajectoryRecord trace : traces) {
            if (trace.getTrajectoryId() != null) {
                ids.add(trace.getTrajectoryId());
            }
        }
        return ids;
    }

    private static double avgSteps(List<TrajectoryRecord> traces) {
        if (traces.isEmpty()) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (TrajectoryRecord t : traces) {
            sum += t.getSteps();
        }
        return sum / traces.size();
    }

    private static double avgTokens(List<TrajectoryRecord> traces) {
        if (traces.isEmpty()) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (TrajectoryRecord t : traces) {
            sum += t.getTotalTokens();
        }
        return sum / traces.size();
    }

    private static double avgDuration(List<TrajectoryRecord> traces) {
        if (traces.isEmpty()) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (TrajectoryRecord t : traces) {
            sum += t.getDurationMillis();
        }
        return sum / traces.size();
    }

    private static String newDiagnosisId() {
        return "diag_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContrastiveDiagnosis {
        private String diagnosisId;
        private String taskType;
        private RootCauseKind rootCause;
        private CandidateTier recommendedTier;
        private String summary;
        private double baselineAvgSteps;
        private double baselineAvgTokens;
        private double problemAvgSteps;
        private double problemAvgTokens;
        @Builder.Default
        private List<String> affectedTrajectoryIds = new ArrayList<String>();
        @Builder.Default
        private List<String> suggestedToolChain = new ArrayList<String>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MiningReport {
        private String scopeKey;
        private String metaVersion;
        private int totalTrajectories;
        @Builder.Default
        private List<ContrastiveDiagnosis> diagnoses = new ArrayList<ContrastiveDiagnosis>();
        @Builder.Default
        private List<HarnessRuleProposal> ruleProposals = new ArrayList<HarnessRuleProposal>();
        @Builder.Default
        private List<SkillPatchCandidate> skillPatches = new ArrayList<SkillPatchCandidate>();
    }
}
