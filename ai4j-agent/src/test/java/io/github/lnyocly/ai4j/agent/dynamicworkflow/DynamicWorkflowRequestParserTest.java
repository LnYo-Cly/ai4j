package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class DynamicWorkflowRequestParserTest {

    @Test
    public void parsesPluginEnvelopeArgumentsRaw() {
        JSONObject arguments = new JSONObject();
        arguments.put("script", "return 'ok'");
        JSONObject args = new JSONObject();
        args.put("file", "src/App.java");
        arguments.put("args", args);
        arguments.put("background", Boolean.TRUE);
        arguments.put("maxAgents", Integer.valueOf(3));
        arguments.put("tokenBudget", Integer.valueOf(1024));
        arguments.put("workflowSpecVersion", "ai4j.dynamic-workflow/v1");

        JSONObject envelope = new JSONObject();
        envelope.put("type", DynamicWorkflowConstants.ENVELOPE_TYPE);
        envelope.put("source", "tool");
        envelope.put("tool", "workflow");
        envelope.put("status", "pending_host_workflow_execution");
        envelope.put("hostAction", DynamicWorkflowConstants.EXECUTE_HOST_ACTION);
        envelope.put("scriptRuntime", DynamicWorkflowConstants.SCRIPT_RUNTIME_HOST_MEDIATED);
        envelope.put("argumentsRaw", arguments.toJSONString());

        DynamicWorkflowRequest request = DynamicWorkflowRequestParser.parse(envelope.toJSONString());

        Assert.assertTrue(DynamicWorkflowRequestParser.isDynamicWorkflowEnvelope(envelope.toJSONString()));
        Assert.assertEquals("return 'ok'", request.getScript());
        Assert.assertEquals(Boolean.TRUE, request.getBackground());
        Assert.assertEquals(Integer.valueOf(3), request.getMaxAgents());
        Assert.assertEquals(Integer.valueOf(1024), request.getTokenBudget());
        Assert.assertEquals("ai4j.dynamic-workflow/v1", request.getWorkflowSpecVersion());
        Assert.assertTrue(request.getArgs() instanceof JSONObject);
        Assert.assertEquals("src/App.java", ((JSONObject) request.getArgs()).getString("file"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonWorkflowEnvelope() {
        JSONObject envelope = new JSONObject();
        envelope.put("type", "other");
        envelope.put("hostAction", DynamicWorkflowConstants.EXECUTE_HOST_ACTION);
        DynamicWorkflowRequestParser.parse(envelope.toJSONString());
    }
}
