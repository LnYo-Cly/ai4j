package io.github.lnyocly.listener;

import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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
    public void shouldNotWriteBlankLinesToStdoutForEscapedNewlinePayloads() throws Exception {
        RecordingSseListener listener = new RecordingSseListener();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(stdout, true, "UTF-8"));
        try {
            listener.onEvent(null, null, null,
                    "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":{\"text\":\"line1\\\\nline2\"}},\"finish_reason\":\"stop\"}]}");
        } finally {
            System.setOut(originalOut);
        }

        Assert.assertEquals("", stdout.toString(StandardCharsets.UTF_8.name()));
    }

    private static final class RecordingSseListener extends SseListener {
        @Override
        protected void send() {
        }
    }
}
