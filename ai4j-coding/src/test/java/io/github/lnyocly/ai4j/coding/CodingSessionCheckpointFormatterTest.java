package io.github.lnyocly.ai4j.coding;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodingSessionCheckpointFormatterTest {

    @Test
    public void shouldIgnoreLeadingProseBeforeMarkdownSections() {
        String summary = "Please let me know how you would like to proceed!\n"
                + "\n"
                + "## Goal\n"
                + "Fix auto compact.\n"
                + "## Constraints & Preferences\n"
                + "- Preserve session state.\n"
                + "## Progress\n"
                + "### Done\n"
                + "- [x] Reproduced the issue.\n"
                + "### In Progress\n"
                + "- [ ] Patch the parser.\n"
                + "### Blocked\n"
                + "- (none)\n";

        CodingSessionCheckpoint checkpoint = CodingSessionCheckpointFormatter.parse(summary);

        assertEquals("Fix auto compact.", checkpoint.getGoal());
        assertTrue(checkpoint.getConstraints().contains("Preserve session state."));
        assertTrue(checkpoint.getDoneItems().contains("Reproduced the issue."));
        assertTrue(checkpoint.getInProgressItems().contains("Patch the parser."));
    }

    @Test
    public void shouldParseStructuredJsonCheckpoint() {
        String summary = "{\n"
                + "  \"goal\": \"Resume the coding task.\",\n"
                + "  \"constraints\": [\"Keep exact file paths.\"],\n"
                + "  \"progress\": {\n"
                + "    \"done\": [\"Updated CodingSession.java.\"],\n"
                + "    \"inProgress\": [\"Finish CodingSessionCompactor.java.\"],\n"
                + "    \"blocked\": [\"Need compile validation.\"]\n"
                + "  },\n"
                + "  \"keyDecisions\": [\"Use CodingSessionCheckpoint as the canonical state.\"],\n"
                + "  \"nextSteps\": [\"Run mvn -pl ai4j-coding -am -DskipTests compile.\"],\n"
                + "  \"criticalContext\": [\"Markdown parsing remains compatibility-only.\"]\n"
                + "}";

        CodingSessionCheckpoint checkpoint = CodingSessionCheckpointFormatter.parse(summary);

        assertEquals("Resume the coding task.", checkpoint.getGoal());
        assertTrue(checkpoint.getConstraints().contains("Keep exact file paths."));
        assertTrue(checkpoint.getDoneItems().contains("Updated CodingSession.java."));
        assertTrue(checkpoint.getInProgressItems().contains("Finish CodingSessionCompactor.java."));
        assertTrue(checkpoint.getBlockedItems().contains("Need compile validation."));
        assertTrue(checkpoint.getKeyDecisions().contains("Use CodingSessionCheckpoint as the canonical state."));
        assertTrue(checkpoint.getNextSteps().contains("Run mvn -pl ai4j-coding -am -DskipTests compile."));
        assertTrue(checkpoint.getCriticalContext().contains("Markdown parsing remains compatibility-only."));
    }
}
