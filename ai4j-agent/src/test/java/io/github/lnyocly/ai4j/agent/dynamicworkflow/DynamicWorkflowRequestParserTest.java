package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.util.AgentJson;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class DynamicWorkflowRequestParserTest {

    @Test
    public void parsesPluginEnvelopeArgumentsRaw() {
        ObjectNode arguments = AgentJson.createObjectNode();
        arguments.put("script", "return 'ok'");
        ObjectNode args = AgentJson.createObjectNode();
        args.put("file", "src/App.java");
        arguments.set("args", args);
        arguments.put("background", Boolean.TRUE);
        arguments.put("maxAgents", Integer.valueOf(3));
        arguments.put("tokenBudget", Integer.valueOf(1024));
        arguments.put("workflowSpecVersion", "ai4j.dynamic-workflow/v1");

        ObjectNode envelope = AgentJson.createObjectNode();
        envelope.put("type", DynamicWorkflowConstants.ENVELOPE_TYPE);
        envelope.put("source", "tool");
        envelope.put("tool", "workflow");
        envelope.put("status", "pending_host_workflow_execution");
        envelope.put("hostAction", DynamicWorkflowConstants.EXECUTE_HOST_ACTION);
        envelope.put("scriptRuntime", DynamicWorkflowConstants.SCRIPT_RUNTIME_HOST_MEDIATED);
        envelope.put("argumentsRaw", AgentJson.toJson(arguments));

        String envelopeJson = AgentJson.toJson(envelope);
        DynamicWorkflowRequest request = DynamicWorkflowRequestParser.parse(envelopeJson);

        Assert.assertTrue(DynamicWorkflowRequestParser.isDynamicWorkflowEnvelope(envelopeJson));
        Assert.assertEquals("return 'ok'", request.getScript());
        Assert.assertEquals(Boolean.TRUE, request.getBackground());
        Assert.assertEquals(Integer.valueOf(3), request.getMaxAgents());
        Assert.assertEquals(Integer.valueOf(1024), request.getTokenBudget());
        Assert.assertEquals("ai4j.dynamic-workflow/v1", request.getWorkflowSpecVersion());
        Assert.assertTrue(request.getArgs() instanceof Map);
        Assert.assertEquals("src/App.java", ((Map<?, ?>) request.getArgs()).get("file"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonWorkflowEnvelope() {
        ObjectNode envelope = AgentJson.createObjectNode();
        envelope.put("type", "other");
        envelope.put("hostAction", DynamicWorkflowConstants.EXECUTE_HOST_ACTION);
        DynamicWorkflowRequestParser.parse(AgentJson.toJson(envelope));
    }
}
