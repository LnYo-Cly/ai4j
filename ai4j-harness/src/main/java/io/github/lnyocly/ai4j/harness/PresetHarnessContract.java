package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Routes {@link HarnessContract} queries to a per-preset contract resolved
 * from the task. By default the preset is read from
 * {@code task.metadata["preset"]} ({@link HarnessContract#PRESET_METADATA_KEY});
 * tasks without a preset fall back to the base contract. Tool-level rules and
 * actor permissions are intentionally harness-wide and always answered by the
 * base contract.
 */
public final class PresetHarnessContract implements HarnessContract {

    private final Function<TaskRecord, String> presetKey;
    private final Map<String, HarnessContract> presets;
    private final HarnessContract base;
    private final UnknownPresetPolicy unknownPresetPolicy;

    private PresetHarnessContract(Function<TaskRecord, String> presetKey,
                                  Map<String, HarnessContract> presets,
                                  HarnessContract base,
                                  UnknownPresetPolicy unknownPresetPolicy) {
        this.presetKey = presetKey;
        this.presets = presets;
        this.base = base;
        this.unknownPresetPolicy = unknownPresetPolicy;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The preset key declared on the task, or null when unset. */
    public String presetKeyOf(TaskRecord task) {
        if (task == null || presetKey == null) {
            return null;
        }
        String key = presetKey.apply(task);
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private HarnessContract resolve(TaskRecord task) {
        String key = presetKeyOf(task);
        if (key == null) {
            return base;
        }
        HarnessContract preset = presets.get(key);
        if (preset != null) {
            return preset;
        }
        return unknownPresetPolicy == UnknownPresetPolicy.FALLBACK_TO_BASE
                ? base : failClosed(key);
    }

    private HarnessContract failClosed(final String key) {
        return new HarnessContract() {
            @Override
            public List<GateResult> evaluateCompletion(TaskRecord task,
                                                       SubmissionRecord submission,
                                                       HarnessState state) {
                return Collections.singletonList(GateResult.fail("preset",
                        "unknown task preset '" + key + "'"));
            }
        };
    }

    @Override
    public boolean requiresApprovedReview(TaskRecord task, SubmissionRecord submission) {
        return resolve(task).requiresApprovedReview(task, submission);
    }

    @Override
    public boolean requiresCompletionEvidence(TaskRecord task, SubmissionRecord submission) {
        return resolve(task).requiresCompletionEvidence(task, submission);
    }

    @Override
    public Set<String> requiredAcceptanceChecks(TaskRecord task, SubmissionRecord submission) {
        return resolve(task).requiredAcceptanceChecks(task, submission);
    }

    @Override
    public List<HarnessGate> completionGates(TaskRecord task) {
        return resolve(task).completionGates(task);
    }

    @Override
    public List<GateResult> evaluateCompletion(TaskRecord task,
                                               SubmissionRecord submission,
                                               HarnessState state) {
        return resolve(task).evaluateCompletion(task, submission, state);
    }

    // Tool-level and actor-level policy stays harness-wide on purpose.

    @Override
    public boolean requiresTaskForTool(String toolName) {
        return base.requiresTaskForTool(toolName);
    }

    @Override
    public boolean requiresApprovalForTool(String toolName) {
        return base.requiresApprovalForTool(toolName);
    }

    @Override
    public boolean acceptsAgentFact(HarnessFactSpec fact) {
        return base.acceptsAgentFact(fact);
    }

    @Override
    public List<HarnessGate> completionGates() {
        return base.completionGates();
    }

    @Override
    public boolean mayApprove(HarnessActor actor, SubmissionRecord submission) {
        return base.mayApprove(actor, submission);
    }

    @Override
    public boolean mayComplete(HarnessActor actor, SubmissionRecord submission) {
        return base.mayComplete(actor, submission);
    }

    @Override
    public boolean mayReconcile(HarnessActor actor, ExecutionRecord execution,
                                ExecutionStatus resolution) {
        return base.mayReconcile(actor, execution, resolution);
    }

    @Override
    public boolean mayReconcileToolInvocation(HarnessActor actor,
                                              ToolInvocationRecord invocation,
                                              ToolInvocationStatus resolution) {
        return base.mayReconcileToolInvocation(actor, invocation, resolution);
    }

    @Override
    public boolean mayPromoteLesson(HarnessActor actor) {
        return base.mayPromoteLesson(actor);
    }

    public static final class Builder {
        private Function<TaskRecord, String> presetKey = new Function<TaskRecord, String>() {
            @Override
            public String apply(TaskRecord task) {
                return HarnessRule.presetOf(task);
            }
        };
        private final Map<String, HarnessContract> presets = new LinkedHashMap<String, HarnessContract>();
        private HarnessContract base = HarnessContract.builder().build();
        private UnknownPresetPolicy unknownPresetPolicy = UnknownPresetPolicy.FAIL_CLOSED;

        /** Customizes where the preset key is read from a task. */
        public Builder presetKey(Function<TaskRecord, String> presetKey) {
            if (presetKey != null) {
                this.presetKey = presetKey;
            }
            return this;
        }

        /** Registers a preset compiled through the standard contract builder. */
        public Builder preset(String name, Consumer<HarnessContract.Builder> configure) {
            String key = normalize(name);
            if (key == null || configure == null) {
                return this;
            }
            HarnessContract.Builder contractBuilder = HarnessContract.builder();
            configure.accept(contractBuilder);
            presets.put(key, contractBuilder.build());
            return this;
        }

        /** Registers a preset backed by a prebuilt contract. */
        public Builder preset(String name, HarnessContract contract) {
            String key = normalize(name);
            if (key == null || contract == null) {
                return this;
            }
            presets.put(key, contract);
            return this;
        }

        /** The contract answering tasks with no preset and harness-wide policy. */
        public Builder base(HarnessContract base) {
            if (base != null) {
                this.base = base;
            }
            return this;
        }

        public Builder onUnknownPreset(UnknownPresetPolicy policy) {
            if (policy != null) {
                this.unknownPresetPolicy = policy;
            }
            return this;
        }

        public PresetHarnessContract build() {
            return new PresetHarnessContract(presetKey,
                    new LinkedHashMap<String, HarnessContract>(presets),
                    base, unknownPresetPolicy);
        }

        private String normalize(String name) {
            if (name == null) {
                return null;
            }
            String trimmed = name.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
