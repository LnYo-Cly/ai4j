package io.github.lnyocly.ai4j.platform.openai.response;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.tool.ResponseRequestToolResolver;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ResponseRequestToolResolverTest {

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
        Tool tool = (Tool) resolved.getTools().get(0);
        assertEquals("function", tool.getType());
        assertEquals("responses_test_weather", tool.getFunction().getName());
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
        assertTrue(resolved.getTools().get(0) instanceof Map);
        assertTrue(resolved.getTools().get(1) instanceof Tool);
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

