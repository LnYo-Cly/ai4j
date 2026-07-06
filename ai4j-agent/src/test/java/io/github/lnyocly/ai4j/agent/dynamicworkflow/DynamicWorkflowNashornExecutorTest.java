package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DynamicWorkflowNashornExecutorTest {

    @Test
    public void executesPhaseLogAndAgentWithModernSyntaxNormalizer() {
        RecordingBridge bridge = new RecordingBridge();
        NashornDynamicWorkflowExecutor executor = new NashornDynamicWorkflowExecutor(bridge);
        JSONObject args = new JSONObject();
        args.put("file", "src/App.java");
        DynamicWorkflowRequest request = DynamicWorkflowRequest.builder()
                .workflowSpecVersion("ai4j.dynamic-workflow/v1")
                .script("export const meta = { name: 'demo', phases: [{ title: 'Scan' }] }\n"
                        + "phase('Scan')\n"
                        + "log('starting', { file: args.file })\n"
                        + "var first = await agent('audit ' + args.file, { label: 'audit' })\n"
                        + "return first")
                .args(args)
                .build();

        DynamicWorkflowExecutionResult result = executor.execute(request);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_COMPLETED, result.getStatus());
        Assert.assertEquals("audit:audit src/App.java", result.getOutput());
        Assert.assertEquals(1, result.getPhases().size());
        Assert.assertEquals("Scan", result.getPhases().get(0));
        Assert.assertEquals(1, result.getLogs().size());
        Assert.assertEquals("starting", result.getLogs().get(0).getMessage());
        Assert.assertEquals(1, result.getAgentCalls().size());
        Assert.assertEquals("audit", result.getAgentCalls().get(0).getLabel());
        Assert.assertEquals(1, bridge.requests.size());
    }

    @Test
    public void executesParallelGroupAndPreservesResultOrder() {
        RecordingBridge bridge = new RecordingBridge();
        NashornDynamicWorkflowExecutor executor = new NashornDynamicWorkflowExecutor(bridge);
        DynamicWorkflowRequest request = DynamicWorkflowRequest.builder()
                .script("phase('Fanout')\n"
                        + "var xs = parallel([\n"
                        + "  () => agent('one', { label: 'one' }),\n"
                        + "  () => agent('two', { label: 'two' })\n"
                        + "])\n"
                        + "return xs.join('|')")
                .build();

        DynamicWorkflowExecutionResult result = executor.execute(request);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_COMPLETED, result.getStatus());
        Assert.assertEquals("one:one|two:two", result.getOutput());
        Assert.assertEquals(2, result.getAgentCalls().size());
        Assert.assertTrue(hasTrace(result, "parallel_start"));
        Assert.assertTrue(hasTrace(result, "parallel_end"));
    }

    @Test
    public void executesPipelinePrimitive() {
        RecordingBridge bridge = new RecordingBridge();
        NashornDynamicWorkflowExecutor executor = new NashornDynamicWorkflowExecutor(bridge);
        DynamicWorkflowRequest request = DynamicWorkflowRequest.builder()
                .script("var result = pipeline([\n"
                        + "  function(input) { return agent('first ' + input, { label: 'first' }); },\n"
                        + "  function(input) { return agent('second ' + input, { label: 'second' }); }\n"
                        + "], 'seed')\n"
                        + "return result")
                .build();

        DynamicWorkflowExecutionResult result = executor.execute(request);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_COMPLETED, result.getStatus());
        Assert.assertEquals("second:second first:first seed", result.getOutput());
        Assert.assertEquals(2, result.getAgentCalls().size());
        Assert.assertTrue(hasTrace(result, "pipeline_start"));
        Assert.assertTrue(hasTrace(result, "pipeline_end"));
    }

    @Test
    public void hidesJavaInteropAndRawBridgeByDefault() {
        NashornDynamicWorkflowExecutor executor = new NashornDynamicWorkflowExecutor(new RecordingBridge());
        DynamicWorkflowRequest request = DynamicWorkflowRequest.builder()
                .script("return [typeof Java, typeof Packages, typeof java, typeof load, typeof __ai4j_dynamic_workflow_bridge].join('|')")
                .build();

        DynamicWorkflowExecutionResult result = executor.execute(request);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_COMPLETED, result.getStatus());
        Assert.assertEquals("undefined|undefined|undefined|undefined|undefined", result.getOutput());
    }

    @Test
    public void rejectsJavaTypeByDefault() {
        NashornDynamicWorkflowExecutor executor = new NashornDynamicWorkflowExecutor(new RecordingBridge());
        DynamicWorkflowRequest request = DynamicWorkflowRequest.builder()
                .script("return Java.type('java.lang.System').getProperty('user.home')")
                .build();

        DynamicWorkflowExecutionResult result = executor.execute(request);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_FAILED, result.getStatus());
        Assert.assertNotNull(result.getError());
    }

    @Test
    public void returnsFailureWhenMaxAgentsExceeded() {
        RecordingBridge bridge = new RecordingBridge();
        NashornDynamicWorkflowExecutor executor = new NashornDynamicWorkflowExecutor(bridge);
        DynamicWorkflowRequest request = DynamicWorkflowRequest.builder()
                .maxAgents(Integer.valueOf(1))
                .script("agent('one', { label: 'one' })\n"
                        + "agent('two', { label: 'two' })\n"
                        + "return 'done'")
                .build();

        DynamicWorkflowExecutionResult result = executor.execute(request);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_FAILED, result.getStatus());
        Assert.assertTrue(result.getError().contains("maxAgents"));
        Assert.assertEquals(1, result.getAgentCalls().size());
    }

    @Test
    public void returnsFailureForInvalidScript() {
        NashornDynamicWorkflowExecutor executor = new NashornDynamicWorkflowExecutor(new RecordingBridge());

        DynamicWorkflowExecutionResult result = executor.execute(DynamicWorkflowRequest.builder()
                .script("function ()")
                .build());

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_FAILED, result.getStatus());
        Assert.assertNotNull(result.getError());
    }

    private boolean hasTrace(DynamicWorkflowExecutionResult result, String type) {
        if (result.getTrace() == null) {
            return false;
        }
        for (DynamicWorkflowTraceEvent event : result.getTrace()) {
            if (type.equals(event.getType())) {
                return true;
            }
        }
        return false;
    }

    private static class RecordingBridge implements DynamicWorkflowAgentBridge {
        private final List<DynamicWorkflowAgentCallRequest> requests = new ArrayList<DynamicWorkflowAgentCallRequest>();

        @Override
        public String runAgent(DynamicWorkflowAgentCallRequest request) {
            requests.add(request);
            return request.getLabel() + ":" + request.getPrompt();
        }
    }
}
