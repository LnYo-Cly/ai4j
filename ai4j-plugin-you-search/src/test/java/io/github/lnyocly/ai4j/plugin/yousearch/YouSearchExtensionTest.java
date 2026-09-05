package io.github.lnyocly.ai4j.plugin.yousearch;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ServiceLoaderExtensionLoader;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class YouSearchExtensionTest {

    @Test
    public void manifestDeclaresYouSearchCapabilities() {
        ExtensionManifest manifest = new YouSearchExtension().manifest();

        Assert.assertEquals("you-search", manifest.getId());
        Assert.assertEquals("You.com Search", manifest.getName());
        Assert.assertEquals("you-search contributors", manifest.getVendor());
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.TOOL));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.COMMAND));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.SKILL));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.PROMPT));
        Assert.assertFalse(manifest.hasCapability(ExtensionCapability.GUARDRAIL));
        Assert.assertEquals("ai4j.extensions.you-search", manifest.getConfigPrefix());
        Assert.assertTrue(manifest.getPermissions().contains("network:you.com"));
    }

    @Test
    public void extensionContractIsValid() {
        ExtensionRegistry registry = ExtensionRegistry.of(new YouSearchExtension());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, "you-search");

        Assert.assertTrue(report.getIssues().toString(), report.isValid());
        Assert.assertEquals("pass", report.getStatus());
    }

    @Test
    public void toolSpecHasStructuralInputSchema() {
        String schema = YouSearchExtension.toolSpec().getInputSchema();

        Assert.assertTrue(schema.contains("\"type\":\"object\""));
        Assert.assertTrue(schema.contains("\"query\""));
        Assert.assertTrue(schema.contains("\"required\""));
    }

    @Test
    public void toolIsDisabledUntilHostEnablesIt() {
        ExtensionRegistry registry = ExtensionRegistry.of(new YouSearchExtension());

        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("you_web_search");

        Assert.assertNull("you_web_search must not be exposed before enable()/exposeTool()", executor);
    }

    @Test
    public void toolReturnsMissingApiKeyEnvelopeWithoutKey() throws Exception {
        String savedKey = System.getenv(YouSearchExtension.API_KEY_ENV);
        if (savedKey != null) {
            // a real key is present in this environment; skip the offline envelope check
            return;
        }
        ExtensionRegistry registry = ExtensionRegistry.of(new YouSearchExtension())
                .enable("you-search")
                .exposeTool("you_web_search");

        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("you_web_search");
        String result = executor.execute(new ExtensionToolCall("you_web_search",
                "{\"query\":\"java 25 release notes\"}"));

        Assert.assertTrue(result.contains("\"type\":\"you.web_search.error\""));
        Assert.assertTrue(result.contains("\"error\":\"missing_api_key\""));
        Assert.assertTrue(result.contains("YDC_API_KEY"));
    }

    @Test
    public void toolReturnsMissingQueryEnvelopeForEmptyArguments() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new YouSearchExtension())
                .enable("you-search")
                .exposeTool("you_web_search");

        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("you_web_search");
        String result = executor.execute(new ExtensionToolCall("you_web_search", "{}"));

        Assert.assertTrue(result.contains("\"type\":\"you.web_search.error\""));
        Assert.assertTrue(result.contains("\"error\":\"missing_query\""));
    }

    @Test
    public void malformedToolArgumentsFallBackToMissingQuery() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new YouSearchExtension())
                .enable("you-search")
                .exposeTool("you_web_search");

        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("you_web_search");
        String result = executor.execute(new ExtensionToolCall("you_web_search", "{bad\njson"));

        Assert.assertTrue(result.contains("\"type\":\"you.web_search.error\""));
        Assert.assertTrue(result.contains("\"error\":\"missing_query\""));
    }

    @Test
    public void searchResponseEnvelopeEscapesResultFields() {
        java.util.List<java.util.Map<String, String>> hits =
                new java.util.ArrayList<java.util.Map<String, String>>();
        java.util.Map<String, String> hit = new java.util.LinkedHashMap<String, String>();
        hit.put("title", "Java \"25\" notes");
        hit.put("url", "https://example.com/a?b=1&c=2");
        hit.put("snippet", "line1\nline2");
        hits.add(hit);

        String response = YouSearchPayloads.searchResponse("what's new", 1, hits);

        Assert.assertTrue(response.contains("\"type\":\"you.web_search.response\""));
        Assert.assertTrue(response.contains("Java \\\"25\\\" notes"));
        Assert.assertTrue(response.contains("https://example.com/a?b=1&c=2"));
        Assert.assertTrue(response.contains("line1\\nline2"));
    }

    @Test
    public void youApiResultExtractionReadsStandardShapedResponses() {
        String apiResponse = "{\"results\":["
                + "{\"title\":\"Result A\",\"url\":\"https://a.example.com\",\"description\":\"First snippet\"},"
                + "{\"title\":\"Result B\",\"url\":\"https://b.example.com\",\"description\":\"Second snippet\"}"
                + "]}";

        List<java.util.Map<String, String>> hits = YouSearchClient.YouApiResults.extractHits(apiResponse);

        Assert.assertEquals(2, hits.size());
        Assert.assertEquals("Result A", hits.get(0).get("title"));
        Assert.assertEquals("https://a.example.com", hits.get(0).get("url"));
        Assert.assertEquals("Second snippet", hits.get(1).get("snippet"));
    }

    @Test
    public void youApiResultExtractionToleratesNestedAndMissingArrays() {
        String nested = "{\"news\":[{\"thumbnail_url\":\"https://img\",\"title\":\"N\",\"url\":\"https://n.example.com\",\"description\":\"d\"}]}";
        List<java.util.Map<String, String>> hits = YouSearchClient.YouApiResults.extractHits(nested);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("N", hits.get(0).get("title"));

        List<java.util.Map<String, String>> empty = YouSearchClient.YouApiResults.extractHits("{\"error\":\"none\"}");
        Assert.assertTrue(empty.isEmpty());
    }

    @Test
    public void serviceLoaderDiscoversYouSearchExtension() {
        ServiceLoaderExtensionLoader loader = new ServiceLoaderExtensionLoader(YouSearchExtension.class.getClassLoader());

        List<Ai4jExtension> extensions = loader.load();

        boolean found = false;
        for (Ai4jExtension extension : extensions) {
            if ("you-search".equals(extension.manifest().getId())) {
                found = true;
            }
        }
        Assert.assertTrue("you-search extension should be discoverable by ServiceLoader", found);
    }
}
