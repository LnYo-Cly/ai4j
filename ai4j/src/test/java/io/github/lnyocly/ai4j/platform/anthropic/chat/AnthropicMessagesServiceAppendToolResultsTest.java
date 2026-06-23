package io.github.lnyocly.ai4j.platform.anthropic.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link AnthropicMessagesService#appendToolResults} — the substantive step of the unified
 * adapter's auto-invoke tool loop: it appends the assistant's tool_use content + a tool_result
 * built by invoking the registered tool. (The full HTTP loop is low-traffic; this covers its core.)
 */
public class AnthropicMessagesServiceAppendToolResultsTest {

    public static class EchoRequest {
        private String text;
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class EchoFunction {
        public String apply(EchoRequest req) {
            return "echo:" + (req == null ? "" : req.getText());
        }
    }

    @Before
    public void registerTool() {
        ToolUtil.toolClassMap.put("echo_text", EchoFunction.class);
        ToolUtil.toolRequestMap.put("echo_text", EchoRequest.class);
    }

    @After
    public void unregisterTool() {
        ToolUtil.toolClassMap.remove("echo_text");
        ToolUtil.toolRequestMap.remove("echo_text");
    }

    @Test
    public void appendToolResultsInvokesToolAndAppendsAssistantPlusToolResult() {
        AnthropicMessage user = new AnthropicMessage();
        user.setRole("user");
        user.setContent("please echo hi");
        List<AnthropicMessage> messages = new ArrayList<AnthropicMessage>(Collections.singletonList(user));

        AnthropicChatCompletionResponse response = new AnthropicChatCompletionResponse();
        AnthropicContentBlock use = new AnthropicContentBlock();
        use.setType("tool_use");
        use.setId("toolu_1");
        use.setName("echo_text");
        use.setInput(JSON.parseObject("{\"text\":\"hi\"}"));
        response.setContent(Collections.singletonList(use));

        List<AnthropicMessage> result = AnthropicMessagesService.appendToolResults(messages, response);

        // original user + assistant(tool_use content) + user(tool_result)
        assertEquals(3, result.size());
        assertEquals("user", result.get(0).getRole());
        assertEquals("assistant", result.get(1).getRole());
        assertEquals("user", result.get(2).getRole());

        @SuppressWarnings("unchecked")
        List<AnthropicContentBlock> resultBlocks = (List<AnthropicContentBlock>) result.get(2).getContent();
        assertEquals("tool_result", resultBlocks.get(0).getType());
        assertEquals("toolu_1", resultBlocks.get(0).getToolUseId());
        String toolOutput = String.valueOf(resultBlocks.get(0).getContent());
        assertTrue("tool_result content should carry the invoked tool output: " + toolOutput,
                toolOutput.contains("echo") && toolOutput.contains("hi"));
    }
}
