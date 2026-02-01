package io.github.lnyocly.ai4j.platform.openai.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseError;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseStreamEvent;


public final class ResponseEventParser {

    private ResponseEventParser() {
    }

    public static ResponseStreamEvent parse(ObjectMapper mapper, String data) throws Exception {
        JsonNode node = mapper.readTree(data);
        ResponseStreamEvent event = new ResponseStreamEvent();
        event.setRaw(node);
        event.setType(asText(node, "type"));
        event.setSequenceNumber(asInt(node, "sequence_number"));
        event.setOutputIndex(asInt(node, "output_index"));
        event.setContentIndex(asInt(node, "content_index"));
        event.setItemId(asText(node, "item_id"));
        event.setDelta(asText(node, "delta"));
        event.setText(asText(node, "text"));
        event.setArguments(asText(node, "arguments"));
        event.setCallId(asText(node, "call_id"));
        if (node.has("response") && !node.get("response").isNull()) {
            event.setResponse(mapper.treeToValue(node.get("response"), Response.class));
        }
        if (node.has("error") && !node.get("error").isNull()) {
            event.setError(mapper.treeToValue(node.get("error"), ResponseError.class));
        }
        return event;
    }

    private static String asText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer asInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }
}

