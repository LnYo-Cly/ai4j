package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Offline tests for the resume-or-capture decorators (no model/network): full resume returns cached
 * results with zero delegate calls; partial resume re-runs only the missing step; tool side effects
 * are skipped on the second identical call.
 */
public class ResumeCacheTest {

    @Test
    public void modelClientShouldCaptureOnFirstRunAndResumeWithZeroCallsOnSecond() throws Exception {
        RecordingModelClient real1 = new RecordingModelClient();
        RecordingModelClient real2 = new RecordingModelClient();
        ResumeCache cache = new ResumeCache();
        ResumableModelClient run1 = new ResumableModelClient(real1, cache);
        ResumableModelClient run2 = new ResumableModelClient(real2, cache);

        AgentPrompt promptA = AgentPrompt.builder().model("m").systemPrompt("sa").build();
        AgentPrompt promptB = AgentPrompt.builder().model("m").systemPrompt("sb").build();

        // run 1: both miss -> real calls + capture
        run1.create(promptA);
        run1.create(promptB);
        assertEquals("run1 made 2 real calls", 2, real1.calls);
        assertEquals(2, cache.modelSize());

        // run 2: both hit -> NO real calls
        run2.create(promptA);
        run2.create(promptB);
        assertEquals("run2 resumed everything, zero real calls", 0, real2.calls);
    }

    @Test
    public void partialResumeShouldReRunOnlyTheMissingStep() throws Exception {
        RecordingModelClient real1 = new RecordingModelClient();
        RecordingModelClient real3 = new RecordingModelClient();
        ResumeCache cache = new ResumeCache();
        ResumableModelClient run1 = new ResumableModelClient(real1, cache);

        AgentPrompt promptA = AgentPrompt.builder().model("m").systemPrompt("sa").build();
        AgentPrompt promptB = AgentPrompt.builder().model("m").systemPrompt("sb").build();
        run1.create(promptA);
        run1.create(promptB);

        // simulate "crashed before the last step": drop the most-recently-recorded model entry
        cache.removeLastModelEntry();
        assertEquals(1, cache.modelSize());

        ResumableModelClient run3 = new ResumableModelClient(real3, cache);
        run3.create(promptA); // hit
        run3.create(promptB); // miss -> 1 real call
        assertEquals("partial resume re-runs only the missing step", 1, real3.calls);
    }

    @Test
    public void resumedModelReturnsTheExactCapturedResult() throws Exception {
        RecordingModelClient real = new RecordingModelClient();
        ResumeCache cache = new ResumeCache();
        ResumableModelClient run1 = new ResumableModelClient(real, cache);
        AgentPrompt prompt = AgentPrompt.builder().model("m").build();
        AgentModelResult captured = run1.create(prompt);

        RecordingModelClient real2 = new RecordingModelClient();
        ResumableModelClient run2 = new ResumableModelClient(real2, cache);
        AgentModelResult resumed = run2.create(prompt);
        assertSame("resume must return the exact cached result object", captured, resumed);
    }

    @Test
    public void toolExecutorShouldSkipSideEffectOnSecondIdenticalCall() throws Exception {
        RecordingToolExecutor real = new RecordingToolExecutor();
        ResumeCache cache = new ResumeCache();
        ResumableToolExecutor rte = new ResumableToolExecutor(real, cache);

        AgentToolCall call = AgentToolCall.builder().name("write_file").arguments("{\"path\":\"/a\",\"c\":1}").build();
        rte.execute(call); // miss -> real
        rte.execute(call); // hit -> skipped
        rte.execute(call); // hit -> skipped
        assertEquals("side effect performed exactly once for 3 identical calls", 1, real.calls);

        // a different argument is a different node -> real
        AgentToolCall call2 = AgentToolCall.builder().name("write_file").arguments("{\"path\":\"/b\",\"c\":1}").build();
        rte.execute(call2);
        assertEquals(2, real.calls);
    }

    static final class RecordingModelClient implements AgentModelClient {
        int calls;
        public AgentModelResult create(AgentPrompt prompt) {
            calls++;
            return AgentModelResult.builder().outputText("out-" + calls).build();
        }
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            calls++;
            return AgentModelResult.builder().outputText("out-" + calls).build();
        }
    }

    static final class RecordingToolExecutor implements ToolExecutor {
        int calls;
        public String execute(AgentToolCall call) {
            calls++;
            return "tool-out-" + calls;
        }
    }
}
