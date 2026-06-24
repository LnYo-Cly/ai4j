package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Content-addressed cache that powers {@link ResumableModelClient} / {@link ResumableToolExecutor}.
 *
 * <p>Keyed by the serialized prompt (MODEL) and by {@code name|arguments} (TOOL). When a run is
 * re-driven with the same input, already-completed nodes hit the cache and short-circuit the real
 * call — which is exactly "skip already-done work", i.e. resume / failure recovery. Counters let
 * tests assert how many nodes were replayed vs. executed for real.</p>
 */
public class ResumeCache {

    private final Map<String, AgentModelResult> modelResults = new LinkedHashMap<String, AgentModelResult>();
    private final Map<String, String> toolOutputs = new LinkedHashMap<String, String>();
    private final AtomicLong modelHits = new AtomicLong();
    private final AtomicLong modelMisses = new AtomicLong();
    private final AtomicLong toolHits = new AtomicLong();
    private final AtomicLong toolMisses = new AtomicLong();

    /** Looks up a cached model result; counts a hit if present, a miss otherwise. */
    public AgentModelResult lookupModel(String promptKey) {
        AgentModelResult r = promptKey == null ? null : modelResults.get(promptKey);
        if (r != null) {
            modelHits.incrementAndGet();
        } else {
            modelMisses.incrementAndGet();
        }
        return r;
    }

    public void recordModel(String promptKey, AgentModelResult result) {
        if (promptKey != null && result != null) {
            modelResults.put(promptKey, result);
        }
    }

    public String lookupTool(String toolKey) {
        String out = toolKey == null ? null : toolOutputs.get(toolKey);
        if (out != null) {
            toolHits.incrementAndGet();
        } else {
            toolMisses.incrementAndGet();
        }
        return out;
    }

    public void recordTool(String toolKey, String output) {
        if (toolKey != null && output != null) {
            toolOutputs.put(toolKey, output);
        }
    }

    /** Removes the most-recently-recorded model entry (simulate "crashed before the last step"). */
    public void removeLastModelEntry() {
        String lastKey = null;
        for (String key : modelResults.keySet()) {
            lastKey = key;
        }
        if (lastKey != null) {
            modelResults.remove(lastKey);
        }
    }

    public int modelSize() { return modelResults.size(); }
    public int toolSize() { return toolOutputs.size(); }
    public long getModelHits() { return modelHits.get(); }
    public long getModelMisses() { return modelMisses.get(); }
    public long getToolHits() { return toolHits.get(); }
    public long getToolMisses() { return toolMisses.get(); }

    /** Deterministic key for a MODEL node: the serialized prompt. */
    public static String promptKey(AgentPrompt prompt) {
        if (prompt == null) {
            return null;
        }
        return com.alibaba.fastjson2.JSON.toJSONString(prompt);
    }

    /** Deterministic key for a TOOL node: {@code name|arguments}. */
    public static String toolKey(AgentToolCall call) {
        if (call == null) {
            return null;
        }
        String name = call.getName() == null ? "" : call.getName();
        String args = call.getArguments() == null ? "" : call.getArguments();
        return name + "|" + args;
    }
}
