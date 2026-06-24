package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * Round-trip tests for {@link ResumeCache#saveToJson} / {@link ResumeCache#loadFromJson}: a cache
 * saved to disk and loaded into a fresh instance (simulating a restart) must preserve its entries
 * so failure recovery can resume across process boundaries.
 */
public class ResumeCachePersistenceTest {

    @Test
    public void saveAndLoadRoundTripsModelAndToolEntries() throws Exception {
        ResumeCache cache = new ResumeCache();
        cache.recordModel("prompt-key-1", AgentModelResult.builder().outputText("out-1").build());
        cache.recordTool("echo|{\"text\":\"hi\"}", "echo result");

        Path tmp = Files.createTempFile("resume-cache-", ".json");
        tmp.toFile().deleteOnExit();
        cache.saveToJson(tmp);

        // fresh instance loaded from disk = "restart"
        ResumeCache loaded = ResumeCache.loadFromJson(tmp);
        AgentModelResult r = loaded.lookupModel("prompt-key-1");
        assertEquals("out-1", r.getOutputText());
        assertEquals("echo result", loaded.lookupTool("echo|{\"text\":\"hi\"}"));
    }

    @Test
    public void loadAbsentFileReturnsEmptyCache() throws Exception {
        ResumeCache loaded = ResumeCache.loadFromJson(Paths.get("no-such-resume-cache.json"));
        assertEquals(0, loaded.modelSize());
        assertEquals(0, loaded.toolSize());
    }
}
