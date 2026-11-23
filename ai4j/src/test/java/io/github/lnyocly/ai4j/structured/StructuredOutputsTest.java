package io.github.lnyocly.ai4j.structured;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.service.IChatService;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StructuredOutputsTest {

    public static class CityWeather {
        private String city;
        private Integer temp;

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public Integer getTemp() { return temp; }
        public void setTemp(Integer temp) { this.temp = temp; }
    }

    public static class Envelope<T> {
        private T data;
        private List<String> notes;

        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        public List<String> getNotes() { return notes; }
        public void setNotes(List<String> notes) { this.notes = notes; }
    }

    /** Captures the outgoing request and returns a canned response. */
    private static class FakeChatService implements IChatService {
        ChatCompletion captured;
        ChatCompletionResponse response;

        FakeChatService(ChatCompletionResponse response) {
            this.response = response;
        }

        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion c) {
            this.captured = c;
            return response;
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion c) {
            this.captured = c;
            return response;
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion c, SseListener l) {
        }

        @Override
        public void chatCompletionStream(ChatCompletion c, SseListener l) {
        }
    }

    private static ChatCompletionResponse responding(String text) {
        return responding(text, null);
    }

    private static ChatCompletionResponse responding(String text, String finishReason) {
        ChatMessage message = ChatMessage.builder()
                .role("assistant")
                .content(Content.ofText(text))
                .build();
        Choice choice = new Choice();
        choice.setMessage(message);
        choice.setFinishReason(finishReason);
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(Collections.singletonList(choice));
        return response;
    }

    private static ChatCompletion request() {
        return ChatCompletion.builder()
                .model("test-model")
                .message(ChatMessage.withUser("weather?"))
                .build();
    }

    @Test
    public void parsesJsonAnswerIntoPojo() throws Exception {
        FakeChatService service = new FakeChatService(responding("{\"city\":\"shanghai\",\"temp\":26}"));

        CityWeather weather = StructuredOutputs.chat(service, request(), CityWeather.class);

        assertEquals("shanghai", weather.getCity());
        assertEquals(Integer.valueOf(26), weather.getTemp());
    }

    @Test
    public void fillsResponseFormatWithJsonSchemaWhenAbsent() throws Exception {
        FakeChatService service = new FakeChatService(responding("{\"city\":\"sh\",\"temp\":1}"));

        StructuredOutputs.chat(service, request(), CityWeather.class);

        Object format = service.captured.getResponseFormat();
        assertNotNull("facade must attach response_format", format);
        JsonNode fmt = (JsonNode) format;
        assertEquals("json_schema", fmt.path("type").asText());
        JsonNode jsonSchema = fmt.path("json_schema");
        assertTrue(jsonSchema.path("strict").asBoolean());
        JsonNode properties = jsonSchema.path("schema").path("properties");
        assertTrue("schema must describe POJO fields", properties.has("city"));
        assertTrue(properties.has("temp"));
    }

    @Test
    public void keepsCallerProvidedResponseFormat() throws Exception {
        FakeChatService service = new FakeChatService(responding("{\"city\":\"sh\",\"temp\":1}"));
        ObjectNode custom = JsonCodec.createObjectNode().put("type", "json_object");
        ChatCompletion req = request().toBuilder().responseFormat(custom).build();

        StructuredOutputs.chat(service, req, CityWeather.class);

        assertSame("caller-provided response_format must win", custom, service.captured.getResponseFormat());
    }

    @Test
    public void stripsMarkdownFenceBeforeParsing() throws Exception {
        FakeChatService service = new FakeChatService(
                responding("```json\n{\"city\":\"sh\",\"temp\":9}\n```"));

        CityWeather weather = StructuredOutputs.chat(service, request(), CityWeather.class);

        assertEquals("sh", weather.getCity());
        assertEquals(Integer.valueOf(9), weather.getTemp());
    }

    @Test
    public void parsesLenientJsonWithCommentsTrailingCommasAndSingleQuotes() throws Exception {
        FakeChatService service = new FakeChatService(
                responding("Here is the JSON:\n```json\n{\n  // city name\n  'city': 'hangzhou',\n  temp: 22,\n}\n```"));

        CityWeather weather = StructuredOutputs.chat(service, request(), CityWeather.class);

        assertEquals("hangzhou", weather.getCity());
        assertEquals(Integer.valueOf(22), weather.getTemp());
    }

    @Test
    public void supportsTypeReferenceOverloads() throws Exception {
        FakeChatService service = new FakeChatService(
                responding("{\"data\":{\"city\":\"nanjing\",\"temp\":18},\"notes\":[\"sunny\"]}"));

        StructuredResult<Envelope<CityWeather>> result = StructuredOutputs.chatDetailed(
                service,
                request(),
                new TypeReference<Envelope<CityWeather>>() {},
                "weather_envelope");

        assertNotNull(result.getValue());
        assertEquals("nanjing", result.getValue().getData().getCity());
        assertEquals(Integer.valueOf(18), result.getValue().getData().getTemp());
        assertEquals(Collections.singletonList("sunny"), result.getValue().getNotes());

        JsonNode fmt = (JsonNode) service.captured.getResponseFormat();
        assertEquals("weather_envelope", fmt.path("json_schema").path("name").asText());
        assertTrue(fmt.path("json_schema").path("schema").path("properties").path("data").path("properties").has("city"));
    }

    @Test
    public void refusalRaisesExceptionWithRawContent() {
        ChatMessage refused = ChatMessage.builder()
                .role("assistant")
                .refusal("I cannot provide that")
                .build();
        Choice choice = new Choice();
        choice.setMessage(refused);
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(Collections.singletonList(choice));
        FakeChatService service = new FakeChatService(response);

        try {
            StructuredOutputs.chat(service, request(), CityWeather.class);
            fail("expected StructuredOutputException on refusal");
        } catch (StructuredOutputException e) {
            assertTrue(e.getMessage().contains("refused"));
            assertEquals("I cannot provide that", e.getRawContent());
        } catch (Exception e) {
            fail("expected StructuredOutputException, got " + e);
        }
    }

    @Test
    public void truncatedOutputRaisesException() {
        FakeChatService service = new FakeChatService(responding("{\"city\":\"sh", "length"));

        try {
            StructuredOutputs.chat(service, request(), CityWeather.class);
            fail("expected StructuredOutputException on length truncation");
        } catch (StructuredOutputException e) {
            assertTrue(e.getMessage().contains("truncated"));
        } catch (Exception e) {
            fail("expected StructuredOutputException, got " + e);
        }
    }

    @Test
    public void invalidJsonRaisesExceptionWithRawContent() {
        FakeChatService service = new FakeChatService(responding("not json at all"));

        try {
            StructuredOutputs.chat(service, request(), CityWeather.class);
            fail("expected StructuredOutputException on unparseable output");
        } catch (StructuredOutputException e) {
            assertEquals("not json at all", e.getRawContent());
        } catch (Exception e) {
            fail("expected StructuredOutputException, got " + e);
        }
    }

    @Test
    public void convenienceOverloadBuildsRequest() throws Exception {
        FakeChatService service = new FakeChatService(responding("{\"city\":\"bj\",\"temp\":3}"));

        CityWeather weather = StructuredOutputs.chat(service, "test-model", "weather in bj?", CityWeather.class);

        assertEquals("bj", weather.getCity());
        assertNotNull(service.captured.getResponseFormat());
    }

    @Test
    public void detailedResultCarriesRawText() throws Exception {
        FakeChatService service = new FakeChatService(responding("{\"city\":\"gz\",\"temp\":30}"));

        StructuredResult<CityWeather> result = StructuredOutputs.chatDetailed(service, request(), CityWeather.class);

        assertEquals("gz", result.getValue().getCity());
        assertEquals("{\"city\":\"gz\",\"temp\":30}", result.getRawText());
    }
}
