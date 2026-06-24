package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;

/**
 * Wraps an {@link AgentModelClient} with resume-or-capture semantics over a {@link ResumeCache}.
 *
 * <p>On a prompt already seen in the cache, returns the captured {@link AgentModelResult} without
 * calling the delegate (no LLM call) — this is how a resumed run skips completed model steps. On a
 * miss, delegates to the real client and records the result, so the same client instance both
 * captures (first run) and resumes (subsequent runs).</p>
 *
 * <p>Resume targets non-streaming runs ({@code create}). {@code createStream} also consults the
 * cache, but note that a cached stream result is returned without re-emitting deltas to the
 * listener.</p>
 */
public class ResumableModelClient implements AgentModelClient {

    private final AgentModelClient delegate;
    private final ResumeCache cache;

    public ResumableModelClient(AgentModelClient delegate, ResumeCache cache) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate model client must not be null");
        }
        if (cache == null) {
            throw new IllegalArgumentException("resume cache must not be null");
        }
        this.delegate = delegate;
        this.cache = cache;
    }

    public ResumeCache getCache() {
        return cache;
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) throws Exception {
        String key = ResumeCache.promptKey(prompt);
        AgentModelResult cached = cache.lookupModel(key);
        if (cached != null) {
            return cached;
        }
        AgentModelResult result = delegate.create(prompt);
        cache.recordModel(key, result);
        return result;
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
        String key = ResumeCache.promptKey(prompt);
        AgentModelResult cached = cache.lookupModel(key);
        if (cached != null) {
            return cached;
        }
        AgentModelResult result = delegate.createStream(prompt, listener);
        cache.recordModel(key, result);
        return result;
    }
}
