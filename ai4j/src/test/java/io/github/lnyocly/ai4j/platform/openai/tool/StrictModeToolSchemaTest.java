package io.github.lnyocly.ai4j.platform.openai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 严格模式（strict mode）对 schema 形态有硬性要求，OpenAI 官方建议开启。
 * 这些测试钉住 SDK 生成的 schema 是否满足这些要求，避免"开了 strict 却被 API 拒绝"。
 */
public class StrictModeToolSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void enforceStrictSchema_setsAdditionalPropertiesFalseAndAllRequired() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<String, Tool.Function.Property>();
        props.put("city", new Tool.Function.Property("string", "city", null, null));
        props.put("days", new Tool.Function.Property("integer", "days", null, null));

        Tool.Function.Parameter param = new Tool.Function.Parameter("object", props,
                java.util.Collections.singletonList("city"));

        param.enforceStrictSchema();

        Assert.assertEquals(Boolean.FALSE, param.getAdditionalProperties());
        Assert.assertEquals("所有字段都应进 required",
                2, param.getRequired().size());
        Assert.assertTrue(param.getRequired().contains("city"));
        Assert.assertTrue(param.getRequired().contains("days"));
    }

    @Test
    public void enforceStrictSchema_marksOptionalFieldsAsNullable() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<String, Tool.Function.Property>();
        props.put("city", new Tool.Function.Property("string", null, null, null));
        props.put("note", new Tool.Function.Property("string", null, null, null));

        Tool.Function.Parameter param = new Tool.Function.Parameter("object", props,
                java.util.Collections.singletonList("city"));

        param.enforceStrictSchema();

        Assert.assertFalse("原本必填的字段不变", props.get("city").isNullable());
        Assert.assertTrue("原本可选的字段标为可空", props.get("note").isNullable());
    }

    @Test
    public void nullableProperty_serializesAsTypeArray() throws Exception {
        Tool.Function.Property prop = new Tool.Function.Property("string", null, null, null);
        prop.setNullable(true);

        String json = mapper.writeValueAsString(prop);

        JsonNode node = mapper.readTree(json);
        Assert.assertTrue("可空类型应序列化为 [\"string\",\"null\"]",
                node.get("type").isArray());
        Assert.assertEquals("string", node.get("type").get(0).asText());
        Assert.assertEquals("null", node.get("type").get(1).asText());
    }

    @Test
    public void nonNullableProperty_serializesAsPlainString() throws Exception {
        Tool.Function.Property prop = new Tool.Function.Property("string", null, null, null);

        String json = mapper.writeValueAsString(prop);

        JsonNode node = mapper.readTree(json);
        Assert.assertEquals("string", node.get("type").asText());
    }

    /**
     * 回环：序列化后能反序列化还原，包括可空字段。
     */
    @Test
    public void nullableProperty_roundTripsThroughDeserialization() throws Exception {
        Tool.Function.Property prop = new Tool.Function.Property("integer", null, null, null);
        prop.setNullable(true);

        String json = mapper.writeValueAsString(prop);
        Tool.Function.Property back = mapper.readValue(json, Tool.Function.Property.class);

        Assert.assertEquals("integer", back.getType());
        Assert.assertTrue(back.isNullable());
    }

    @Test
    public void functionStrictFlag_isOmittedWhenNullAndPresentWhenTrue() throws Exception {
        Tool.Function without = new Tool.Function();
        without.setName("f");
        Assert.assertFalse(mapper.writeValueAsString(without).contains("\"strict\""));

        Tool.Function with = new Tool.Function();
        with.setName("f");
        with.setStrict(Boolean.TRUE);
        Assert.assertTrue(mapper.writeValueAsString(with).contains("\"strict\":true"));
    }
}
