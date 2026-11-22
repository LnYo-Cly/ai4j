package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Stream;

public class ReplayModelClientTest {

    @Test
    public void parsesFixtureAndReplaysResponsesInOrder() {
        ReplayModelClient client = loadWeatherFixture();

        Assert.assertEquals("deepseek-weather-toolcall", client.getFixture().getName());
        Assert.assertEquals("deepseek", client.getFixture().getRecordedFrom());
        Assert.assertEquals(2, client.getFixture().getExchanges().size());

        AgentPrompt firstPrompt = prompt("weather in shanghai?");
        firstPrompt.setTools(Collections.<Object>singletonList(
                Collections.singletonMap("name", "get_weather")));
        AgentModelResult first = client.create(firstPrompt);
        Assert.assertNotNull(first.getToolCalls());
        Assert.assertEquals("get_weather", first.getToolCalls().get(0).getName());
        Assert.assertEquals("call_weather_1", first.getToolCalls().get(0).getCallId());
        Assert.assertEquals(Long.valueOf(120L), first.getInputTokens());

        AgentModelResult second = client.create(prompt("tool result: sunny"));
        Assert.assertEquals("It is sunny in Shanghai, around 26C.", second.getOutputText());
        Assert.assertEquals(2, client.getInvocationCount());
    }

    @Test
    public void promptPinMismatchFailsAtTheInvocation() {
        ReplayModelClient client = loadWeatherFixture();
        try {
            client.create(prompt("nothing about the pinned city here"));
            Assert.fail("expected prompt pin mismatch to fail");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("deepseek-weather-toolcall"));
            Assert.assertTrue(e.getMessage().contains("shanghai"));
        }
    }

    @Test
    public void malformedFixtureFailsFastWithReason() {
        try {
            ReplayModelClient.parse("{\"name\":\"empty\"}");
            Assert.fail("expected missing exchanges to fail");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("exchanges"));
        }
    }

    @Test
    public void handlesNullOptionalFieldsWhenParsingFixture() {
        String json = "{"
                + "\"name\":\"null-fields\","
                + "\"recordedFrom\":null,"
                + "\"exchanges\":[{"
                + "\"expectedPromptContains\":[null,\"\",\"ping\"],"
                + "\"response\":{"
                + "\"outputText\":\"pong\","
                + "\"reasoningText\":null,"
                + "\"inputTokens\":10,"
                + "\"outputTokens\":null,"
                + "\"totalTokens\":null"
                + "}"
                + "}]"
                + "}";
        ReplayModelClient client = ReplayModelClient.parse(json);
        Assert.assertNull(client.getFixture().getRecordedFrom());
        Assert.assertEquals(Collections.singletonList("ping"),
                client.getFixture().getExchanges().get(0).getExpectedPromptContains());

        AgentModelResult result = client.create(prompt("ping"));
        Assert.assertEquals("pong", result.getOutputText());
        Assert.assertNull(result.getReasoningText());
        Assert.assertEquals(Long.valueOf(10L), result.getInputTokens());
        Assert.assertNull(result.getOutputTokens());
        Assert.assertNull(result.getTotalTokens());
    }

    @Test
    public void noFastjson2ReferencesRemainInTestingClasses() throws IOException, URISyntaxException {
        Path classesDir = resolveTestingClassesDir();
        Assert.assertTrue("Expected compiled classes directory: " + classesDir, Files.isDirectory(classesDir));

        final int[] classFileCount = new int[]{0};
        try (Stream<Path> walk = Files.walk(classesDir)) {
            walk.filter(new java.util.function.Predicate<Path>() {
                @Override
                public boolean test(Path path) {
                    return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class");
                }
            }).forEach(new java.util.function.Consumer<Path>() {
                @Override
                public void accept(Path classFile) {
                    classFileCount[0]++;
                    try {
                        byte[] bytes = Files.readAllBytes(classFile);
                        String content = new String(bytes, StandardCharsets.ISO_8859_1);
                        Assert.assertFalse(
                                "Fastjson2 reference remained in " + classFile,
                                content.contains("com/alibaba/fastjson2/") || content.contains("com.alibaba.fastjson2."));
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read class file " + classFile, e);
                    }
                }
            });
        }
        Assert.assertTrue("Expected compiled .class files in " + classesDir, classFileCount[0] > 0);
    }

    private static Path resolveTestingClassesDir() throws URISyntaxException {
        URL location = ModelFixture.class.getProtectionDomain().getCodeSource().getLocation();
        if (location != null) {
            Path path = Paths.get(location.toURI());
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        Path direct = Paths.get("target", "classes");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Paths.get("ai4j-testing", "target", "classes");
    }

    private static ReplayModelClient loadWeatherFixture() {
        try {
            InputStream in = ReplayModelClientTest.class
                    .getResourceAsStream("/fixtures/deepseek-weather-toolcall.json");
            Assert.assertNotNull("fixture resource must exist", in);
            return ReplayModelClient.load(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AgentPrompt prompt(String userText) {
        AgentPrompt prompt = new AgentPrompt();
        prompt.setItems(Collections.<Object>singletonList(AgentInputItem.userMessage(userText)));
        return prompt;
    }
}
