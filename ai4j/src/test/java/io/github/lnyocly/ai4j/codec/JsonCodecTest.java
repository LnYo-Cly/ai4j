package io.github.lnyocly.ai4j.codec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class JsonCodecTest {

    public static class SampleDto {
        private String name;
        private Integer count;
        private String optionalNote;

        public SampleDto() {
        }

        public SampleDto(String name, Integer count, String optionalNote) {
            this.name = name;
            this.count = count;
            this.optionalNote = optionalNote;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public String getOptionalNote() {
            return optionalNote;
        }

        public void setOptionalNote(String optionalNote) {
            this.optionalNote = optionalNote;
        }
    }

    @Test
    public void exposesConfiguredSingletonMappers() {
        ObjectMapper protocol = JsonCodec.protocolMapper();
        ObjectMapper lenient = JsonCodec.lenientMapper();

        Assert.assertSame(protocol, JsonCodec.protocolMapper());
        Assert.assertSame(lenient, JsonCodec.lenientMapper());
        Assert.assertNotSame(protocol, lenient);

        Assert.assertFalse(protocol.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        Assert.assertEquals(
                JsonInclude.Include.NON_NULL,
                protocol.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion());

        Assert.assertTrue(lenient.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
        Assert.assertTrue(lenient.isEnabled(JsonParser.Feature.ALLOW_SINGLE_QUOTES));
        Assert.assertTrue(lenient.isEnabled(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES));
        Assert.assertTrue(lenient.isEnabled(JsonParser.Feature.ALLOW_TRAILING_COMMA));
        Assert.assertFalse(lenient.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    public void serializesAndDeserializesStrictProtocolJson() {
        SampleDto dto = new SampleDto("alpha", 3, null);
        String json = JsonCodec.toJson(dto);

        Assert.assertTrue(json.contains("\"name\":\"alpha\""));
        Assert.assertTrue(json.contains("\"count\":3"));
        Assert.assertFalse("null properties must be omitted", json.contains("optionalNote"));

        byte[] bytes = JsonCodec.toJsonBytes(dto);
        Assert.assertEquals(json, new String(bytes, StandardCharsets.UTF_8));

        SampleDto parsed = JsonCodec.fromJson("{\"name\":\"beta\",\"count\":7,\"unknownField\":true}", SampleDto.class);
        Assert.assertEquals("beta", parsed.getName());
        Assert.assertEquals(Integer.valueOf(7), parsed.getCount());
    }

    @Test
    public void deserializesTypeReferenceAndJavaType() {
        String listJson = "[{\"name\":\"a\",\"count\":1},{\"name\":\"b\",\"count\":2}]";
        List<SampleDto> list = JsonCodec.fromJson(listJson, new TypeReference<List<SampleDto>>() {
        });
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("b", list.get(1).getName());

        JavaType mapType = JsonCodec.protocolMapper().getTypeFactory()
                .constructMapType(Map.class, String.class, SampleDto.class);
        Map<String, SampleDto> map = JsonCodec.fromJson("{\"k\":{\"name\":\"mapped\",\"count\":9}}", mapType);
        Assert.assertEquals("mapped", map.get("k").getName());
        Assert.assertEquals(Integer.valueOf(9), map.get("k").getCount());
    }

    @Test
    public void throwsUncheckedJsonCodecExceptionOnInvalidOrBlankInput() {
        assertThrowsCodecException(new Runnable() {
            @Override
            public void run() {
                JsonCodec.fromJson("   ", SampleDto.class);
            }
        });
        assertThrowsCodecException(new Runnable() {
            @Override
            public void run() {
                JsonCodec.fromJson((String) null, SampleDto.class);
            }
        });
        assertThrowsCodecException(new Runnable() {
            @Override
            public void run() {
                JsonCodec.fromJson("{invalid", SampleDto.class);
            }
        });
        assertThrowsCodecException(new Runnable() {
            @Override
            public void run() {
                JsonCodec.readTree("not-json");
            }
        });
    }

    @Test
    public void parsesLenientLlmOutputsWithFencesProseAndComments() {
        String fencedWithProse = "Sure! Here is the structured response:\n"
                + "```json\n"
                + "{\n"
                + "  // model annotation\n"
                + "  name: 'gamma',\n"
                + "  'count': 42,\n"
                + "  extra: 'ignored',\n"
                + "}\n"
                + "```\n"
                + "Let me know if you need anything else.";

        SampleDto dto = JsonCodec.fromJsonLenient(fencedWithProse, SampleDto.class);
        Assert.assertEquals("gamma", dto.getName());
        Assert.assertEquals(Integer.valueOf(42), dto.getCount());

        String arrayProse = "[INFO] Result list: ```\n[{name: 'x', count: 1,}, {name: 'y', count: 2,}]\n```";
        List<SampleDto> items = JsonCodec.fromJsonLenient(arrayProse, new TypeReference<List<SampleDto>>() {
        });
        Assert.assertEquals(2, items.size());
        Assert.assertEquals("y", items.get(1).getName());

        JsonNode tree = JsonCodec.readTreeLenient("[Note] Output is {'ok': true, list: [1, 2,],}");
        Assert.assertTrue(tree.get("ok").asBoolean());
        Assert.assertEquals(2, tree.get("list").size());
    }

    @Test
    public void extractJsonPayloadKeepsPlainJsonIntactAndStripsWrappers() {
        String plainWithEmbeddedTicks = "{\"markdown\":\"```json\\n{\\\"inner\\\":1}\\n```\",\"ok\":true}";
        Assert.assertEquals(plainWithEmbeddedTicks, JsonCodec.extractJsonPayload(plainWithEmbeddedTicks));

        String plainArray = "  [1, 2, 3]  ";
        Assert.assertEquals("[1, 2, 3]", JsonCodec.extractJsonPayload(plainArray));

        String singleLineFence = "```JSON {\"a\":1} ```";
        Assert.assertEquals("{\"a\":1}", JsonCodec.extractJsonPayload(singleLineFence));

        String unclosedFence = "Output:\n```json\n{\"a\": 1}";
        Assert.assertEquals("{\"a\": 1}", JsonCodec.extractJsonPayload(unclosedFence));

        String proseWrapped = "Here is the payload: {\"a\":\"b {c}\"} thanks!";
        Assert.assertEquals("{\"a\":\"b {c}\"}", JsonCodec.extractJsonPayload(proseWrapped));

        String trailingProse = "{\"a\":1}\nExplanation: done.";
        Assert.assertEquals("{\"a\":1}", JsonCodec.extractJsonPayload(trailingProse));

        String bracketPrefixedProse = "[Note] {\"a\": 1}";
        Assert.assertEquals("{\"a\": 1}", JsonCodec.extractJsonPayload(bracketPrefixedProse));

        Assert.assertNull(JsonCodec.extractJsonPayload(null));
        Assert.assertEquals("", JsonCodec.extractJsonPayload("   "));
    }

    @Test
    public void supportsTreeHelpers() {
        ObjectNode obj = JsonCodec.createObjectNode();
        obj.put("name", "tree");
        ArrayNode arr = JsonCodec.createArrayNode();
        arr.add("one").add("two");
        obj.set("items", arr);

        JsonNode parsed = JsonCodec.readTree(JsonCodec.toJson(obj));
        Assert.assertEquals("tree", parsed.get("name").asText());
        Assert.assertEquals(2, parsed.get("items").size());

        SampleDto source = new SampleDto("converted", 5, null);
        JsonNode convertedTree = JsonCodec.valueToTree(source);
        Assert.assertEquals("converted", convertedTree.get("name").asText());
        Assert.assertFalse(convertedTree.has("optionalNote"));

        SampleDto roundTrip = JsonCodec.treeToValue(convertedTree, SampleDto.class);
        Assert.assertEquals("converted", roundTrip.getName());
        Assert.assertEquals(Integer.valueOf(5), roundTrip.getCount());
        Assert.assertNull(JsonCodec.treeToValue(null, SampleDto.class));
    }

    private static void assertThrowsCodecException(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected JsonCodecException");
        } catch (JsonCodecException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }
}
