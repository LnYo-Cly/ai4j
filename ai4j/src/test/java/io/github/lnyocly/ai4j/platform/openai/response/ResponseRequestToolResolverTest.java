package io.github.lnyocly.ai4j.platform.openai.response;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.tool.ResponseRequestToolResolver;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ResponseRequestToolResolverTest {

    /**
     * Responses declares function tools flat ({@code type} / {@code name} /
     * {@code description} / {@code parameters} at the top level), unlike Chat
     * Completions which nests them under {@code function}. The resolver shares
     * Chat's tool registry, so it must project rather than pass through.
     *
     * <p>Asserted on the resulting map, not on the Java type: an earlier version
     * of this test checked {@code instanceof Tool}, which held while the
     * serialized payload still carried the Chat-shaped nesting.
     */
    @Test
    public void shouldResolveAnnotatedFunctionsIntoResponsesTools() {
        ResponseRequest request = ResponseRequest.builder()
                .model("test-model")
                .input("hello")
                .functions("responses_test_weather")
                .build();

        ResponseRequest resolved = ResponseRequestToolResolver.resolve(request);

        assertNotNull(resolved.getTools());
        assertEquals(1, resolved.getTools().size());

        Object tool = resolved.getTools().get(0);
        assertTrue("Responses 工具应为扁平结构，实际类型: " + tool.getClass(), tool instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> flat = (Map<String, Object>) tool;
        assertEquals("function", flat.get("type"));
        assertEquals("responses_test_weather", flat.get("name"));
        assertNotNull("parameters 应在顶层", flat.get("parameters"));
        assertFalse("不应保留 Chat Completions 的 function 嵌套", flat.containsKey("function"));
    }

    @Test
    public void shouldMergeManualToolsWithResolvedFunctions() {
        Map<String, Object> manualTool = new LinkedHashMap<String, Object>();
        manualTool.put("type", "web_search_preview");

        ResponseRequest request = ResponseRequest.builder()
                .model("test-model")
                .input("hello")
                .tools(Collections.<Object>singletonList(manualTool))
                .functions("responses_test_weather")
                .build();

        ResponseRequest resolved = ResponseRequestToolResolver.resolve(request);

        assertNotNull(resolved.getTools());
        assertEquals(2, resolved.getTools().size());

        // 调用方手写的内置工具原样保留
        assertEquals("web_search_preview", ((Map<?, ?>) resolved.getTools().get(0)).get("type"));
        // 注册表解析出的函数工具已投影成 Responses 扁平结构
        assertEquals("responses_test_weather", ((Map<?, ?>) resolved.getTools().get(1)).get("name"));
    }

    @FunctionCall(name = "responses_test_weather", description = "test weather function for responses")
    public static class ResponsesTestWeatherFunction implements Function<ResponsesTestWeatherFunction.Request, String> {

        @Override
        public String apply(Request request) {
            return request.getLocation();
        }

        @FunctionRequest
        public static class Request {
            @FunctionParameter(description = "query location", required = true)
            private String location;

            public String getLocation() {
                return location;
            }

            public void setLocation(String location) {
                this.location = location;
            }
        }
    }
}

