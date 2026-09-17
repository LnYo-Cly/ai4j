package io.github.lnyocly.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SseListenerTest {

    @Test
    public void shouldIgnoreEmptyToolCallDeltaWhenFinishReasonIsToolCalls() {
        RecordingSseListener listener = new RecordingSseListener();

        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[]},\"finish_reason\":\"tool_calls\"}]}");
    }

    @Test
    public void shouldIgnoreToolCallDeltaWithoutFunctionPayload() {
        RecordingSseListener listener = new RecordingSseListener();
        listener.setShowToolArgs(true);

        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{}]},\"finish_reason\":null}]}");
    }

    @Test
    public void shouldFinalizeFragmentedToolCallArgumentsWhenFinishReasonArrivesWithoutDelta() {
        RecordingSseListener listener = new RecordingSseListener();

        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"bash\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"function\":{\"arguments\":\"{\\\"action\\\":\\\"exec\\\",\"}}]},\"finish_reason\":null}]}");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"function\":{\"arguments\":\"\\\"command\\\":\\\"date\\\"}\"}}]},\"finish_reason\":null}]}");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");

        Assert.assertEquals(1, listener.getToolCalls().size());
        ToolCall toolCall = listener.getToolCalls().get(0);
        Assert.assertEquals("bash", toolCall.getFunction().getName());
        Assert.assertEquals("{\"action\":\"exec\",\"command\":\"date\"}", toolCall.getFunction().getArguments());
    }

    @Test
    public void shouldAggregateMiniMaxStyleToolCallFragmentsIntoSingleCall() {
        RecordingSseListener listener = new RecordingSseListener();

        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"delegate_plan\",\"arguments\":\"{\\\"task\\\": \\\"Create a short implementation plan\"}}]},\"finish_reason\":null}]}");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"arguments\":\" for adding a hello endpoint demo app in this empty workspace.\"}}]},\"finish_reason\":null}]}");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"arguments\":\"\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}");

        Assert.assertEquals(1, listener.getToolCalls().size());
        ToolCall toolCall = listener.getToolCalls().get(0);
        Assert.assertEquals("delegate_plan", toolCall.getFunction().getName());
        Assert.assertEquals("{\"task\": \"Create a short implementation plan for adding a hello endpoint demo app in this empty workspace.\"}", toolCall.getFunction().getArguments());
    }

    @Test
    public void shouldKeepInterleavedToolCallFragmentsPairedByCallId() {
        RecordingSseListener listener = new RecordingSseListener();
        listener.setShowToolArgs(true);

        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"tool_calls\":["
                        + "{\"id\":\"call_read\",\"type\":\"function\",\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\\\"a\"}},"
                        + "{\"id\":\"call_write\",\"type\":\"function\",\"function\":{\"name\":\"write_file\",\"arguments\":\"{\\\"path\\\":\\\"b\"}}"
                        + "]},\"finish_reason\":null}]} ");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"tool_calls\":["
                        + "{\"id\":\"call_read\",\"function\":{\"arguments\":\".txt\\\"}\"}},"
                        + "{\"id\":\"call_write\",\"function\":{\"arguments\":\".txt\\\"}\"}}"
                        + "]},\"finish_reason\":null}]} ");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]} ");

        Assert.assertEquals(2, listener.getToolCalls().size());
        Assert.assertEquals("call_read", listener.getToolCalls().get(0).getId());
        Assert.assertEquals("read_file", listener.getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("{\"path\":\"a.txt\"}", listener.getToolCalls().get(0).getFunction().getArguments());
        Assert.assertEquals("call_write", listener.getToolCalls().get(1).getId());
        Assert.assertEquals("write_file", listener.getToolCalls().get(1).getFunction().getName());
        Assert.assertEquals("{\"path\":\"b.txt\"}", listener.getToolCalls().get(1).getFunction().getArguments());

        Assert.assertEquals(4, listener.toolCallbackIds.size());
        Assert.assertEquals("call_read", listener.toolCallbackIds.get(0));
        Assert.assertEquals("read_file", listener.toolCallbackNames.get(0));
        Assert.assertEquals("{\"path\":\"a", listener.toolCallbackArguments.get(0));
        Assert.assertEquals("call_write", listener.toolCallbackIds.get(1));
        Assert.assertEquals("write_file", listener.toolCallbackNames.get(1));
        Assert.assertEquals("{\"path\":\"b", listener.toolCallbackArguments.get(1));
        Assert.assertEquals("call_read", listener.toolCallbackIds.get(2));
        Assert.assertEquals("read_file", listener.toolCallbackNames.get(2));
        Assert.assertEquals(".txt\"}", listener.toolCallbackArguments.get(2));
        Assert.assertEquals("call_write", listener.toolCallbackIds.get(3));
        Assert.assertEquals("write_file", listener.toolCallbackNames.get(3));
        Assert.assertEquals(".txt\"}", listener.toolCallbackArguments.get(3));
    }

    @Test
    public void shouldUseStreamIndexWhenContinuationOmitsCallId() {
        RecordingSseListener listener = new RecordingSseListener();

        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"tool_calls\":["
                        + "{\"id\":\"call_0\",\"index\":0,\"type\":\"function\",\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\\\"a\"}},"
                        + "{\"id\":\"call_1\",\"index\":1,\"type\":\"function\",\"function\":{\"name\":\"write_file\",\"arguments\":\"{\\\"path\\\":\\\"b\"}}"
                        + "]},\"finish_reason\":null}]} ");
        listener.onEvent(null, null, null,
                "{\"choices\":[{\"delta\":{\"content\":\"\",\"tool_calls\":["
                        + "{\"index\":0,\"function\":{\"arguments\":\"\\\"}\"}},"
                        + "{\"index\":1,\"function\":{\"arguments\":\"\\\"}\"}}"
                        + "]},\"finish_reason\":\"stop\"}]} ");

        Assert.assertEquals(2, listener.getToolCalls().size());
        Assert.assertEquals("call_0", listener.getToolCalls().get(0).getId());
        Assert.assertEquals("{\"path\":\"a\"}", listener.getToolCalls().get(0).getFunction().getArguments());
        Assert.assertEquals("call_1", listener.getToolCalls().get(1).getId());
        Assert.assertEquals("{\"path\":\"b\"}", listener.getToolCalls().get(1).getFunction().getArguments());
        Assert.assertEquals("tool_calls", listener.getFinishReason());
    }

    @Test
    public void shouldReadStreamIndexButNeverSendItBackInToolCallPayloads() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolCall call = mapper.readValue(
                "{\"id\":\"call_1\",\"type\":\"function\",\"index\":0,"
                        + "\"function\":{\"name\":\"read_file\",\"arguments\":\"{}\"}}",
                ToolCall.class);

        Assert.assertEquals(Integer.valueOf(0), call.getIndex());
        String serialized = mapper.writeValueAsString(call);
        Assert.assertFalse(serialized.contains("\"index\""));
    }

    @Test
    public void shouldNotWriteBlankLinesToStdoutForEscapedNewlinePayloads() throws Exception {
        RecordingSseListener listener = new RecordingSseListener();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(stdout, true, "UTF-8"));
        try {
            listener.onEvent(null, null, null,
                    "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"line1\\\\nline2\"},\"finish_reason\":\"stop\"}]}");
        } finally {
            System.setOut(originalOut);
        }

        Assert.assertEquals("", stdout.toString(StandardCharsets.UTF_8.name()));
    }

    private static final class RecordingSseListener extends SseListener {
        private final List<String> toolCallbackIds = new ArrayList<String>();
        private final List<String> toolCallbackNames = new ArrayList<String>();
        private final List<String> toolCallbackArguments = new ArrayList<String>();

        @Override
        protected void send() {
            if (getToolCall() != null && getCurrToolName() != null && !getCurrToolName().isEmpty()) {
                toolCallbackIds.add(getToolCall().getId());
                toolCallbackNames.add(getCurrToolName());
                toolCallbackArguments.add(getCurrStr());
            }
        }
    }
}
