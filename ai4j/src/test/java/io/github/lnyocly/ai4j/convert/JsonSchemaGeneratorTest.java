package io.github.lnyocly.ai4j.convert;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests {@link JsonSchemaGenerator} — Java class & TypeReference → JSON Schema generation.
 */
public class JsonSchemaGeneratorTest {

    // --- Test fixtures ---

    public enum Sentiment { POSITIVE, NEGATIVE, NEUTRAL }

    public static class Address {
        private String city;
        private String zipCode;
    }

    public static class Person {
        private String name;
        private Integer age;
        private Boolean active;
        private Double score;
        private Sentiment sentiment;
        private Address address;
        private List<String> tags;
        private int primitiveCount;
    }

    public static class GenericEnvelope<T> {
        @JsonProperty("payload_item")
        private T payload;
        private List<T> items;
    }

    private static Set<String> toStringSet(JsonNode arrayNode) {
        Set<String> set = new HashSet<String>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                set.add(item.asText());
            }
        }
        return set;
    }

    // --- Tests ---

    @Test
    public void generatesBasicObjectSchema() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(Address.class);

        assertEquals("object", schema.path("type").asText());
        assertFalse(schema.path("additionalProperties").asBoolean());

        JsonNode props = schema.path("properties");
        assertTrue(props.isObject());
        assertTrue(props.has("city"));
        assertTrue(props.has("zipCode"));

        Set<String> required = toStringSet(schema.path("required"));
        assertTrue("city should be required", required.contains("city"));
        assertTrue("zipCode should be required", required.contains("zipCode"));
    }

    @Test
    public void mapsJavaTypesToJsonTypes() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(Person.class);
        JsonNode props = schema.path("properties");

        assertEquals("string", props.path("name").path("type").asText());
        assertEquals("integer", props.path("age").path("type").asText());
        assertEquals("boolean", props.path("active").path("type").asText());
        assertEquals("number", props.path("score").path("type").asText());
        assertEquals("integer", props.path("primitiveCount").path("type").asText());
    }

    @Test
    public void enumGeneratesStringWithEnumValues() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(Person.class);
        JsonNode sentiment = schema.path("properties").path("sentiment");

        assertEquals("string", sentiment.path("type").asText());
        JsonNode valuesNode = sentiment.path("enum");
        assertTrue(valuesNode.isArray());
        assertEquals(3, valuesNode.size());
        Set<String> values = toStringSet(valuesNode);
        assertTrue(values.contains("POSITIVE"));
        assertTrue(values.contains("NEGATIVE"));
        assertTrue(values.contains("NEUTRAL"));
    }

    @Test
    public void nestedObjectRecurses() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(Person.class);
        JsonNode address = schema.path("properties").path("address");

        assertEquals("object", address.path("type").asText());
        assertTrue(address.path("properties").has("city"));
        assertTrue(address.path("properties").has("zipCode"));
        assertFalse(address.path("additionalProperties").asBoolean());
    }

    @Test
    public void listGeneratesArrayWithItems() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(Person.class);
        JsonNode tags = schema.path("properties").path("tags");

        assertEquals("array", tags.path("type").asText());
        assertEquals("string", tags.path("items").path("type").asText());
    }

    @Test
    public void allFieldsRequired() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(Person.class);
        ArrayNode required = (ArrayNode) schema.path("required");

        assertEquals(8, required.size());
        Set<String> requiredSet = toStringSet(required);
        for (String field : new String[]{"name", "age", "active", "score", "sentiment", "address", "tags", "primitiveCount"}) {
            assertTrue(field + " should be required", requiredSet.contains(field));
        }
    }

    @Test
    public void responseFormatProducesCorrectStructure() {
        ObjectNode rf = JsonSchemaGenerator.responseFormat(Address.class, "address_schema");

        assertEquals("json_schema", rf.path("type").asText());

        JsonNode inner = rf.path("json_schema");
        assertEquals("address_schema", inner.path("name").asText());
        assertTrue(inner.path("strict").asBoolean());
        assertTrue(inner.path("schema").isObject());
        assertEquals("object", inner.path("schema").path("type").asText());
    }

    @Test
    public void generateReturnsString() {
        String json = JsonSchemaGenerator.generate(Address.class);
        JsonNode parsed = JsonCodec.readTree(json);

        assertEquals("object", parsed.path("type").asText());
        assertFalse(parsed.path("additionalProperties").asBoolean());
    }

    @Test
    public void listOListGeneratesArrayOfArrays() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(ListOfLists.class);
        JsonNode matrix = schema.path("properties").path("matrix");

        assertEquals("array", matrix.path("type").asText());
        JsonNode innerItems = matrix.path("items");
        assertEquals("array", innerItems.path("type").asText());
        assertEquals("string", innerItems.path("items").path("type").asText());
    }

    public static class ListOfLists {
        private List<List<String>> matrix;
    }

    @Test
    public void listOfNestedObjects() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(Team.class);
        JsonNode members = schema.path("properties").path("members");

        assertEquals("array", members.path("type").asText());
        JsonNode memberItem = members.path("items");
        assertEquals("object", memberItem.path("type").asText());
        assertTrue(memberItem.path("properties").has("name"));
    }

    public static class Team {
        private String teamName;
        private List<Person> members;
    }

    @Test
    public void staticAndTransientFieldsExcluded() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(WithStatic.class);
        JsonNode props = schema.path("properties");

        assertTrue(props.has("normalField"));
        assertFalse("static field should be excluded", props.has("staticField"));
        assertFalse("transient field should be excluded", props.has("transientField"));
    }

    public static class WithStatic {
        private String normalField;
        private static String staticField = "ignore";
        private transient String transientField;
    }

    @Test
    public void typeReferenceResolvesGenericTypeVariablesAndJsonProperty() {
        ObjectNode schema = JsonSchemaGenerator.generateObject(new TypeReference<GenericEnvelope<Address>>() {});
        JsonNode props = schema.path("properties");

        assertTrue(props.has("payload_item"));
        assertEquals("object", props.path("payload_item").path("type").asText());
        assertTrue(props.path("payload_item").path("properties").has("city"));

        assertEquals("array", props.path("items").path("type").asText());
        assertEquals("object", props.path("items").path("items").path("type").asText());
        assertTrue(props.path("items").path("items").path("properties").has("zipCode"));
    }
}
