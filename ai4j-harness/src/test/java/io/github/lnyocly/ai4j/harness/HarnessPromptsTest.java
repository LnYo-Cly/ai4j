package io.github.lnyocly.ai4j.harness;

import org.junit.Assert;
import org.junit.Test;

public class HarnessPromptsTest {

    @Test
    public void instructionsRequireGenericDurableExecutionDiscipline() {
        String instructions = HarnessPrompts.instructions();

        assertContains(instructions, "inspect the workspace, existing input files, state, and available tools");
        assertContains(instructions, "Treat every path or file named by the current task or input as an authorized workspace pointer");
        assertContains(instructions, "use the configured read, list, or state tools to inspect it before asking");
        assertContains(instructions, "A path alone is not a reason to ask the user to paste a file");
        assertContains(instructions, "only after a tool reports that the item is missing, inaccessible, or unreadable");
        assertContains(instructions, "resume from the latest durable context");
        assertContains(instructions, "read the actual artifact back");
        assertContains(instructions, "required format, fields, constraints, and preservation requirements");
        assertContains(instructions, "repair it and verify again before reporting success");
        assertContains(instructions, "record meaningful checkpoints and recovery points");
        assertContains(instructions, "request a durable wait only when an external input, approval, or asynchronous event genuinely blocks progress");
        Assert.assertFalse(instructions.contains("for checkpoints or waits only when"));
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

    @Test
    public void resumedInstructionsRequireToolFirstRecovery() {
        String instructions = HarnessPrompts.resumedInstructions();

        assertContains(instructions, "This is a resumed Harness execution");
        assertContains(instructions, "Continue from the restored session, durable artifacts, prior tool results, and the current input");
        assertContains(instructions, "Before asking for any named file or path, call the configured workspace inspection tool");
        assertContains(instructions, "Do not ask for pasted contents merely because the input supplied a path");
        assertContains(instructions, "missing, permission denied, or unreadable");
        assertContains(instructions, "read the real file back and repair any format or constraint failure");
    }

    @Test
    public void resumedInstructionsRemainProviderAndWorkKindNeutral() {
        String instructions = HarnessPrompts.resumedInstructions().toLowerCase();

        Assert.assertFalse(instructions.contains("coding agent"));
        Assert.assertFalse(instructions.contains("harnessbench"));
        Assert.assertFalse(instructions.contains("benchmark"));
        Assert.assertFalse(instructions.contains("provider"));
    }

    private void assertContains(String value, String expected) {
        Assert.assertTrue("Expected prompt to contain: " + expected, value.contains(expected));
    }
}
