package io.github.lnyocly.ai4j.convert;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests {@link JsonSchemaGenerator} — Java class → JSON Schema generation via reflection.
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

    // --- Tests ---

    @Test
    public void generatesBasicObjectSchema() {
        JSONObject schema = JsonSchemaGenerator.generateObject(Address.class);

        assertEquals("object", schema.getString("type"));
        assertEquals(false, schema.getBoolean("additionalProperties"));

        JSONObject props = schema.getJSONObject("properties");
        assertNotNull(props);
        assertNotNull(props.get("city"));
        assertNotNull(props.get("zipCode"));

        JSONArray required = schema.getJSONArray("required");
        assertTrue("city should be required", required.contains("city"));
        assertTrue("zipCode should be required", required.contains("zipCode"));
    }

    @Test
    public void mapsJavaTypesToJsonTypes() {
        JSONObject schema = JsonSchemaGenerator.generateObject(Person.class);
        JSONObject props = schema.getJSONObject("properties");

        assertEquals("string", props.getJSONObject("name").getString("type"));
        assertEquals("integer", props.getJSONObject("age").getString("type"));
        assertEquals("boolean", props.getJSONObject("active").getString("type"));
        assertEquals("number", props.getJSONObject("score").getString("type"));
        assertEquals("integer", props.getJSONObject("primitiveCount").getString("type"));
    }

    @Test
    public void enumGeneratesStringWithEnumValues() {
        JSONObject schema = JsonSchemaGenerator.generateObject(Person.class);
        JSONObject sentiment = schema.getJSONObject("properties").getJSONObject("sentiment");

        assertEquals("string", sentiment.getString("type"));
        JSONArray values = sentiment.getJSONArray("enum");
        assertNotNull(values);
        assertEquals(3, values.size());
        assertTrue(values.contains("POSITIVE"));
        assertTrue(values.contains("NEGATIVE"));
        assertTrue(values.contains("NEUTRAL"));
    }

    @Test
    public void nestedObjectRecurses() {
        JSONObject schema = JsonSchemaGenerator.generateObject(Person.class);
        JSONObject address = schema.getJSONObject("properties").getJSONObject("address");

        assertEquals("object", address.getString("type"));
        assertNotNull(address.getJSONObject("properties").get("city"));
        assertNotNull(address.getJSONObject("properties").get("zipCode"));
        assertEquals(false, address.getBoolean("additionalProperties"));
    }

    @Test
    public void listGeneratesArrayWithItems() {
        JSONObject schema = JsonSchemaGenerator.generateObject(Person.class);
        JSONObject tags = schema.getJSONObject("properties").getJSONObject("tags");

        assertEquals("array", tags.getString("type"));
        assertEquals("string", tags.getJSONObject("items").getString("type"));
    }

    @Test
    public void allFieldsRequired() {
        JSONObject schema = JsonSchemaGenerator.generateObject(Person.class);
        JSONArray required = schema.getJSONArray("required");

        // Person has 8 fields — all should be required
        assertEquals(8, required.size());
        for (String field : new String[]{"name", "age", "active", "score", "sentiment", "address", "tags", "primitiveCount"}) {
            assertTrue(field + " should be required", required.contains(field));
        }
    }

    @Test
    public void responseFormatProducesCorrectStructure() {
        JSONObject rf = JsonSchemaGenerator.responseFormat(Address.class, "address_schema");

        assertEquals("json_schema", rf.getString("type"));

        JSONObject inner = rf.getJSONObject("json_schema");
        assertEquals("address_schema", inner.getString("name"));
        assertEquals(true, inner.getBoolean("strict"));
        assertNotNull(inner.getJSONObject("schema"));
        assertEquals("object", inner.getJSONObject("schema").getString("type"));
    }

    @Test
    public void generateReturnsString() {
        String json = JsonSchemaGenerator.generate(Address.class);
        JSONObject parsed = JSONObject.parseObject(json);

        assertEquals("object", parsed.getString("type"));
        assertEquals(false, parsed.getBoolean("additionalProperties"));
    }

    @Test
    public void listOListGeneratesArrayOfArrays() {
        // Edge case: List<List<String>>
        JSONObject schema = JsonSchemaGenerator.generateObject(ListOfLists.class);
        JSONObject matrix = schema.getJSONObject("properties").getJSONObject("matrix");

        assertEquals("array", matrix.getString("type"));
        JSONObject innerItems = matrix.getJSONObject("items");
        assertEquals("array", innerItems.getString("type"));
        assertEquals("string", innerItems.getJSONObject("items").getString("type"));
    }

    public static class ListOfLists {
        private List<List<String>> matrix;
    }

    @Test
    public void listOfNestedObjects() {
        JSONObject schema = JsonSchemaGenerator.generateObject(Team.class);
        JSONObject members = schema.getJSONObject("properties").getJSONObject("members");

        assertEquals("array", members.getString("type"));
        JSONObject memberItem = members.getJSONObject("items");
        assertEquals("object", memberItem.getString("type"));
        assertNotNull(memberItem.getJSONObject("properties").get("name"));
    }

    public static class Team {
        private String teamName;
        private List<Person> members;
    }

    @Test
    public void staticAndTransientFieldsExcluded() {
        JSONObject schema = JsonSchemaGenerator.generateObject(WithStatic.class);
        JSONObject props = schema.getJSONObject("properties");

        assertNotNull(props.get("normalField"));
        assertNull("static field should be excluded", props.get("staticField"));
        assertNull("transient field should be excluded", props.get("transientField"));
    }

    public static class WithStatic {
        private String normalField;
        private static String staticField = "ignore";
        private transient String transientField;
    }
}
