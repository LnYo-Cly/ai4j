package io.github.lnyocly.ai4j.harness;

import org.junit.Assert;
import org.junit.Test;

public class HarnessPromptsTest {

    @Test
    public void instructionsRequireGenericDurableExecutionDiscipline() {
        String instructions = HarnessPrompts.instructions();

        assertContains(instructions, "inspect the workspace, existing input files, state, and available tools");
        assertContains(instructions, "resume from the latest durable context");
        assertContains(instructions, "read the actual artifact back");
        assertContains(instructions, "required format, fields, constraints, and preservation requirements");
        assertContains(instructions, "repair it and verify again before reporting success");
        assertContains(instructions, "external input, approval, or asynchronous event genuinely blocks progress");
        assertContains(instructions, "never claim completion because a slice ended");
        assertContains(instructions, "a file was merely created");
        assertContains(instructions, "the response says it is done");
    }

    @Test
    public void instructionsRemainProviderAndWorkKindNeutral() {
        String instructions = HarnessPrompts.instructions().toLowerCase();

        Assert.assertFalse(instructions.contains("coding agent"));
        Assert.assertFalse(instructions.contains("harnessbench"));
        Assert.assertFalse(instructions.contains("benchmark"));
        Assert.assertFalse(instructions.contains("provider"));
        Assert.assertTrue(instructions.contains("real user work"));
    }

    @Test
    public void instructionsPreserveGovernanceBoundary() {
        String instructions = HarnessPrompts.instructions();

        assertContains(instructions, "Harness tools as persistence and governance rather than a substitute");
        assertContains(instructions, "A Task submission is not completion");
        assertContains(instructions, "harness_submission_request");
    }

    private void assertContains(String value, String expected) {
        Assert.assertTrue("Expected prompt to contain: " + expected, value.contains(expected));
    }
}
