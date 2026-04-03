package io.github.lnyocly.ai4j.agent.util;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseContentPart;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseItem;

import java.util.ArrayList;
import java.util.List;

public final class ResponseUtil {

    private ResponseUtil() {
    }

    public static String extractOutputText(Response response) {
        if (response == null || response.getOutput() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ResponseItem item : response.getOutput()) {
            if (item.getContent() == null) {
                continue;
            }
            for (ResponseContentPart part : item.getContent()) {
                if (part == null) {
                    continue;
                }
                if ("output_text".equals(part.getType()) && part.getText() != null) {
                    builder.append(part.getText());
                }
            }
        }
        return builder.toString();
    }

    public static List<AgentToolCall> extractToolCalls(Response response) {
        List<AgentToolCall> calls = new ArrayList<>();
        if (response == null || response.getOutput() == null) {
            return calls;
        }
        for (ResponseItem item : response.getOutput()) {
            if (item == null) {
                continue;
            }
            if (item.getCallId() != null && item.getName() != null && item.getArguments() != null) {
                calls.add(AgentToolCall.builder()
                        .callId(item.getCallId())
                        .name(item.getName())
                        .arguments(item.getArguments())
                        .type(item.getType())
                        .build());
            }
        }
        return calls;
    }
}
