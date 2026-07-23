package io.github.lnyocly.ai4j.agent.rag;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.rag.RagQuery;
import io.github.lnyocly.ai4j.rag.RagResult;
import io.github.lnyocly.ai4j.rag.RagService;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RagToolTest {

    @Test
    public void builderBindsServerSideFilterWithoutExposingItToModelSchema() throws Exception {
        CapturingRagService ragService = new CapturingRagService();
        Map<String, Object> filter = new HashMap<String, Object>();
        filter.put("tenant", "tenant_a");

        RagTool ragTool = RagTool.builder(ragService)
                .dataset("shared-kb")
                .embeddingModel("embed")
                .topK(3)
                .filter(filter)
                .build();

        filter.put("tenant", "tenant_b");

        Tool tool = ragTool.tool();
        Map<String, Tool.Function.Property> properties = tool.getFunction()
                .getParameters()
                .getProperties();
        Assert.assertTrue(properties.containsKey("query"));
        Assert.assertFalse(properties.containsKey("filter"));

        String output = ragTool.executor().execute(AgentToolCall.builder()
                .name("knowledge_search")
                .arguments("{\"query\":\"policy\",\"filter\":{\"tenant\":\"attacker\"}}")
                .build());

        Assert.assertEquals("ctx", output);
        Assert.assertEquals("policy", ragService.lastQuery.getQuery());
        Assert.assertEquals("shared-kb", ragService.lastQuery.getDataset());
        Assert.assertEquals("embed", ragService.lastQuery.getEmbeddingModel());
        Assert.assertEquals(Integer.valueOf(3), ragService.lastQuery.getTopK());
        Assert.assertEquals("tenant_a", ragService.lastQuery.getFilter().get("tenant"));
    }

    private static class CapturingRagService implements RagService {
        private RagQuery lastQuery;

        @Override
        public RagResult search(RagQuery query) {
            this.lastQuery = query;
            return RagResult.builder().context("ctx").build();
        }
    }
}
