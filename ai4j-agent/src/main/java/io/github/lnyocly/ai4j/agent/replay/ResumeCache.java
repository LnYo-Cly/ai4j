package io.github.lnyocly.ai4j.agent.replay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * Persists the cache to a JSON file so failure recovery can resume across a real process
     * restart (run 1 captures + saves; after restart, run 2 loads + resumes).
     */
    public void saveToJson(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        JSONObject obj = new JSONObject();
        obj.put("modelResults", modelResults);
        obj.put("toolOutputs", toolOutputs);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, JSON.toJSONString(obj).getBytes(StandardCharsets.UTF_8));
    }

    /** Loads a cache previously written by {@link #saveToJson(Path)}; empty cache if the file is absent. */
    public static ResumeCache loadFromJson(Path path) throws IOException {
        ResumeCache cache = new ResumeCache();
        if (path == null || !Files.isRegularFile(path)) {
            return cache;
        }
        JSONObject obj = JSON.parseObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        if (obj == null) {
            return cache;
        }
        JSONObject models = obj.getJSONObject("modelResults");
        if (models != null) {
            for (String key : models.keySet()) {
                AgentModelResult r = models.getObject(key, AgentModelResult.class);
                if (r != null) {
                    cache.modelResults.put(key, r);
                }
            }
        }
        JSONObject tools = obj.getJSONObject("toolOutputs");
        if (tools != null) {
            for (String key : tools.keySet()) {
                String out = tools.getString(key);
                if (out != null) {
                    cache.toolOutputs.put(key, out);
                }
            }
        }
        return cache;
    }

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
