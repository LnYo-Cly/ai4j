package io.github.lnyocly.ai4j.tool;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.function.Function;

/**
 * 验证 {@code @FunctionCall(strict = true)} 在两条主线上都产出合规的 schema：
 *
 * <ul>
 *   <li>Chat 主线（直接走 {@code Tool}）：{@code strict=true} + schema 满足约束</li>
 *   <li>Responses 主线（经 {@link ResponseRequestToolResolver} 投影）：
 *       {@code strict} 和扁平字段一起进入 payload</li>
 * </ul>
 */
public class StrictModeRegistrationTest {

    @FunctionCall(name = "strict_test_lookup", description = "lookup", strict = true)
    public static class StrictLookup implements Function<StrictLookup.Request, String> {
        @Override
        public String apply(Request request) {
            return "{}";
        }

        @io.github.lnyocly.ai4j.annotation.FunctionRequest
        public static class Request {
            @FunctionParameter(description = "id", required = true)
            private String id;
            @FunctionParameter(description = "optional note", required = false)
            private String note;
        }
    }

    @Test
    public void strictRegistrationProducesCompliantChatSchema() {
        Tool.Function fn = ToolUtil.getFunctionEntity("strict_test_lookup");

        Assert.assertNotNull(fn);
        Assert.assertEquals("strict 应被开启", Boolean.TRUE, fn.getStrict());

        Tool.Function.Parameter params = fn.getParameters();
        Assert.assertEquals("additionalProperties 应为 false",
                Boolean.FALSE, params.getAdditionalProperties());
        Assert.assertTrue("必填字段应在 required",
                params.getRequired().contains("id"));
        Assert.assertTrue("可选字段也应进 required",
                params.getRequired().contains("note"));

        Map<String, Tool.Function.Property> props = params.getProperties();
        Assert.assertFalse("必填字段不可空", props.get("id").isNullable());
        Assert.assertTrue("可选字段应标为可空", props.get("note").isNullable());
    }

    @Test
    public void responsesProjectionCarriesStrictFlag() {
        ResponseRequest request = ResponseRequest.builder()
                .model("gpt-4o-mini")
                .input("hi")
                .functions("strict_test_lookup")
                .build();

        ResponseRequest resolved = ResponseRequestToolResolver.resolve(request);

        Assert.assertNotNull(resolved.getTools());
        Assert.assertEquals(1, resolved.getTools().size());

        Object tool = resolved.getTools().get(0);
        Assert.assertTrue(tool instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> flat = (Map<String, Object>) tool;

        Assert.assertEquals("function", flat.get("type"));
        Assert.assertEquals("strict_test_lookup", flat.get("name"));
        Assert.assertEquals("strict 应随扁平结构一起进入 Responses payload",
                Boolean.TRUE, flat.get("strict"));
    }
}
