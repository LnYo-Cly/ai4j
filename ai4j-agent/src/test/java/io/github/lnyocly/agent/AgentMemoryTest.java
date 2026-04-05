package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.memory.WindowedMemoryCompressor;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AgentMemoryTest {

    @Test
    public void test_windowed_compression() {
        InMemoryAgentMemory memory = new InMemoryAgentMemory(new WindowedMemoryCompressor(2));
        memory.addUserInput("one");
        memory.addUserInput("two");
        memory.addUserInput("three");

        List<Object> items = memory.getItems();
        Assert.assertEquals(2, items.size());
    }
}
