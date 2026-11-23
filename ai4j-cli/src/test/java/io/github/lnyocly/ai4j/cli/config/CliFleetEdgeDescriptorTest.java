package io.github.lnyocly.ai4j.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CliFleetEdgeDescriptorTest {

    @Test
    public void normalizesFieldsAndEvaluatesCapabilitiesAndAffinity() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put(" region ", " us-east ");
        labels.put("", "ignored");

        CliFleetEdgeDescriptor descriptor = CliFleetEdgeDescriptor.builder()
                .enabled(Boolean.TRUE)
                .nodeId("  edge-gpu-01 ")
                .centerUrl(" https://center.example.com ")
                .tags(Arrays.asList(" gpu ", "colab", "gpu", " "))
                .capabilities(Arrays.asList("python", "sandbox", "python"))
                .allowedAgents(Arrays.asList("researcher", "coder"))
                .allowedScopeKeys(Arrays.asList("team-alpha", ""))
                .maxConcurrentExecutions(Integer.valueOf(4))
                .labels(labels)
                .build();

        Assert.assertTrue(descriptor.isActive());
        Assert.assertEquals("edge-gpu-01", descriptor.getNodeId());
        Assert.assertEquals("https://center.example.com", descriptor.getCenterUrl());
        Assert.assertEquals(Arrays.asList("gpu", "colab"), descriptor.getTags());
        Assert.assertEquals(Arrays.asList("python", "sandbox"), descriptor.getCapabilities());
        Assert.assertEquals(Arrays.asList("researcher", "coder"), descriptor.getAllowedAgents());
        Assert.assertEquals(Collections.singletonList("team-alpha"), descriptor.getAllowedScopeKeys());
        Assert.assertEquals(Integer.valueOf(4), descriptor.getMaxConcurrentExecutions());
        Assert.assertEquals("us-east", descriptor.getLabels().get("region"));

        Assert.assertTrue(descriptor.hasTag("GPU"));
        Assert.assertFalse(descriptor.hasTag("cpu"));
        Assert.assertTrue(descriptor.hasCapability("PYTHON"));
        Assert.assertFalse(descriptor.hasCapability("docker"));
        Assert.assertTrue(descriptor.supportsAgent("Researcher"));
        Assert.assertFalse(descriptor.supportsAgent("reviewer"));
        Assert.assertTrue(descriptor.supportsScopeKey("TEAM-ALPHA"));
        Assert.assertFalse(descriptor.supportsScopeKey("team-beta"));

        Assert.assertTrue(descriptor.matchesAffinity(
                Arrays.asList("python", "sandbox"),
                Collections.singletonList("colab"),
                "coder",
                "team-alpha"
        ));
        Assert.assertFalse(descriptor.matchesAffinity(
                Collections.singletonList("docker"),
                Collections.singletonList("colab"),
                "coder",
                "team-alpha"
        ));
    }

    @Test
    public void mergesOverrideAndRoundTripsWithWorkspaceConfig() throws Exception {
        CliFleetEdgeDescriptor base = CliFleetEdgeDescriptor.builder()
                .enabled(Boolean.TRUE)
                .nodeId("edge-1")
                .centerUrl("https://center-a")
                .tags(Arrays.asList("linux", "cpu"))
                .capabilities(Collections.singletonList("bash"))
                .labels(Collections.singletonMap("zone", "a"))
                .build();
        CliFleetEdgeDescriptor override = CliFleetEdgeDescriptor.builder()
                .centerUrl("https://center-b")
                .capabilities(Arrays.asList("bash", "python"))
                .maxConcurrentExecutions(Integer.valueOf(0))
                .labels(Collections.singletonMap("tier", "edge"))
                .build();

        CliFleetEdgeDescriptor merged = base.mergeWith(override);
        Assert.assertTrue(merged.isActive());
        Assert.assertEquals("edge-1", merged.getNodeId());
        Assert.assertEquals("https://center-b", merged.getCenterUrl());
        Assert.assertEquals(Arrays.asList("linux", "cpu"), merged.getTags());
        Assert.assertEquals(Arrays.asList("bash", "python"), merged.getCapabilities());
        Assert.assertNull(merged.getMaxConcurrentExecutions());
        Assert.assertEquals("a", merged.getLabels().get("zone"));
        Assert.assertEquals("edge", merged.getLabels().get("tier"));
        Assert.assertTrue(merged.supportsAgent("any-agent"));
        Assert.assertTrue(merged.supportsScopeKey("any-scope"));

        CliWorkspaceConfig workspaceConfig = new CliWorkspaceConfig();
        workspaceConfig.setFleetEdge(merged);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(workspaceConfig);
        CliWorkspaceConfig restored = mapper.readValue(json, CliWorkspaceConfig.class);
        Assert.assertNotNull(restored.getFleetEdge());
        Assert.assertEquals("edge-1", restored.getFleetEdge().getNodeId());
        Assert.assertEquals(Arrays.asList("bash", "python"), restored.getFleetEdge().getCapabilities());
        Assert.assertFalse(CliFleetEdgeDescriptor.builder().enabled(Boolean.TRUE).nodeId(" ").build().isActive());
    }
}
